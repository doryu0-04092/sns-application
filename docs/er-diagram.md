# ER図

```mermaid
erDiagram
    USERS ||--o{ POSTS : "投稿する"
    USERS ||--o{ COMMENTS : "コメントする"
    USERS ||--o{ LIKES : "いいねする"
    USERS ||--o{ COMMENT_LIKES : "コメントにいいねする"
    USERS ||--o{ FOLLOWS : "フォローする(follower)"
    USERS ||--o{ FOLLOWS : "フォローされる(followee)"
    USERS ||--o{ REFRESH_TOKENS : "リフレッシュトークンを持つ"
    POSTS ||--o{ POST_IMAGES : "画像を持つ"
    POSTS ||--o{ COMMENTS : "コメントされる"
    POSTS ||--o{ LIKES : "いいねされる"
    COMMENTS ||--o{ COMMENTS : "返信を持つ(親子)"
    COMMENTS ||--o{ COMMENT_LIKES : "いいねされる"

    USERS {
        bigint id PK
        string email UK
        string password_hash
        string display_name
        string bio
        string avatar_url
        datetime created_at
        datetime updated_at
    }

    POSTS {
        bigint id PK
        bigint user_id FK
        text body
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    POST_IMAGES {
        bigint id PK
        bigint post_id FK
        string image_url
        int display_order
        datetime created_at
    }

    COMMENTS {
        bigint id PK
        bigint post_id FK
        bigint user_id FK
        bigint parent_comment_id FK "自己参照。NULLならトップレベルコメント"
        text body
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }

    LIKES {
        bigint id PK
        bigint post_id FK
        bigint user_id FK
        datetime created_at
    }

    COMMENT_LIKES {
        bigint id PK
        bigint comment_id FK
        bigint user_id FK
        datetime created_at
    }

    FOLLOWS {
        bigint id PK
        bigint follower_id FK "フォローする側のuser_id"
        bigint followee_id FK "フォローされる側のuser_id"
        datetime created_at
    }

    REFRESH_TOKENS {
        bigint id PK
        bigint user_id FK
        string token_hash UK "生トークンのSHA-256ハッシュ"
        datetime expires_at
        datetime revoked_at "NULLなら有効"
        datetime created_at
    }
```

## 補足

- `LIKES` は `(post_id, user_id)` の組み合わせで一意制約を設け、同一ユーザーが同一投稿に重複していいねできないようにする。
- `COMMENT_LIKES` は `(comment_id, user_id)` の組み合わせで一意制約を設け、同一ユーザーが同一コメントに重複していいねできないようにする。いずれも自分自身の投稿・コメントへのいいねはアプリケーション層で禁止する。
- `FOLLOWS` は `(follower_id, followee_id)` の組み合わせで一意制約を設け、重複フォローを防止する。
- `COMMENTS.parent_comment_id` はコメントへの返信(ネスト)を表現する自己参照外部キー。NULLの場合は投稿に対する直接コメント。
- 投稿数・コメント数・いいね数は都度カウントするか、非正規化してキャッシュ列(例: `POSTS.like_count`, `POSTS.comment_count`)を持たせるかは実装フェーズで検討する。
- `REFRESH_TOKENS`は使用の都度ローテーション(新規発行+旧トークンの`revoked_at`セット)する方式。既に`revoked_at`が設定済みのトークンが再度提示された場合はトークン盗用の兆候とみなし、該当ユーザーの全トークンを一括失効させる。
