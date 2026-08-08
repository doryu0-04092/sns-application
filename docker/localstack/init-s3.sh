#!/bin/bash
# LocalStack起動時に実行され、開発用のS3バケットを作成する。
# (LocalStackは /etc/localstack/init/ready.d/ 配下のスクリプトを起動完了時に自動実行する)
set -e

BUCKET="${STORAGE_S3_BUCKET:-sns-application-images-local}"

awslocal s3api create-bucket \
  --bucket "$BUCKET" \
  --create-bucket-configuration LocationConstraint="${AWS_DEFAULT_REGION:-ap-northeast-1}"

# 本番(Terraform側)と同じCORS設定を入れる。
# これが無いとブラウザからの直接PUTがプリフライトで失敗する。
awslocal s3api put-bucket-cors --bucket "$BUCKET" --cors-configuration '{
  "CORSRules": [
    {
      "AllowedMethods": ["PUT", "GET"],
      "AllowedOrigins": ["http://localhost:5173"],
      "AllowedHeaders": ["*"],
      "ExposeHeaders": ["ETag"],
      "MaxAgeSeconds": 3000
    }
  ]
}'

echo "LocalStack: bucket '$BUCKET' created with CORS."
