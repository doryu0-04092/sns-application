package com.snsapp.backend.dto;

import com.snsapp.backend.entity.User;

/**
 * ログイン中ユーザーの情報。
 *
 * @param avatarUrl 表示用の署名付きURL。DBが保持するのはS3キーであり、
 *                  ここへ載せる直前にStorageServiceで変換する
 */
public record UserResponse(
        Long id,
        String email,
        String displayName,
        String bio,
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
