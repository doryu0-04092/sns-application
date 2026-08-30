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
        // **失効した時刻を猶予の外まで遡らせる。**
        // 直後の再提示は「同時に飛んだ2本目」として通す仕様になったため、
        // 盗用として扱われるのは時間が経ってからである。
        ageRevocation(stolen);
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
        ageRevocation(stolen);
        String bystanderToken = refreshTokenService.issue(bystander.getId());

        assertThatThrownBy(() -> refreshTokenService.rotate(stolen))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(findByRawToken(bystanderToken).getRevokedAt()).isNull();
    }

    /**
     * <b>これが無いと、正常な利用者が突然ログアウトされる。</b>
     *
     * <p>タブを2枚開いていて同時に401になると、2本のリクエストが同じ
     * リフレッシュトークンを使う。先に着いた方がローテーションを終えた時点で
     * 元のトークンは失効済みになり、後から着いた方が盗用と判定されていた。
     * 全トークンが失効するため、<b>開いていた全てのタブがログイン画面へ飛ぶ</b>。
     *
     * <p>実際に curl で再現している(2026-08-30):
     *
     * <pre>
     *   A: 200  refresh token rotated userId=1
     *   B: 401  refresh token reuse detected, revoking all tokens userId=1
     * </pre>
     */
    @Test
    void 直後の再提示は並行リフレッシュとして通り全トークンを失効させない() {
        User user = fixtures.user();
        String shared = refreshTokenService.issue(user.getId());
        // 別デバイスのセッション。巻き添えで失効していないことを確かめるために置く。
        String otherSession = refreshTokenService.issue(user.getId());

        // 1本目。ここで shared は失効する。
        String first = refreshTokenService.rotate(shared).newRawToken();

        // 2本目。同じトークンを、失効した直後に提示する。
        RefreshTokenService.RotationResult second = refreshTokenService.rotate(shared);

        // **締め出さない。** 新しいトークンが発行される。
        assertThat(second.userId()).isEqualTo(user.getId());
        assertThat(second.newRawToken()).isNotBlank().isNotEqualTo(first);

        // **1本目のトークンも、別デバイスのセッションも生きている。**
        assertThat(findByRawToken(first).getRevokedAt()).isNull();
        assertThat(findByRawToken(otherSession).getRevokedAt()).isNull();
    }

    /**
     * 猶予は「時間の窓」でしかない。窓を出れば、これまでどおり盗用として扱う。
     *
     * <p><b>代償を明記する。</b> 盗まれたトークンが正規のローテーションから
     * 10秒以内に使われた場合は検知できない。誤検知(正常な利用者の締め出し)を
     * 避けるために受け入れた交換条件である。
     */
    @Test
    void 猶予を過ぎた再提示はこれまでどおり盗用として扱う() {
        User user = fixtures.user();
        String stolen = refreshTokenService.issue(user.getId());
        refreshTokenService.rotate(stolen);
        ageRevocation(stolen);

        assertThatThrownBy(() -> refreshTokenService.rotate(stolen))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    /**
     * <b>盗用検知が自分で無効化されないこと。</b>
     *
     * <p>一括失効も revoked_at を「今」にする。猶予の判定を時間だけで行うと、
     * <b>失効させた直後の再提示が猶予で通り、セッションが復活する</b>。
     * 実際に E2E のログで次の順に起きた(2026-08-30):
     *
     * <pre>
     *   10.357  refresh token rotated
     *   12.039  reuse detected, revoking all tokens
     *   12.178  concurrent refresh within grace   ← 139ms後に復活
     * </pre>
     *
     * <p>一括失効は後継トークンを残さないため、猶予の対象にならない。
     */
    @Test
    void 一括失効された直後の再提示は猶予で通らない() {
        User user = fixtures.user();
        String stolen = refreshTokenService.issue(user.getId());
        String rotated = refreshTokenService.rotate(stolen).newRawToken();
        ageRevocation(stolen);

        // 盗用として検知され、全トークンが失効する。
        assertThatThrownBy(() -> refreshTokenService.rotate(stolen))
                .isInstanceOf(InvalidRefreshTokenException.class);

        // **その直後に、失効させられた正規のトークンを提示しても回復しない。**
        // 一括失効は後継を残さないため、猶予の判定に入らない。
        assertThat(findByRawToken(rotated).getReplacedBy()).isNull();
        assertThatThrownBy(() -> refreshTokenService.rotate(rotated))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokeするとそのトークンでローテーションできなくなる() {
        User user = fixtures.user();
        String token = refreshTokenService.issue(user.getId());

        refreshTokenService.revoke(token);

        // **ログアウトは後継トークンを残さない**ため、直後の再提示でも
        // 並行リフレッシュの猶予は効かない。時間を進める必要は無い。
        assertThat(findByRawToken(token).getRevokedAt()).isNotNull();
        assertThat(findByRawToken(token).getReplacedBy()).isNull();
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

    /**
     * 失効した時刻を猶予の外まで遡らせる。
     *
     * <p>並行リフレッシュの猶予(10秒)を待つ代わりに、記録の側を動かす。
     * <b>テストで実時間を待たない</b>ためである。
     */
    private void ageRevocation(String rawToken) {
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET revoked_at = ? WHERE token_hash = ?",
                LocalDateTime.now().minusMinutes(1),
                sha256Hex(rawToken));
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
