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
#
# 表示(GET)はCloudFront経由に移ったが、アップロード(PUT)は引き続きブラウザから
# S3へ直接送るため、この設定は残す。許可オリジンにはCloudFrontのドメインを自動で加える。
resource "aws_s3_bucket_cors_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  cors_rule {
    allowed_methods = ["PUT", "GET"]
    allowed_origins = concat(
      var.allowed_origins,
      ["https://${aws_cloudfront_distribution.main.domain_name}"],
    )
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
# 画像バケットへのアクセス権
########################################

# かつてはIAMユーザー + アクセスキーで認証していたが、ECSへの移行にあわせて廃止した。
# 権限はECSのタスクロールが持つ(iam.tf の aws_iam_role.task)。
# タスクは一時的な認証情報を自動で受け取るため、鍵の発行・保管・ローテーションが不要になる。
#
# バケットポリシーは置かない。同一アカウント内であればIAM側の許可だけで到達でき、
# 許可の置き場所を1箇所に保てるため。
