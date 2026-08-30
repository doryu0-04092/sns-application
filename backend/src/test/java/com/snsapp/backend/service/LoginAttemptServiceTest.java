package com.snsapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snsapp.backend.exception.TooManyLoginAttemptsException;
import com.snsapp.backend.ratelimit.RateLimitProperties;
import com.snsapp.backend.ratelimit.RateLimitProperties.Rule;
import org.junit.jupiter.api.Test;

/**
 * 同一アカウントへのログイン失敗の数え方。
 *
 * <p><b>IP単位の制限では止まらない攻撃を担当する部分である。</b>
 * 多数のIPから1つのアカウントを狙う形では、IPごとの回数は上限に届かないまま
 * 試行だけが積み上がる。ここが効かないと、その経路は無防備になる。
 */
class LoginAttemptServiceTest {

    private static final String EMAIL = "user@example.com";

    private static LoginAttemptService service(int capacity) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setAccountLogin(new Rule(capacity, 1));
        return new LoginAttemptService(properties);
    }

    /**
     * <b>最も重要な1件。</b> 成功で消費してしまうと、
     * 正しく使っている利用者が連続してログインしただけで締め出される。
     */
    @Test
    void 成功を繰り返しても締め出されない() {
        LoginAttemptService service = service(3);

        for (int i = 0; i < 20; i++) {
            service.checkNotLockedOut(EMAIL);
            service.onSuccess(EMAIL);
        }

        assertThatCode(() -> service.checkNotLockedOut(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void 失敗が上限に達すると弾かれる() {
        LoginAttemptService service = service(3);

        for (int i = 0; i < 3; i++) {
            service.checkNotLockedOut(EMAIL);
            service.onFailure(EMAIL);
        }

        assertThatThrownBy(() -> service.checkNotLockedOut(EMAIL))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void 上限に達する手前では通る() {
        LoginAttemptService service = service(3);

        service.onFailure(EMAIL);
        service.onFailure(EMAIL);

        assertThatCode(() -> service.checkNotLockedOut(EMAIL)).doesNotThrowAnyException();
    }

    /** 大文字小文字を変えるだけで別の鍵になっては、数えている意味が無い。 */
    @Test
    void 大文字小文字と前後の空白は同じ鍵として扱う() {
        LoginAttemptService service = service(2);

        service.onFailure("User@Example.com");
        service.onFailure("  USER@EXAMPLE.COM  ");

        assertThatThrownBy(() -> service.checkNotLockedOut(EMAIL))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void 別のアカウントは互いに影響しない() {
        LoginAttemptService service = service(1);

        service.onFailure(EMAIL);
        assertThatThrownBy(() -> service.checkNotLockedOut(EMAIL))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        assertThatCode(() -> service.checkNotLockedOut("other@example.com")).doesNotThrowAnyException();
    }

    @Test
    void 再試行までの秒数を伝える() {
        LoginAttemptService service = service(1);
        service.onFailure(EMAIL);

        assertThatThrownBy(() -> service.checkNotLockedOut(EMAIL))
                .isInstanceOfSatisfying(
                        TooManyLoginAttemptsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isGreaterThanOrEqualTo(1L));
    }

    @Test
    void 無効にすると数えない() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(false);
        properties.setAccountLogin(new Rule(1, 1));
        LoginAttemptService service = new LoginAttemptService(properties);

        for (int i = 0; i < 10; i++) {
            service.onFailure(EMAIL);
        }

        assertThatCode(() -> service.checkNotLockedOut(EMAIL)).doesNotThrowAnyException();
    }

    /** null が渡っても落ちない(検証で弾かれる前に呼ばれる経路を想定)。 */
    @Test
    void nullでも落ちない() {
        LoginAttemptService service = service(2);

        assertThatCode(() -> {
            service.checkNotLockedOut(null);
            service.onFailure(null);
        }).doesNotThrowAnyException();
    }
}
