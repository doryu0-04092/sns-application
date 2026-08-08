terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # stateはローカル管理で開始する。
  # S3バックエンド化は「stateを置くS3を作るのにstateが要る」鶏卵問題があるため、
  # Terraformの基本操作に慣れてからの課題とする。
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
