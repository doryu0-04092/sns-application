package com.snsapp.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;
    private long accessTokenExpirationSeconds = 900;
    private long refreshTokenExpirationSeconds = 604800;

    /**
     * 並行リフレッシュを盗用と誤判定しないための猶予時間(秒)。
     *
     * <p>タブを2枚開いていて同時に401になると、2本のリクエストが同じ
     * リフレッシュトークンを使う。先に着いた方がローテーションを終えた時点で
     * 元のトークンは失効済みになり、<b>後から着いた方が盗用と判定される</b>。
     * 全トークンが失効するため、開いていた全てのタブがログイン画面へ飛ぶ。
     *
     * <p><b>設定にしているのは、E2Eで盗用検知そのものを確かめるためである。</b>
     * 猶予が10秒あると、テストは10秒待たないと「猶予の外」を作れない。
     * E2E用のスタックだけ短くして、同じ仕組みを短い時間で通す。
     *
     * <p><b>0 にはしない。</b> 0 は「猶予なし」であり、直そうとしている
     * 不具合そのものに戻る。
     */
    private long refreshReuseGraceSeconds = 10;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    public void setAccessTokenExpirationSeconds(long accessTokenExpirationSeconds) {
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    public long getRefreshReuseGraceSeconds() {
        return refreshReuseGraceSeconds;
    }

    public void setRefreshReuseGraceSeconds(long refreshReuseGraceSeconds) {
        this.refreshReuseGraceSeconds = refreshReuseGraceSeconds;
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationSeconds;
    }

    public void setRefreshTokenExpirationSeconds(long refreshTokenExpirationSeconds) {
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
    }
}
