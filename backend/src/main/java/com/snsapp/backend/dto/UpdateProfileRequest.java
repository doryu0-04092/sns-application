package com.snsapp.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * プロフィール編集リクエスト(F-14)。
 *
 * @param avatarKey 事前に {@code POST /api/uploads/presign} で採番され、ブラウザがS3へ直接
 *                  アップロード済みのキー。null の場合は現在のアイコンを維持する
 */
@Schema(description = "プロフィール編集リクエスト。PATCHだが displayName は常に必須")
public record UpdateProfileRequest(
        @Schema(description = "表示名（1〜100文字）。**必須**。変更しない場合も現在の値を送ること",
                example = "山田太郎")
        @NotBlank(message = "表示名は1文字以上100文字以内で入力してください")
        @Size(max = 100, message = "表示名は1文字以上100文字以内で入力してください")
        String displayName,

        @Schema(description = "自己紹介（500文字以内）。空文字を送ると空になる",
                example = "バックエンドを担当しています", types = {"string", "null"})
        @Size(max = 500, message = "自己紹介は500文字以内で入力してください")
        String bio,

        @Schema(description = """
                新しいアイコンのS3キー（`POST /api/uploads/presign` で採番したもの）。
                **省略（null）または空文字なら現在のアイコンを維持する**。
                新しいキーを指定すると、古いアイコンはS3から削除される""",
                example = "pending/1f0c9a7e-2b44-4d1e-9f83-5c2a0b7d6e11.png", types = {"string", "null"})
        String avatarKey) {
}
