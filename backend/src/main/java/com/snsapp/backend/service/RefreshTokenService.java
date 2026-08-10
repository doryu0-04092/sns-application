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

@Service
public class RefreshTokenService {

    // トークンの値そのものは絶対に出さない(ログに残れば認証情報の漏洩になる)。
    // 記録するのは「何が起きたか」と userId のみ。
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

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
     * リフレッシュトークンをローテーションする。既に失効済みのトークンが提示された場合は
     * 盗用の兆候とみなし、そのユーザーの全リフレッシュトークンを一括失効させる。
     */
    public RotationResult rotate(String rawToken) {
        RefreshToken existing = refreshTokenMapper.findByTokenHash(hash(rawToken));
        if (existing == null) {
            throw new InvalidRefreshTokenException();
        }
        if (existing.getRevokedAt() != null) {
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

        refreshTokenMapper.revoke(existing.getId());
        String newRawToken = issue(existing.getUserId());
        log.info("refresh token rotated {}", kv("userId", existing.getUserId()));
        return new RotationResult(existing.getUserId(), newRawToken);
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
