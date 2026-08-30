package com.snsapp.backend.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.snsapp.backend.exception.TooManyLoginAttemptsException;
import com.snsapp.backend.ratelimit.RateLimitProperties;
import com.snsapp.backend.ratelimit.RateLimiter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 同一アカウントへのログイン失敗を数える。
 *
 * <p><b>IP単位の制限では止まらない攻撃がある。</b>
 * 多数のIPから1つのアカウントを狙う形(クレデンシャルスタッフィング)では、
 * IPごとの回数は上限に届かないまま試行だけが積み上がる。
 * ここはアカウントを軸にするため、送信元が分散していても効く。
 *
 * <p><b>成功したら消す。</b> 失敗だけを数えるので、正しく使っている限り一度も当たらない。
 *
 * <p><b>鍵はメールアドレスそのものではなく、小文字化した文字列である。</b>
 * 大文字小文字を変えるだけで別の鍵になっては、数えている意味が無い。
 *
 * <p><b>個人情報の扱い(③)</b>: このクラスはメールアドレスをメモリ上の鍵として持つが、
 * <b>ログには出さない</b>。永続化もしないため、プロセスが終われば消える。
 * どのアカウントが狙われたかは、アクセスログの時刻と併せて調べる。
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final RateLimiter limiter;
    private final boolean enabled;

    public LoginAttemptService(RateLimitProperties properties) {
        RateLimitProperties.Rule rule = properties.getAccountLogin();
        this.enabled = properties.isEnabled();
        this.limiter = new RateLimiter("accountLogin", rule.getCapacity(), rule.getRefillPerMinute() / 60d);
    }

    /**
     * 認証を試す前に呼ぶ。上限に達していれば例外を投げる。
     *
     * <p><b>パスワード照合の前に落とすことに意味がある。</b> BCryptの照合は意図的に重く、
     * 総当たりを受けるとCPUがそこで消費される。数える処理の後に照合を置くと、
     * <b>制限をかけているのに負荷だけは受け続ける</b>ことになる。
     */
    /**
     * 認証を試す前に呼ぶ。すでに上限に達していれば例外を投げる。
     *
     * <p><b>ここでは数えない。</b> 数えるのは失敗した時だけである
     * ({@link #onFailure(String)})。ここで消費すると、正しく使っている利用者が
     * 連続してログインしただけで締め出される。
     *
     * <p><b>パスワード照合の前に落とすことに意味がある。</b> BCryptの照合は意図的に重く、
     * 総当たりを受けるとCPUがそこで消費される。照合の後に判定を置くと、
     * <b>制限をかけているのに負荷だけは受け続ける</b>ことになる。
     */
    public void checkNotLockedOut(String email) {
        if (!enabled) {
            return;
        }
        long retryAfter = limiter.retryAfterSeconds(keyOf(email));
        if (retryAfter <= 0L) {
            return;
        }
        // **メールアドレスは出さない。** どのアカウントが狙われたかは
        // アクセスログの時刻と併せて調べる。
        log.warn("login attempts exceeded for an account {}", kv("retryAfterSeconds", retryAfter));
        throw new TooManyLoginAttemptsException(retryAfter);
    }

    /** ログインが失敗したら呼ぶ。ここで初めて1回分を消費する。 */
    public void onFailure(String email) {
        if (!enabled) {
            return;
        }
        limiter.tryAcquire(keyOf(email));
    }

    /**
     * ログインが成功したら呼ぶ。
     *
     * <p>現状は何もしない。<b>意図的である。</b>
     * 成功で数え直すと、正しい資格情報を1つ知っている攻撃者が
     * 「成功 → 失敗を上限まで → 成功」を繰り返して制限を実質無効化できる。
     * 回復は時間の経過だけに任せる。
     *
     * <p>メソッドを残しているのは、呼び出し側から見た成功・失敗の対応を明示するためである。
     */
    public void onSuccess(String email) {
        // 何もしない(上記の理由による)。
    }
    private static String keyOf(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
