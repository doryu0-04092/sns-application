########################################
# S3 バケット(画像保存先)
########################################

resource "aws_s3_bucket" "images" {
  bucket = var.bucket_name

  # **中身があると destroy が失敗する。**
  #
  # 実際に踏んだ(2026-08-30)。CDがフロントエンドを配置した後に destroy したところ、
  # このバケットだけが削除できずに残った。S3は空でないバケットを削除できず、
  # Terraform も既定では中身を消しにいかない。
  #
  # **このプロジェクトは「使わない期間は destroy する」運用を前提にしている**
  # (docs/aws-architecture.md)。その前提と「中身があると消せない」は両立しないため、
  # ECRの force_delete と同じ割り切りを置く。
  #
  # **こちらは利用者の投稿画像が入る。** 静的配信バケットより重い判断になるが、
  # destroy はバケットごと消す操作であり、中身を残す選択肢は元々無い。
  # 「中身があるときだけ失敗する」状態は、消えないことの保証にはならず、
  # 後片付けが中途半端に終わるだけである。
  #
  # **本番では false にすること。** 消えては困るものが消える。
  force_destroy = true
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

# このバケットには性質の異なる2つのアクセス経路があり、許可の与え方も別になる。
#
# 1. ECSタスク(アップロードと署名付きURLの発行) — IAMプリンシパル。
#    権限はタスクロールが持つ(iam.tf の aws_iam_role.task)。同一アカウント内なので
#    IAM側の許可だけで到達でき、バケットポリシーは要らない。
#    かつてのIAMユーザー + アクセスキーはECSへの移行にあわせて廃止した。
#
# 2. CloudFront(画像の配信) — サービスプリンシパル。
#    こちらはIAMロールを持たないため、下のバケットポリシーで明示的に許可しないと
#    S3が AccessDenied を返す。静的サイト用バケット(frontend.tf)と同じ形。

data "aws_iam_policy_document" "images_oac" {
  statement {
    sid    = "AllowCloudFrontRead"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.images.arn}/*"]

    # この条件が無いと、OACを設定した他のディストリビューションからも読めてしまう。
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.main.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "images" {
  bucket = aws_s3_bucket.images.id
  policy = data.aws_iam_policy_document.images_oac.json
}
