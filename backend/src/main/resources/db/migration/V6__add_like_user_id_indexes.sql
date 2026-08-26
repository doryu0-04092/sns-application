-- いいね判定(is_liked)がテーブル全件走査になっていた問題を解消する。
--
-- 問題:
--   PostMapper / CommentMapper の is_liked は次の形をしている。
--     EXISTS (SELECT 1 FROM likes WHERE post_id = p.id AND user_id = ?)
--   しかし likes の索引は id(PK) / (post_id, user_id) の UNIQUE / post_id 単独 の3つで、
--   user_id を先頭に持つ索引が1つも無かった。複合索引 (post_id, user_id) は
--   先頭列が post_id なので user_id 単独の絞り込みには使えない。
--   その結果プランナは「このユーザーのいいね全部」をハッシュ化する計画を選び、
--   1リクエストにつき likes テーブル全体を走査していた。
--
-- 影響の性質:
--   LIMIT 20 を付けてもこのサブプランは loops=1 で1回だけ走り、毎回全件を読む。
--   つまり1ページの表示件数ではなく、サービス全体のいいね総数に比例して重くなる。
--   さらにテーブルが大きくなると PostgreSQL が並列スキャンに切り替わり、
--   1リクエストあたりのCPU消費が3倍(リーダー+ワーカー2)になるため、悪化は超線形になる。
--
-- 実測(docs/perf-test-report.md 5-1, 8-4, 8-5):
--   投稿10万件・いいね50万件で負荷テスト(VU 10->50、3分)を実施したところ、
--   GET /api/posts の p95 が 394.20ms から 4.93ms になった(80倍)。
--   スループットも同条件で 9,556 -> 11,496 リクエストに増えた。
--
-- 列の順序について:
--   (user_id, post_id) の複合にしているのは、is_liked の条件が user_id と post_id の
--   両方を使うため、索引だけで判定が完結する Index Only Scan になるからである。
--   user_id 単独の索引でも全件走査は避けられるが、索引からテーブル本体への参照が1回増える。

CREATE INDEX idx_likes_user_id ON likes (user_id, post_id);

CREATE INDEX idx_comment_likes_user_id ON comment_likes (user_id, comment_id);
