# infra — Terraform による AWS インフラ定義

CloudFront / S3 / ALB / ECS+Fargate / RDS でアプリケーション全体を構築する。
構成の全体像・設計判断・概算費用は [AWS構成設計](../docs/aws-architecture.md) にある。
ここには**適用の手順とハマりどころ**だけを書く。

> **現在の状態: `terraform destroy` 済み。AWS上にリソースは無い。**
> stateは空なので `terraform output` は何も返さない。下の手順で作り直せる。
> 2026-08-28 に一度デプロイして全機能を確認しており、そのときの実測結果は
> [AWS構成設計](../docs/aws-architecture.md) の「デプロイ実績」にある。

## ファイル構成

| ファイル | 内容 |
|---|---|
| `versions.tf` | プロバイダのバージョン、既定タグ、state方針 |
| `variables.tf` / `terraform.tfvars.example` | 入力値 |
| `network.tf` | VPC、public/privateサブネット(各2AZ)、IGW、ルートテーブル、セキュリティグループ |
| `secrets.tf` | DBパスワード・JWTシークレット・オリジン検証ヘッダー・CloudFront署名鍵の生成とSSMへの保管 |
| `main.tf` | 画像用S3バケット |
| `frontend.tf` | 静的サイト用S3バケット |
| `alb.tf` | ALB、ターゲットグループ、リスナー(既定403 + ヘッダー一致で転送) |
| `ecr.tf` | バックエンドのコンテナレジストリ |
| `iam.tf` | ECSのタスク実行ロールとタスクロール |
| `ecs.tf` | クラスタ、タスク定義、サービス、ロググループ |
| `rds.tf` | サブネットグループ、PostgreSQL |
| `cloudfront.tf` | OAC、CloudFront Functions、署名用キーグループ、ディストリビューション |
| `outputs.tf` | デプロイと障害調査で使う値 |

## 適用手順

**`terraform apply` を一度に流すと失敗する。** ECRにイメージが無い状態でECSサービスが
タスクを起動しようとするため。順序を分ける。

```powershell
cd infra
copy terraform.tfvars.example terraform.tfvars   # バケット名を一意な名前に変更する
terraform init

# 1. ECRリポジトリだけ先に作る
#    -target= の引数は必ず引用符で囲むこと。囲まないとPowerShellが引数を分解し、
#    terraform には "aws_ecr_repository" までしか届かず Invalid target で失敗する。
terraform apply "-target=aws_ecr_repository.backend"

# 2. バックエンドのイメージをビルドしてpushする
#    Fargateはlinux/amd64で動くため、他アーキテクチャの開発機では --platform を指定する
$repo = terraform output -raw ecr_repository_url
$region = terraform output -raw region
aws ecr get-login-password --region $region | docker login --username AWS --password-stdin $repo.Split('/')[0]
#    --provenance=false --sbom=false は必須。付けないとbuildxが attestation を含む
#    OCIイメージインデックスを作り、ECS Fargateがイメージを取得できないことがある
docker build --platform linux/amd64 --provenance=false --sbom=false -t "${repo}:latest" ../backend
docker push "${repo}:latest"

# 3. 残りのリソースを作る(10〜15分ほどかかる。RDSとCloudFrontの作成が長い)
terraform apply

# 4. フロントエンドをビルドして配置する
cd ../frontend
$env:VITE_API_BASE_URL = "/api"; npm run build
$bucket = terraform -chdir=../infra output -raw static_bucket_name
aws s3 sync dist/ "s3://$bucket/" --delete --exclude index.html `
  --cache-control "public,max-age=31536000,immutable"
aws s3 cp dist/index.html "s3://$bucket/index.html" --cache-control "no-cache"

# 5. index.html のキャッシュを無効化する
$dist = terraform -chdir=../infra output -raw cloudfront_distribution_id
aws cloudfront create-invalidation --distribution-id $dist --paths "/index.html"

# 6. 開く
terraform -chdir=../infra output -raw cloudfront_domain
```

ハッシュ付きのアセットは長期キャッシュ、`index.html` だけ `no-cache` にして invalidate する。
この分け方をしないと、新しいアセットを置いても古い `index.html` が参照され続けて更新が反映されない。

## 動作確認

```powershell
# CloudFront経由のAPIが通る(200)
curl "$(terraform output -raw cloudfront_domain)/api/health"

# ALBを直接叩くと弾かれる(403が正常)
curl "http://$(terraform output -raw alb_dns_name)/api/health"

# バックエンドのログ。Flywayが V1〜V6 を適用したことを確認する
aws logs tail $(terraform output -raw log_group_name) --follow
```

## 破棄

**使わない期間は破棄すること。** ALBとRDSは起動しているだけで課金され、合計で月$40〜50になる。

```powershell
# S3バケットに中身が残っていると destroy が失敗するため、先に空にする
aws s3 rm "s3://$(terraform output -raw images_bucket_name)" --recursive
aws s3 rm "s3://$(terraform output -raw static_bucket_name)" --recursive

terraform destroy
```

Terraformのコードは残るので、必要になったら同じ手順で作り直せる。

## ハマりどころ

- **ECRにイメージが無いとECSサービスの作成が失敗する。** 上の手順1〜3の順序を守ること。
- **`STORAGE_S3_ENDPOINT` に空文字を明示的に渡している。** `application.yml` の既定値が
  LocalStack向けの `http://localhost:4566` なので、渡さないとコンテナ内から到達できない
  アドレスへ接続しに行って起動に失敗する。
- **SPAのフォールバックはCloudFront Functionで行っている。** カスタムエラーレスポンスは
  ディストリビューション全体に効くため、APIが返す403/404まで `index.html` に書き換えてしまう。
- **画像のパスは `/images/*` に置いている。** SPAが `/posts/:postId` というルートを持つため、
  CDNのルート直下に画像を置くと衝突する。S3上のキーは `posts/...` なので、
  CloudFront Functionで `/images` を剥がしてからオリジンへ渡している。
- **CloudFrontの秘密鍵はPKCS#8で渡している。** JavaのKeyFactoryがPKCS#1を直接読めないため、
  `private_key_pem` ではなく `private_key_pem_pkcs8` を使っている。
- **バケット名は全AWSアカウントで一意。** 名前の衝突で失敗したら `terraform.tfvars` を変更する。
- **RDSとCloudFrontの作成には時間がかかる。** 手順3は10〜15分ほど待つ。

## 注意

- **`terraform.tfvars` と `*.tfstate` は絶対にコミットしないこと。**
  stateにはDBパスワード・JWTシークレット・CloudFrontの秘密鍵が平文で保存される。
  ルートの `.gitignore` で除外済み。
- `.terraform.lock.hcl` はプロバイダのバージョン固定ファイルなので、**コミットする**。
- stateはローカル管理。S3バックエンド化は「stateを置くS3を作るのにstateが要る」
  鶏卵問題があるため、別の課題としている。

## ローカル開発では使わない

普段の開発は LocalStack(`docker compose` で起動)を使うため、実AWSへの `apply` は不要。
LocalStack 側のバケットは `docker/localstack/init-s3.sh` が起動時に自動作成する。
