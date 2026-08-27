package com.snsapp.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 認証クッキーの属性のうち、環境によって変える必要があるもの。
 *
 * <p>{@code secure} を設定で切り替えられるようにしているのは、ローカル開発がHTTPで、
 * デプロイ先(CloudFront配下)がHTTPSだからである。Secure属性の付いたクッキーはHTTPでは
 * 送信されないため、値を固定すると必ずどちらかの環境が壊れる。
 *
 * <p>既定を {@code false} にしてあるのは、設定漏れの結果を安全側に倒すためではなく逆で、
 * ローカル開発とE2Eを既定のまま動かせるようにするためである。
 * <b>デプロイ時は {@code COOKIE_SECURE=true} を必ず渡すこと。</b>
 */
@Component
@ConfigurationProperties(prefix = "app.cookie")
public class CookieProperties {

    private boolean secure = false;

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }
}
