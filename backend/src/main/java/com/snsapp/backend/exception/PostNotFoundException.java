package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// 投稿IDが存在しない/既に削除済み(かつ返信を持たずツームストーン表示の対象外)の場合にスロー。
// PostService#getPost・requireOwnedPost・CommentService#listComments/requireActivePost から使用。
public class PostNotFoundException extends ApiException {

    public PostNotFoundException() {
        super(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "投稿が見つかりません");
    }
}
