-- 「正規のローテーションで置き換えられた」ことを記録する列を足す。
--
-- **失効した時刻だけでは、失効の理由を区別できない。**
--
-- 並行リフレッシュ(タブ2枚で同時に401)を盗用と誤判定しないよう、
-- 失効直後の再提示には猶予を設けた。ところが判定を revoked_at の
-- 新しさだけで行うと、**盗用検知による一括失効の直後にも猶予が効いてしまう**。
--
-- 実際に次の順で起きた(2026-08-30、E2Eのログ):
--
--   10.357  refresh token rotated
--   12.039  reuse detected, revoking all tokens   ← 正しく検知
--   12.178  concurrent refresh within grace       ← 139ms後に失効が取り消される
--
-- 検知が自分で無効化される状態だった。
--
-- 猶予を与えてよいのは「後継トークンが存在する = 正規に置き換えられた」場合だけである。
-- 一括失効(盗用検知)とログアウトはこの列を NULL のままにするため、猶予の対象にならない。
ALTER TABLE refresh_tokens
    ADD COLUMN replaced_by BIGINT NULL REFERENCES refresh_tokens (id) ON DELETE SET NULL;

COMMENT ON COLUMN refresh_tokens.replaced_by IS
    '正規のローテーションで発行された後継トークンのID。一括失効・ログアウトでは NULL のまま';
