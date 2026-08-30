package com.snsapp.backend.controller;

import com.snsapp.backend.common.ApiResponse;
import com.snsapp.backend.config.OpenApiConfig;
import com.snsapp.backend.dto.LoginRequest;
import com.snsapp.backend.dto.SignupRequest;
import com.snsapp.backend.dto.UserResponse;
import com.snsapp.backend.exception.InvalidRefreshTokenException;
import com.snsapp.backend.security.CookieProperties;
import com.snsapp.backend.security.JwtAuthFilter;
import com.snsapp.backend.security.JwtProperties;
import com.snsapp.backend.security.JwtService;
import com.snsapp.backend.service.AuthService;
import com.snsapp.backend.service.RefreshTokenService;
import com.snsapp.backend.storage.CdnSignedCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
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
@Tag(name = "認証", description = "サインアップ・ログイン・トークン再発行。認証状態はすべてhttpOnlyクッキーで保持する")
public class AuthController {

    private static final String AUTH_COOKIE_NAME = "auth_token";
    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final CookieProperties cookieProperties;
    private final CdnSignedCookieService cdnSignedCookieService;

    public AuthController(
            AuthService authService,
            RefreshTokenService refreshTokenService,
            JwtService jwtService,
            JwtProperties jwtProperties,
            CookieProperties cookieProperties,
            CdnSignedCookieService cdnSignedCookieService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
        this.cdnSignedCookieService = cdnSignedCookieService;
    }

    /**
     * 新規ユーザーを登録し、そのままログイン状態にする(F-01)。
     *
     * <p>docs/screens.md の画面遷移(S-02 登録成功 -> S-03 タイムライン)がログイン済みであることを
     * 前提としているため、login と同じアクセス/リフレッシュトークンのクッキーをここで発行する。
     * これによりフロントエンドが登録直後にログインを再度呼ぶ必要がなくなる。
     */
    @Operation(
            summary = "新規登録",
            description = """
                    ユーザーを登録し、**同時にログイン状態にする**。
                    成功時に `auth_token` と `refresh_token` の両クッキーがSet-Cookieされるため、
                    クライアントは登録後にログインを呼び直す必要がない。

                    成功時のステータスは **201 Created**。

                    エラー: メールアドレスが登録済みの場合は400 `EMAIL_ALREADY_EXISTS`。
                    """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "登録成功。認証クッキーが発行される")
    @SecurityRequirements
    @PostMapping("/api/auth/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(@Valid @RequestBody SignupRequest request) {
        UserResponse user = authService.signup(request);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, issueAccessCookie(user.id()).toString())
                .header(HttpHeaders.SET_COOKIE, issueRefreshCookie(user.id()).toString());
        addCookies(response, cdnSignedCookieService.issue());
        return response.body(ApiResponse.of(user));
    }

    @Operation(
            summary = "ログイン",
            description = """
                    成功時に2つのクッキーを発行する。

                    - `auth_token` — アクセストークン(JWT・15分・`Path=/`)
                    - `refresh_token` — リフレッシュトークン(opaque・7日・`Path=/api/auth`)

                    どちらもhttpOnlyのためJavaScriptからは読めない。
                    ブラウザ以外のクライアントはSet-Cookieを保持して送り返す必要がある。
                    """)
    @OpenApiConfig.ErrorResponse(
            status = "401", code = "INVALID_CREDENTIALS",
            message = "メールアドレスまたはパスワードが正しくありません",
            description = "資格情報が一致しない。メールとパスワードのどちらが誤りかは区別せず同じ応答を返す")
    @OpenApiConfig.ErrorResponse(
            status = "429", code = "TOO_MANY_LOGIN_ATTEMPTS",
            message = "ログインの試行が多すぎます。しばらく待ってからやり直してください",
            description = """
                    試行が多すぎる。Retry-Afterヘッダに再試行までの秒数が入る。
                    軸が2つあり、codeで区別できる。
                    `TOO_MANY_LOGIN_ATTEMPTS` は同一アカウントへの失敗が上限に達した場合で、
                    多数のIPから1つのアカウントを狙う形に効く。
                    `RATE_LIMITED` は送信元IP単位の上限に達した場合。
                    どちらも、未登録のアドレスかどうかで挙動を変えない
                    (変えると「制限された = 実在する」という手掛かりを与えるため)。
                    """)
    @SecurityRequirements
    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@Valid @RequestBody LoginRequest request) {
        UserResponse user = authService.login(request);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, issueAccessCookie(user.id()).toString())
                .header(HttpHeaders.SET_COOKIE, issueRefreshCookie(user.id()).toString());
        addCookies(response, cdnSignedCookieService.issue());
        return response.body(ApiResponse.of(user));
    }

    @Operation(
            summary = "トークン再発行",
            description = """
                    `refresh_token` クッキーを検証し、アクセストークンとリフレッシュトークンの両方を
                    **ローテーション**して再発行する（使い終わった古いリフレッシュトークンは失効する）。

                    リクエストボディは不要。認証は `refresh_token` クッキーのみで行う。
                    このクッキーは `Path=/api/auth` に限定されているため、
                    他のAPIパスへのリクエストには送信されない。

                    典型的な使い方: 任意のAPIが401を返したら本APIを1回呼び、成功したら元のリクエストを再試行する。
                    401が返った場合は再ログインが必要。
                    """)
    @OpenApiConfig.ErrorResponse(
            status = "401", code = "INVALID_REFRESH_TOKEN",
            message = "セッションの有効期限が切れました。再度ログインしてください",
            description = "リフレッシュトークンが無い・失効・再利用検知。再ログインが必要")
    @SecurityRequirements
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

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        // 画像用クッキーもここで発行し直す。利用を続けている間は期限切れにならないようにするため。
        addCookies(response, cdnSignedCookieService.issue());
        return response.body(ApiResponse.of(null));
    }

    @Operation(
            summary = "ログアウト",
            description = """
                    リフレッシュトークンをサーバー側で失効させ、両クッキーを空・MaxAge=0で上書きして削除する。

                    `refresh_token` クッキーが無い場合もエラーにせず200を返す（冪等）。
                    ログアウト処理がクライアント側の状態に依存して失敗しないようにするため。
                    """)
    @PostMapping("/api/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshTokenCookie) {
        if (refreshTokenCookie != null) {
            refreshTokenService.revoke(refreshTokenCookie);
        }

        ResponseCookie accessCookie = buildAuthCookie("", 0);
        ResponseCookie refreshCookie = buildRefreshCookie("", 0);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        addCookies(response, cdnSignedCookieService.clear());
        return response.body(ApiResponse.of(null));
    }

    @Operation(
            summary = "ログイン中ユーザーの取得",
            description = """
                    `auth_token` クッキーから解決した現在のユーザーを返す。

                    アプリ起動時のログイン状態の確認に使う。401が返ればログイン画面へ、
                    200が返ればそのまま利用を継続してよい。
                    """)
    @GetMapping("/api/auth/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE);
        UserResponse user = authService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    /**
     * 画像取得用のCDNクッキーを応答に載せる。
     * CDNが無効な環境ではリストが空になるため、ここは何もしない。
     */
    private void addCookies(ResponseEntity.BodyBuilder response, List<ResponseCookie> cookies) {
        for (ResponseCookie cookie : cookies) {
            response.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
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
                .secure(cookieProperties.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    private ResponseCookie buildRefreshCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }
}
