# テスト計画

単体テストと結合テストの範囲、テスト項目、そして**あえてテストしない項目とその理由**をまとめる。

本書の当初のスコープに E2E テストは含めていなかったが、その後
**実ブラウザによる E2E 検証を1回実施した**（→ [E2Eテスト結果レポート](e2e-test-report.md)）。
本書が「E2E の領域」として先送りした項目がそこで検証されている。
ただし自動化はしておらず、CI では再実行されない。

本書が扱うのは「正しく動くか」であり、「どれだけ速いか・どこで壊れるか」は扱わない。
性能については [パフォーマンステスト計画](perf-test-plan.md) と
[パフォーマンステスト結果レポート](perf-test-report.md) を参照。

## 1. テスト層の構成

このリポジトリのテストは当初すべて `@SpringBootTest` + Testcontainers の結合テストだった。
正しさは検証できるが、コンテナと Spring コンテキストの起動を伴うため試行のたびに時間がかかる。
そこで **DB を必要としない層を分離**し、日常の試行はそこで回す。

| 層 | 対象 | 実行に必要なもの | 件数 | 置き場所 |
|---|---|---|---|---|
| **L1** Service 単体 | Service 8本の分岐 | なし（Mapper を Mockito でモック） | 189 | `backend/src/test/java/.../service/unit/` |
| **L1'** 横断部品の単体 | 認証フィルタ・JWT・ログ出力 | なし（`MockHttpServletRequest` と Mockito） | 34 | `backend/src/test/java/.../security/`, `.../logging/` |
| **L2** Web層スライス | Controller 8本 | なし（Service をモック、`@WebMvcTest`） | 148 | `backend/src/test/java/.../web/controller/` |
| **L3a** Mapper 単体 | Mapper XML の SQL を Service を介さず直接 | Docker（PostgreSQL） | 40 | `backend/src/test/java/.../mapper/` |
| **L3b** 結合 | Service〜SQL〜S3 を通した振る舞い | Docker（PostgreSQL + LocalStack） | 既存 | `backend/src/test/java/.../service/`, `.../web/` |
| **F1** フロント純ロジック | utils / hooks / api クライアント | なし | 71 | `frontend/src/**/*.test.ts` |
| **F2** コンポーネント | components / pages | なし（jsdom） | 97 | `frontend/src/**/*.test.tsx` |

L1 + L1' + L2 の 371 件が **Docker なしで十数秒**、フロントの 168 件が約7秒で完走する。

```powershell
# 日常の試行（Docker 不要）
cd backend; mvn -B test -Dtest='com.snsapp.backend.service.unit.*Test,com.snsapp.backend.web.controller.*Test,com.snsapp.backend.security.*Test,com.snsapp.backend.logging.*Test'
cd frontend; npm test

# 全部（Docker 必須）
cd backend; mvn -B test
```

### H2 を使わない理由

インメモリ DB で試行速度を上げる案は**採用しない**。MyBatis の XML SQL が PostgreSQL 固有構文
（`ON CONFLICT DO NOTHING` / `ILIKE` / `GENERATED ALWAYS AS IDENTITY` / 相関サブクエリ）に依存しており、
7つの Mapper XML すべてで使われている。H2 に載せ替えると SQL を書き換えるか二重管理する必要があり、
**本番と別の SQL を検証することになって、SQL の正しさを保証できなくなる**。

代わりに「SQL を検証しない層（L1/L2）」を分離することで試行速度を確保している。
SQL の検証は L3 の実 PostgreSQL でのみ行う。

---

## 2. ホワイトボックスとブラックボックスの使い分け

| 観点 | 手法 | 適用する層 | 書き方 |
|---|---|---|---|
| **ホワイトボックス** | 分岐網羅（C1） | L1 | 実装の `if` / 三項演算子 / 例外スロー を1本ずつ通す |
| **ブラックボックス** | 同値分割・境界値分析・デシジョンテーブル・状態遷移 | L2 / L3 / F2 | 実装を見ず、OpenAPI スキーマと仕様から書く |

**両方が必要な理由。** ホワイトボックスだけだと「実装された分岐は全部通ったが、実装し忘れた条件は永遠に見つからない」。
ブラックボックスだけだと「代表値は通ったが、内部の特定分岐に到達していない」。
たとえば `PostService#requireOwnedPost` は「存在しない」「削除済み」「他人のもの」の3分岐を持つが、
ブラックボックスの発想で「エラーケース」を1件だけ書くと2分岐が未到達のまま残る。

---

## 3. 正常系・異常系をどこまで検証できるか

### 3.1 検証できる（今回カバーする）

