########################################
# ALB / ターゲットグループ / リスナー
########################################

resource "aws_lb" "main" {
  name               = "${var.project}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  # 既定のまま60秒。アプリ側のHikariCP接続待ち(10秒)より十分長い。
  # ここを縮めると、上流が先に切ってアプリ側にエラーが残らないまま
  # 利用者だけがエラーを見る状態が起きうる。
  idle_timeout = 60
}

resource "aws_lb_target_group" "backend" {
  name        = "${var.project}-backend"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip" # Fargate(awsvpc)はENIのIPで登録される

  # **DBを見ない /api/livez を向ける。**
  #
  # 以前は /api/health(DB疎通込み)を向けていた。そのため
  # **RDSが一時的に不調になると全タスクが同時にunhealthyと判定され、
  # 一斉に置き換えが走る**構造だった。置き換えてもRDSは回復しないため、
  # 動いているタスクを失うぶん状況が悪化するだけである。
  #
  # タスクを入れ替えるべき理由になるのは「プロセスが生きていないこと」だけ。
  # /api/readyz はデプロイ時の投入判定と状況確認に使い、ここには向けない。
  # unhealthy_threshold を3にしているのは、単発の失敗で外さないためである。
  health_check {
    path                = "/api/livez"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  deregistration_delay = 30
}

# デフォルトは403。CloudFrontを経由しないアクセス(ALBのDNS名を直接叩く等)は
# ここで止まる。転送はこの下のリスナールールに一致した場合だけ行われる。
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "application/json"
      message_body = jsonencode({
        error = {
          code    = "FORBIDDEN"
          message = "This endpoint is only reachable through CloudFront."
        }
      })
      status_code = "403"
    }
  }
}

# CloudFrontが付与するシークレットヘッダーが一致したときだけ転送する。
#
# セキュリティグループのプレフィックスリスト制限だけでは「他人のCloudFront
# ディストリビューション」も同じIPレンジから来るため通ってしまう。
# 実際の防御線はこのヘッダー検証である。
resource "aws_lb_listener_rule" "from_cloudfront" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }

  condition {
    http_header {
      http_header_name = "X-Origin-Verify"
      values           = [random_password.origin_verify.result]
    }
  }
}
