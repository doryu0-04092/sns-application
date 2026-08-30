#!/bin/bash
# LocalStack起動時に実行され、開発用のS3バケットを作成する。
# (LocalStackは /etc/localstack/init/ready.d/ 配下のスクリプトを起動完了時に自動実行する)
set -e

BUCKET="${STORAGE_S3_BUCKET:-sns-application-images-local}"

# ブラウザからの直接PUTを許可するオリジン。
# 開発用スタックは既定の 5173、E2E用スタックは 5273 を使う(docker-compose.e2e.yml)。
# 複数オリジンをまとめて許可しないのは、本番(Terraform側)と設定の形を揃えるため。
ALLOWED_ORIGIN="${STORAGE_S3_ALLOWED_ORIGIN:-http://localhost:5173}"

awslocal s3api create-bucket \
  --bucket "$BUCKET" \
  --create-bucket-configuration LocationConstraint="${AWS_DEFAULT_REGION:-ap-northeast-1}"

# 本番(Terraform側)と同じCORS設定を入れる。
# これが無いとブラウザからの直接PUTがプリフライトで失敗する。
CORS_CONFIGURATION=$(cat <<JSON
{
  "CORSRules": [
    {
      "AllowedMethods": ["PUT", "GET"],
      "AllowedOrigins": ["$ALLOWED_ORIGIN"],
      "AllowedHeaders": ["*"],
      "ExposeHeaders": ["ETag"],
      "MaxAgeSeconds": 3000
    }
  ]
}
JSON
)

awslocal s3api put-bucket-cors --bucket "$BUCKET" --cors-configuration "$CORS_CONFIGURATION"

echo "LocalStack: bucket '$BUCKET' created with CORS for $ALLOWED_ORIGIN."
