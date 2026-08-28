########################################
# IAM(ECSタスクの権限)
########################################

# アクセスキーを発行するIAMユーザーは廃止した。ECSタスクは一時的な認証情報を
# 自動で受け取るため、鍵の発行・保管・ローテーションそのものが不要になる。

data "aws_iam_policy_document" "ecs_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

########################################
# タスク実行ロール(ECSエージェントが使う)
########################################

# コンテナを起動するために必要な権限。アプリのコードからは使われない。
# ECRからのイメージ取得、CloudWatch Logsへの書き込み、SSMからの秘密取得。
resource "aws_iam_role" "task_execution" {
  name               = "${var.project}-task-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

resource "aws_iam_role_policy_attachment" "task_execution" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# マネージドポリシーにはSSMからの読み取りが含まれていないため、必要な分だけ足す。
# 対象はこのプロジェクトのパラメータに限定する。
data "aws_iam_policy_document" "task_execution_secrets" {
  statement {
    sid    = "ReadOwnParameters"
    effect = "Allow"

    actions = ["ssm:GetParameters"]

    resources = [
      aws_ssm_parameter.db_password.arn,
      aws_ssm_parameter.jwt_secret.arn,
      aws_ssm_parameter.cdn_private_key.arn,
    ]
  }

  # SecureStringの復号に必要。SSMの既定キー(alias/aws/ssm)経由でしか使えないよう
  # 条件で絞り、他のKMSキーには使えないようにする。
  statement {
    sid    = "DecryptSecureString"
    effect = "Allow"

    actions   = ["kms:Decrypt"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${var.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "task_execution_secrets" {
  name   = "read-parameters"
  role   = aws_iam_role.task_execution.id
  policy = data.aws_iam_policy_document.task_execution_secrets.json
}

########################################
# タスクロール(アプリのコードが使う)
########################################

resource "aws_iam_role" "task" {
  name               = "${var.project}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

# 最小権限。対象バケット配下のオブジェクト操作のみを許可し、
# バケットの作成・削除やほかのバケットへのアクセスは許可しない。
data "aws_iam_policy_document" "task_s3_access" {
  statement {
    sid    = "ObjectAccess"
    effect = "Allow"

    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
    ]

    resources = ["${aws_s3_bucket.images.arn}/*"]
  }
}

resource "aws_iam_role_policy" "task_s3_access" {
  name   = "s3-image-access"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task_s3_access.json
}
