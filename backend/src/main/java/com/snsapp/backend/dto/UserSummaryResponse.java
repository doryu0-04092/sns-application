package com.snsapp.backend.dto;

/**
 * ユーザー一覧・検索・フォロー一覧の1件分。
 *
 * @param avatarUrl 表示用の署名付きURL。
 *                  <b>MyBatisが組み立てた直後だけはS3キーが入っている</b>点に注意。
 *                  各Serviceが {@link #withAvatarUrl} で署名付きURLへ差し替えてから返す
 */
public record UserSummaryResponse(
        Long id,
        Long userId,
        String displayName,
        String avatarUrl,
        boolean isFollowing) {

    /** avatarUrl だけを差し替えた新しいインスタンスを返す(キー→署名付きURLの変換用)。 */
    public UserSummaryResponse withAvatarUrl(String newAvatarUrl) {
        return new UserSummaryResponse(id, userId, displayName, newAvatarUrl, isFollowing);
    }
}
