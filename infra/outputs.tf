########################################
# デプロイ手順で使う値
########################################

output "cloudfront_domain" {
  description = "利用者がアクセスするドメイン。ブラウザでこのURLを開く"
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "cloudfront_distribution_id" {
  description = "キャッシュ無効化に使う。aws cloudfront create-invalidation --distribution-id"
  value       = aws_cloudfront_distribution.main.id
}

output "ecr_repository_url" {
  description = "バックエンドのイメージをpushする先。docker build -t <この値>:latest"
  value       = aws_ecr_repository.backend.repository_url
}

output "static_bucket_name" {
  description = "フロントエンドの成果物を置く先。aws s3 sync frontend/dist s3://<この値>/"
  value       = aws_s3_bucket.static.id
}

output "images_bucket_name" {
  description = "画像の保存先バケット名"
  value       = aws_s3_bucket.images.id
}

output "region" {
  description = "リソースを作成したリージョン"
  value       = var.region
}

########################################
# 障害調査で使う値
########################################

output "alb_dns_name" {
  description = <<-EOT
    ALBのDNS名。ここを直接叩くと403が返るのが正常
    (CloudFrontのシークレットヘッダーが無いため)。
  EOT
  value       = aws_lb.main.dns_name
}

output "rds_endpoint" {
  description = "RDSのエンドポイント。privateサブネットにあるためVPC外からは到達できない"
  value       = aws_db_instance.main.address
}

output "log_group_name" {
  description = "バックエンドのログ。aws logs tail <この値> --follow"
  value       = aws_cloudwatch_log_group.backend.name
}

########################################
# 注意
########################################

# DBパスワード・JWTシークレット・CloudFrontの秘密鍵はoutputに出さない。
# ECSタスクへはSSM Parameter Store経由で直接注入されるため、
# 人間が値を取り出して転記する必要がそもそも無い。
#
# 値を確認する必要が生じた場合は AWS コンソールか
# `aws ssm get-parameter --with-decryption` を使うこと(既定では許可していない)。
