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

variable "allowed_origins" {
  description = <<-EOT
    ブラウザからS3へ直接アップロード(presigned PUT)する際のCORS許可オリジン。
    ここに載っていないオリジンからのPUTはブラウザ側で拒否される。
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