| 種別 | 内容 | 層 |
|---|---|---|
| 正常系 | Service 全メソッドの成功経路 | L1 |
| 正常系 | 全27エンドポイントの 200/201 とレスポンス形式 | L2 |
| 正常系 | SQL が実際に意図した行を返すこと（カーソル、ツームストーン、ILIKE 検索） | L3 |
| 正常系 | コンポーネントの描画と主要操作 | F2 |
| 異常系（例外） | カスタム例外 **16種すべて** | L1 |
| 異常系（入力） | Bean Validation 全項目の境界値 | L2 |
| 異常系（認可） | 401 / 403 / 404 の区別 | L1 + L2 |
| 異常系（HTTP） | 壊れた JSON、パラメータ欠落・型不一致、未知パス、500 | L2 |
| 異常系（外部） | S3 の HeadObject 404/403、形式違反、サイズ超過、削除失敗 | L1（モック） |
| 異常系（フロント） | `ApiError` 時のエラー表示、楽観的更新のロールバック、未認証リダイレクト | F2 |

### 3.2 工夫すれば検証できる

| 項目 | 方法 |
|---|---|
| S3 の障害系 | **L1 で `S3Client` をモックする**。LocalStack（L3）では 403 や接続失敗を再現できないため、ここは単体テストの方が強い |
| JWT の期限切れ | `JwtProperties` に 0 秒／負値の有効期限を注入。`Thread.sleep` は使わない（**実施済み**: `JwtServiceTest`） |
| 無限スクロール | `IntersectionObserver` を stub し「sentinel が可視になったら `fetchNextPage` が呼ばれる」まで。実スクロールは不可（→ [E2Eテスト結果レポート](e2e-test-report.md) で実スクロールを検証済み。重複・欠落なし） |
| 相対時刻表示 | `vi.setSystemTime()` で現在時刻を固定 |
| 画像アップロード | `File` オブジェクトを組み立て、S3 への PUT は `fetch` モックで代替 |

### 3.3 検証しない（できない、または割に合わない）

| 項目 | 理由 |
|---|---|
| DB の一意制約の競合（同時に2回いいね） | `ON CONFLICT DO NOTHING` に守られているが、レースの再現には並列制御が必要。得られる保証に対しコストが見合わない |
| ブラウザ実挙動（HttpOnly クッキーの実送信、CORS プリフライト、S3 への実 PUT の署名検証） | jsdom では再現できない。**E2E の領域**であり、この層では扱わない。→ [E2Eテスト結果レポート](e2e-test-report.md) 2章で**3項目とも実測済み（いずれも合格）** |
| クッキーの `Secure` 属性 | `AuthController.java:195` で `secure(false)` 固定。既知の課題であり、テストで固定すると将来の HTTPS 対応を縛る |
| Swagger UI が `JwtAuthFilter` の対象外である点 | 既定 OFF で回避済みの既知事項（README.md:27） |
| Flyway マイグレーションの互換性 | L3 のコンテナ起動時に毎回全マイグレーションが流れるため実質検証済み |
| 署名付き URL の有効期限が実際に切れること | 24時間待つ必要がある。期限値が渡っていることの確認までに留める |

---

## 4. テスト項目一覧

### 4.1 L1 — Service 単体（ホワイトボックス：分岐網羅）

DB を使わないため、Mapper の戻り値を自由に作れる。**実 DB では作りにくい状態**（削除済み、
存在しない ID、S3 障害）に到達できるのがこの層の価値。

#### AuthService — 専用テストが存在しなかった

| # | 分岐 | 期待 |
|---|---|---|
| A-1 | 未登録の email | ユーザーを insert、パスワードは `passwordEncoder.encode` を通した値 |
| A-2 | 登録済みの email | `DuplicateEmailException`（400 EMAIL_ALREADY_EXISTS）。**insert が呼ばれないこと** |
| A-3 | login: 存在するユーザー・パスワード一致 | `UserResponse` を返す |
| A-4 | login: ユーザーが存在しない | `InvalidCredentialsException` |
| A-5 | login: パスワード不一致 | `InvalidCredentialsException`。**A-4 と同一の例外**（ユーザー列挙攻撃を防ぐ設計を仕様として固定する） |
| A-6 | getCurrentUser: ユーザーが存在する | `UserResponse` を返す |
| A-7 | getCurrentUser: ユーザーが存在しない | `UnauthenticatedException`（401） |
| A-8 | avatarKey が null | `presignedGetUrl(null)` の結果（= null）がそのまま入る |

#### UserService — 専用テストが存在しなかった

| # | 分岐 | 期待 |
|---|---|---|
| U-1 | getProfile: 存在する | avatarUrl が署名付き URL に差し替わる |
| U-2 | getProfile: 存在しない | `UserNotFoundException`（404） |
| U-3 | updateProfile: avatarKey が null | `promote`/`delete` を**呼ばず**、既存の avatarKey を維持 |
| U-4 | updateProfile: avatarKey が空白文字のみ | U-3 と同じ（`isBlank()` の分岐） |
| U-5 | updateProfile: 新しい avatarKey | `promote` → **旧アイコンを `delete`** → 新キーで update、の順序を `InOrder` で検証 |
| U-6 | updateProfile: promote が失敗 | 例外が伝播し、**`userMapper.update` が呼ばれない**（中途半端な状態を作らない） |
| U-7 | searchUsers: query が null | `searchByDisplayName` に null が渡る（絞り込みなし） |
| U-8 | searchUsers: query が空白のみ | U-7 と同じ |
| U-9 | searchUsers: query に前後の空白 | `trim()` された値が渡る |
| U-10 | searchUsers: limit 0 / 1 / 50 / 51 | 1 / 1 / 50 / 50 にクランプ。Mapper には `clampedLimit + 1` が渡る |
| U-11 | searchUsers: 取得件数 > limit | `hasMore` 相当。`nextCursor` = 最後の要素の id、items は limit 件 |
| U-12 | searchUsers: 取得件数 ≤ limit | `nextCursor` が null |

