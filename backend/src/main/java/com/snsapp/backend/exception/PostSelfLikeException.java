package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// いいね対象postIdの投稿者がcurrentUserId自身の場合にスロー。LikeService#like から使用。
public class PostSelfLikeException extends ApiException {

    public PostSelfLikeException() {
        super(HttpStatus.BAD_REQUEST, "POST_SELF_LIKE_NOT_ALLOWED", "自分の投稿にはいいねできません");
    }
}
