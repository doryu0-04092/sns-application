########################################
# RDS(PostgreSQL)
########################################

resource "aws_db_subnet_group" "main" {
  name       = "${var.project}-db"
  subnet_ids = aws_subnet.private[*].id

  tags = { Name = "${var.project}-db" }
}

resource "aws_db_instance" "main" {
  identifier = "${var.project}-db"

  engine = "postgres"
  # メジャーバージョンのみを指定し、マイナーはAWSの既定に任せる。
  # ローカル開発のdocker-composeもpostgres:16なので、開発と本番でエンジンが揃う。
  engine_version = "16"

  instance_class    = var.db_instance_class
  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  # privateサブネット(IGWへの経路なし)に置いたうえで、明示的にも無効にする。
  publicly_accessible = false

  # --- ここから下は学習用の割り切り。本番では設定を変えること ---

  # 冗長構成にしない。まず最小で立てて実測し、必要なら上げる方針
  # (docs/aws-architecture.md「RDSはSingle-AZ / db.t4g.microから始める」)。
  multi_az = false

  backup_retention_period = 1

  # destroy時に最終スナップショットを取らない。学習用なので消えて困るデータが無く、
  # 取ると destroy のたびにスナップショットが残って課金される。
  skip_final_snapshot = true

  # 誤操作でDBを消せる状態にしてある。使わない期間に terraform destroy で
  # 丸ごと落とす運用を前提にしているため。本番では必ず true にすること。
  deletion_protection = false

  # Flywayは起動時に自動でマイグレーションを適用する(V1〜V6)。
  # 初期化用のSQLをここで流す必要はない。
}
