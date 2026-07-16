package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// フォロー対象のuserIdが存在しない場合にスロー。FollowService#follow から使用。
public class UserNotFoundException extends ApiException {

    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "ユーザーが見つかりません");
    }
}
