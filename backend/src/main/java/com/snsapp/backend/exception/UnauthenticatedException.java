package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// JwtAuthFilterを通過した(=有効なアクセストークンを持つ)はずのユーザーIDがusersテーブルに存在しない場合にスロー。
// 通常は起こらない想定(退会機能が無いため) — AuthService#getCurrentUser(/api/auth/me)から使用。
public class UnauthenticatedException extends ApiException {

    public UnauthenticatedException() {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "認証が必要です");
    }
}
