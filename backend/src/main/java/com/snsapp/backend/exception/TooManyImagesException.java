package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// 投稿への添付画像が上限(4枚)を超えている場合にスロー。PostService#createPost から使用。
public class TooManyImagesException extends ApiException {

    public TooManyImagesException() {
        super(HttpStatus.BAD_REQUEST, "TOO_MANY_IMAGES", "画像は4枚まで添付できます");
    }
}
