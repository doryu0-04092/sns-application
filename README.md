# SNS Application

X(旧Twitter)のようなテキストベースのコミュニケーションツールを想定した、社内向けSNSアプリケーションです。

## ドキュメント

- [要件定義書](docs/requirements.md)
- [機能一覧](docs/features.md)
- [画面設計](docs/screens.md)
- [ER図](docs/er-diagram.md)
- [API設計](docs/api-design.md) — 設計方針と採用理由。**エンドポイントごとの詳細な仕様は下記のSwagger UI**
- [技術スタック](docs/tech-stack.md)
- [AWS構成設計](docs/aws-architecture.md) — CloudFront / S3 / ALB / ECS+Fargate / RDS の構成と設計判断、実際にデプロイして確認した結果
- [テスト計画](docs/test-plan.md)
- [運用設計](docs/operations.md) — ログ設計・監視項目と閾値・障害対応フロー

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

- **公開環境では有効にしない**。仕様書は開発者が読めればよく、インターネットから匿名で読める必要はありません。エンドポイント名やフィールド名から未実装の機能や内部構造が読み取れること、Swagger UI の Try it out が実データを操作できてしまうことが理由です
- 既定が無効なので、**デプロイ時に特別な作業は不要**です。`SPRINGDOC_ENABLED` を設定しなければ配信されません。実際に AWS へデプロイした際も、ECS のタスク定義でこの変数を渡さないことで配信されない状態を保ちました
- 将来「デプロイ先でも仕様書を見たい」となった場合、`JwtAuthFilter` はこれらのパスを保護しないため、アプリ側の変更かインフラ側(ALB/CloudFront等)でのアクセス制限が別途必要になります

### フロントエンドのAPI型は仕様から生成しています

フロントエンドの API 型（`frontend/src/types/*.ts`）は、**バックエンドのDTOから生成**しています。手で書くと、バックエンド側でフィールドを改名しても双方のテストが通ってしまい、実行時まで壊れに気づけないためです。

```
バックエンドのDTO
   ↓ 実装から生成
docs/openapi.json          ← コミット済み。OpenApiSnapshotTest が実装との一致を保証
   ↓ npm run gen:api
frontend/src/api/generated/ ← コミット済み。生成物なので直接編集しない
   ↓ 別名を付けるだけ
frontend/src/types/*.ts
```

**APIを変更したときの手順**は2ステップです。

```bash
# 1. 仕様を更新する（backend で実行）
mvn test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true

# 2. 型を生成し直す（frontend で実行）
npm run gen:api
```

差分をコミットすれば完了です。**手順1の差分がそのままAPIの変更点**になるので、PR上で何が変わったか読めます。

生成し忘れても CI が検出します（再生成して差分が出たら失敗する）。また、バックエンドでフィールドを改名して型を生成し直すと、**使用箇所すべてが `tsc` でエラーになります** — 実行時まで気づけなかった問題が、コンパイル時に必ず止まります。

## 前提

- 単一組織(社内)を前提とした、複数ユーザーが利用するSNS
- タイムラインはフォローベース

## リポジトリ構成

| ディレクトリ | 内容 |
|---|---|
| `backend/` | Spring Boot(Java 21 + MyBatis + Flyway)によるAPIサーバー |
| `frontend/` | React 19 + Vite + TypeScript によるWebクライアント(現行の実装) |
| `infra/` | Terraform による AWS インフラ定義(CloudFront / S3 / ALB / ECS+Fargate / RDS) |
| `perf/` | パフォーマンステスト(k6のシナリオ・計測スクリプト・結果) |
| `docs/` | 要件・機能・画面・ER図・API・技術スタック・AWS構成設計・テスト計画・運用設計のドキュメント |
| `mockup/` | 実装前に作成した静的プロトタイプ(S-01〜S-08)。**現行実装ではなく、バックエンドにも接続されていない参考資料**。`docs/screens.md` に要素定義のない画面のデザイン意図を残す目的で保持している |

## デプロイの状況

2026-08-28 に AWS へデプロイし、ブラウザで全機能を確認したのち **`terraform destroy` しました。現在 AWS 上にリソースは無く、課金は発生していません。**

