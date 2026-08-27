package com.snsapp.backend.storage;

import com.snsapp.backend.security.CookieProperties;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.cookie.CookiesForCustomPolicy;
import software.amazon.awssdk.services.cloudfront.model.CustomSignerRequest;

/**
 * 画像を取得するためのCloudFront署名付きクッキーを発行する。
 *
 * <p>クッキーは3つ(Policy / Signature / Key-Pair-Id)で1組であり、3つ揃って初めてCloudFrontが
 * {@code /images/*} の配信を許可する。{@code Path=/images} に絞っているため、APIへのリクエストには
 * 付かない。
 *
 * <p><b>URLではなくクッキーに署名を載せる理由。</b>署名付きURLは文字列を持っている人なら誰でも
 * 使えてしまい、DevTools・Referer・ブラウザ履歴・プロキシのログ・利用者によるコピー&amp;ペーストと、
 * 通常の利用のなかで外へ出る経路が多い。クッキーにすればHttpOnlyでJavaScriptから読めず、
 * URL自体は秘密でなくなるのでこれらの経路が塞がる。あわせてURLが固定になるためCDNのキャッシュも効く。
 *
 * <p><b>限界。</b>クッキーもまた「持っている人なら誰でも使える」資格情報である点は変わらない。
 * サーバー側で個別に失効させる手段は無く、取り消すにはキーグループから公開鍵を外して
 * 全利用者分を一斉に無効化するしかない。この方式が効くのは「盗まれた後」ではなく
 * 「普通に使っているだけで漏れる経路を塞ぐ」側である。
 */
@Service
public class CdnSignedCookieService {

    /** 署名の対象。ワイルドカードを含むためcanned policyではなくcustom policyを使う。 */
    private static final String RESOURCE_SUFFIX = "/images/*";

    private static final String COOKIE_PATH = "/images";

    private final CdnProperties cdnProperties;
    private final CookieProperties cookieProperties;

    public CdnSignedCookieService(CdnProperties cdnProperties, CookieProperties cookieProperties) {
        this.cdnProperties = cdnProperties;
        this.cookieProperties = cookieProperties;
    }

    /**
     * 署名付きクッキーを発行する。CDNが無効なら空のリストを返す。
     *
     * <p>ログイン・サインアップ・トークン再発行のたびに呼ばれるため、利用を続けている間は
     * 期限切れにならない。
     */
    public List<ResponseCookie> issue() {
        if (!cdnProperties.isEnabled()) {
            return List.of();
        }

        CookiesForCustomPolicy cookies = CloudFrontUtilities.create()
                .getCookiesForCustomPolicy(CustomSignerRequest.builder()
                        .resourceUrl(cdnProperties.getBaseUrl() + RESOURCE_SUFFIX)
                        .privateKey(parsePrivateKey(cdnProperties.getPrivateKey()))
                        .keyPairId(cdnProperties.getKeyPairId())
                        .expirationDate(Instant.now().plus(cdnProperties.getCookieExpiry()))
                        .build());

        return List.of(
                toCookie(cookies.policyHeaderValue(), cdnProperties.getCookieExpiry()),
                toCookie(cookies.signatureHeaderValue(), cdnProperties.getCookieExpiry()),
                toCookie(cookies.keyPairIdHeaderValue(), cdnProperties.getCookieExpiry()));
    }

    /**
     * 署名付きクッキーを削除する(ログアウト時)。CDNが無効なら空のリストを返す。
     *
     * <p>これで消えるのはブラウザに保存された値だけで、署名そのものが無効になるわけではない。
     * 期限が来るまでは、値を控えていれば引き続き使える。
     */
    public List<ResponseCookie> clear() {
        if (!cdnProperties.isEnabled()) {
            return List.of();
        }

        return List.of(
                expiredCookie("CloudFront-Policy"),
                expiredCookie("CloudFront-Signature"),
                expiredCookie("CloudFront-Key-Pair-Id"));
    }

    /**
     * SDKは {@code "CloudFront-Policy=<値>"} の形で返すため、最初の {@code =} で名前と値に分ける。
     * 値はURLセーフなbase64(AWSが {@code +/=} を {@code -_~} に置換したもの)なので、
     * クッキーの値としてそのまま使える。
     */
    private ResponseCookie toCookie(String headerValue, Duration maxAge) {
        String[] parts = headerValue.split("=", 2);
        return ResponseCookie.from(parts[0], parts[1])
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie expiredCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    /**
     * PKCS#8のPEMを {@link PrivateKey} に変換する。
     *
     * <p>環境変数で渡す都合上、改行が {@code \n} のままだったり無かったりするため、
     * ヘッダ・フッタと空白をすべて取り除いてからbase64として解釈する。
     */
    private PrivateKey parsePrivateKey(String pem) {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "")
                .replaceAll("\\s", "");

        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ex) {
            // 秘密鍵そのものは例外メッセージにも載せない。原因の切り分けに要るのは
            // 「読めなかった」という事実と形式の指定だけである。
            throw new IllegalStateException(
                    "CDN_PRIVATE_KEY を読み込めませんでした。PKCS#8形式(BEGIN PRIVATE KEY)である必要があります", ex);
        }
    }
}
