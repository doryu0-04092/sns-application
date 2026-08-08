package com.snsapp.backend.dto;

import com.snsapp.backend.common.ContentLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 投稿作成リクエスト(F-04/F-05)。
 *
 * @param imageKeys 事前に {@code POST /api/uploads/presign} で採番され、ブラウザがS3へ直接
 *                  アップロード済みのキー。実体の検証はサーバー側で行うため、ここでは件数のみ検証する
 */
public record CreatePostRequest(
        @NotBlank(message = ContentLimits.BODY_MESSAGE)
        @Size(max = ContentLimits.MAX_BODY_LENGTH, message = ContentLimits.BODY_MESSAGE)
        String body,

        @Size(max = 4, message = "画像は4枚まで添付できます")
        List<String> imageKeys) {

    /** imageKeys 未指定(null)を空リストとして扱う。 */
    public List<String> imageKeysOrEmpty() {
        return imageKeys == null ? List.of() : imageKeys;
    }
}