#### FollowService — 専用テストが存在しなかった

| # | 分岐 | 期待 |
|---|---|---|
| FL-1 | follow: 自分自身 | `SelfFollowException`（400）。**ユーザー存在確認より先に判定される** |
| FL-2 | follow: 対象が存在しない | `UserNotFoundException`（404） |
| FL-3 | follow: 正常 | `insertIgnoreDuplicate` が呼ばれる |
| FL-4 | follow: 既にフォロー済み | 例外なし（SQL 側の `ON CONFLICT` に委ねている。L3 で検証） |
| FL-5 | **unfollow: 存在しないユーザー ID** | **例外なし・200**。冪等な設計の固定（→ 6.2） |
| FL-6 | listFollowers/listFollowing: 対象が存在しない | `UserNotFoundException` |
| FL-7 | listFollowers/listFollowing: limit 0 / 51 | 1 / 50 にクランプ |
| FL-8 | listFollowers/listFollowing: ページング | `nextCursor` の有無、avatarUrl の差し替え |

#### CommentService — 専用テストが存在しなかった

| # | 分岐 | 期待 |
|---|---|---|
| C-1 | listComments: 投稿が存在しない | `PostNotFoundException` |
| C-2 | listComments: 投稿がツームストーン（削除済みだが `findById` が返す） | **取得できる**（既存コメントの閲覧は許可される設計） |
| C-3 | createComment: 投稿が削除済み | `PostNotFoundException`。**ツームストーンへの新規追記は禁止**（C-2 と挙動が違う点が要点） |
| C-4 | createComment: parentCommentId が null | トップレベルコメントとして作成 |
| C-5 | createComment: 親が存在しない | `CommentNotFoundException` |
| C-6 | createComment: 親が削除済み | `CommentNotFoundException` |
| C-7 | createComment: 親が**別の投稿**のコメント | `CommentNotFoundException`（他投稿へのぶら下げ防止） |
| C-8 | update/deleteComment: コメントが存在しない | `CommentNotFoundException`（404） |
| C-9 | update/deleteComment: 既に削除済み | `CommentNotFoundException`（404）。二重削除も 404 |
| C-10 | update/deleteComment: 他人のコメント | `CommentForbiddenException`（403）。**C-8/C-9 と区別されること** |
| C-11 | deleteComment: 正常 | `softDelete` が呼ばれる（物理削除ではない） |

#### S3StorageService — LocalStack では到達できない分岐を突く

| # | 分岐 | 期待 |
|---|---|---|
| S-1 | createUploadUrl: jpeg/png/webp/gif | 対応する拡張子付きの `pending/<uuid>.<ext>` キー |
| S-2 | createUploadUrl: 許可外の contentType | `InvalidImageTypeException` |
| S-3 | promote: key が null | `InvalidImageTypeException`。**S3 を一切呼ばない** |
| S-4 | promote: `pending/` で始まらない | 同上（他人のオブジェクトを移動させられない） |
| S-5 | promote: `..` を含む | 同上（パストラバーサル防止） |
| S-6 | promote: HeadObject が **404** | `InvalidImageTypeException` |
| S-7 | promote: HeadObject が **403** | `InvalidImageTypeException`（実 AWS の挙動。LocalStack では再現不可） |
| S-8 | promote: HeadObject が **500** | **`S3Exception` がそのまま伝播**（握りつぶさない） |
| S-9 | promote: 実体の contentType が許可外 | `InvalidImageTypeException`（拡張子偽装の検出） |
| S-10 | promote: contentLength が 5MB ちょうど | **成功**（境界値） |
| S-11 | promote: contentLength が 5MB + 1 | `ImageTooLargeException` かつ **pending を delete する**副作用 |
| S-12 | promote: contentLength が null | サイズ判定をスキップして成功 |
| S-13 | promote: 正常 | copy → delete の順、finalKey が `<category>/<uuid>.<ext>` |
| S-14 | presignedGetUrl: null / 空文字 | **null を返す**（例外にしない） |
| S-15 | delete: null / 空文字 | 何もしない。S3 を呼ばない |
| S-16 | delete: `SdkException` 発生 | **例外を投げない**（画像削除の失敗で投稿削除を巻き込まない） |

#### PostService / LikeService / CommentLikeService / RefreshTokenService

L3 に既存テストがあるが、モックでしか作れない状態を L1 で補う。

