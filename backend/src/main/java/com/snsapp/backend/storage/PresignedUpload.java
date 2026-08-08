package com.snsapp.backend.storage;

/**
 * ブラウザが画像をS3へ直接アップロードするための情報。
 *
 * @param key       採番されたS3オブジェクトキー。アップロード完了後、クライアントはこのキーを
 *                  投稿作成APIへ渡す(キーはサーバー側で採番するため、他人のオブジェクトを指定される余地はない)
 * @param uploadUrl 署名付きPUT URL。有効期限内に、署名時と同じContent-Typeで送る必要がある
 */
public record PresignedUpload(String key, String uploadUrl) {
}
