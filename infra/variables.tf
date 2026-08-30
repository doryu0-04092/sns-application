variable "project" {
  description = <<-EOT
    リソース名の接頭辞。ALBやターゲットグループの名前には32文字の上限があるため、
    長い名前に変更する場合は上限に注意すること。
  EOT
  type        = string
  default     = "sns-application"
}

variable "region" {
  description = "リソースを作成するAWSリージョン"
  type        = string
  default     = "ap-northeast-1"
}

variable "bucket_name" {
  description = <<-EOT
    画像を保存するS3バケット名。S3のバケット名は全AWSアカウントで一意である必要があるため、
    他と衝突しない名前を terraform.tfvars で指定すること(例: sns-application-images-<任意の識別子>)。
  EOT
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", var.bucket_name))
    error_message = "バケット名は3〜63文字の小文字英数字・ハイフン・ピリオドで指定してください。"
  }
}

variable "static_bucket_name" {
  description = <<-EOT
    フロントエンドの静的ファイル(frontend/dist)を置くS3バケット名。画像用とは別のバケットにする。
    こちらも全AWSアカウントで一意である必要がある。
  EOT
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", var.static_bucket_name))
    error_message = "バケット名は3〜63文字の小文字英数字・ハイフン・ピリオドで指定してください。"
  }
}

variable "allowed_origins" {
  description = <<-EOT
    ブラウザからS3へ直接アップロード(presigned PUT)する際のCORS許可オリジン。
    ここに載っていないオリジンからのPUTはブラウザ側で拒否される。

    CloudFrontのドメインは apply 時に自動で追加されるため、ここには書かない。
    書くとしてもローカル開発用のオリジンだけでよい。
  EOT
  type        = list(string)
  default     = ["http://localhost:5173"]
}

variable "orphan_expiration_days" {
  description = <<-EOT
    アップロードされたまま投稿に紐づかなかった孤児オブジェクトを削除するまでの日数。
    presignedアップロードは「S3に置いたが投稿を作成しなかった」状態が起こりうるため、
    ライフサイクルルールで自動的に掃除する。
  EOT
  type        = number
  default     = 7
}

variable "vpc_cidr" {
  description = "VPCのCIDRブロック"
  type        = string
  default     = "10.0.0.0/16"
}

variable "db_name" {
  description = "RDSに作成するデータベース名。バックエンドの DB_NAME に対応する"
  type        = string
  default     = "sns_application"
}

variable "db_username" {
  description = "RDSのマスターユーザー名。バックエンドの DB_USER に対応する"
  type        = string
  default     = "sns_user"
}

variable "db_instance_class" {
  description = <<-EOT
    RDSのインスタンスクラス。まず最小構成で立てて実測し、必要なら上げる方針
    (docs/aws-architecture.md「RDSはSingle-AZ / db.t4g.microから始める」)。
  EOT
  type        = string
  default     = "db.t4g.micro"
}

variable "task_cpu" {
  description = "ECSタスクのCPUユニット(256 = 0.25 vCPU)"
  type        = string
  default     = "256"
}

variable "task_memory" {
  description = "ECSタスクのメモリ(MiB)"
  type        = string
  default     = "512"
}

variable "desired_count" {
  description = <<-EOT
    ECSサービスのタスク数。増やす場合は「タスク数 × DB_POOL_MAX_SIZE ≦ RDSのmax_connections」
    が成り立つことを確認すること(db.t4g.microのmax_connectionsは約112なので、既定のプール10なら11タスクが上限)。
  EOT
  type        = number
  default     = 1
}

variable "log_retention_days" {
  description = "CloudWatch Logsの保持日数"
  type        = number
  default     = 7
}

variable "cdn_cookie_expiry" {
  description = <<-EOT
    画像取得用のCloudFront署名付きクッキーの寿命(ISO-8601 duration)。
    ログイン・サインアップ・トークン再発行のたびに再発行されるため、利用中は切れない。
  EOT
  type        = string
  default     = "PT12H"
}

variable "github_oidc_provider_arn" {
  description = <<-EOT
    既存のGitHub OIDCプロバイダのARN。空なら新規に作成する。
    **OIDCプロバイダはAWSアカウントに1つしか作れない。**
    同じアカウントで別のリポジトリが既に作っている場合は、そのARNをここに渡すこと
    (渡さないと EntityAlreadyExists で apply が失敗する)。
  EOT
  type        = string
  default     = ""
}

variable "github_deploy_subjects" {
  description = <<-EOT
    デプロイロールを引き受けられるGitHub Actionsの実行元(OIDCトークンの sub)。

    **ここを絞らないと、GitHub上のどのリポジトリからでもこのロールを引き受けられる。**
    OIDC設定で最も多い致命的な誤りがこれ。リポジトリ名は必ず含めること。

    既定は master ブランチからの実行のみ。
  EOT
  type        = list(string)
  default     = ["repo:doryu0-04092/sns-application:ref:refs/heads/master"]
}
