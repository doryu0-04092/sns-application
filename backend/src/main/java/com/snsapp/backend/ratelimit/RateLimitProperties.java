package com.snsapp.backend.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * レート制限の設定。
 *
 * <p><b>設定にしている理由</b>: E2Eは1つのIPから短時間に何十回も登録・ログインする。
 * 本番相当の上限のままだと、テストが自分自身を締め出して落ちる。
 * かといって「テスト時は制限を通さない」形にすると、
 * <b>フィルタが実際に働いているかを一度も通らずにデプロイされる</b>。
 * そこで、仕組みは常に同じものを通し、値だけを環境ごとに変える
 * (リフレッシュの猶予時間と同じ考え方)。
 *
 * <p>制限そのものの正しさは、バックエンドの結合テストが
 * 実際のフィルタ連鎖を通して検証している。
 *
 * <p><b>単位の読み方</b>: capacity は「連続して通せる上限(バースト)」、
 * refillPerMinute は「1分あたりに回復する量」である。
 * 定常的な上限は refillPerMinute の方で、capacity は瞬間的な山を吸収する幅にあたる。
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /**
     * 有効にするか。
     *
     * <p>切れるようにしてあるのは、<b>障害時に落とすため</b>である。
     * 制限の設定を誤って利用者を締め出した時、<b>コードの修正とビルドを挟まずに</b>戻せる。
     *
     * <p><b>即時ではない点に注意。</b> ECSでは環境変数がタスク定義に埋まっているため、
     * 実際にはタスク定義の更新とサービスの入れ替えが要り、数分かかる。
     * 即時性が要る場面ではアプリの外(CloudFront / ALB / WAF)で止める方が速い。
     */
    private boolean enabled = true;

    /**
     * 信頼できるプロキシの段数(アプリ自身を除く)。
     * CloudFront → ALB → アプリ なら 1。{@link ClientIpResolver} を参照。
     */
    private int trustedProxyHops = 1;

    /** ログイン。IP単位。総当たりの主戦場なので最も厳しくする。 */
    private Rule login = new Rule(10, 20);

    /** 新規登録。IP単位。1つのIPから大量のアカウントが作られるのを防ぐ。 */
    private Rule signup = new Rule(5, 10);

    /** トークンの更新。IP単位。正常な利用でも複数タブから並ぶため緩め。 */
    private Rule refresh = new Rule(20, 60);

    /** 認証済みの書き込み(POST/PATCH/DELETE)。ユーザー単位。 */
    private Rule write = new Rule(30, 120);

    /**
     * 同一アカウントへのログイン失敗。メールアドレス単位。
     *
     * <p><b>IP単位の制限では防げない攻撃がある。</b>
     * 多数のIPから1つのアカウントを狙う形(クレデンシャルスタッフィング)では、
     * IPごとの回数は上限に届かないまま試行が積み上がる。
     */
    private Rule accountLogin = new Rule(10, 10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTrustedProxyHops() {
        return trustedProxyHops;
    }

    public void setTrustedProxyHops(int trustedProxyHops) {
        this.trustedProxyHops = trustedProxyHops;
    }

    public Rule getLogin() {
        return login;
    }

    public void setLogin(Rule login) {
        this.login = login;
    }

    public Rule getSignup() {
        return signup;
    }

    public void setSignup(Rule signup) {
        this.signup = signup;
    }

    public Rule getRefresh() {
        return refresh;
    }

    public void setRefresh(Rule refresh) {
        this.refresh = refresh;
    }

    public Rule getWrite() {
        return write;
    }

    public void setWrite(Rule write) {
        this.write = write;
    }

    public Rule getAccountLogin() {
        return accountLogin;
    }

    public void setAccountLogin(Rule accountLogin) {
        this.accountLogin = accountLogin;
    }

    /** 1つの制限の設定。 */
    public static class Rule {

        private int capacity;
        private int refillPerMinute;

        public Rule() {
        }

        public Rule(int capacity, int refillPerMinute) {
            this.capacity = capacity;
            this.refillPerMinute = refillPerMinute;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getRefillPerMinute() {
            return refillPerMinute;
        }

        public void setRefillPerMinute(int refillPerMinute) {
            this.refillPerMinute = refillPerMinute;
        }

        double refillPerSecond() {
            return refillPerMinute / 60d;
        }
    }
}
