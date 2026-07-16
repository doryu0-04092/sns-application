package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// サインアップ時、指定emailで既にusersレコードが存在する場合にスロー。AuthService#signup から使用。
public class DuplicateEmailException extends ApiException {

    public DuplicateEmailException() {
        super(HttpStatus.BAD_REQUEST, "EMAIL_ALREADY_EXISTS", "このメールアドレスは既に登録されています");
    }
}
