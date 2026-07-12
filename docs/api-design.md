# API設計

## 基本方針

- REST + JSON。バックエンド(Spring Boot)がAPIを提供し、フロントエンド(React)が`fetch`/`axios`経由で呼び出す。
- ベースURL: `http://localhost:8080/api`(ローカル開発時)
- 認証はhttpOnlyクッキーに格納したJWTで行う。フロントエンドからのリクエストは`credentials: include`で送信する。

## レスポンス規約

成功時:
```json
{ "data": { } }
```

エラー時:
```json
{ "error": { "code": "NOT_FOUND", "message": "投稿が見つかりません" } }
```

- 認証エラー: `401 Unauthorized`
- 権限エラー(他人の投稿を編集/削除しようとした等): `403 Forbidden`
- 存在しないリソース: `404 Not Found`
- バリデーションエラー(280文字超過等): `400 Bad Request`

## ページネーション

タイムライン・コメント一覧はカーソルベースページネーションを採用する。

- リクエスト: `?cursor=<最後に取得した投稿のID>&limit=20`
- レスポンス: `{ "data": { "items": [...], "nextCursor": "123" } }`(`nextCursor`が`null`なら末尾)

## エンドポイント一覧

### 認証

| メソッド | パス | 概要 | 認証要否 |
|---|---|---|---|
| POST | /api/auth/signup | 新規ユーザー登録(email, password, displayName) | 不要 |
| POST | /api/auth/login | ログイン。成功時、JWTをhttpOnlyクッキーにセット | 不要 |
| POST | /api/auth/logout | ログアウト。クッキーを無効化 | 要 |
| GET | /api/auth/me | ログイン中ユーザー情報を取得 | 要 |

### ユーザー/プロフィール

| メソッド | パス | 概要 | 認証要否 |
|---|---|---|---|
| GET | /api/users?query={displayName} | 表示名の部分一致でユーザーを検索(F-15) | 要 |
| GET | /api/users/{userId} | プロフィール取得(投稿一覧・フォロー数/フォロワー数を含む)(F-13) | 要 |
| PATCH | /api/users/me | 自分のプロフィール編集(displayName, bio, avatarUrl)(F-14) | 要 |
| GET | /api/users/{userId}/followers | フォロワー一覧(F-18) | 要 |
| GET | /api/users/{userId}/following | フォロー中一覧(F-18) | 要 |
| POST | /api/users/{userId}/follow | フォローする(F-12) | 要 |
| DELETE | /api/users/{userId}/follow | フォロー解除する(F-12) | 要 |

### タイムライン/投稿

| メソッド | パス | 概要 | 認証要否 |
|---|---|---|---|
| GET | /api/posts?feed=all\|following&cursor=&limit= | タイムライン取得。`feed=all`で全体、`feed=following`でフォロー中のみ(F-03) | 要 |
| POST | /api/posts | 投稿作成(body: テキスト280文字以内, images: 画像複数枚, multipart/form-data)(F-04, F-05) | 要 |
| GET | /api/posts/{postId} | 投稿詳細取得(いいね数・コメント数を含む)(F-06) | 要 |
| PATCH | /api/posts/{postId} | 自分の投稿を編集(body)(F-21) | 要(本人のみ) |
| DELETE | /api/posts/{postId} | 自分の投稿を削除(論理削除)(F-16) | 要(本人のみ) |

### いいね

| メソッド | パス | 概要 | 認証要否 |
|---|---|---|---|
| POST | /api/posts/{postId}/like | いいねする(F-10) | 要 |
| DELETE | /api/posts/{postId}/like | いいねを解除する(F-10) | 要 |

### コメント(ネスト返信含む)

| メソッド | パス | 概要 | 認証要否 |
|---|---|---|---|
| GET | /api/posts/{postId}/comments | コメント一覧取得(ネスト構造で返却)(F-07, F-08, F-09) | 要 |
| POST | /api/posts/{postId}/comments | コメント投稿。`parentCommentId`を指定すると返信になる(F-07, F-08) | 要 |
| PATCH | /api/comments/{commentId} | 自分のコメントを編集(F-22) | 要(本人のみ) |
| DELETE | /api/comments/{commentId} | 自分のコメントを削除(論理削除)(F-17) | 要(本人のみ) |

## v1における簡略化(明示的な割り切り)

- 画像は投稿作成時にのみ添付可能とし、作成後の画像の追加・削除・並び替えAPIは設けない。
- いいね・フォローは「作成(POST)」「解除(DELETE)」の2エンドポイントとし、トグル1本化はしない(DBのユニーク制約とAPIの意味を一致させるため)。
- コメント一覧は`GET /api/posts/{postId}/comments`で常に全件+ネスト構造を返す方針とし、コメント自体のページネーションは初期スコープでは設けない(コメント数が増えた場合は将来検討)。
