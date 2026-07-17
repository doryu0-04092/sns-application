package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// multipart/form-dataで受け取るbodyはBean Validationの対象外(@RequestParam)のため、
// PostService#createPost で手動チェックした結果、空または280文字超過の場合にスロー。
public class InvalidPostBodyException extends ApiException {

    public InvalidPostBodyException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_POST_BODY", "本文は1文字以上280文字以内で入力してください");
    }
}
