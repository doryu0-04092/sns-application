package com.snsapp.backend.dto;

/**
 * プロフィール画面(S-05)の表示内容。
 *
 * @param avatarUrl 表示用の署名付きURL。
 *                  <b>MyBatisが組み立てた直後だけはS3キーが入っている</b>点に注意。
 *                  {@link com.snsapp.backend.service.UserService} が
 *                  {@link #withAvatarUrl} で署名付きURLへ差し替えてから返す
 */
public record ProfileResponse(
        Long id,
        String displayName,
        String bio,
        String avatarUrl,
        long followerCount,
        long followingCount,
        boolean isMine,
        boolean isFollowing) {

    /** avatarUrl だけを差し替えた新しいインスタンスを返す(キー→署名付きURLの変換用)。 */
    public ProfileResponse withAvatarUrl(String newAvatarUrl) {
        return new ProfileResponse(
                id, displayName, bio, newAvatarUrl, followerCount, followingCount, isMine, isFollowing);
    }
}
