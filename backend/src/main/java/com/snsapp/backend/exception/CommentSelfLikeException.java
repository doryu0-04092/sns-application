package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// いいね対象commentIdのコメント投稿者がcurrentUserId自身の場合にスロー。CommentLikeService#like から使用。
public class CommentSelfLikeException extends ApiException {

    public CommentSelfLikeException() {
        super(HttpStatus.BAD_REQUEST, "COMMENT_SELF_LIKE_NOT_ALLOWED", "自分のコメントにはいいねできません");
    }
}
