package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// multipart/form-dataで受け取るdisplayNameはBean Validationの対象外(@RequestParam)のため、
// UserService#updateProfile で手動チェックした結果、空または100文字超過の場合にスロー。
public class InvalidDisplayNameException extends ApiException {

    public InvalidDisplayNameException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_DISPLAY_NAME", "表示名は1文字以上100文字以内で入力してください");
    }
}