| # | 分岐 | 期待 |
|---|---|---|
| P-1 | listFeed: feed が `all` / `following` 以外 | `InvalidFeedParameterException` |
| P-2 | listFeed: limit 0 / 1 / 50 / 51 | 1 / 1 / 50 / 50 にクランプ。**Mapper には clampedLimit+1 が渡る**ことを検証 |
| P-3 | listFeed: authorId 指定あり | feed の値に関わらず `findByAuthor` が呼ばれる |
| P-4 | listFeed: authorId なし + `following` | `findFeedFollowing` |
| P-5 | listFeed: authorId なし + `all` | `findFeedAll` |
| P-6 | listFeed: 空リスト | `withImages` が早期 return し、**画像取得クエリを発行しない**（N+1 対策の確認） |
| P-7 | listFeed: 複数投稿 | 画像取得が**1クエリにまとまる**（`findByPostIds` の呼び出し回数が1回） |
| P-8 | createPost: 画像 4枚 / 5枚 | 成功 / `TooManyImagesException`（境界値） |
| P-9 | createPost: 2枚目の promote が失敗 | 例外が伝播し、**`postMapper.insert` が呼ばれない** |
| P-10 | createPost: 画像の順序 | `display_order` が 0,1,2... の順で insert される |
| P-11 | requireOwnedPost: 存在しない / 削除済み / 他人 | 404 / 404 / 403 の**3分岐の区別** |
| P-12 | deletePost: 正常 | S3 delete → post_images 物理削除 → posts 論理削除 の順序 |
| P-13 | withImages: 投稿が削除済み | **imageUrls が必ず空**（ツームストーンから画像 URL を漏らさない） |
| LK-1 | like: 投稿が存在しない / 削除済み | `PostNotFoundException` |
| LK-2 | like: 自分の投稿 | `PostSelfLikeException` |
| LK-3 | **unlike: 存在しない投稿 ID** | **例外なし・200**（冪等な設計の固定。→ 6.2） |
| CL-1〜3 | CommentLikeService も同型 | `CommentNotFoundException` / `CommentSelfLikeException` / unlike は無検査 |
| R-1 | rotate: ハッシュ一致なし | `InvalidRefreshTokenException` |
| R-2 | rotate: 失効済みトークンの再利用 | 401 かつ **`revokeAllForUser` が呼ばれる**（盗用検知） |
| R-3 | rotate: 期限切れ | `InvalidRefreshTokenException` |
| R-4 | rotate: 正常 | 旧トークン revoke + 新トークン発行 |
| R-5 | issue | DB に入るのは **SHA-256 ハッシュで、生トークンではない** |
| R-6 | revoke: 該当なし | 例外を投げない |

### 4.1' L1' — 横断部品の単体（認証・ログ）

全リクエストが通る共通部品。Service より手前にあるため、ここが破れると
どのエンドポイントも守られない。**「通してよいものだけを通す」判定を1分岐ずつ固定する**。

#### JwtService — アクセストークンの発行と検証

「正しく往復できること」より **「不正なトークンを拒否すること」** に重点を置く。
署名アルゴリズムの実装自体は jjwt の責務（5節）。検証するのは**使い方**。

| # | 分岐 | 期待 |
|---|---|---|
| J-1 | 発行 → 検証 | subject から userId を復元できる |
| J-2 | 有効期限 | 設定値どおりの `exp` が入る |
| J-3 | **期限切れ** | 検証に失敗する。有効期限に負値を注入して再現（`Thread.sleep` は使わない） |
| J-4 | **別のシークレットで署名** | 拒否。攻撃者が自作したトークンを弾く |
| J-5 | **payload の改ざん** | 拒否。`sub` を他人のIDへ書き換えても署名が合わない |
| J-6 | **署名なし（alg=none 型）** | 拒否。署名検証を必須にしていないと通ってしまう |
| J-7 | JWT の形をしていない / 空文字 | 拒否 |
| J-8 | subject が数値でない | `NumberFormatException`。`JwtAuthFilter` が捕まえて401にするため500にはならない |

#### JwtAuthFilter — 認証の関門

| # | 分岐 | 期待 |
|---|---|---|
| JF-1 | `/api/` 配下以外 | 素通り（`JwtService` を呼ばない）。Swagger UI・静的リソースが該当 |
| JF-2 | OPTIONS（プリフライト） | 素通り。ブラウザがクッキーを付けないため認証を要求すると必ず失敗する |
| JF-3 | 公開4パス（signup / login / refresh / health） | 素通り |
| JF-4 | **`/api/health/details`** | **401**。公開パスが前方一致ではなく**完全一致**であることの固定 |
| JF-5 | 有効なトークン | `currentUserId` 属性を設定して後続へ |
| JF-6 | クッキー無し / `auth_token` 以外のみ | 401、後続を呼ばない |
| JF-7 | 検証失敗（期限切れ・改ざん等） | 401。`currentUserId` は設定されない |
| JF-8 | 401 の本文 | `{"error":{"code":"UNAUTHENTICATED",...}}`。ここだけ形式が違うと認証切れのときだけエラー表示が壊れる |

