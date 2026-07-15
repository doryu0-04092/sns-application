package com.snsapp.backend.service;

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
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

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
            refreshTokenMapper.revokeAllForUser(existing.getUserId());
            throw new InvalidRefreshTokenException();
        }
        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException();
        }

        refreshTokenMapper.revoke(existing.getId());
        String newRawToken = issue(existing.getUserId());
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
