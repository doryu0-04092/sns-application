package com.snsapp.backend.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 画像の表示をCloudFront経由にするための設定。
 *
 * <p><b>{@code baseUrl} が空のときはCDNを使わず、従来どおりS3の署名付きURLを返す。</b>
 * ローカル開発とE2EにはCloudFrontが存在しない(LocalStackはS3しか持たない)ため、
 * この分岐が無いと開発環境で画像が一切表示できなくなる。
 *
 * <p>署名は<b>URLではなくクッキー</b>に載せる。署名付きURLだと発行のたびにURLが変わるため
 * CDNのキャッシュキーが毎回変わってヒットせず、CloudFrontを挟む意味がなくなる。
 * クッキー方式ならURLが {@code <baseUrl>/images/<key>} で固定になる。
 *
 * <p>{@code privateKey} は<b>PKCS#8形式</b>(<code>-----BEGIN PRIVATE KEY-----</code>)であること。
 * JavaのKeyFactoryがPKCS#1を直接読めないためで、Terraformでは
 * {@code tls_private_key} の {@code private_key_pem_pkcs8} を使う。
 */
@Component
@ConfigurationProperties(prefix = "app.cdn")
public class CdnProperties {

    private String baseUrl = "";
    private String keyPairId = "";
    private String privateKey = "";
    private Duration cookieExpiry = Duration.ofHours(12);

    /** CDN配信が有効か。baseUrlが空ならS3の署名付きURLにフォールバックする。 */
    public boolean isEnabled() {
        return !baseUrl.isBlank();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    public String getKeyPairId() {
        return keyPairId;
    }

    public void setKeyPairId(String keyPairId) {
        this.keyPairId = keyPairId == null ? "" : keyPairId.trim();
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey == null ? "" : privateKey;
    }

    public Duration getCookieExpiry() {
        return cookieExpiry;
    }

    public void setCookieExpiry(Duration cookieExpiry) {
        this.cookieExpiry = cookieExpiry;
    }
}
