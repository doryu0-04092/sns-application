########################################
# S3 バケット(画像保存先)
########################################

resource "aws_s3_bucket" "images" {
  bucket = var.bucket_name
}

# ACLを完全に無効化し、アクセス制御をバケットポリシーとIAMに一本化する。
# 現在のAWSの推奨設定。
resource "aws_s3_bucket_ownership_controls" "images" {
  bucket = aws_s3_bucket.images.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# バケットは完全非公開にする。画像はすべてPresigned URL経由で配信するため、
# パブリックアクセスを一切許可する必要がない。
resource "aws_s3_bucket_public_access_block" "images" {
  bucket = aws_s3_bucket.images.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# ブラウザから直接 presigned PUT でアップロードするため、CORSの許可が必須。
# これが無いとブラウザ側でプリフライトに失敗しアップロードできない。
resource "aws_s3_bucket_cors_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  cors_rule {
    allowed_methods = ["PUT", "GET"]
    allowed_origins = var.allowed_origins
    allowed_headers = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

# アップロードは一旦 pending/ に置き、投稿に紐づいた時点で posts/ や avatars/ へ移動する。
# 「S3に上げたが投稿を作成しなかった」孤児オブジェクトは pending/ に残るため、
# このルールで自動的に掃除される。
resource "aws_s3_bucket_lifecycle_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  rule {
    id     = "expire-pending-uploads"
    status = "Enabled"

    filter {
      prefix = "pending/"
    }

    expiration {
      days = var.orphan_expiration_days
    }
  }

  rule {
    id     = "abort-incomplete-multipart-uploads"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

########################################
# IAM(バックエンドがS3を操作するための認証情報)
########################################

# 現状はECS等に載せていないため、IAMユーザー + アクセスキーで認証する。
# ECSへ移行する際は、このユーザーを廃止してECSタスクロールに置き換えること
# (アクセスキーの発行・管理そのものが不要になる)。
resource "aws_iam_user" "app" {
  name = "${var.bucket_name}-app"
}

resource "aws_iam_access_key" "app" {
  user = aws_iam_user.app.name
}

# 最小権限。対象バケット配下のオブジェクト操作のみを許可し、
# バケットの作成・削除やほかのバケットへのアクセスは許可しない。
data "aws_iam_policy_document" "app_s3_access" {
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

resource "aws_iam_user_policy" "app_s3_access" {
  name   = "s3-image-access"
  user   = aws_iam_user.app.name
  policy = data.aws_iam_policy_document.app_s3_access.json
}
