package com.snsapp.backend.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.snsapp.backend.entity.RefreshToken;
import com.snsapp.backend.exception.InvalidRefreshTokenException;
import com.snsapp.backend.mapper.RefreshTokenMapper;
import com.snsapp.backend.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    // トークンの値そのものは絶対に出さない(ログに残れば認証情報の漏洩になる)。
    // 記録するのは「何が起きたか」と userId のみ。
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /**
     * 並行リフレッシュを盗用と誤判定しないための猶予時間。
     *
     * <p><b>これが無いと、正常な利用者が突然ログアウトされる。</b>
     * タブを2枚開いていて同時に401になると、2本のリクエストが同じ
     * リフレッシュトークンを使う。先に着いた方がローテーションを終えた時点で
     * 元のトークンは失効済みになり、<b>後から着いた方が「失効済みの再提示」
     * つまり盗用と判定される</b>。全トークンが失効するため、
     * 開いていた全てのタブがログイン画面へ飛ぶ。
     *
     * <p>実際に curl で再現している(2026-08-30):
     *
     * <pre>
     *   A: 200  refresh token rotated userId=1
     *   B: 401  refresh token reuse detected, revoking all tokens userId=1
     * </pre>
     *
     * <p><b>代償を明記する。</b> 盗まれたトークンが、正規のローテーションから
     * この時間内に使われた場合は検知できない。短くするほど誤検知が増え、
     * 長くするほど検知が緩む。既定の10秒は「同時に飛んだリクエストが着く間隔」
     * としては十分に長く、盗用の窓としては十分に短い、という判断である。
     *
     * <p>値は {@link JwtProperties#getRefreshReuseGraceSeconds()} から読む。
     */

    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenMapper refreshTokenMapper, JwtProperties jwtProperties) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.jwtProperties = jwtProperties;
    }

    public String issue(Long userId) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshTokenExpirationSeconds()));
        refreshTokenMapper.insert(refreshToken);

        return rawToken;
    }

    /**
     * リフレッシュトークンをローテーションする。
     *
     * <p>既に失効済みのトークンが提示された場合は盗用の兆候とみなし、
     * そのユーザーの全リフレッシュトークンを一括失効させる。
     * <b>ただし直前のローテーション直後だけは例外にする</b>
     * ({@link #CONCURRENT_REFRESH_GRACE_SECONDS})。
     * 並行リフレッシュを盗用と取り違えると、正常な利用者を締め出すことになる。
     */
    // **ひとつのトランザクションにする。**
    //
    // 「後継を発行する」と「旧を失効させる」が別々に確定すると、
    // 途中で落ちたときに次のどちらかが残る。
    //   - 後継だけできて旧が有効なまま = 使い捨てのはずが2本有効になる
    //   - 旧だけ失効して後継が無い     = 利用者が締め出される
    //
    // **noRollbackFor が要る。** 盗用検知は「全トークンを失効させてから
    // 例外を投げる」形であり、既定のままだと例外でその失効ごと巻き戻る。
    // 検知しても何も失効しない、という最悪の形になる。
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotationResult rotate(String rawToken) {
        RefreshToken existing = refreshTokenMapper.findByTokenHash(hash(rawToken));
        if (existing == null) {
            throw new InvalidRefreshTokenException();
        }
        if (existing.getRevokedAt() != null) {
            if (isConcurrentRefresh(existing)) {
                // 直前にローテーションされたばかりのトークン = 同時に飛んだ2本目。
                // 盗用ではないので締め出さず、新しいトークンを兄弟として発行する。
                //
                // **後継トークンを返さないのは、生の値を保存していないためである**
                // (DBにはハッシュしか無い)。新しく発行しても利用者から見た結果は同じで、
                // 古い方は失効済みのまま残る。
                log.info("concurrent refresh within grace, issuing sibling token {}",
                        kv("userId", existing.getUserId()));
                return new RotationResult(existing.getUserId(), issue(existing.getUserId()));
            }
            // 使用済みトークンの再提示 = 盗用の兆候。ここが鳴ったら攻撃を疑って調査する。
            // 全トークンを失効させるため、正規の利用者も再ログインを強いられる(影響が利用者に及ぶ)。
            log.warn("refresh token reuse detected, revoking all tokens {}",
                    kv("userId", existing.getUserId()));
            refreshTokenMapper.revokeAllForUser(existing.getUserId());
            throw new InvalidRefreshTokenException();
        }
        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            // 7日間アクセスが無かった場合に起きる。単体では正常だが、急増はセッション設定の誤りを疑う。
            log.info("refresh token expired {}", kv("userId", existing.getUserId()));
            throw new InvalidRefreshTokenException();
        }

        // **後継を先に作ってから、そのIDを記録して失効させる。**
        // 順序が逆だと、記録すべきIDがまだ存在しない。
        String newRawToken = issue(existing.getUserId());
        RefreshToken successor = refreshTokenMapper.findByTokenHash(hash(newRawToken));
        refreshTokenMapper.revokeReplacedBy(existing.getId(), successor.getId());
        log.info("refresh token rotated {}", kv("userId", existing.getUserId()));
        return new RotationResult(existing.getUserId(), newRawToken);
    }

    /**
     * 「同時に飛んだ2本目」かどうかを判定する。
     *
     * <p>失効してからの経過時間だけで見る。<b>盗まれたトークンかどうかは区別できない</b>ため、
     * 判断は時間の窓に委ねている。窓を狭めるほど誤検知(正常な利用者の締め出し)が増え、
     * 広げるほど盗用の検知が緩む。
     */
    /**
     * 「同時に飛んだ2本目」かどうかを判定する。
     *
     * <p>条件は2つで、<b>どちらも欠かせない。</b>
     *
     * <ol>
     *   <li><b>後継トークンがある</b> = 正規のローテーションで置き換えられた。
     *       一括失効(盗用検知)とログアウトは後継を残さないため、ここで弾かれる</li>
     *   <li>失効してから猶予時間内である</li>
     * </ol>
     *
     * <p><b>時間だけで判断すると、盗用検知が自分で無効化される。</b>
     * 一括失効も revoked_at を「今」にするため、その直後の再提示が
     * 猶予で通ってしまい、失効させたはずのセッションが復活する。
     * 実際に E2E のログで次の順に起きた(2026-08-30):
     *
     * <pre>
     *   10.357  refresh token rotated
     *   12.039  reuse detected, revoking all tokens
     *   12.178  concurrent refresh within grace   ← 139ms後に復活
     * </pre>
     */
    private boolean isConcurrentRefresh(RefreshToken existing) {
        if (existing.getReplacedBy() == null) {
            return false;
        }
        return existing.getRevokedAt()
                .isAfter(LocalDateTime.now()
                        .minusSeconds(jwtProperties.getRefreshReuseGraceSeconds()));
    }

    public void revoke(String rawToken) {
        RefreshToken existing = refreshTokenMapper.findByTokenHash(hash(rawToken));
        if (existing != null) {
            refreshTokenMapper.revoke(existing.getId());
        }
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record RotationResult(Long userId, String newRawToken) {
    }
}
