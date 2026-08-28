########################################
# ECS / Fargate(バックエンド)
########################################

resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/${var.project}-backend"
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_cluster" "main" {
  name = var.project
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "${var.project}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    {
      name      = "backend"
      image     = "${aws_ecr_repository.backend.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]

      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "docker" },

        { name = "DB_HOST", value = aws_db_instance.main.address },
        { name = "DB_PORT", value = tostring(aws_db_instance.main.port) },
        { name = "DB_NAME", value = var.db_name },
        { name = "DB_USER", value = var.db_username },

        { name = "STORAGE_S3_BUCKET", value = aws_s3_bucket.images.id },
        { name = "STORAGE_S3_REGION", value = var.region },

        # 空文字を明示的に渡すこと。application.yml の既定はLocalStack向けの
        # http://localhost:4566 なので、渡さないとコンテナ内から到達できない
        # アドレスへ接続しに行って起動に失敗する。
        { name = "STORAGE_S3_ENDPOINT", value = "" },
        { name = "STORAGE_S3_PUBLIC_ENDPOINT", value = "" },

        # CloudFrontが唯一の窓口なのでフロントとAPIは同一オリジンになり、
        # 通常の経路ではCORSは発生しない。設定漏れで動かなくならないよう値は入れておく。
        { name = "CORS_ALLOWED_ORIGIN", value = "https://${aws_cloudfront_distribution.main.domain_name}" },

        # デプロイ先はHTTPSなのでSecure属性を付ける。
        { name = "COOKIE_SECURE", value = "true" },

        { name = "CDN_BASE_URL", value = "https://${aws_cloudfront_distribution.main.domain_name}" },
        { name = "CDN_KEY_PAIR_ID", value = aws_cloudfront_public_key.cdn.id },
        { name = "CDN_COOKIE_EXPIRY", value = var.cdn_cookie_expiry },
      ]

      # 値そのものはタスク定義に載らない。ECSエージェントが起動時にSSMから取得する。
      secrets = [
        { name = "DB_PASSWORD", valueFrom = aws_ssm_parameter.db_password.arn },
        { name = "JWT_SECRET", valueFrom = aws_ssm_parameter.jwt_secret.arn },
        { name = "CDN_PRIVATE_KEY", valueFrom = aws_ssm_parameter.cdn_private_key.arn },
      ]

      # アプリは構造化ログをJSON1行で標準出力に出す。収集先を知らない作りにしてあるため、
      # ここでCloudWatch Logsに向けるだけでよい(docs/tech-stack.md のログ設計を参照)。
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.backend.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "backend"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "backend" {
  name            = "${var.project}-backend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = aws_subnet.public[*].id
    security_groups = [aws_security_group.ecs_tasks.id]

    # NATゲートウェイを置かない代わりに、タスクにパブリックIPを付けてIGW経由で
    # ECR・SSMへ出る。インバウンドはSGでALBからの:8080だけに絞ってある。
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "backend"
    container_port   = 8080
  }

  # 起動に失敗し続けるデプロイを自動で打ち切り、直前の状態に戻す。
  # これが無いと、壊れたイメージを配ったときにタスクの起動失敗が延々と繰り返される。
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # ヘルスチェックの猶予。Spring Boot + Flywayの起動に時間がかかるため、
  # 起動しきる前にunhealthyと判定されて置き換えループに入らないようにする。
  health_check_grace_period_seconds = 120

  # リスナーより先にサービスを作るとターゲットグループの登録に失敗する。
  depends_on = [aws_lb_listener_rule.from_cloudfront]
}
