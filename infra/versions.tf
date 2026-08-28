terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    # DBパスワード・JWTシークレット・オリジン検証ヘッダーの生成に使う。
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    # CloudFrontの署名付きクッキー用のRSA鍵ペアの生成に使う。
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # stateはローカル管理で開始する。
  # S3バックエンド化は「stateを置くS3を作るのにstateが要る」鶏卵問題があるため、
  # Terraformの基本操作に慣れてからの課題とする。
  #
  # なおstateには生成したDBパスワード・JWTシークレット・CloudFrontの秘密鍵が
  # 平文で保存される。ルートの .gitignore で除外済みだが、扱いには注意すること。
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "sns-application"
      ManagedBy = "terraform"
    }
  }
}
