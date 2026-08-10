package com.snsapp.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/**
 * アクセストークン(JWT)の発行と検証。
 *
 * <p>このクラスはログイン状態そのものを表すトークンを作る。ここが破れると、
 * 攻撃者が任意のユーザーになりすませる。そのため「正しく往復できること」より
 * <strong>「不正なトークンを確実に拒否すること」</strong>に重点を置いて検証する。
 *
 * <p>署名アルゴリズムやハッシュの実装そのものはライブラリ(jjwt)の責務なので検証しない。
 * 検証するのは<strong>使い方</strong> — 鍵の取り違え、期限の設定、改ざんの検知が
 * 意図どおりに働いているか。
 */
class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256!";
    private static final String OTHER_SECRET = "another-secret-key-long-enough-for-hs256!!!!!!";
    private static final Long USER_ID = 42L;

    private static JwtService jwtService(long expirationSeconds) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessTokenExpirationSeconds(expirationSeconds);
        return new JwtService(properties);
    }

    private static JwtService jwtService() {
        return jwtService(900);
    }

    private static SecretKey keyOf(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // --- 発行と検証の往復 ---

    @Test
    void 発行したトークンから利用者IDを復元できる() {
        JwtService service = jwtService();

        String token = service.issueToken(USER_ID);

        assertThat(service.parseUserId(token)).isEqualTo(USER_ID);
    }

    @Test
    void 有効期限は設定値どおりに入る() {
        JwtService service = jwtService(900);
        Instant before = Instant.now();

        String token = service.issueToken(USER_ID);

        Date expiration = Jwts.parser()
                .verifyWith(keyOf(SECRET))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
        // 発行時刻の取得と実際の発行の間に僅かなずれが出るため、幅を持たせて確認する。
        assertThat(expiration.toInstant())
                .isBetween(before.plusSeconds(895), before.plusSeconds(905));
    }

    // --- 期限切れ ---
    // docs/test-plan.md 3.2「JWTの期限切れ」。Thread.sleepではなく有効期限の注入で再現する。

    @Test
    void 期限切れのトークンは検証に失敗する() {
        // 発行した瞬間に期限が切れているトークンを作る。
        String expired = jwtService(-1).issueToken(USER_ID);

        assertThatThrownBy(() -> jwtService().parseUserId(expired))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void 有効期限内のトークンは検証を通る() {
        String token = jwtService(60).issueToken(USER_ID);

        assertThat(jwtService().parseUserId(token)).isEqualTo(USER_ID);
    }

    // --- 不正なトークンの拒否 ---

    // 鍵が違えば通らないこと。設定ミスや、攻撃者が自作したトークンをここで弾く。
    @Test
    void 別のシークレットで署名されたトークンは拒否される() {
        String forged = Jwts.builder()
                .subject(USER_ID.toString())
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(keyOf(OTHER_SECRET))
                .compact();

        assertThatThrownBy(() -> jwtService().parseUserId(forged))
                .isInstanceOf(RuntimeException.class);
    }

    // payloadだけ書き換えても署名が合わなくなるため通らない。
    // これが通ると「他人のIDに書き換えてなりすます」ことが可能になる。
    @Test
    void 改ざんされたトークンは拒否される() {
        JwtService service = jwtService();
        String[] parts = service.issueToken(USER_ID).split("\\.");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"999\"}".getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> service.parseUserId(tampered))
                .isInstanceOf(RuntimeException.class);
    }

    // 署名部分を削ぎ落とした「alg=none」型のトークン。署名検証を必須にしていないと通ってしまう。
    @Test
    void 署名の無いトークンは拒否される() {
        String unsigned = Jwts.builder()
                .subject(USER_ID.toString())
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .compact();

        assertThatThrownBy(() -> jwtService().parseUserId(unsigned))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void JWTの形をしていない文字列は拒否される() {
        assertThatThrownBy(() -> jwtService().parseUserId("not-a-jwt"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void 空文字は拒否される() {
        assertThatThrownBy(() -> jwtService().parseUserId(""))
                .isInstanceOf(RuntimeException.class);
    }

    // subjectが数値でないトークン。署名は正しいので検証は通り、Long変換で落ちる。
    // JwtAuthFilterはRuntimeExceptionを捕まえて401にするため、500にはならない。
    @Test
    void 利用者IDが数値でないトークンは拒否される() {
        String token = Jwts.builder()
                .subject("not-a-number")
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(keyOf(SECRET))
                .compact();

        assertThatThrownBy(() -> jwtService().parseUserId(token))
                .isInstanceOf(NumberFormatException.class);
    }
}
