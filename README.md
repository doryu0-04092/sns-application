# SNS Application

X(旧Twitter)のようなテキストベースのコミュニケーションツールを想定した、社内向けSNSアプリケーションです。

## ドキュメント

- [要件定義書](docs/requirements.md)
- [機能一覧](docs/features.md)
- [画面設計](docs/screens.md)
- [ER図](docs/er-diagram.md)
- [API設計](docs/api-design.md) — 設計方針と採用理由。**エンドポイントごとの詳細な仕様は下記のSwagger UI**
- [技術スタック](docs/tech-stack.md)

### API仕様書(Swagger UI)

エンドポイントごとの詳細な仕様(パラメータ・レスポンスの型・制約・エラーコード)は Swagger UI で参照します。実装(コントローラとDTO)から自動生成されるため、実装とずれません。

`docker compose up` で起動したローカル環境では有効になっており、<http://localhost:8080/swagger-ui.html> で開けます。

- **Try it out** ボタンでその場でAPIを実行できます。認証は画面右上の Authorize ではなく、まず `POST /api/auth/signup` か `POST /api/auth/login` を実行してください。認証がhttpOnlyクッキーのため、実行するとブラウザがクッキーを保持し、以降のリクエストに自動で乗ります。
- OpenAPI形式のJSONは <http://localhost:8080/v3/api-docs> から取得できます。

#### 既定は「配信しない」

**アプリ単体の既定値は無効(`SPRINGDOC_ENABLED` 未設定なら配信しない)** です。上記の2つのURLは、有効化しない限り 404 を返します。

`/swagger-ui/**` と `/v3/api-docs` は `/api/` 配下ではないため `JwtAuthFilter` を通りません。**配信する = 認証なしで誰でも読める**、という意味になります。

既定を有効にしてしまうと、環境変数を設定し忘れた時点で公開されます。既定を無効にしておけば、設定を忘れた場合の結果は「開発中に仕様書が見えない」だけで済み、すぐ気づけて実害もありません。設定漏れが安全側に倒れる形にしてあります。

ローカルで有効になっているのは、[docker-compose.yml](docker-compose.yml) が `SPRINGDOC_ENABLED=true` を渡しているためです。`mvn spring-boot:run` で直接起動する場合は自分で渡してください。

```bash
SPRINGDOC_ENABLED=true mvn spring-boot:run
```

#### 公開環境での扱い

**このリポジトリのバックエンドは、まだどこにもデプロイしていません**([infra/](infra/) が作るのは画像保存用の S3 と IAM のみで、ECS 等のリソースは含まれません)。そのため以下は、デプロイする際の方針の記録です。

- **公開環境では有効にしない**。仕様書は開発者が読めればよく、インターネットから匿名で読める必要はありません。エンドポイント名やフィールド名から未実装の機能や内部構造が読み取れること、Swagger UI の Try it out が実データを操作できてしまうことが理由です
- 既定が無効なので、**デプロイ時に特別な作業は不要**です。`SPRINGDOC_ENABLED` を設定しなければ配信されません
- 将来「デプロイ先でも仕様書を見たい」となった場合、`JwtAuthFilter` はこれらのパスを保護しないため、アプリ側の変更かインフラ側(ALB/CloudFront等)でのアクセス制限が別途必要になります

## 前提

- 単一組織(社内)を前提とした、複数ユーザーが利用するSNS
- タイムラインはフォローベース

## リポジトリ構成

| ディレクトリ | 内容 |
|---|---|
| `backend/` | Spring Boot(Java 21 + MyBatis + Flyway)によるAPIサーバー |
| `frontend/` | React 19 + Vite + TypeScript によるWebクライアント(現行の実装) |
| `docs/` | 要件・機能・画面・ER図・API・技術スタックの設計ドキュメント |
| `mockup/` | 実装前に作成した静的プロトタイプ(S-01〜S-08)。**現行実装ではなく、バックエンドにも接続されていない参考資料**。`docs/screens.md` に要素定義のない画面のデザイン意図を残す目的で保持している |

## ローカル起動

```bash
# 1. 環境変数を用意する(DB接続情報・JWTシークレット・画像保存先)
cp .env.example .env
cp frontend/.env.example frontend/.env

# 2. PostgreSQL + バックエンドを起動する(Flywayマイグレーションは起動時に自動適用)
docker compose up -d --build

# 3. フロントエンドを起動する
cd frontend && npm install && npm run dev
```

- フロントエンド: http://localhost:5173
- バックエンド: http://localhost:8080
- API仕様書(Swagger UI): http://localhost:8080/swagger-ui.html

バックエンドを頻繁に変更する場合の反復手順や技術選定の理由は [技術スタック](docs/tech-stack.md) を参照してください。

## テスト

```bash
# バックエンド(JUnit 5 + Testcontainers)
cd backend && mvn test

# フロントエンド(Vitest)
cd frontend && npm test
```

- **バックエンドのテストには Docker が必要です。** MyBatis の SQL は PostgreSQL 固有の構文に依存しているため、Testcontainers が実際の PostgreSQL コンテナを起動し、Flyway マイグレーションを適用した状態で検証します。初回はイメージの取得で数分かかります。
- コンテナは JVM ごとに1度だけ起動し、各テストはトランザクションのロールバックで分離されます。
- フロントエンドは `npm run test:watch` でウォッチ実行できます。
