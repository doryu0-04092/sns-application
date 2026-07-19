# SNS Application

X(旧Twitter)のようなテキストベースのコミュニケーションツールを想定した、社内向けSNSアプリケーションです。

## ドキュメント

- [要件定義書](docs/requirements.md)
- [機能一覧](docs/features.md)
- [画面設計](docs/screens.md)
- [ER図](docs/er-diagram.md)
- [API設計](docs/api-design.md)
- [技術スタック](docs/tech-stack.md)

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

バックエンドを頻繁に変更する場合の反復手順や技術選定の理由は [技術スタック](docs/tech-stack.md) を参照してください。
