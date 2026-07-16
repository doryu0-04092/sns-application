package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// フォロー対象userIdがcurrentUserId自身の場合にスロー。FollowService#follow から使用。
public class SelfFollowException extends ApiException {

    public SelfFollowException() {
        super(HttpStatus.BAD_REQUEST, "SELF_FOLLOW_NOT_ALLOWED", "自分自身をフォローすることはできません");
    }
}
