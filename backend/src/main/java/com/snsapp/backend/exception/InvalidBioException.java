package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// UserService#updateProfile: bioが500文字を超過している場合にスロー。
public class InvalidBioException extends ApiException {

    public InvalidBioException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_BIO", "自己紹介は500文字以内で入力してください");
    }
}
