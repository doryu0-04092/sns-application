package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// 同一アカウントへのログイン失敗が上限に達した場合にスロー。LoginAttemptService から使用。
//
// IP単位の制限(RateLimitFilter)とは別の軸である。多数のIPから1つのアカウントを狙う形では、
// IPごとの回数は上限に届かないまま試行だけが積み上がるため、IP単位だけでは止まらない。
//
// **メールアドレスの存在有無で挙動を変えない。** 未登録のアドレスでも同じように数える。
// 分けると「429が返る = 実在する」という手掛かりを与えることになり、
// InvalidCredentialsException で潰した列挙攻撃の穴がここで開く。
public class TooManyLoginAttemptsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                "TOO_MANY_LOGIN_ATTEMPTS",
                "ログインの試行が多すぎます。しばらく待ってからやり直してください");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
