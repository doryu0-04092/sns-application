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

  # /api/health はDBへの疎通も確認して200を返す(HealthController参照)。
  # 200が返る = アプリとDBの両方が生きている、という意味になる。
  #
  # 裏を返すと、RDSが一時的に不調になると全タスクがunhealthyと判定されて
  # 置き換えが走る。unhealthy_threshold を既定の2より緩い3にしているのはそのため。
  health_check {
    path                = "/api/health"
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
