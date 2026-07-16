package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// ログイン時、emailが存在しない、またはpasswordがBCryptハッシュと一致しない場合にスロー。AuthService#login から使用。
// メール未登録とパスワード不一致を同じエラーにまとめ、登録済みメールアドレスの推測(列挙攻撃)を防いでいる。
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "メールアドレスまたはパスワードが正しくありません");
    }
}
