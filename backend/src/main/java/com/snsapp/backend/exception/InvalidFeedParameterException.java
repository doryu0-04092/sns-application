package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// GET /api/posts の feed パラメータが "all"/"following" 以外の場合にスロー。PostService#listFeed から使用。
// フロントは常に "all"/"following" しか送らないため、通常はAPI直叩き時のみ発生する。
public class InvalidFeedParameterException extends ApiException {

    public InvalidFeedParameterException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_FEED", "feedパラメータが不正です");
    }
}