#### RequestLoggingFilter — 追跡IDとアクセスログ

運用設計は [operations.md](operations.md) を参照。

| # | 分岐 | 期待 |
|---|---|---|
| RL-1 | 通常のリクエスト | メソッド・パス・ステータス・所要時間がログに出る |
| RL-2 | **クエリ文字列** | **ログに出さない**（トークン等の混入経路を作らない） |
| RL-3 | 追跡ID | `X-Request-Id` に付与。リクエストごとに異なる |
| RL-4 | 後続処理の中 | MDC から追跡IDを参照できる（全ログに自動で付く） |
| RL-5 | **処理後 / 例外時** | **MDC がクリアされる**。怠るとスレッド使い回しで別リクエストへ漏れる |
| RL-6 | 認証済み / 未認証 | 利用者IDが付く / 付かない |
| RL-7 | ヘルスチェック・プリフライト | DEBUG へ格下げ。本番(INFO)では出力されない |

### 4.2 L2 — Controller（ブラックボックス：境界値・同値分割・デシジョンテーブル）

Service をモックするため、**入出力の契約だけ**を見る。ビジネスロジックは L1 の担当。

#### 境界値（`@Valid` の検証）

| 対象 | 値 | 期待 |
|---|---|---|
| 投稿・コメント本文 | 0文字 / 空白のみ | 400 VALIDATION_ERROR（`@NotBlank`） |
| 投稿・コメント本文 | 279 / 280 / 281 文字 | 200 / 200 / 400 |
| パスワード | 7 / 8 / 72 / 73 文字 | 400 / 201 / 201 / 400 |
| displayName | 0 / 100 / 101 文字 | 400 / 200 / 400 |
| bio | 500 / 501 文字 | 200 / 400 |
| imageKeys | 4 / 5 個 | 200 / 400 |
| contentTypes | 0 / 4 / 5 個 | 400（`@NotEmpty`） / 200 / 400 |
| email | `a@b.co` / `not-an-email` | 200 / 400 |

#### 同値分割

- `feed` = `all`（有効） / `following`（有効） / `invalid`（400 INVALID_FEED） / 未指定（既定値）
- `limit` = 負値 / 0 / 1 / 50 / 51 / 巨大値 → いずれも Service 側でクランプされ 200
- `postId` = 数値 / 文字列 → 200 / 400 VALIDATION_ERROR（`MethodArgumentTypeMismatch`）

#### デシジョンテーブル（投稿の操作）

| 投稿の状態 | 操作者 | GET | PATCH | DELETE |
|---|---|---|---|---|
| 存在・自分 | 本人 | 200 | 200 | 204/200 |
| 存在・他人 | 他人 | 200 | **403** | **403** |
| 削除済み（返信あり＝ツームストーン） | 本人 | 200（body が null） | **404** | **404** |
| 削除済み（返信なし） | 本人 | **404** | 404 | 404 |
| 存在しない | 誰でも | 404 | 404 | 404 |
| 任意 | **未認証** | **401** | 401 | 401 |

コメントも同型の表を作る。

#### エラーレスポンスの形式

| ケース | 期待 |
|---|---|
| Service が `ApiException` を投げる | 対応するステータス + `{"error":{"code","message"}}` |
| Service が想定外の `RuntimeException` を投げる | **500 INTERNAL_ERROR**。例外メッセージが**クライアントに漏れない**こと |
| 壊れた JSON を送る | 400 VALIDATION_ERROR |
| 必須クエリパラメータの欠落 | 400 VALIDATION_ERROR |
| 存在しないパス | 404 NOT_FOUND |
| 複数フィールドが同時に不正 | 400 VALIDATION_ERROR。**メッセージ文字列は断定しない**（→ 6.4） |
| 定義されていないHTTPメソッド | 405 METHOD_NOT_ALLOWED（→ 6.1 不具合2で修正） |

#### 認証クッキー（AuthController）

| ケース | 期待 |
|---|---|
| signup / login 成功 | `auth_token` と `refresh_token` の2枚、いずれも **HttpOnly** |
| refresh: クッキーなし | 401 INVALID_REFRESH_TOKEN |
| logout: クッキーあり／なし | **どちらも 200**（冪等）、両クッキーが `Max-Age=0` |

### 4.3a L3a — Mapper 単体（実 PostgreSQL に対して SQL を直接実行）

**なぜ Service 経由では足りないか。** SQL の検証を「その Mapper を使う Service の結合テスト」に任せると、
**結合テストを持たない Service の Mapper が丸ごと抜け落ちる**。実際に監査したところ、
以下は実 DB に対して一度も実行されていなかった（Service 単体テストは Mapper をモックするため、
何件通っても SQL の誤りは検出できない）。

| 未実行だった SQL | 壊れても気づけなかった機能 |
|---|---|
| `FollowMapper.findFollowers` / `findFollowing` | フォロワー・フォロー中一覧（JOIN の向き、カーソルページング） |
| `FollowMapper.delete` | アンフォロー |
| `CommentLikeMapper.delete` | コメントのいいね解除 |
| `UserMapper.findProfileById` | プロフィールのフォロワー数・フォロー中数・フォロー済み判定（相関サブクエリ4本） |

