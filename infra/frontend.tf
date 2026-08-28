########################################
# S3バケット(フロントエンドの静的ファイル)
########################################

# 画像用とは別のバケットにする。用途が違えばライフサイクルもCORSも変わるうえ、
# 「静的サイトを消したい」ときに投稿画像まで巻き込まないようにするため。
resource "aws_s3_bucket" "static" {
  bucket = var.static_bucket_name
}

resource "aws_s3_bucket_ownership_controls" "static" {
  bucket = aws_s3_bucket.static.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# S3の静的ウェブサイトホスティングは使わない。バケットは完全非公開のままにし、
# CloudFrontのOAC(オリジンアクセスコントロール)経由でのみ読めるようにする。
resource "aws_s3_bucket_public_access_block" "static" {
  bucket = aws_s3_bucket.static.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "static" {
  bucket = aws_s3_bucket.static.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# このディストリビューションからのGETだけを許可する。
# SourceArn の条件が無いと、OACを設定した他のディストリビューションからも読めてしまう。
data "aws_iam_policy_document" "static_oac" {
  statement {
    sid    = "AllowCloudFrontRead"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.static.arn}/*"]

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.main.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "static" {
  bucket = aws_s3_bucket.static.id
  policy = data.aws_iam_policy_document.static_oac.json
}
