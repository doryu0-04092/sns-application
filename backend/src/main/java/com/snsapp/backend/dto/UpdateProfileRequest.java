package com.snsapp.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * プロフィール編集リクエスト(F-14)。
 *
 * @param avatarKey 事前に {@code POST /api/uploads/presign} で採番され、ブラウザがS3へ直接
 *                  アップロード済みのキー。null の場合は現在のアイコンを維持する
 */
public record UpdateProfileRequest(
        @NotBlank(message = "表示名は1文字以上100文字以内で入力してください")
        @Size(max = 100, message = "表示名は1文字以上100文字以内で入力してください")
        String displayName,

        @Size(max = 500, message = "自己紹介は500文字以内で入力してください")
        String bio,

        String avatarKey) {
}
