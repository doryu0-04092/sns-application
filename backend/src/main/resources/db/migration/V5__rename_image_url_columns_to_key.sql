-- 画像の保存先をローカルディスクからS3(非公開バケット + Presigned URL)へ移行した。
-- 保持する値が「配信URL」から「S3オブジェクトキー」(例: posts/uuid.jpg)に変わったため、
-- 実態と食い違う列名を改める。
--
-- 既存データは旧形式のURL(http://localhost:8080/uploads/...)であり、S3上に実体が無いため
-- そのまま残しても表示できない。移行対象の運用データが無い学習用プロジェクトのため、
-- 値はNULLで初期化して作り直す方針とする。

ALTER TABLE users RENAME COLUMN avatar_url TO avatar_key;
ALTER TABLE post_images RENAME COLUMN image_url TO image_key;

-- キーはURLより短いため上限を縮める(posts/ + UUID + 拡張子 = 約50文字)
ALTER TABLE users ALTER COLUMN avatar_key TYPE VARCHAR(255);
ALTER TABLE post_images ALTER COLUMN image_key TYPE VARCHAR(255);

-- S3に実体が存在しない旧URLを掃除する
UPDATE users SET avatar_key = NULL WHERE avatar_key IS NOT NULL;
DELETE FROM post_images;
