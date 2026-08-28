########################################
# CloudFront(利用者からの唯一の窓口)
########################################

# 1つのディストリビューションに3つのオリジンを束ね、パスで振り分ける。
# ブラウザから見て静的ファイル・API・画像がすべて同一オリジンになるため、
# CORSとクロスサイトCookieの問題が構造的に消える。

# S3オリジン用の署名。バケットを完全非公開に保ったまま、CloudFrontだけが読める。
# 静的用と画像用で同じ設定でよいので1つを共用する。
resource "aws_cloudfront_origin_access_control" "s3" {
  name                              = "${var.project}-s3"
  description                       = "静的サイトと画像のS3バケットへのアクセス"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

########################################
# 署名付きクッキーの検証に使う公開鍵
########################################

resource "aws_cloudfront_public_key" "cdn" {
  name        = "${var.project}-image-signing"
  comment     = "画像取得用の署名付きクッキーを検証する公開鍵"
  encoded_key = tls_private_key.cdn.public_key_pem
}

resource "aws_cloudfront_key_group" "cdn" {
  name    = "${var.project}-image-signing"
  comment = "画像配信ビヘイビアの trusted_key_groups に指定する"
  items   = [aws_cloudfront_public_key.cdn.id]
}

########################################
# CloudFront Functions
########################################

# SPAのフォールバック。
#
# カスタムエラーレスポンスを使わないのは、あれがディストリビューション全体に効くため。
# APIが返す403や404まで index.html に書き換えてしまい、JSONを期待している箇所に
# HTMLが返る。関数なら特定のビヘイビアにだけ紐づけられる。
resource "aws_cloudfront_function" "spa_fallback" {
  name    = "${var.project}-spa-fallback"
  runtime = "cloudfront-js-2.0"
  comment = "拡張子を持たないURIを /index.html に書き換える(SPAのディープリンク対応)"
  publish = true

  code = <<-JS
    function handler(event) {
      var request = event.request;
      var uri = request.uri;

      // 末尾がスラッシュ、または最後のセグメントにドットが無いものはSPAのルートとみなす。
      // /assets/index-a1b2c3.js のようなファイルはそのまま通す。
      var lastSegment = uri.substring(uri.lastIndexOf('/') + 1);
      if (lastSegment === '' || lastSegment.indexOf('.') === -1) {
        request.uri = '/index.html';
      }

      return request;
    }
  JS
}

# 画像のパスからCDN上のプレフィックスを取り除く。
#
# ブラウザは /images/posts/abc.jpg を要求するが、S3上のキーは posts/abc.jpg である。
# origin_path は「前に付ける」ものなので剥がすのには使えず、関数で書き換える。
#
# /images/* という専用の名前空間にしているのは、SPAが /posts/:postId という
# ルートを持っており、CDNのルート直下に画像を置くと衝突するため。
resource "aws_cloudfront_function" "strip_images_prefix" {
  name    = "${var.project}-strip-images-prefix"
  runtime = "cloudfront-js-2.0"
  comment = "/images/<key> を <key> に書き換えてS3へ渡す"
  publish = true

  code = <<-JS
    function handler(event) {
      var request = event.request;

      if (request.uri.indexOf('/images/') === 0) {
        request.uri = request.uri.substring('/images'.length);
      }

      return request;
    }
  JS
}

########################################
# ディストリビューション
########################################

# マネージドポリシー。IDを直書きせず名前で引くことで、意図が読める形にする。
data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_origin_request_policy" "all_viewer" {
  name = "Managed-AllViewer"
}

resource "aws_cloudfront_distribution" "main" {
  enabled             = true
  comment             = "${var.project} — 静的サイト / API / 画像の唯一の窓口"
  default_root_object = "index.html"

  # 価格クラス200は北米・欧州・アジア。日本からのアクセスを想定しつつ、
  # 全世界(All)より安い。
  price_class = "PriceClass_200"

  # --- オリジン1: 静的サイト ---
  origin {
    origin_id                = "static"
    domain_name              = aws_s3_bucket.static.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  # --- オリジン2: 画像 ---
  origin {
    origin_id                = "images"
    domain_name              = aws_s3_bucket.images.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  # --- オリジン3: API(ALB) ---
  origin {
    origin_id   = "api"
    domain_name = aws_lb.main.dns_name

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only" # 独自ドメインが無くALBに証明書を張れないため
      origin_ssl_protocols   = ["TLSv1.2"]
    }

    # ALBはこのヘッダーが一致したリクエストだけを転送する(alb.tf)。
    custom_header {
      name  = "X-Origin-Verify"
      value = random_password.origin_verify.result
    }
  }

  # --- 既定のビヘイビア: 静的サイト ---
  default_cache_behavior {
    target_origin_id       = "static"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    # Viteの出力はファイル名にハッシュが付くため長期キャッシュして安全。
    # index.html だけはデプロイ時に no-cache を付けて配置する。
    cache_policy_id = data.aws_cloudfront_cache_policy.caching_optimized.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_fallback.arn
    }
  }

  # --- /api/*: キャッシュしない ---
  #
  # 性能上の妥協ではなく安全性の判断。このAPIはCookieのJWTで認証しており、
  # レスポンスが閲覧者ごとに異なる(is_liked、フォロー中フィード、/api/auth/me)。
  # キャッシュすると、ある利用者向けのレスポンスが別の利用者に配られる事故になる。
  ordered_cache_behavior {
    path_pattern           = "/api/*"
    target_origin_id       = "api"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
  }

  # --- /images/*: 署名付きクッキーを要求しつつキャッシュする ---
  #
  # URLが /images/<key> で固定なのでキャッシュキーが安定し、エッジにもブラウザにも効く。
  # CachingOptimized はCookieをキャッシュキーに含めないため、利用者ごとに
  # キャッシュが分裂することもない。
  ordered_cache_behavior {
    path_pattern           = "/images/*"
    target_origin_id       = "images"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    cache_policy_id    = data.aws_cloudfront_cache_policy.caching_optimized.id
    trusted_key_groups = [aws_cloudfront_key_group.cdn.id]

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.strip_images_prefix.arn
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # 独自ドメインを使わないため、CloudFrontの既定証明書をそのまま使う。
  viewer_certificate {
    cloudfront_default_certificate = true
  }
}