「テストが存在するか」ではなく **「そのコードを通るテストが存在するか」** で見る必要がある。

**H2 を使わない。** インメモリ DB でこの層を作る案は採らない（→ 1節）。
`ON CONFLICT DO NOTHING` / `ILIKE` / `GENERATED ALWAYS AS IDENTITY` は PostgreSQL 固有で、
H2 では**通ってしまう or 挙動が変わる**。本番と別の SQL を検証しても意味がない。

| 対象 | 主な検証項目 |
|---|---|
| `FollowMapper` | フォロー/アンフォローの反映、**二重フォローで一意制約違反にならない**（`ON CONFLICT`）、followers と following の向きが逆であること、`isFollowing` が閲覧者ごとに判定されること、カーソルページングで重複・欠落が出ないこと、新しい順に並ぶこと |
| `UserMapper` | プロフィールの follower/following カウントが別々に数えられること、`isMine` / `isFollowing` の判定、**`ILIKE` が大文字小文字を無視すること**、検索結果に自分が含まれないこと、insert で ID が埋め戻されること、update が他ユーザーに波及しないこと |
| `CommentLikeMapper` | いいねの反映と解除、二重いいねでカウントが1のままであること、**解除が自分のいいねだけを消すこと**（WHERE 句から `user_id` が抜けると全員分消える）、`isLiked` が閲覧者ごとに判定されること |

各テストは `@Transactional` でロールバックされ、**実行順序に依存しない**。
前のテストが作ったデータを前提にすると、実行順が変わった瞬間に壊れる。

### 4.3b L3b — 結合（Service〜SQL〜S3 を通した振る舞い）

既存の8ファイルは削除も縮小もしない。この層でしか検証できないものだけを担う。

- MyBatis XML の SQL が意図した行を返すこと（カーソルページング、`sinceId` による昇順反転、ツームストーンの可視性）
- `ON CONFLICT DO NOTHING` による二重いいね・二重フォローの冪等性
- `ILIKE` の大文字小文字を無視した部分一致、自分自身の除外
- Flyway マイグレーションが通ること、UNIQUE / NOT NULL 制約が実際に効くこと
- LocalStack に対する実際の署名付き URL の生成とオブジェクト操作
- OpenAPI スキーマと `docs/openapi.json` の一致

### 4.4 F1 — フロント純ロジック

| 対象 | 項目 |
|---|---|
| `buildCommentTree` | 空配列 / 単一 / 2階層 / 3階層以上 / 同一階層の順序保持 / **親が一覧に無い孤児コメントが消える**こと |
| `formatRelativeTime` | 0秒 / 59秒 / 60秒 / 59分 / 60分 / 23時間 / 24時間 / 6日 / 7日（→ 日付表記へ切り替わる境界）。`vi.setSystemTime` で固定 |
| `useCharCount` | 0 / 279 / 280 / 281 文字での `remaining` と `isOver`。**サロゲートペア（絵文字）が `String.length` で2文字として数えられる**現挙動の確認 |
| `api/*.ts` ラッパ | 組み立てられる URL とクエリ文字列、リクエストボディ |
| `apiFetch`（既存） | 変更しない |
| `queryKeys`（既存） | 変更しない |

### 4.5 F2 — コンポーネント

| # | 対象 | 項目 |
|---|---|---|
| 1 | `ProtectedRoute` | ローディング中の表示 / 認証エラー時に `/login` へリダイレクト / 認証済みなら children を描画 |
| 2 | `LoginPage` / `SignupPage` | 入力と送信 / 成功時にキャッシュへ投入し `/home` へ遷移 / `ApiError` のメッセージ表示 / それ以外の例外は固定文言 / 送信中はボタン無効 |
| 3 | `PostComposer` / `CommentForm` | 280文字を超えると送信できない / 残り文字数の表示 / 送信成功で入力がクリアされる / エラー表示 |
| 4 | `LikeButton` / `FollowButton` | クリックで即座に見た目が変わる（楽観的更新） / **失敗時に元に戻る** / 連打時の挙動 |
| 5 | `PostDetailCard` / `CommentThread` | 本人には編集・削除ボタンが出る / 他人には出ない / ツームストーンの表示 / 再帰的なネスト描画 |
| 6 | `AppHeader` | ログアウトでキャッシュが無効化され `/login` へ遷移 |

#### 未着手（この層で計画したが、まだ書いていない）

網羅の判断としては書くべきだが、優先度の都合で手を付けていないもの。
「意図的に書かない」（5節）とは区別する。

