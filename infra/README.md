# infra — Terraform による AWS インフラ定義

投稿画像・プロフィールアイコンを保存する S3 バケットと、バックエンドがそれを操作するための
IAM を定義する。

## 構成方針

| 項目 | 決定 | 理由 |
|---|---|---|
| バケットの公開範囲 | **完全非公開** | 画像はすべて Presigned URL で配信するため、公開する必要がない |
| CDN | **使わない** | 今回は学習スコープを S3 に絞る。将来 CloudFront を前に置く場合も、アプリ側は配信URLの向き先を変えるだけで済む |
| 認証方式 | IAMユーザー + アクセスキー | 現状 ECS に載せていないため。**ECS移行時はタスクロールに置き換える**(アクセスキーが不要になる) |
| state | ローカル管理 | 「stateを置くS3を作るのにstateが要る」鶏卵問題を避けるため、まずはローカルで開始する |

## 作成されるリソース

- `aws_s3_bucket` — 画像保存先
- `aws_s3_bucket_public_access_block` — 4項目すべて有効(完全非公開)
- `aws_s3_bucket_ownership_controls` — ACLを無効化し、制御をIAMに一本化
- `aws_s3_bucket_server_side_encryption_configuration` — 保存時暗号化(AES256)
- `aws_s3_bucket_cors_configuration` — ブラウザからの直接アップロードを許可
- `aws_s3_bucket_lifecycle_configuration` — 孤児オブジェクト(`pending/`)の自動削除
- `aws_iam_user` / `aws_iam_access_key` / `aws_iam_user_policy` — 最小権限のアプリ用認証情報

## 使い方

```powershell
cd infra
copy terraform.tfvars.example terraform.tfvars   # bucket_name を一意な名前に変更する
terraform init
terraform plan                                    # 作成される内容を確認する
terraform apply                                   # 実際にAWSリソースを作成する(課金が発生する)
```

適用後、バックエンドに渡す値を取り出す。

```powershell
terraform output bucket_name
terraform output access_key_id
terraform output -raw secret_access_key
```

これらをリポジトリ直下の `.env` に設定する(`.env.example` を参照)。

## 注意

- **`terraform.tfvars` と `*.tfstate` は絶対にコミットしないこと。** state にはIAMシークレットキーが平文で保存される。ルートの `.gitignore` で除外済み。
- `.terraform.lock.hcl` はプロバイダのバージョン固定ファイルなので、**コミットする**(除外しない)。
- バケット名は全AWSアカウントで一意である必要がある。`terraform apply` が名前の衝突で失敗した場合は `bucket_name` を変更する。
- 破棄する場合は `terraform destroy`。ただし**バケット内にオブジェクトが残っていると失敗する**ため、先に中身を空にすること。

## ローカル開発では使わない

普段の開発は LocalStack(`docker compose` で起動)を使うため、実AWSへの `apply` は不要。
LocalStack 側のバケットは `docker/localstack/init-s3.sh` が起動時に自動作成する。
