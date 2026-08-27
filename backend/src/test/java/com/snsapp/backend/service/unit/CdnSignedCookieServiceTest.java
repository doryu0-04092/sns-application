package com.snsapp.backend.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snsapp.backend.security.CookieProperties;
import com.snsapp.backend.storage.CdnProperties;
import com.snsapp.backend.storage.CdnSignedCookieService;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/**
 * {@link CdnSignedCookieService} の検証。
 *
 * <p>署名の値そのものはCloudFront側でしか検証できないため、ここで確認するのは
 * <b>クッキーの構成と属性</b>である。3つ揃っていること、{@code /images} に絞られていること、
 * JavaScriptから読めないこと、そしてCDN無効時に何も発行しないこと。
 *
 * <p>鍵はテスト内で生成する。固定の鍵をリポジトリに置くと、それがたとえテスト用でも
 * 「秘密鍵をコミットしてよい」という前例になるため。
 */
class CdnSignedCookieServiceTest {

    private static final String BASE_URL = "https://dxxxx.cloudfront.net";

    private CdnProperties cdnProperties;
    private CookieProperties cookieProperties;
    private CdnSignedCookieService service;

    @BeforeEach
    void setUp() throws Exception {
        cdnProperties = new CdnProperties();
        cdnProperties.setKeyPairId("K2JCJMDEHXQW5F");
        cdnProperties.setPrivateKey(generatePkcs8Pem());
        cdnProperties.setCookieExpiry(Duration.ofHours(12));

        cookieProperties = new CookieProperties();
        service = new CdnSignedCookieService(cdnProperties, cookieProperties);
    }

    /** CloudFrontはPolicy / Signature / Key-Pair-Id の3つが揃って初めて配信を許可する。 */
    @Test
    void 署名付きクッキーは3つで1組として発行される() {
        cdnProperties.setBaseUrl(BASE_URL);

        assertThat(service.issue())
                .extracting(ResponseCookie::getName)
                .containsExactlyInAnyOrder("CloudFront-Policy", "CloudFront-Signature", "CloudFront-Key-Pair-Id");
    }

    /**
     * Path を {@code /images} に絞ることで、APIへのリクエストにはこのクッキーが乗らない。
     * HttpOnly なのはJavaScriptから署名を読み出せないようにするため。
     */
    @Test
    void 署名付きクッキーは画像のパスに限定されJavaScriptから読めない() {
        cdnProperties.setBaseUrl(BASE_URL);

        assertThat(service.issue()).allSatisfy(cookie -> {
            assertThat(cookie.getPath()).isEqualTo("/images");
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofHours(12));
        });
    }

    /** 認証クッキーと同じ設定でSecureを切り替える。HTTPSのデプロイ先だけで付けばよい。 */
    @Test
    void 設定を有効にすると署名付きクッキーにもSecureが付く() {
        cdnProperties.setBaseUrl(BASE_URL);
        cookieProperties.setSecure(true);

        assertThat(service.issue()).allSatisfy(cookie -> assertThat(cookie.isSecure()).isTrue());
    }

    /** ログアウト時は同じPathで空値・MaxAge=0を返し、ブラウザ側の値を消す。 */
    @Test
    void ログアウト時は同じパスで空の値を返す() {
        cdnProperties.setBaseUrl(BASE_URL);

        List<ResponseCookie> cleared = service.clear();

        assertThat(cleared).hasSize(3).allSatisfy(cookie -> {
            assertThat(cookie.getValue()).isEmpty();
            assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
            assertThat(cookie.getPath()).isEqualTo("/images");
        });
    }

    /**
     * ローカル開発とE2EにはCloudFrontが無い。base-urlが空のときに何も発行しないことで、
     * 鍵の設定なしでもアプリが起動し動作する。
     */
    @Test
    void CDNが無効なら発行も削除も何もしない() {
        assertThat(service.issue()).isEmpty();
        assertThat(service.clear()).isEmpty();
    }

    /**
     * PKCS#1(BEGIN RSA PRIVATE KEY)を渡すと読めない。JavaのKeyFactoryがPKCS#8しか
     * 直接扱えないためで、Terraform側で private_key_pem_pkcs8 を使う必要がある。
     * 気づかずに設定した場合に、原因が分かる形で失敗することを確認する。
     */
    @Test
    void 秘密鍵が読めない形式なら理由の分かる例外になる() {
        cdnProperties.setBaseUrl(BASE_URL);
        cdnProperties.setPrivateKey("-----BEGIN RSA PRIVATE KEY-----\nbm90LWEta2V5\n-----END RSA PRIVATE KEY-----");

        assertThatThrownBy(() -> service.issue())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PKCS#8");
    }

    private static String generatePkcs8Pem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        // getEncoded() はPKCS#8のDERを返すため、PEMのヘッダを付けるだけでよい。
        String base64 = Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }
}