学習用の構成のため、ALB と RDS が起動しているだけで月 $40〜50 かかります。確認が済んだ時点で破棄する運用にしてあります。

再構築は [infra/README.md](infra/README.md) の手順で行えます。**`terraform apply` を一度に流すと失敗します**(ECR にイメージが無い状態で ECS がタスクを起動しようとするため)。ECR を先に作る → イメージを push → 残りを apply の順に分ける必要があります。

実測の結果、デプロイ中に見つけた不備、設計上の判断は [AWS構成設計](docs/aws-architecture.md) にまとめています。

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

# E2E(Playwright / 実ブラウザ)
cd frontend && npm run test:e2e
```

- **バックエンドのテストには Docker が必要です。** MyBatis の SQL は PostgreSQL 固有の構文に依存しているため、Testcontainers が実際の PostgreSQL コンテナを起動し、Flyway マイグレーションを適用した状態で検証します。初回はイメージの取得で数分かかります。
- コンテナは JVM ごとに1度だけ起動し、各テストはトランザクションのロールバックで分離されます。
- フロントエンドは `npm run test:watch` でウォッチ実行できます。

### E2E(Playwright)

jsdom では原理的に検証できない領域だけを実ブラウザで確認します。HttpOnly クッキーの実送信、
CORS プリフライト、S3 への実 PUT、canvas による画像縮小など、ブラウザの実装そのものが要る箇所です。

```bash
cd frontend && npm run test:e2e
```

- **必要なものは Docker だけです。** 事前に `docker compose up` を実行する必要はありません。
  テストが [docker-compose.e2e.yml](docker-compose.e2e.yml) の専用スタックを起動し、終了時に破棄します。
- **開発用スタックには一切触れません。** Compose プロジェクト名・ポート・DB名・S3バケットがすべて別で、
  `npm run dev` を動かしたままでも実行できます(E2E用のフロントエンドは 5273 で起動します)。
- **テストが作ったデータは残りません。** 専用スタックは永続ボリュームを持たないため、
  破棄した時点で DB の中身も S3 のオブジェクトも消えます。毎回まっさらな状態から始まります。
- 失敗した場合は `npm run test:e2e:report` でトレースとスクリーンショットを開けます。
- 反復して調べたい場合は `E2E_KEEP_STACK=1`(スタックを破棄しない)、
  `E2E_SKIP_STACK=1`(起動も破棄もせず、自分で起動済みのスタックを使う)が使えます。
  異常終了でコンテナが残った場合は次で後始末できます:
  `docker compose -p snsapp-e2e -f docker-compose.e2e.yml down -v`
- **CI では実行していません。** 専用スタックの起動を CI が毎回負担することになるためです。
  ただしテストコードの型検査は `npm run build` 経由で CI が行っています。

テスト層の分け方と「あえてテストしない項目」は [テスト計画](docs/test-plan.md) にまとめています。
実ブラウザでの検証で見つかった不具合は [E2Eテスト結果レポート](docs/e2e-test-report.md) にあります。

## 運用・ログ監視

障害が起きたときに追跡できることを目的に、ログの出し方・監視項目と閾値・障害対応フローを
[運用設計](docs/operations.md) にまとめています。

- **構造化ログ**: コンテナ実行時は JSON 1行で標準出力へ出します。出力先はアプリが持たず収集は基盤の責任とするため、Datadog / CloudWatch Logs / Grafana Loki のいずれにもアプリを変更せず接続できます。
- **リクエスト追跡**: 全リクエストに追跡ID(`requestId`)を発行し、そのリクエスト中の全ログに付与します。レスポンスヘッダー `X-Request-Id` でも返すため、問い合わせ内容からログを直接辿れます。
- **秘密情報**: パスワード・トークン・Cookie・リクエストボディ・クエリ文字列はログに渡さない設計です(マスキングではなく経路を作らない)。

```bash
# 特定のリクエストに関わるログを時系列で全て追う
docker compose logs backend --no-log-prefix | grep '<X-Request-Id の値>'
```
