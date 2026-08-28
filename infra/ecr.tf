########################################
# ECR(バックエンドのコンテナイメージ)
########################################

resource "aws_ecr_repository" "backend" {
  name = "${var.project}-backend"

  image_scanning_configuration {
    scan_on_push = true
  }

  # 学習用の割り切り。destroy時にイメージが残っていても削除できるようにする。
  # 本番ではイメージが消えると復旧できなくなるため false にすること。
  force_delete = true
}

# 古いイメージを溜め続けない。直近5世代を残せばロールバックには足りる。
resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "直近5世代のイメージだけを保持する"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 5
        }
        action = { type = "expire" }
      }
    ]
  })
}
