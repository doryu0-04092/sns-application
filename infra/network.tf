########################################
# VPC / サブネット / ルーティング
########################################

# ALBもRDSのサブネットグループも2つ以上のAZを要求するため、2AZ分用意する。
# 冗長化が目的ではなくAWS側の制約を満たすためで、実際に動くタスクとDBは各1つである。
data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "main" {
  cidr_block = var.vpc_cidr

  # RDSのエンドポイント(*.rds.amazonaws.com)をVPC内から名前解決するために必要。
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${var.project}-vpc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${var.project}-igw" }
}

# ALBとECSタスクを置く。NATゲートウェイ(月$35前後の常時課金)を避けるため、
# タスクはここに置いてIGW経由でECR・SSMへ出る。
# タスクにパブリックIPは付くが、セキュリティグループでALBからの:8080しか受け付けない。
resource "aws_subnet" "public" {
  count = 2

  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone = data.aws_availability_zones.available.names[count.index]

  # サブネット既定でのパブリックIP付与はしない。ECSサービス側の
  # assign_public_ip で明示的に付ける方が、どのリソースが外に出るのか読み取りやすい。
  map_public_ip_on_launch = false

  tags = { Name = "${var.project}-public-${count.index}" }
}

# RDSだけを置く。ルートテーブルにIGWへの経路を持たせないことで、
# 「パブリックサブネットにタスクを置く」という妥協をアプリ層だけに閉じ込める。
resource "aws_subnet" "private" {
  count = 2

  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 10)
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = { Name = "${var.project}-private-${count.index}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${var.project}-public" }
}

# インターネットへの経路を持たないルートテーブル。VPC内部の通信だけが成立する。
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${var.project}-private" }
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  count = length(aws_subnet.private)

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

########################################
# セキュリティグループ
########################################

# CloudFrontのエッジが使う送信元IPレンジ。AWSがマネージドで更新するため、
# 自分でIPレンジを管理する必要がない。
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

# ALBはinternet-facingにせざるを得ない(デフォルトドメインのみの構成では
# CloudFrontのVPCオリジンや内部ALBが使えないため)。そのぶん二重に絞る。
#
# 1. ここでCloudFrontの送信元レンジに限定する
# 2. リスナールールでシークレットヘッダーの一致を要求する(alb.tf)
#
# 1だけでは「他人のCloudFrontディストリビューション」も同じレンジから来るため不十分で、
# 実際の防御線は2である。
resource "aws_security_group" "alb" {
  name        = "${var.project}-alb"
  description = "ALB: CloudFrontのエッジからのHTTPのみ受け付ける"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.project}-alb" }
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_cloudfront" {
  security_group_id = aws_security_group.alb.id
  description       = "CloudFrontのエッジからのHTTP"

  prefix_list_id = data.aws_ec2_managed_prefix_list.cloudfront.id
  from_port      = 80
  to_port        = 80
  ip_protocol    = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_tasks" {
  security_group_id = aws_security_group.alb.id
  description       = "ターゲットグループ(ECSタスク)へのヘルスチェックと転送"

  referenced_security_group_id = aws_security_group.ecs_tasks.id
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
}

# タスクにはパブリックIPが付くが、受け付けるのはALBのSGからの:8080だけなので
# インターネットからは到達できない。
resource "aws_security_group" "ecs_tasks" {
  name        = "${var.project}-ecs-tasks"
  description = "ECSタスク: ALBからの8080のみ受け付け、外部へは自由に出る"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.project}-ecs-tasks" }
}

resource "aws_vpc_security_group_ingress_rule" "tasks_from_alb" {
  security_group_id = aws_security_group.ecs_tasks.id
  description       = "ALBからのアプリケーションポート"

  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
}

# ECRからのイメージ取得、SSM Parameter Storeからの秘密取得、S3への署名付きURL発行のため。
# NATが無いぶん、この経路はIGW経由になる。
resource "aws_vpc_security_group_egress_rule" "tasks_to_internet" {
  security_group_id = aws_security_group.ecs_tasks.id
  description       = "ECR / SSM / S3 への通信(IGW経由)"

  cidr_ipv4   = "0.0.0.0/0"
  ip_protocol = "-1"
}

# RDSはECSタスクのSGからのみ到達できる。アウトバウンドのルールは作らない
# (DBから外部へ出て行く必要が無いため)。
resource "aws_security_group" "rds" {
  name        = "${var.project}-rds"
  description = "RDS: ECSタスクからの5432のみ受け付ける"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.project}-rds" }
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_tasks" {
  security_group_id = aws_security_group.rds.id
  description       = "ECSタスクからのPostgreSQL"

  referenced_security_group_id = aws_security_group.ecs_tasks.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}
