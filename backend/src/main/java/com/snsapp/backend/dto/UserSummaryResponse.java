package com.snsapp.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ユーザー一覧・検索・フォロー一覧の1件分。
 *
 * @param avatarUrl 表示用の署名付きURL。
 *                  <b>MyBatisが組み立てた直後だけはS3キーが入っている</b>点に注意。
 *                  各Serviceが {@link #withAvatarUrl} で署名付きURLへ差し替えてから返す
 */
@Schema(description = "一覧表示用のユーザー1件。**`id` と `userId` の使い分けに注意**")
public record UserSummaryResponse(
        @Schema(description = """
                ページネーション用のレコードID。**ユーザーIDとは限らない**。
                フォロワー/フォロー中一覧ではフォロー関係のレコードIDが入り、
                ユーザー検索ではユーザーIDが入る。
                用途は `cursor` に渡すことだけで、画面遷移などには使わないこと""",
                example = "58")
        Long id,

        @Schema(description = "ユーザーID。プロフィール取得やフォロー操作にはこちらを使う", example = "7")
        Long userId,

        @Schema(description = "表示名", example = "山田太郎")
        String displayName,

        @Schema(description = "アイコンの署名付きURL（有効期限24時間）。未設定なら null",
                types = {"string", "null"})
        String avatarUrl,

        @Schema(description = "自分がこのユーザーをフォローしているか")
        boolean isFollowing) {

    /** avatarUrl だけを差し替えた新しいインスタンスを返す(キー→署名付きURLの変換用)。 */
    public UserSummaryResponse withAvatarUrl(String newAvatarUrl) {
        return new UserSummaryResponse(id, userId, displayName, newAvatarUrl, isFollowing);
    }
}
