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
| 画像保存 | 非公開のAWS S3バケット。画像本体はバックエンドを経由しない。アップロードはPresigned URL、**表示はCloudFront経由 + 署名付きCookie**(→[AWS構成設計](aws-architecture.md))。ローカル開発はLocalStackで代替 | サーバーを経由させないことで、大きなファイルでバックエンドの帯域・タイムアウトがボトルネックになるのを避ける。表示側をCloudFrontに移すのは、Presigned URLだと(a)発行のたびにURLが変わりキャッシュが効かない (b)URL自体が資格情報なのでコピー・Referer・ログ経由で漏れると期限内は誰でも取得できる、の2点を解消するため |
| IaC | Terraform(`infra/`) | 日本の求人数が最も多く、宣言的IaCとstate管理の概念が他ツール(OpenTofu/Pulumi/CDK)へ転用できるため。現在の実装範囲はS3+IAMのみで、ECS/RDS/ALB/CloudFrontは設計確定済み・実装はこれから(→[AWS構成設計](aws-architecture.md)) |
| バリデーション | Bean Validation(`jakarta.validation`) | 投稿・コメントの280文字制限など、エンティティ/DTOレベルで宣言的に検証する |
| ログ | SLF4J + Logback(Spring Boot標準) + `logstash-logback-encoder`。`logback-spring.xml` でプロファイルを切り替え、ローカルは人間可読の1行テキスト(自作コードのみDEBUG)、コンテナ実行時はJSON 1行(INFO)。出力先は**標準出力のみ**でファイルには書かない | アプリがログの送り先を知らない形にしておくと、収集先(Datadog / CloudWatch Logs / Grafana Loki)を変えてもアプリを変更せずに済む。収集はコンテナ基盤の責任として分離する(12-factor)。本番でDEBUGを出さないのは、書き込みI/Oとログ量が跳ね上がるため |
| リクエスト追跡 | `RequestLoggingFilter`(全フィルタの最外周)がリクエストごとにUUIDを発行し、MDCへ載せてそのリクエスト中の全ログに自動付与。レスポンスヘッダー `X-Request-Id` でも返す。アクセスログはメソッド/パス/ステータス/所要時間のみ | 複数利用者のリクエストが同時に処理されるため、追跡IDが無いと「どのログがどのリクエストのものか」を後から追えない。ヘッダー・Cookie・ボディ・クエリ文字列は**構造的に渡さない**設計にし、認証トークンやパスワードがログに入る経路自体を作らない(マスキングは消し忘れで漏れるため) |
| 監視 | 監視ツールは未導入。運用設計(ログレベルの使い分け・監視項目と閾値・ダッシュボード構成・障害対応フロー)は [運用設計](operations.md) に文書化済み | 構造化ログをstdoutへ出す形にしてあるため、収集エージェントを足すだけで後から接続できる。監視の3本柱のうち現状はログのみで、メトリクス(Actuator + Micrometer + Prometheus)とトレースは未実装 |

## ローカル開発環境のセットアップ手順(概要)

1. `.env.example`を`.env`にコピーし、値を設定する(DB名・ユーザー名・パスワード)
2. `docker compose up -d --build`でPostgreSQL + バックエンドを起動する(デフォルトポート: DB 5432, バックエンド 8080)。Flywayマイグレーションは起動時に自動適用される
3. フロントエンド起動: `cd frontend && npm install && npm run dev` (デフォルトポート: 5173、ネイティブ起動)

バックエンドのコードを頻繁に変更する場合は、`docker compose up -d postgres`でDBのみコンテナ起動し、`cd backend && mvn spring-boot:run`でバックエンドをネイティブ起動する方が反復が速い(`application.yml`は`DB_HOST`未設定時に`localhost`にフォールバックするため、そのまま接続できる)。

## スキーマ管理方針(Flyway)

- `backend/src/main/resources/db/migration/`配下に`V1__init.sql`のようなバージョン付きSQLファイルを置き、Flywayが起動時に適用する。
- ER図(docs/er-diagram.md)のテーブル定義をそのままDDLに変換する。エンティティの追加・変更時は新しいバージョンのマイグレーションファイルを追加する(既存ファイルは変更しない)。

## AWS移行の状況

構成の設計は確定した。詳細(構成図・設定値・設計判断・費用・デプロイ手順)は [AWS構成設計](aws-architecture.md) にまとめてある。ここでは進捗の状態だけを記す。

**対応済み**

- 画像保存をS3へ移行(`infra/`のTerraformで構築)。バケットは非公開で、Presigned URLで読み書きする。

**設計確定・実装はこれから**

CloudFront / S3 / ALB / ECS+Fargate / RDS の5サービスで構成し、利用者からの窓口をCloudFrontに一本化する(CloudFront → ALB → ECS+Fargate → RDS)。CloudFrontは1つのディストリビューションに3つのオリジンを束ね、静的ファイル・`/api/*`・`/images/*`をパスで振り分ける。フロントとAPIが同一オリジンになるため、CORSとクロスサイトCookieの問題が構造的に消える。

実装は次の順序で進める。

1. **アプリ側のコード変更**: Cookieの`Secure`属性の環境変数化、フロントのAPIベースURLの相対パス化、画像URLのCloudFront経由への切り替え(署名付きCookie)。
2. **Terraform実装**: `infra/`にVPC・ALB・ECS・RDS・CloudFront・ECR・SSM Parameter Storeを追加。あわせて`aws_iam_user`(アクセスキー方式)を廃止し、**ECSタスクロールに置き換える**。
3. **デプロイと検証**: 既存のE2Eテストと負荷計測を、デプロイ先に対して実行する。

**未対応(今後の課題)**

- **Terraformのstate**: 現在はローカル管理。チーム開発や環境の永続化を考えるならS3バックエンド化を検討する。
- **CD**: GitHub ActionsからのECR push / ECSデプロイ / S3 syncは未実装。当面は手動デプロイとする。
- **画像アップロードの経路**: 表示はCloudFront経由に移すが、アップロードは引き続きブラウザからS3へPresigned PUTで直接送る。S3エンドポイントの隠蔽はGET側のみになる。
- **署名付きURLの寿命**: 一時認証情報(ECSタスクロール)で署名した場合、`X-Amz-Expires`の指定に関わらず認証情報の寿命(数時間)で切れる。表示用URLはCloudFrontの署名付きCookieに移るためこの制約を踏まないが、**CDN未設定時のフォールバック経路だけは踏む**。`app.storage.s3.presign-expiry`はローカル開発専用の設定として残す。
