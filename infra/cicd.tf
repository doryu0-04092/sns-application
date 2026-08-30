########################################
# CD(GitHub Actions からのデプロイ)
########################################

# **アクセスキーは発行しない。** GitHub の OIDC トークンを AWS に信頼させ、
# 実行のたびに一時的な認証情報を受け取る形にする。
# 長期キーをリポジトリのシークレットに置くと、漏れた時に失効させるまで有効なままになり、
# ローテーションの手間も残り続ける。
#
# **このロールが行えるのはアプリのデプロイだけである。** インフラの変更(terraform apply)は
# 含めていない。stateをローカルで管理しており(versions.tf 参照)、CIから共有できないためである。
# インフラもCDに載せるなら、先にstateをS3+DynamoDBへ移す必要がある。

########################################
# OIDC プロバイダ
########################################

# **アカウントに1つしか作れない。** 同じAWSアカウントで別のリポジトリが既に
# 作っている場合、ここで作ろうとすると EntityAlreadyExists で失敗する。
# その場合は github_oidc_provider_arn に既存のARNを渡すこと。
resource "aws_iam_openid_connect_provider" "github" {
  count = var.github_oidc_provider_arn == "" ? 1 : 0

  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]

  # thumbprint_list は指定しない。
  # このissuerについてAWSは自前の信頼ストアで検証しており、値を渡しても使われない。
  # プロバイダのスキーマ上も optional かつ computed(未指定ならAWS側の値が入る)。
  # **固定値を書くと、GitHub側の証明書が変わった時に更新漏れの原因になるだけである。**
}

locals {
  github_oidc_provider_arn = var.github_oidc_provider_arn != "" ? var.github_oidc_provider_arn : aws_iam_openid_connect_provider.github[0].arn
}

########################################
# デプロイ用ロール
########################################

data "aws_iam_policy_document" "github_actions_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    # **aud の確認は省略できない。** 省くと、他のOIDC利用者が発行させたトークンでも
    # 通りうる形になる。
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # **ここが最も重要。** sub を絞らないと、GitHub上の**どのリポジトリからでも**
    # このロールを引き受けられる。OIDCの設定で最も多い致命的な誤りがこれである。
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = var.github_deploy_subjects
    }
  }
}

resource "aws_iam_role" "github_actions" {
  name               = "${var.project}-github-actions-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role.json

  description = "GitHub Actionsからアプリをデプロイするためのロール。インフラの変更権限は持たない。"
}

########################################
# 権限(デプロイに必要な分だけ)
########################################

data "aws_iam_policy_document" "github_actions_deploy" {
  # --- ECR: イメージのpush ---
  # GetAuthorizationToken だけは特定のリポジトリに絞れない(トークンはレジストリ単位のため)。
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
      "ecr:BatchGetImage",
      "ecr:DescribeImages",
    ]
    resources = [aws_ecr_repository.backend.arn]
  }

  # --- ECS: 新しいタスク定義を登録し、サービスを入れ替える ---
  # RegisterTaskDefinition と DescribeTaskDefinition はリソース単位で絞れない
  # (登録前のタスク定義にARNが無いため)。AWS側の制約であり、書き方の問題ではない。
  statement {
    sid = "EcsRegisterTaskDefinition"
    actions = [
      "ecs:RegisterTaskDefinition",
      "ecs:DescribeTaskDefinition",
    ]
    resources = ["*"]
  }

  statement {
    sid = "EcsDeploy"
    actions = [
      "ecs:UpdateService",
      "ecs:DescribeServices",
    ]
    resources = [aws_ecs_service.backend.id]
  }

  # --- タスク定義に載せるロールを渡す権限 ---
  # **これが無いと RegisterTaskDefinition が失敗する。** 渡せる相手を2つのロールに限定し、
  # さらに渡し先のサービスをECSに限定する。絞らないと、
  # **任意のロールをECSタスクとして起動できる**ことになり、権限昇格の経路になる。
  statement {
    sid     = "PassTaskRoles"
    actions = ["iam:PassRole"]
    resources = [
      aws_iam_role.task_execution.arn,
      aws_iam_role.task.arn,
    ]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }

  # --- S3: フロントエンドの配置 ---
  # --delete を使うため DeleteObject が要る。対象は静的配信バケットのみ。
  # **画像バケット(aws_s3_bucket.images)は含めない。** 利用者の投稿画像であり、
  # デプロイで消えてよいものではない。
  statement {
    sid       = "StaticBucketList"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.static.arn]
  }

  statement {
    sid = "StaticBucketWrite"
    actions = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:DeleteObject",
    ]
    resources = ["${aws_s3_bucket.static.arn}/*"]
  }

  # --- CloudFront: index.html のキャッシュ無効化 ---
  statement {
    sid = "CloudFrontInvalidate"
    actions = [
      "cloudfront:CreateInvalidation",
      "cloudfront:GetInvalidation",
    ]
    resources = [aws_cloudfront_distribution.main.arn]
  }
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  name   = "${var.project}-github-actions-deploy"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.github_actions_deploy.json
}
