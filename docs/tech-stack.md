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
| 認証 | 自前実装。BCryptPasswordEncoderでパスワードハッシュ化、`jjwt`ライブラリでJWTを発行・検証し、httpOnly + SameSite=Laxクッキーに格納。`JwtAuthFilter`で保護エンドポイントを検証 | 本プロジェクトの学習目的が「ログイン機能を作ること」自体であるため、NextAuth等のライブラリで仕組みを隠さず、ハッシュ化・トークン発行・検証を自分で実装して理解する |
| CORS | フロントのオリジン(`http://localhost:5173`)を許可し、`allowCredentials=true`を設定 | フロントとバックエンドが別オリジンとなるため、クッキーを用いた認証には明示的なCORS設定が必須 |
| フロントエンド | React 19 + Vite + TypeScript + `@tanstack/react-query` + `react-router-dom` + Tailwind CSS | Trelloと同じ技術選定。TanStack Queryでサーバー状態のキャッシュ・再検証を扱う |
| 画像保存 | バックエンドのローカルディスク(`uploads/`)に保存し、静的配信。`StorageService`インターフェースで抽象化 | `image_url`/`avatar_url`は文字列カラムのため、将来S3実装に差し替えてもDBスキーマ変更は不要 |
| バリデーション | Bean Validation(`jakarta.validation`) | 投稿・コメントの280文字制限など、エンティティ/DTOレベルで宣言的に検証する |

## ローカル開発環境のセットアップ手順(概要)

1. `.env.example`を`.env`にコピーし、値を設定する(DB名・ユーザー名・パスワード)
2. `docker compose up -d --build`でPostgreSQL + バックエンドを起動する(デフォルトポート: DB 5432, バックエンド 8080)。Flywayマイグレーションは起動時に自動適用される
3. フロントエンド起動: `cd frontend && npm install && npm run dev` (デフォルトポート: 5173、ネイティブ起動)

バックエンドのコードを頻繁に変更する場合は、`docker compose up -d postgres`でDBのみコンテナ起動し、`cd backend && mvn spring-boot:run`でバックエンドをネイティブ起動する方が反復が速い(`application.yml`は`DB_HOST`未設定時に`localhost`にフォールバックするため、そのまま接続できる)。

## スキーマ管理方針(Flyway)

- `backend/src/main/resources/db/migration/`配下に`V1__init.sql`のようなバージョン付きSQLファイルを置き、Flywayが起動時に適用する。
- ER図(docs/er-diagram.md)のテーブル定義をそのままDDLに変換する。エンティティの追加・変更時は新しいバージョンのマイグレーションファイルを追加する(既存ファイルは変更しない)。

## 将来のAWS移行に向けたメモ

- DBはPostgreSQLのまま、接続先をAWS RDSに向けるだけで移行できる想定(Flywayマイグレーションはそのまま適用可能)。
- 画像保存は`StorageService`をS3実装に差し替える(呼び出し側のコードは変更不要)。
- フロントエンド/バックエンドのデプロイ先(例: Amplify, ECS等)は本ドキュメントのスコープ外とし、実装が進んだ段階で別途検討する。
