package com.snsapp.backend.dto;

import com.snsapp.backend.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ログイン中ユーザーの情報。
 *
 * @param avatarUrl 表示用の署名付きURL。DBが保持するのはS3キーであり、
 *                  ここへ載せる直前にStorageServiceで変換する
 */
@Schema(description = """
        ログイン中ユーザー自身の情報。メールアドレスを含むため、**自分に関するAPIでのみ返る**
        （他人の情報は ProfileResponse / UserSummaryResponse で返り、メールアドレスは含まれない）""")
public record UserResponse(
        @Schema(description = "ユーザーID", example = "7")
        Long id,

        @Schema(description = "メールアドレス。自分自身にのみ返る", example = "user@example.com")
        String email,

        @Schema(description = "表示名（100文字以内）", example = "山田太郎")
        String displayName,

        @Schema(description = "自己紹介（500文字以内）。未設定なら null",
                example = "バックエンドを担当しています", types = {"string", "null"})
        String bio,

        @Schema(description = "アイコンの署名付きURL（有効期限24時間）。未設定なら null",
                types = {"string", "null"})
        String avatarUrl) {

    /**
     * @param avatarUrl {@code user.getAvatarKey()} を署名付きURLへ変換したもの(未設定ならnull)。
     *                  Userエンティティはキーしか持たず変換手段も持たないため、呼び出し側で用意する。
     */
    public static UserResponse from(User user, String avatarUrl) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                avatarUrl);
    }
}
