package com.snsapp.backend.storage;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ブラウザが画像をS3へ直接アップロードするための情報。
 *
 * @param key       採番されたS3オブジェクトキー。アップロード完了後、クライアントはこのキーを
 *                  投稿作成APIへ渡す(キーはサーバー側で採番するため、他人のオブジェクトを指定される余地はない)
 * @param uploadUrl 署名付きPUT URL。有効期限内に、署名時と同じContent-Typeで送る必要がある
 */
@Schema(description = "1ファイル分のアップロード先。リクエストの contentTypes と同じ順序で返る")
public record PresignedUpload(
        @Schema(description = """
                サーバーが採番したS3オブジェクトキー。アップロード完了後、この値を
                `POST /api/posts` の `imageKeys` または `PATCH /api/users/me` の `avatarKey` に渡す""",
                example = "pending/1f0c9a7e-2b44-4d1e-9f83-5c2a0b7d6e11.png")
        String key,

        @Schema(description = """
                署名付きPUT URL。ここへブラウザから直接アップロードする。
                **署名時と同じ Content-Type を付けること**（異なるとS3が拒否する）。
                このURLへのリクエストに認証クッキーは不要""")
        String uploadUrl) {
}
