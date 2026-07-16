package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends ApiException {

    public DuplicateEmailException() {
        super(HttpStatus.BAD_REQUEST, "EMAIL_ALREADY_EXISTS", "このメールアドレスは既に登録されています");
    }
}