| 対象 | 状況 |
|---|---|
| `PostCard` | テストが無い。`Avatar` を間接的にカバーする前提だったが成立していない（下記） |
| `CommentLikeButton` | テストが無い。`LikeButton` と同型のため優先度は低いが、楽観的更新のロールバックは独立して壊れうる |
| `NewPostsBanner` | テストが無い |
| 主要画面（`TimelinePage` / `PostDetailPage` / `ProfilePage` / `ProfileEditPage` / `SearchPage` / `FollowListPage`） | テストが無い。F2 でテストがあるのは `LoginPage` / `SignupPage` の2画面のみ |
| hooks（`usePostsFeed` / `useInfiniteScrollSentinel` / `useNewPostsBanner` / `useUserSearch` / `useUserConnections` / `useUserPosts` / `useCurrentUser`） | `useCharCount` 以外はテストが無い |

現状のフロントエンドは **代表例を絞って検証する方針**であり、バックエンドのような網羅は行っていない。
バックエンドが Service 8本・Controller 7本・カスタム例外16種すべてを通しているのとは方針が異なる。

---

## 5. テストしない項目とその理由

「網羅率を上げる」ためだけのテストは、壊れやすさだけを増やして不具合を見つけない。
以下は**意図的に書かない**。

| 対象 | 理由 |
|---|---|
| Entity（`User` / `Post` 等）の getter / setter | 分岐が無い。値を保持するだけで、壊れようがない |
| DTO（`record`）の `equals` / `hashCode` / アクセサ | 言語仕様が生成する。テストするのはコンパイラの検証であって自分のコードではない |
| `ApiResponse` / `ApiError` の構造そのもの | `ApiContractTest` と `OpenApiSnapshotTest` が既に固定している。三重に固定しない |
| Mapper インターフェースを**モックした**単体テスト | 実装の本体は XML の SQL。**モックしたら検証対象が消える**。SQL は実 DB でのみ検証できる（→ L3a で Mapper を直接呼ぶ形にした） |
| `BCryptPasswordEncoder` / `jjwt` の暗号処理そのもの | ライブラリの責務。自前で検証し直さない（**使い方**は検証する。例：R-5 のハッシュ保存） |
| private メソッドの直接呼び出し（リフレクション等） | public 経由で全分岐に到達できる。到達できないなら、それは不要なコード |
| Tailwind のクラス名のアサート | 実装詳細。見た目を変えただけで落ちるが、落ちても不具合ではない |
| `src/types/*.ts`（生成型の再エクスポート） | 型のみで実行時のコードが無い。`tsc -b` が CI で検証済み |
| `Avatar` 単体 | 分岐は「avatarUrl の有無」1つ。ただし**カバー元として想定した `PostCard` にテストが無いため、現状は誰も通っていない**（4.5 の未着手を参照） |
| 生成物 `src/api/generated/` | 生成コード。CI が再生成の差分をチェックしている |
| `HealthController` の詳細 | DB 疎通の1行。L3 のコンテナ起動が通ること自体が検証になっている |
| E2E（Playwright / Cypress） | **自動化はしない。** 手動での E2E 検証は実施済み（→ [E2Eテスト結果レポート](e2e-test-report.md)）。資産化には `data-testid` の付与が前提になるため、同レポート9章に着手順序だけを残している |
| **カバレッジ 100% を目標にすること** | 目標にしない。上記を埋めるだけの作業に化け、テストの信頼性はむしろ下がる |

---

## 6. テスト作成で見つかった不具合と論点

### 6.1 修正した不具合（3件）

いずれも新しいテストが実際に落ちて発覚したもの。修正のうえ、再発を防ぐテストを残した。

#### 不具合1：`contentTypes` に null 要素があると 500 になる

`PresignUploadRequest` の `@NotEmpty` / `@Size(max=4)` は**リスト自体しか検証せず、要素は検証しない**。
そのため `{"contentTypes": [null]}` はバリデーションを通り、`UploadController` が
`createUploadUrl(null)` を呼ぶ。`S3StorageService` は `Map.of(...)` を使っており、
`Map.of()` は `get(null)` で **NullPointerException** を投げる（不変マップは null キーを拒否する）。
結果、他の不正な形式が 400 を返すのに、null だけ **500 INTERNAL_ERROR** になっていた。

→ `S3StorageService#createUploadUrl` の先頭で null を弾き、400 `INVALID_IMAGE_TYPE` に揃えた。
テスト：`S3StorageServiceTest`（S-2 に null を追加）、`UploadControllerTest`。

#### 不具合2：誤った HTTP メソッドが 405 ではなく 500 になる

`GlobalExceptionHandler` は `HttpRequestMethodNotSupportedException` を扱っておらず、
`@ExceptionHandler(Exception.class)` の catch-all に落ちていた。
たとえば `PATCH /api/users/7`（`GET` のみ定義されたパス）は Spring が 405 を投げるが、
クライアントには **500 INTERNAL_ERROR** が返り、サーバーログにも ERROR が積もる。
`NoResourceFoundException` を個別にハンドルしているのと同じ理由で、これも個別に扱うべきだった。

→ ハンドラを追加し、405 `METHOD_NOT_ALLOWED` を返すようにした。
テスト：`UserControllerTest`。

