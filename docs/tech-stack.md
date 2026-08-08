# 技術スタック

## アーキテクチャ概要

フロントエンド・バックエンド・DBを分離した3層構造を採用する(学習課題として、既存プロジェクト「Trello」と同じ技術選定に揃え、学習の連続性を優先)。

```
[React + Vite (frontend/)]  <-- REST/JSON -->  [Spring Boot (backend/)]  <-->  [PostgreSQL]
     :5173 (ネイティブ起動)                    :8080 (Dockerコンテナ)          (Dockerコンテナ)
```

- バックエンド(Spring Boot)とDB(PostgreSQL)はdocker-composeでコンテナ化する。
- フロントエンド(React+Vite)はホットリロードを優先し、`npm run dev`でネイティブ起動する。

## 技術選定

| 項目 | 決定内容 | 理由 |
|---|---|---|
| 全体構成 | フロントエンド(React+Vite+TS) / バックエンド(Java+Spring Boot) / DB(PostgreSQL) の3層構造 | 単一プロジェクト構成も検討したが、3層構造を明示的に希望されたため。Trelloプロジェクトと同じ構成に揃え、学習効率を優先 |
| バックエンド | Java 21 + Spring Boot 3.5(Maven)。Spring Web, MyBatis(`mybatis-spring-boot-starter`), PostgreSQLドライバ, Spring Security Crypto(BCryptPasswordEncoder), Bean Validation, Flyway | SQLを自分で書いて理解する学習目的のため、JPAではなくMyBatisを採用。Flywayによる明示的なスキーマ管理 |
| DB/マイグレーション | PostgreSQL(Dockerコンテナ、docker-compose管理) + Flyway | 将来のAWS RDS移行時も同一エンジンのため開発/本番の差異が出にくい。Flywayでスキーマ変更履歴を明示的に管理する |
| 認証 | 自前実装。BCryptPasswordEncoderでパスワードハッシュ化、`jjwt`ライブラリでアクセストークン(JWT, 15分)を発行・検証し、httpOnly + SameSite=Laxクッキーに格納。`JwtAuthFilter`で保護エンドポイントを検証 | 本プロジェクトの学習目的が「ログイン機能を作ること」自体であるため、NextAuth等のライブラリで仕組みを隠さず、ハッシュ化・トークン発行・検証を自分で実装して理解する |
| リフレッシュトークン | opaqueな乱数トークン(7日)をDB(`REFRESH_TOKENS`テーブル)にSHA-256ハッシュで保存。使用の都度ローテーション(新規発行+旧トークン失効)し、既に失効済みのトークンが再提示されたら盗用の兆候とみなしそのユーザーの全トークンを一括失効。フロントエンドは401時に自動でリフレッシュ+リトライ(`api/client.ts`) | アクセストークンを短命化しつつ、ユーザーには再ログインを意識させないため。DB保存によりリフレッシュトークンの個別失効・盗用検知が可能になり、学習目的として一般的な実装パターンを体験できる |
| CORS | フロントのオリジン(`http://localhost:5173`)を許可し、`allowCredentials=true`を設定 | フロントとバックエンドが別オリジンとなるため、クッキーを用いた認証には明示的なCORS設定が必須 |
| フロントエンド | React 19 + Vite + TypeScript + `@tanstack/react-query` + `react-router-dom` + Tailwind CSS | Trelloと同じ技術選定。TanStack Queryでサーバー状態のキャッシュ・再検証を扱う |
| 画像保存 | 非公開のAWS S3バケット。アップロード・表示ともにPresigned URLを使い、画像本体はバックエンドを経由しない。ローカル開発はLocalStackで代替 | サーバーを経由させないことで、大きなファイルでバックエンドの帯域・タイムアウトがボトルネックになるのを避ける。バケットを非公開に保てるためURLの漏洩リスクも抑えられる |
| IaC | Terraform(`infra/`) | 日本の求人数が最も多く、宣言的IaCとstate管理の概念が他ツール(OpenTofu/Pulumi/CDK)へ転用できるため。今回のスコープはS3+IAMのみで、ECS/RDSは今後追加する |
| バリデーション | Bean Validation(`jakarta.validation`) | 投稿・コメントの280文字制限など、エンティティ/DTOレベルで宣言的に検証する |

## ローカル開発環境のセットアップ手順(概要)

1. `.env.example`を`.env`にコピーし、値を設定する(DB名・ユーザー名・パスワード)
2. `docker compose up -d --build`でPostgreSQL + バックエンドを起動する(デフォルトポート: DB 5432, バックエンド 8080)。Flywayマイグレーションは起動時に自動適用される
3. フロントエンド起動: `cd frontend && npm install && npm run dev` (デフォルトポート: 5173、ネイティブ起動)

バックエンドのコードを頻繁に変更する場合は、`docker compose up -d postgres`でDBのみコンテナ起動し、`cd backend && mvn spring-boot:run`でバックエンドをネイティブ起動する方が反復が速い(`application.yml`は`DB_HOST`未設定時に`localhost`にフォールバックするため、そのまま接続できる)。

## スキーマ管理方針(Flyway)

- `backend/src/main/resources/db/migration/`配下に`V1__init.sql`のようなバージョン付きSQLファイルを置き、Flywayが起動時に適用する。
- ER図(docs/er-diagram.md)のテーブル定義をそのままDDLに変換する。エンティティの追加・変更時は新しいバージョンのマイグレーションファイルを追加する(既存ファイルは変更しない)。

## AWS移行の状況

**対応済み**

- 画像保存をS3へ移行(`infra/`のTerraformで構築)。バケットは非公開で、Presigned URLで読み書きする。

**未対応(今後の課題)**

- **DB**: PostgreSQLのまま、接続先をAWS RDSに向けるだけで移行できる想定(Flywayマイグレーションはそのまま適用可能)。
- **バックエンドのデプロイ**: ECS想定。移行時は`infra/`の`aws_iam_user`(アクセスキー方式)を**ECSタスクロールに置き換える**こと。
  - あわせて、署名付きURLの寿命が一時認証情報の寿命(数時間)に制限されるため、`app.storage.s3.presign-expiry`の見直しが必要になる。
- **Terraformのstate**: 現在はローカル管理。チーム開発や環境の永続化を考えるならS3バックエンド化を検討する。
- **CDN**: CloudFrontは未導入。導入する場合はバケットをOAC経由に切り替え、署名付きURLからCloudFrontの固定URLへ変更する(キャッシュが効くようになる)。
- **フロントエンドのデプロイ先**(例: Amplify等)は本ドキュメントのスコープ外。
