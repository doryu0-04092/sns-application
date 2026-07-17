package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// 指定されたuserIdのユーザーが存在しない場合にスロー。FollowService#follow/listFollowers/listFollowing、UserService#getProfile から使用。
public class UserNotFoundException extends ApiException {

    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "ユーザーが見つかりません");
    }
}