#### 不具合3：`["me"]` クエリキーが5箇所に文字列直書き

`queryKeys.ts` に集約されておらず、`useCurrentUser.ts` / `LoginPage.tsx` / `AppHeader.tsx` /
`SignupPage.tsx` / `ProfileEditPage.tsx` の5箇所に散在していた（当初3箇所と見積もったが、
全文検索で2箇所多かった）。キーの不一致は TypeScript が検出できないため、
**エラーが出ないままキャッシュ更新だけが効かなくなる**という壊れ方をする。

→ `meKeys` を `queryKeys.ts` に追加し、5箇所すべてを置き換えた。

### 6.2 現状を固定した挙動（意図的な設計）

#### `unlike` / `unfollow` は存在確認をしない（冪等）

`POST /api/posts/999/like` は 404 だが `DELETE /api/posts/999/like` は 200 を返す。
Service 層のコードだけを見ると実装漏れに見えるが、**Controller 層で意図的に文書化されている**
（`LikeController` / `FollowController` の OpenAPI 説明に「エラーにならず200を返す（冪等）」と明記、
さらに `@OpenApiConfig.SkipNotFound` でスキーマからも 404 を外している）。

→ 設計として妥当。契約が崩れていないことをテストで固定する
（`LikeServiceUnitTest` LK-3、`LikeControllerTest`、`FollowControllerTest`）。

#### 全角スペースだけの本文は投稿できる

`@NotBlank` は Hibernate Validator 内部で `String.trim()` を使い、`trim()` は **U+0020 以下の
コードポイントしか除去しない**。全角スペース U+3000 は残るため空文字と判定されない。
一方 `String.isBlank()` は Unicode 対応で全角スペースも空白と見なすため、
同じ「空白のみ」でも判定が割れる。

→ 見た目が空の投稿を許すかは仕様判断のため、現状を固定するに留めた
（`PostControllerTest`「全角スペースのみの本文は現状では投稿できてしまう」）。
弾きたくなった場合はこのテストが落ちて変更に気づける。

### 6.3 記録のみ（今回は対応しない）

#### `UserService#updateProfile` がユーザーの存在を確認していない

`UserService.java:44-45`：

```java
User existing = userMapper.findById(currentUserId);
String avatarKey = existing.getAvatarKey();   // existing が null なら NPE
```

`AuthService#getCurrentUser` は同じ状況で `UnauthenticatedException`（401）を投げるのに対し、
ここは **NPE → 500** になる。JWT が有効なまま該当ユーザーが DB から消えた場合に到達するが、
**退会機能が無い現状では到達経路が存在しない**ため、テストも書いていない。

#### 外部キーに `ON DELETE` の指定が無い

V1〜V4 の全外部キーが PostgreSQL 既定の `NO ACTION`。子行があると親を物理削除できない。
現状は論理削除のみで物理削除の経路が存在しないため顕在化しない。
退会機能を作る際に「カスケード削除にするか、退会も論理削除にするか」を決める必要がある。

### 6.4 テスト実装上の注意

#### バリデーションエラーのメッセージを断定しない

`GlobalExceptionHandler.java:30-32` に「先頭1件だけ返す」設計意図と拡張方法が明記されており、
設計としては妥当。ただし `getFieldErrors()` の順序は Bean Validation 仕様上**保証されていない**。

複数フィールドが同時に不正なテストケースでは、`code == "VALIDATION_ERROR"` と HTTP 400 までを検証し、
**メッセージ文字列は断定しない**。ライブラリのバージョン更新で原因の分かりにくい失敗を招くため。
単一フィールドのみ不正なケースではメッセージを断定してよい。

#### `mutationFn` は2引数で呼ばれる

react-query v5 は `mutationFn` を `(variables, context)` で呼ぶ。
`expect(fn).toHaveBeenCalledWith({...})` は引数の数まで一致を求めるため落ちる。
`fn.mock.calls[0][0]` で第1引数だけを見ること。

#### テスト用 QueryClient の `gcTime` を 0 にしない

`gcTime: 0` にすると `setQueryData` で入れた値が観測者のいない時点で即座に破棄され、
「ログイン成功でキャッシュへ入る」といった検証ができなくなる。
クライアントはテストごとに作り直すので、持ち越しの心配は元々ない。

#### テストファイルも `tsc -b` の検査対象

`tsconfig.app.json` の `include: ["src"]` によりテストも型検査される。
過去に `c07cc29` でテストの型付けがビルドを壊した経緯があり、今回も
`IntersectionObserver` の stub で同じ落ち方をした（`scrollMargin` の実装漏れ）。
**`npm test` が通ってもビルドは別に確認すること。**

---

## 7. 完了条件

- `mvn test` と `npm test` が両方グリーン。既存テストを1件も壊していない
- `npm run build`（`tsc -b`）が通る。`include: ["src"]` によりテストファイルも型検査対象
- **L1 + L2 が Docker を起動せずに完走する**（層分離ができている証拠）
- `docs/openapi.json` に差分が出ない（プロダクトコードの API を変更していない）
