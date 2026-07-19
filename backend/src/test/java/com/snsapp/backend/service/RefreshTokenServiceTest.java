package com.snsapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snsapp.backend.entity.RefreshToken;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.exception.InvalidRefreshTokenException;
import com.snsapp.backend.mapper.RefreshTokenMapper;
import com.snsapp.backend.support.AbstractIntegrationTest;
import com.snsapp.backend.support.TestFixtures;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * リフレッシュトークンのローテーションと盗用検知の検証。
 *
 * <p>このアプリで最もセキュリティ上重要な分岐であり、失敗しても画面上は「再ログインを求められる」
 * としか見えないため、壊れても気づきにくい。特に「失効済みトークンの再提示で全トークンを
 * 一括失効させる」挙動を明示的に固定する。
 */
@Transactional
class RefreshTokenServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenMapper refreshTokenMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestFixtures fixtures;

    @Test
    void ローテーションすると新しいトークンが発行され元のトークンは失効する() {
        User user = fixtures.user();
        String original = refreshTokenService.issue(user.getId());

        RefreshTokenService.RotationResult result = refreshTokenService.rotate(original);

        assertThat(result.userId()).isEqualTo(user.getId());
        assertThat(result.newRawToken()).isNotEqualTo(original);
        assertThat(findByRawToken(original).getRevokedAt()).isNotNull();
        assertThat(findByRawToken(result.newRawToken()).getRevokedAt()).isNull();
    }

    @Test
    void 新しいトークンでさらにローテーションできる() {
        User user = fixtures.user();
        String first = refreshTokenService.issue(user.getId());

        String second = refreshTokenService.rotate(first).newRawToken();
        String third = refreshTokenService.rotate(second).newRawToken();

        assertThat(third).isNotEqualTo(second).isNotEqualTo(first);
        assertThat(findByRawToken(third).getRevokedAt()).isNull();
    }

    @Test
    void 存在しないトークンでのローテーションは失敗する() {
        assertThatThrownBy(() -> refreshTokenService.rotate("this-token-was-never-issued"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void 期限切れトークンでのローテーションは失敗する() {
        User user = fixtures.user();
        String token = refreshTokenService.issue(user.getId());
        expire(token);

        assertThatThrownBy(() -> refreshTokenService.rotate(token))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    /**
     * 盗用検知の中核。攻撃者が盗んだ古いトークンを使うと、正規ユーザーが持っている
     * 有効なトークンごと全て失効し、両者が再ログインを強いられる(=被害の連鎖を止める)。
     */
    @Test
    void 失効済みトークンを再提示するとそのユーザーの全トークンが失効する() {
        User user = fixtures.user();
        String stolen = refreshTokenService.issue(user.getId());
        String rotated = refreshTokenService.rotate(stolen).newRawToken();
        // 無関係な別ログインセッション(別デバイス等)のトークンも巻き添えで失効することを確認する
        String otherSession = refreshTokenService.issue(user.getId());

        assertThatThrownBy(() -> refreshTokenService.rotate(stolen))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(findByRawToken(rotated).getRevokedAt()).isNotNull();
        assertThat(findByRawToken(otherSession).getRevokedAt()).isNotNull();
    }

    @Test
    void 盗用検知は他ユーザーのトークンには影響しない() {
        User victim = fixtures.user();
        User bystander = fixtures.user();
        String stolen = refreshTokenService.issue(victim.getId());
        refreshTokenService.rotate(stolen);
        String bystanderToken = refreshTokenService.issue(bystander.getId());

        assertThatThrownBy(() -> refreshTokenService.rotate(stolen))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(findByRawToken(bystanderToken).getRevokedAt()).isNull();
    }

    @Test
    void revokeするとそのトークンでローテーションできなくなる() {
        User user = fixtures.user();
        String token = refreshTokenService.issue(user.getId());

        refreshTokenService.revoke(token);

        assertThat(findByRawToken(token).getRevokedAt()).isNotNull();
        assertThatThrownBy(() -> refreshTokenService.rotate(token))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void 存在しないトークンのrevokeは例外にならない() {
        refreshTokenService.revoke("this-token-was-never-issued");
    }

    @Test
    void 生トークンはDBに保存されずSHA256ハッシュだけが保存される() {
        User user = fixtures.user();
        String rawToken = refreshTokenService.issue(user.getId());

        RefreshToken stored = findByRawToken(rawToken);
        assertThat(stored.getTokenHash()).isNotEqualTo(rawToken).hasSize(64);
        Long rawMatches = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Long.class, rawToken);
        assertThat(rawMatches).isZero();
    }

    private RefreshToken findByRawToken(String rawToken) {
        return refreshTokenMapper.findByTokenHash(sha256Hex(rawToken));
    }

    private void expire(String rawToken) {
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET expires_at = ? WHERE token_hash = ?",
                LocalDateTime.now().minusDays(1),
                sha256Hex(rawToken));
    }

    // 本番コードのhash()はprivateのため、テスト側で同じ計算を再現して保存状態を確認する。
    private String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
