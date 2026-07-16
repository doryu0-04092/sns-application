package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// CommentService#requireOwnedComment: コメントは存在するが currentUserId が投稿者と一致しない場合(PATCH/DELETE)にスロー
public class CommentForbiddenException extends ApiException {

    public CommentForbiddenException() {
        super(HttpStatus.FORBIDDEN, "COMMENT_FORBIDDEN", "自分のコメントのみ編集・削除できます");
    }
}
