-- パフォーマンステスト用のシードデータ投入。
--
-- 使い方(件数は psql 変数で渡す。呼び出し例は perf/README.md を参照):
--   psql -v ON_ERROR_STOP=1 -v bcrypt_hash="<BCryptハッシュ>"
--        -v users_n=500 -v posts_n=20000 -v comments_n=60000
--        -v likes_n=100000 -v follows_n=10000 -v comment_likes_n=30000 -f seed.sql
--
-- API経由ではなくSQLで直接投入する理由:
--   2万件の投稿をAPIで作ると投入だけで十数分かかり、投入行為そのものが負荷テストになってしまう。
--
-- ただしSQL直接投入はアプリの不変条件(UNIQUE制約・親コメントのpost_id整合・自己フォロー禁止)を
-- 迂回するため、投入後に必ず verify.sql で検証すること。
--
-- 分布は乱数ではなく素数による剰余で決めている。乱数だと実行ごとにデータが変わり、
-- 「前回より遅い」がデータのせいかアプリのせいか区別できなくなるため、再現性を優先する。

\set ON_ERROR_STOP on
\timing on

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. ユーザー
--    password_hash は BCrypt なのでSQLでは生成できない。呼び出し側が signup API で
--    1件だけ作ったハッシュを渡し、それを全ユーザーで使い回す。
--    これで全VUが同じパスワードでログインでき、BCrypt検証のコストは本番と同一になる。
-- ---------------------------------------------------------------------------
INSERT INTO users (email, password_hash, display_name, bio, created_at, updated_at)
SELECT
    'perf_' || g.n || '@example.test',
    :'bcrypt_hash',
    -- 検索シナリオが ILIKE で引っかけられるよう、一部に共通語を含める
    'PerfUser ' || g.n || CASE WHEN g.n % 5 = 0 THEN ' tester' ELSE '' END,
    CASE WHEN g.n % 3 = 0 THEN 'performance seed account no.' || g.n ELSE NULL END,
    now() - (g.n % 180) * interval '1 day',
    now()
FROM generate_series(1, :users_n) AS g(n);

CREATE TEMP TABLE perf_users AS
SELECT id, (row_number() OVER (ORDER BY id) - 1)::bigint AS idx
FROM users
WHERE email LIKE 'perf|_%@example.test' ESCAPE '|';

CREATE INDEX ON perf_users (idx);

-- ---------------------------------------------------------------------------
-- 2. 投稿
--    2.5%を論理削除済みにする。PostMapper の
--    WHERE (deleted_at IS NULL OR EXISTS(コメントあり)) という OR 条件を
--    実際に踏ませるため(削除済みが0件だとこの分岐のコストが測れない)。
-- ---------------------------------------------------------------------------
INSERT INTO posts (user_id, body, created_at, updated_at, deleted_at)
SELECT
    pu.id,
    'perf post #' || g.n || ' ' || repeat('sample body text ', 1 + (g.n % 6)),
    t.ts,
    t.ts,
    CASE WHEN g.n % 40 = 0 THEN t.ts + interval '1 hour' ELSE NULL END
FROM generate_series(0, :posts_n - 1) AS g(n)
JOIN perf_users pu ON pu.idx = (g.n::bigint * 7919) % (SELECT count(*) FROM perf_users)
CROSS JOIN LATERAL (
    SELECT now() - (g.n % 90) * interval '1 day' - (g.n % 1440) * interval '1 minute'
) AS t(ts);

CREATE TEMP TABLE perf_posts AS
SELECT id, created_at, (row_number() OVER (ORDER BY id) - 1)::bigint AS idx
FROM posts
WHERE body LIKE 'perf post #%';

CREATE INDEX ON perf_posts (idx);

-- ---------------------------------------------------------------------------
-- 3. コメント(親)  — 全体の80%
--    3%を論理削除済みにする。CommentMapper も posts と同じ OR 条件を持つため。
-- ---------------------------------------------------------------------------
INSERT INTO comments (post_id, user_id, body, created_at, updated_at, deleted_at)
SELECT
    p.id,
    pu.id,
    'perf comment #' || g.n || ' on post ' || p.id,
    p.created_at + (g.n % 240) * interval '1 minute',
    p.created_at + (g.n % 240) * interval '1 minute',
    CASE WHEN g.n % 33 = 0 THEN p.created_at + interval '2 hours' ELSE NULL END
FROM generate_series(0, (:comments_n * 4 / 5) - 1) AS g(n)
JOIN perf_posts p  ON p.idx  = (g.n::bigint * 7919)   % (SELECT count(*) FROM perf_posts)
JOIN perf_users pu ON pu.idx = (g.n::bigint * 104729) % (SELECT count(*) FROM perf_users);

CREATE TEMP TABLE perf_root_comments AS
SELECT id, post_id, created_at, (row_number() OVER (ORDER BY id) - 1)::bigint AS idx
FROM comments
WHERE body LIKE 'perf comment #%' AND parent_comment_id IS NULL;

