package com.snsapp.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * プロフィール画面(S-05)の表示内容。
 *
 * @param avatarUrl 表示用の署名付きURL。
 *                  <b>MyBatisが組み立てた直後だけはS3キーが入っている</b>点に注意。
 *                  {@link com.snsapp.backend.service.UserService} が
 *                  {@link #withAvatarUrl} で署名付きURLへ差し替えてから返す
 */
@Schema(description = "プロフィール画面の表示内容。**投稿一覧は含まれない**（`GET /api/posts?authorId=` で別途取得する）")
public record ProfileResponse(
        @Schema(description = "ユーザーID", example = "7")
        Long id,

        @Schema(description = "表示名（100文字以内）", example = "山田太郎")
        String displayName,

        @Schema(description = "自己紹介（500文字以内）。未設定なら null",
                example = "バックエンドを担当しています", types = {"string", "null"})
        String bio,

        @Schema(description = "アイコンの署名付きURL（有効期限24時間）。未設定なら null",
                types = {"string", "null"})
        String avatarUrl,

        @Schema(description = "このユーザーをフォローしている人数", example = "24")
        long followerCount,

        @Schema(description = "このユーザーがフォローしている人数", example = "31")
        long followingCount,

        @Schema(description = "自分自身のプロフィールか。true ならフォローボタンではなく編集導線を出す")
        boolean isMine,

        @Schema(description = "自分がこのユーザーをフォローしているか")
        boolean isFollowing) {

    /** avatarUrl だけを差し替えた新しいインスタンスを返す(キー→署名付きURLの変換用)。 */
    public ProfileResponse withAvatarUrl(String newAvatarUrl) {
        return new ProfileResponse(
                id, displayName, bio, newAvatarUrl, followerCount, followingCount, isMine, isFollowing);
    }
}
