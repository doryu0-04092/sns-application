-- 計測後に実行して、遅さの原因をクエリ単位まで下ろすための調査SQL。
--
-- k6 の結果は「このエンドポイントが何ms」までしか教えてくれない。
-- そこから先(どのSQLが何回呼ばれ、どの実行計画で、何を待っていたか)は
-- ここで見る。レポートの「ボトルネックの特定と根拠」はこの出力を根拠にする。
--
-- 前提: 計測開始前に SELECT pg_stat_statements_reset(); を実行してあること。
--       シード投入時の INSERT が混ざると順位が実態と変わる。

\echo ''
\echo '=== 1. 総実行時間の多いクエリ 上位10件 ==='
\echo '    total_ms が大きいものが、そのワークロードで最も時間を食っている。'
\echo '    mean_ms が小さくても calls が多ければ total_ms は大きくなる点に注意。'
SELECT
    round(total_exec_time)::bigint AS total_ms,
    calls,
    round(mean_exec_time::numeric, 2) AS mean_ms,
    round(max_exec_time::numeric, 2) AS max_ms,
    rows,
    left(regexp_replace(query, '\s+', ' ', 'g'), 110) AS query
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 10;

\echo ''
\echo '=== 2. 1回あたりが遅いクエリ 上位10件(呼び出し20回以上) ==='
\echo '    総時間では埋もれるが、単発で重いクエリを拾う。'
SELECT
    round(mean_exec_time::numeric, 2) AS mean_ms,
    calls,
    round(total_exec_time)::bigint AS total_ms,
    left(regexp_replace(query, '\s+', ' ', 'g'), 110) AS query
FROM pg_stat_statements
WHERE calls >= 20
ORDER BY mean_exec_time DESC
LIMIT 10;

\echo ''
\echo '=== 3. インデックスの使われ方 ==='
\echo '    seq_scan が多く idx_scan が少ないテーブルは、索引が足りていない可能性がある。'
\echo '    ただし小さいテーブルは全件走査の方が速いため、行数と併せて判断する。'
SELECT
    relname,
    n_live_tup AS rows,
    seq_scan,
    seq_tup_read,
    idx_scan,
    CASE WHEN seq_scan > 0
         THEN round((seq_tup_read::numeric / seq_scan), 0)
         ELSE 0 END AS avg_rows_per_seq_scan
FROM pg_stat_user_tables
WHERE relname IN ('users','posts','comments','likes','comment_likes','follows')
ORDER BY seq_tup_read DESC;

\echo ''
\echo '=== 4. 使われていないインデックス ==='
\echo '    書き込みのたびに更新コストだけ払っている索引を洗い出す。'
SELECT
    relname AS table_name,
    indexrelname AS index_name,
    idx_scan,
    pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan ASC, pg_relation_size(indexrelid) DESC;

\echo ''
\echo '=== 5. タイムライン(1ページ目)の実行計画 ==='
\echo '    PostMapper.findFeedAll。1投稿ごとに相関サブクエリが5本走る。'
\echo '    SubPlan の loops が LIMIT の件数だけ回っていれば、その仮説の裏付けになる。'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT p.id,
       CASE WHEN p.deleted_at IS NOT NULL THEN NULL ELSE p.body END AS body,
       u.id AS author_id, u.display_name, u.avatar_key,
       p.created_at, p.updated_at,
       (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id
          AND (c.deleted_at IS NULL OR EXISTS (SELECT 1 FROM comments child WHERE child.parent_comment_id = c.id))
       ) AS comment_count,
       (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id) AS like_count,
       (p.user_id = 2) AS is_mine,
       EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = 2 AND f.followee_id = p.user_id) AS is_following,
       EXISTS (SELECT 1 FROM likes l WHERE l.post_id = p.id AND l.user_id = 2) AS is_liked,
       (p.deleted_at IS NOT NULL) AS is_deleted
FROM posts p JOIN users u ON u.id = p.user_id
WHERE (p.deleted_at IS NULL OR EXISTS (SELECT 1 FROM comments c2 WHERE c2.post_id = p.id))
ORDER BY p.id DESC
LIMIT 20;

\echo ''
\echo '=== 6. コメント全件取得の実行計画(コメント502件の投稿) ==='
\echo '    CommentMapper.findByPostId。LIMIT が無いため件数に比例して重くなるはず。'
\echo '    :hot_post_id は verify.sql の「コメント数が多い投稿」で確認したIDを渡す。'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT c.id, c.post_id, c.parent_comment_id,
       CASE WHEN c.deleted_at IS NOT NULL THEN NULL ELSE c.body END AS body,
       u.id AS author_id, u.display_name, u.avatar_key,
       c.created_at, c.updated_at,
       (SELECT COUNT(*) FROM comment_likes cl WHERE cl.comment_id = c.id) AS like_count,
       (c.user_id = 2) AS is_mine,
       EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = 2 AND f.followee_id = c.user_id) AS is_following,
       EXISTS (SELECT 1 FROM comment_likes cl WHERE cl.comment_id = c.id AND cl.user_id = 2) AS is_liked,
       (c.deleted_at IS NOT NULL) AS is_deleted
FROM comments c JOIN users u ON u.id = c.user_id
WHERE c.post_id = :hot_post_id
  AND (c.deleted_at IS NULL OR EXISTS (SELECT 1 FROM comments child WHERE child.parent_comment_id = c.id))
ORDER BY c.id ASC;

\echo ''
\echo '=== 7. ユーザー検索の実行計画 ==='
\echo '    display_name に索引が無く、かつ両端ワイルドカードなので'
\echo '    Seq Scan になるはず。行数に比例して重くなる。'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT u.id, u.display_name, u.bio, u.avatar_key
FROM users u
WHERE u.display_name ILIKE '%' || 'tester' || '%'
ORDER BY u.id DESC
LIMIT 20;