CREATE INDEX ON perf_root_comments (idx);

-- ---------------------------------------------------------------------------
-- 4. コメント(返信) — 全体の20%
--    親コメントの post_id をそのまま使うことで、
--    「親と子の post_id が食い違う」不整合が構造的に起きないようにする。
-- ---------------------------------------------------------------------------
INSERT INTO comments (post_id, user_id, parent_comment_id, body, created_at, updated_at)
SELECT
    c.post_id,
    pu.id,
    c.id,
    'perf reply #' || g.n || ' to comment ' || c.id,
    c.created_at + (1 + g.n % 120) * interval '1 minute',
    c.created_at + (1 + g.n % 120) * interval '1 minute'
FROM generate_series(0, (:comments_n / 5) - 1) AS g(n)
JOIN perf_root_comments c ON c.idx  = (g.n::bigint * 7919)   % (SELECT count(*) FROM perf_root_comments)
JOIN perf_users pu        ON pu.idx = (g.n::bigint * 104729) % (SELECT count(*) FROM perf_users);

-- ---------------------------------------------------------------------------
-- 5. コメントが極端に多い投稿を3件つくる
--    GET /api/posts/{id}/comments は LIMIT が無く全件返す(CommentMapper.findByPostId)。
--    平均的なデータだけではこの設計の影響が数字に出ないため、意図的に山を作る。
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE perf_hot_posts AS
SELECT id, created_at FROM perf_posts WHERE idx IN (10, 20, 30);

INSERT INTO comments (post_id, user_id, body, created_at, updated_at)
SELECT
    h.id,
    pu.id,
    'perf hot comment #' || g.n || ' on post ' || h.id,
    h.created_at + (g.n % 500) * interval '1 minute',
    h.created_at + (g.n % 500) * interval '1 minute'
FROM perf_hot_posts h
CROSS JOIN generate_series(0, 499) AS g(n)
JOIN perf_users pu ON pu.idx = (g.n::bigint * 104729) % (SELECT count(*) FROM perf_users);

-- ---------------------------------------------------------------------------
-- 6. いいね(投稿)
--    UNIQUE(post_id, user_id) があるため ON CONFLICT DO NOTHING を付ける。
--    衝突した分だけ実際の件数は目標を下回るので、verify.sql で実数を記録する。
-- ---------------------------------------------------------------------------
INSERT INTO likes (post_id, user_id, created_at)
SELECT
    p.id,
    pu.id,
    p.created_at + (g.n % 600) * interval '1 minute'
FROM generate_series(0, :likes_n - 1) AS g(n)
JOIN perf_posts p  ON p.idx  = g.n % (SELECT count(*) FROM perf_posts)
JOIN perf_users pu ON pu.idx = (g.n / (SELECT count(*) FROM perf_posts) * 7919 + g.n::bigint * 13)
                               % (SELECT count(*) FROM perf_users)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 7. いいね(コメント)
-- ---------------------------------------------------------------------------
INSERT INTO comment_likes (comment_id, user_id, created_at)
SELECT
    c.id,
    pu.id,
    c.created_at + (g.n % 600) * interval '1 minute'
FROM generate_series(0, :comment_likes_n - 1) AS g(n)
JOIN perf_root_comments c ON c.idx  = g.n % (SELECT count(*) FROM perf_root_comments)
JOIN perf_users pu        ON pu.idx = (g.n / (SELECT count(*) FROM perf_root_comments) * 7919 + g.n::bigint * 13)
                                      % (SELECT count(*) FROM perf_users)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 8. フォロー
--    followee は follower のインデックス + 1以上のオフセットで決める。
--    オフセットが 0 にならないため、自己フォローが構造的に発生しない。
-- ---------------------------------------------------------------------------
INSERT INTO follows (follower_id, followee_id, created_at)
SELECT
    f.id,
    t.id,
    now() - (g.n % 80) * interval '1 day'
FROM generate_series(0, :follows_n - 1) AS g(n)
JOIN perf_users f ON f.idx = g.n % (SELECT count(*) FROM perf_users)
JOIN perf_users t ON t.idx = (g.n % (SELECT count(*) FROM perf_users)
                              + 1 + g.n / (SELECT count(*) FROM perf_users))
                             % (SELECT count(*) FROM perf_users)
WHERE f.id <> t.id
ON CONFLICT DO NOTHING;

COMMIT;

-- ---------------------------------------------------------------------------
-- 9. 統計情報の更新
--    これを飛ばすとプランナが実データと違う前提で実行計画を選び、
--    測定結果が実態からずれる。投入直後に必ず実行する。
-- ---------------------------------------------------------------------------
ANALYZE users;
ANALYZE posts;
ANALYZE comments;
ANALYZE likes;
ANALYZE comment_likes;
ANALYZE follows;
