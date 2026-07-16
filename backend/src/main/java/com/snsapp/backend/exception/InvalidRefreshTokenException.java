package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// refresh_token クッキーが未提示/DBに存在しない/期限切れ/既に失効済み(盗用検知によるローテーション)の場合にスロー。
// RefreshTokenService#rotate・AuthController#refresh から使用。フロントはこれを受けてログイン画面へ遷移する。
public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "セッションの有効期限が切れました。再度ログインしてください");
    }
}
