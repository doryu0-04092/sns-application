########################################
# 秘密情報の生成と保管
########################################

# Secrets Manager ではなく SSM Parameter Store の SecureString を使う。
# 学習用途では秘密の数が少なく自動ローテーションも不要なため、
# 月$0.40/シークレットを払う理由がない。

# RDSのマスターパスワード。
# special = false にしているのは、RDSがマスターパスワードに使えない文字
# ("/" '"' "@" 空白)があり、生成した値が弾かれるのを避けるため。
# 長さで強度を確保する。
resource "random_password" "db" {
  length  = 32
  special = false
}

resource "random_password" "jwt" {
  length  = 64
  special = false
}

# ALBがCloudFront経由のリクエストだけを通すための合言葉。
# 値はTerraformが生成し、CloudFrontのカスタムオリジンヘッダーと
# ALBのリスナールールの両方から参照される。
resource "random_password" "origin_verify" {
  length  = 48
  special = false
}

# CloudFrontの署名付きクッキーに使うRSA鍵ペア。CloudFrontはRSA 2048のみを受け付ける。
resource "tls_private_key" "cdn" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "aws_ssm_parameter" "db_password" {
  name        = "/${var.project}/db-password"
  description = "RDSのマスターパスワード。ECSタスクへ DB_PASSWORD として注入する"
  type        = "SecureString"
  value       = random_password.db.result
}

resource "aws_ssm_parameter" "jwt_secret" {
  name        = "/${var.project}/jwt-secret"
  description = "JWTの署名鍵。ECSタスクへ JWT_SECRET として注入する"
  type        = "SecureString"
  value       = random_password.jwt.result
}

# バックエンドはPKCS#8しか読めない(JavaのKeyFactoryがPKCS#1を直接扱えないため)。
# private_key_pem ではなく private_key_pem_pkcs8 を渡すこと。
resource "aws_ssm_parameter" "cdn_private_key" {
  name        = "/${var.project}/cdn-private-key"
  description = "CloudFront署名付きクッキー用の秘密鍵(PKCS#8)。ECSタスクへ CDN_PRIVATE_KEY として注入する"
  type        = "SecureString"
  value       = tls_private_key.cdn.private_key_pem_pkcs8
}
