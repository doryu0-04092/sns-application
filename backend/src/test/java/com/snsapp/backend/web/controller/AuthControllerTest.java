package com.snsapp.backend.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.snsapp.backend.controller.AuthController;
import com.snsapp.backend.dto.UserResponse;
import com.snsapp.backend.exception.DuplicateEmailException;
import com.snsapp.backend.exception.InvalidCredentialsException;
import com.snsapp.backend.exception.InvalidRefreshTokenException;
import com.snsapp.backend.exception.UnauthenticatedException;
import com.snsapp.backend.security.CookieProperties;
import com.snsapp.backend.security.JwtProperties;
import com.snsapp.backend.security.JwtService;
import com.snsapp.backend.service.AuthService;
import com.snsapp.backend.service.RefreshTokenService;
import com.snsapp.backend.storage.CdnSignedCookieService;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@link AuthController} のWeb層スライステスト(docs/test-plan.md 4.2)。
 *
 * <p>認証クッキーの発行・削除がこのControllerの主な責務なので、Set-Cookieヘッダを重点的に見る。
 * {@code Secure} 属性は {@link CookieProperties} で環境ごとに切り替わるため、
 * 既定(false)と有効時(true)の両方を検証する。
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest extends AbstractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @MockitoBean
    private CookieProperties cookieProperties;

    /**
     * 画像用の署名付きクッキーの中身は {@code CdnSignedCookieServiceTest} が見る。
     * ここではモックの既定(空リスト)のまま、認証クッキーの検証を邪魔しないようにしておく。
     */
    @MockitoBean
    private CdnSignedCookieService cdnSignedCookieService;

    private static final UserResponse USER = new UserResponse(1L, "user@example.com", "山田", null, null);

    @BeforeEach
    void setUp() {
        when(jwtProperties.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(jwtProperties.getRefreshTokenExpirationSeconds()).thenReturn(604800L);
        when(jwtService.issueToken(anyLong())).thenReturn("jwt-token");
        when(refreshTokenService.issue(anyLong())).thenReturn("refresh-token");
    }

    private static String signupBody(String email, String password, String displayName) {
        return "{\"email\": \"%s\", \"password\": \"%s\", \"displayName\": \"%s\"}"
                .formatted(email, password, displayName);
    }

    // --- POST /api/auth/signup ---

    @Test
    void 新規登録すると201になる() throws Exception {
        when(authService.signup(any())).thenReturn(USER);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("user@example.com", "password123", "山田")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("user@example.com"));
    }

    /** 登録と同時にログイン状態にするため、両クッキーが発行される。 */
    @Test
    void 新規登録で認証クッキーが2枚発行される() throws Exception {
        when(authService.signup(any())).thenReturn(USER);

        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("user@example.com", "password123", "山田")))
                .andReturn();

        assertThat(setCookies(result))
                .hasSize(2)
                .anySatisfy(cookie -> assertThat(cookie).contains("auth_token=jwt-token"))
                .anySatisfy(cookie -> assertThat(cookie).contains("refresh_token=refresh-token"));
    }

    /** JavaScriptからトークンを読めないようにするための属性。XSS時の被害を抑える。 */
    @Test
    void 認証クッキーはHttpOnlyで発行される() throws Exception {
        when(authService.signup(any())).thenReturn(USER);

        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("user@example.com", "password123", "山田")))
                .andReturn();

        assertThat(setCookies(result)).isNotEmpty().allSatisfy(cookie -> assertThat(cookie).contains("HttpOnly"));
    }

    /**
     * 既定は Secure なし。ローカル開発とE2EはHTTPで動くため、既定で付けると追加設定なしでは
     * クッキーが送信されず認証が通らなくなる。
     */
    @Test
    void 既定では認証クッキーにSecureが付かない() throws Exception {
        when(authService.signup(any())).thenReturn(USER);

        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("user@example.com", "password123", "山田")))
                .andReturn();

        assertThat(setCookies(result)).isNotEmpty().allSatisfy(cookie -> assertThat(cookie)
                .doesNotContain("Secure"));
    }

    /**
     * COOKIE_SECURE=true を渡すと Secure が付く。デプロイ先(CloudFront配下)はHTTPSなので、
     * これが無いとHTTPでもクッキーが送信されてしまう。発行時と削除時の両方が同じ生成経路を
     * 通ることも、あわせてここで担保する。
     */
    @Test
    void 設定を有効にすると発行時も削除時も認証クッキーにSecureが付く() throws Exception {
        when(cookieProperties.isSecure()).thenReturn(true);
        when(authService.signup(any())).thenReturn(USER);

        MvcResult issued = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("user@example.com", "password123", "山田")))
                .andReturn();
        MvcResult deleted = mockMvc.perform(authenticated(post("/api/auth/logout")))
                .andReturn();

        assertThat(setCookies(issued)).hasSize(2).allSatisfy(cookie -> assertThat(cookie).contains("Secure"));
        assertThat(setCookies(deleted)).hasSize(2).allSatisfy(cookie -> assertThat(cookie).contains("Secure"));
    }

    /** refresh_token は /api/auth 配下にしか送られないよう Path を絞ってある。 */
    @Test
    void リフレッシュトークンのクッキーはPathが限定される() throws Exception {
        when(authService.signup(any())).thenReturn(USER);

        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("user@example.com", "password123", "山田")))
                .andReturn();

        assertThat(setCookies(result))
                .anySatisfy(cookie ->
                        assertThat(cookie).contains("refresh_token=").contains("Path=/api/auth"))
                .anySatisfy(cookie -> assertThat(cookie).contains("auth_token=").contains("Path=/"));
    }

    @Test
    void 登録済みメールアドレスでは400になる() throws Exception {
        when(authService.signup(any())).thenThrow(new DuplicateEmailException());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("taken@example.com", "password123", "山田")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    /** パスワードの長さの境界値。8文字未満と72文字超は弾かれる。 */
    @ParameterizedTest
    @CsvSource({"7, 400", "8, 201", "71, 201", "72, 201", "73, 400"})
    void パスワードの長さの境界値を検証する(int length, int expectedStatus) throws Exception {
        when(authService.signup(any())).thenReturn(USER);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("user@example.com", "a".repeat(length), "山田")))
                .andExpect(status().is(expectedStatus));
    }

    /** 表示名の長さの境界値。100文字ちょうどは通り、101文字は弾かれる。 */
    @ParameterizedTest
    @CsvSource({"1, 201", "100, 201", "101, 400"})
    void 表示名の長さの境界値を検証する(int length, int expectedStatus) throws Exception {
        when(authService.signup(any())).thenReturn(USER);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("user@example.com", "password123", "あ".repeat(length))))
                .andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "user@", "@example.com", "user example.com", ""})
    void メールアドレスの形式が不正なら400になる(String email) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(email, "password123", "山田")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(authService, never()).signup(any());
    }

    /**
     * 複数フィールドが同時に不正な場合、エラーコードだけを検証しメッセージは断定しない。
     * getFieldErrors() の順序はBean Validationの仕様上保証されないため
     * (docs/test-plan.md 6.4)。
     */
    @Test
    void 複数フィールドが不正でもVALIDATION_ERRORで返る() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("bad-email", "short", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").isNotEmpty());
    }

    // --- POST /api/auth/login ---

    @Test
    void ログインできる() throws Exception {
        when(authService.login(any())).thenReturn(USER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"user@example.com\", \"password\": \"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("auth_token=jwt-token")));
    }

    @Test
    void 資格情報が不正なら401になる() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"user@example.com\", \"password\": \"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    /** ログインのパスワードは長さ制約なし(@NotBlankのみ)。既存ユーザーを締め出さないため。 */
    @Test
    void ログインのパスワードは長さで弾かれない() throws Exception {
        when(authService.login(any())).thenReturn(USER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"user@example.com\", \"password\": \"a\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void ログインでパスワードが空なら400になる() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"user@example.com\", \"password\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // --- POST /api/auth/refresh ---

    @Test
    void リフレッシュトークンで再発行できる() throws Exception {
        when(refreshTokenService.rotate("old-token"))
                .thenReturn(new RefreshTokenService.RotationResult(1L, "new-refresh-token"));

        MvcResult result = mockMvc.perform(
                        post("/api/auth/refresh").cookie(new Cookie("refresh_token", "old-token")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(setCookies(result))
                .anySatisfy(cookie -> assertThat(cookie).contains("refresh_token=new-refresh-token"))
                .anySatisfy(cookie -> assertThat(cookie).contains("auth_token=jwt-token"));
    }

    @Test
    void リフレッシュトークンのクッキーが無ければ401になる() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));

        verify(refreshTokenService, never()).rotate(anyString());
    }

    @Test
    void リフレッシュトークンが無効なら401になる() throws Exception {
        when(refreshTokenService.rotate(anyString())).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "revoked")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    // --- POST /api/auth/logout ---

    @Test
    void ログアウトするとトークンが失効しクッキーが削除される() throws Exception {
        MvcResult result = mockMvc.perform(authenticated(post("/api/auth/logout"))
                        .cookie(new Cookie("refresh_token", "token")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(setCookies(result)).hasSize(2).allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0"));
        verify(refreshTokenService).revoke("token");
    }

    /** クライアント側の状態に依存して失敗しないよう、クッキーが無くても200(冪等)。 */
    @Test
    void リフレッシュトークンのクッキーが無くてもログアウトは成功する() throws Exception {
        MvcResult result = mockMvc.perform(authenticated(post("/api/auth/logout")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(setCookies(result)).hasSize(2).allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0"));
        verify(refreshTokenService, never()).revoke(anyString());
    }

    // --- GET /api/auth/me ---

    @Test
    void ログイン中のユーザーを取得できる() throws Exception {
        when(authService.getCurrentUser(USER_ID)).thenReturn(USER);

        mockMvc.perform(authenticated(get("/api/auth/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("user@example.com"));
    }

    @Test
    void トークンが指すユーザーが存在しなければ401になる() throws Exception {
        when(authService.getCurrentUser(any())).thenThrow(new UnauthenticatedException());

        mockMvc.perform(authenticated(get("/api/auth/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }
}
