package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// PostService#requireOwnedPost: 投稿は存在するが currentUserId が投稿者と一致しない場合(PATCH/DELETE)にスロー
public class PostForbiddenException extends ApiException {

    public PostForbiddenException() {
        super(HttpStatus.FORBIDDEN, "POST_FORBIDDEN", "自分の投稿のみ編集・削除できます");
    }
}
