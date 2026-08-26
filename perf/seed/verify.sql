-- シードデータの整合性検証。
--
-- seed.sql はアプリを通さずSQLで直接投入するため、アプリが守っている不変条件を迂回できてしまう。
-- 壊れたデータのまま計測すると、遅さの原因がアプリなのかデータなのか分からなくなる。
-- そのため計測を始める前に必ずこれを実行し、違反が 0 件であることを確認する。

\set ON_ERROR_STOP on

\echo ''
\echo '=== 1. 投入件数 ==='
SELECT 'users'         AS table_name, count(*) AS rows FROM users
UNION ALL SELECT 'posts',         count(*) FROM posts
UNION ALL SELECT 'comments',      count(*) FROM comments
UNION ALL SELECT 'likes',         count(*) FROM likes
UNION ALL SELECT 'comment_likes', count(*) FROM comment_likes
UNION ALL SELECT 'follows',       count(*) FROM follows
ORDER BY table_name;

\echo ''
\echo '=== 2. 不変条件の検証(すべて 0 であること) ==='
SELECT
    -- 親コメントと子コメントの post_id が食い違っていないか。
    -- 食い違うとフロントの buildCommentTree が孤児ノードを作り、描画結果が変わる。
    (SELECT count(*) FROM comments c
       JOIN comments p ON p.id = c.parent_comment_id
      WHERE c.post_id <> p.post_id)                                    AS orphan_reply_post_mismatch,

    -- 自己フォロー。アプリ側は FollowService で弾いているがSQL投入では素通りする。
    (SELECT count(*) FROM follows WHERE follower_id = followee_id)     AS self_follow,

    -- UNIQUE 制約は DB が保証しているが、制約が想定通り効いているかを念のため確認する。
    (SELECT count(*) FROM (
        SELECT post_id, user_id FROM likes GROUP BY 1,2 HAVING count(*) > 1) x) AS dup_likes,
    (SELECT count(*) FROM (
        SELECT comment_id, user_id FROM comment_likes GROUP BY 1,2 HAVING count(*) > 1) x) AS dup_comment_likes,
    (SELECT count(*) FROM (
        SELECT follower_id, followee_id FROM follows GROUP BY 1,2 HAVING count(*) > 1) x) AS dup_follows,

    -- 存在しないユーザー/投稿を指す行(FKがあるので0のはずだが、FK未設定の取り違えを検出する)
    (SELECT count(*) FROM posts p    LEFT JOIN users u ON u.id = p.user_id WHERE u.id IS NULL) AS post_without_user,
    (SELECT count(*) FROM comments c LEFT JOIN posts p ON p.id = c.post_id WHERE p.id IS NULL) AS comment_without_post;

\echo ''
\echo '=== 3. 分布の確認(計測の前提が満たされているか) ==='
SELECT
    -- 論理削除済みが0件だと、Mapper の OR 条件の分岐コストが測れない
    (SELECT count(*) FROM posts    WHERE deleted_at IS NOT NULL) AS deleted_posts,
    (SELECT count(*) FROM comments WHERE deleted_at IS NOT NULL) AS deleted_comments,
    -- 返信が0件だと再帰描画の負荷が測れない
    (SELECT count(*) FROM comments WHERE parent_comment_id IS NOT NULL) AS reply_comments,
    -- 1投稿あたりのコメント数の最大。LIMITなし全件取得の影響を測るため 500 前後が必要
    (SELECT max(c) FROM (SELECT count(*) AS c FROM comments GROUP BY post_id) x) AS max_comments_per_post,
    (SELECT round(avg(c), 1) FROM (SELECT count(*) AS c FROM comments GROUP BY post_id) x) AS avg_comments_per_post;

\echo ''
\echo '=== 4. コメント数が多い投稿 上位5件(post-detail シナリオの対象) ==='
SELECT post_id, count(*) AS comment_count
FROM comments
GROUP BY post_id
ORDER BY comment_count DESC
LIMIT 5;

\echo ''
\echo '=== 5. 統計情報が更新されているか(ANALYZE 済みか) ==='
SELECT relname, n_live_tup, last_analyze IS NOT NULL AS analyzed
FROM pg_stat_user_tables
WHERE relname IN ('users','posts','comments','likes','comment_likes','follows')
ORDER BY relname;
