package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// コメントIDが存在しない/既に削除済みの場合(編集・削除対象、または返信先の親コメント指定時)にスロー。
// CommentService#createComment(親コメント検証)・requireOwnedComment から使用。
public class CommentNotFoundException extends ApiException {

    public CommentNotFoundException() {
        super(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "コメントが見つかりません");
    }
}
