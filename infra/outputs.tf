output "bucket_name" {
  description = "作成したS3バケット名。バックエンドの STORAGE_S3_BUCKET に設定する"
  value       = aws_s3_bucket.images.id
}

output "region" {
  description = "バケットのリージョン。バックエンドの STORAGE_S3_REGION に設定する"
  value       = var.region
}

output "access_key_id" {
  description = "バックエンド用IAMユーザーのアクセスキーID。AWS_ACCESS_KEY_ID に設定する"
  value       = aws_iam_access_key.app.id
}

# sensitive = true にしておくと apply のログや output 一覧に値が出ない。
# 取り出すときは `terraform output -raw secret_access_key` を使う。
output "secret_access_key" {
  description = "バックエンド用IAMユーザーのシークレットアクセスキー。AWS_SECRET_ACCESS_KEY に設定する"
  value       = aws_iam_access_key.app.secret
  sensitive   = true
}
