package com.snsapp.backend.controller;

import com.snsapp.backend.common.ApiResponse;
import com.snsapp.backend.dto.LoginRequest;
import com.snsapp.backend.dto.SignupRequest;
import com.snsapp.backend.dto.UserResponse;
import com.snsapp.backend.exception.InvalidRefreshTokenException;
import com.snsapp.backend.security.JwtAuthFilter;
import com.snsapp.backend.security.JwtProperties;
import com.snsapp.backend.security.JwtService;
import com.snsapp.backend.service.AuthService;
import com.snsapp.backend.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private static final String AUTH_COOKIE_NAME = "auth_token";
    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthController(
            AuthService authService,
            RefreshTokenService refreshTokenService,
            JwtService jwtService,
            JwtProperties jwtProperties) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * 新規ユーザーを登録し、そのままログイン状態にする(F-01)。
     *
     * <p>docs/screens.md の画面遷移(S-02 登録成功 -> S-03 タイムライン)がログイン済みであることを
     * 前提としているため、login と同じアクセス/リフレッシュトークンのクッキーをここで発行する。
     * これによりフロントエンドが登録直後にログインを再度呼ぶ必要がなくなる。
     */
    @PostMapping("/api/auth/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(@Valid @RequestBody SignupRequest request) {
        UserResponse user = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, issueAccessCookie(user.id()).toString())
                .header(HttpHeaders.SET_COOKIE, issueRefreshCookie(user.id()).toString())
                .body(ApiResponse.of(user));
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@Valid @RequestBody LoginRequest request) {
        UserResponse user = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, issueAccessCookie(user.id()).toString())
                .header(HttpHeaders.SET_COOKIE, issueRefreshCookie(user.id()).toString())
                .body(ApiResponse.of(user));
    }

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshTokenCookie) {
        if (refreshTokenCookie == null) {
            throw new InvalidRefreshTokenException();
        }

        RefreshTokenService.RotationResult result = refreshTokenService.rotate(refreshTokenCookie);
        ResponseCookie accessCookie = buildAuthCookie(
                jwtService.issueToken(result.userId()), jwtProperties.getAccessTokenExpirationSeconds());
        ResponseCookie refreshCookie = buildRefreshCookie(
                result.newRawToken(), jwtProperties.getRefreshTokenExpirationSeconds());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.of(null));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshTokenCookie) {
        if (refreshTokenCookie != null) {
            refreshTokenService.revoke(refreshTokenCookie);
        }

        ResponseCookie accessCookie = buildAuthCookie("", 0);
        ResponseCookie refreshCookie = buildRefreshCookie("", 0);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.of(null));
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE);
        UserResponse user = authService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    private ResponseCookie issueAccessCookie(Long userId) {
        return buildAuthCookie(jwtService.issueToken(userId), jwtProperties.getAccessTokenExpirationSeconds());
    }

    private ResponseCookie issueRefreshCookie(Long userId) {
        return buildRefreshCookie(refreshTokenService.issue(userId), jwtProperties.getRefreshTokenExpirationSeconds());
    }

    private ResponseCookie buildAuthCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    private ResponseCookie buildRefreshCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }
}
