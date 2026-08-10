package com.snsapp.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 認証フィルタの分岐網羅。
 *
 * <p>このフィルタは全リクエストの関門で、ここを素通りできるパスがあると
 * 認証を回避できてしまう。「通してよいものだけを通し、それ以外は必ず401にする」
 * という判定を1分岐ずつ固定する。
 *
 * <p>{@link JwtService} はモックする。トークンの中身の検証は {@link JwtServiceTest} の担当で、
 * ここで見たいのは<strong>検証結果を受けてフィルタがどう振る舞うか</strong>のため。
 */
class JwtAuthFilterTest {

    private static final Long USER_ID = 7L;
    private static final String VALID_TOKEN = "valid-token";

    private JwtService jwtService;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        filter = new JwtAuthFilter(jwtService, new ObjectMapper());
    }

    private static MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private static MockHttpServletRequest authenticated(String path) {
        MockHttpServletRequest request = request("GET", path);
        request.setCookies(new Cookie("auth_token", VALID_TOKEN));
        return request;
    }

    // --- 素通りさせる分岐（認証を要求しない） ---

    // /api/ 配下だけが保護対象。Swagger UIや静的リソースはここを通らない。
    @Test
    void API配下でないパスは認証せず通す() throws Exception {
        MockHttpServletRequest request = request("GET", "/swagger-ui.html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(jwtService);
    }

    // CORSプリフライトはブラウザがクッキーを付けずに送るため、認証を要求すると必ず失敗する。
    @Test
    void プリフライトは認証せず通す() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("OPTIONS", "/api/posts"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(jwtService);
    }

    // ログイン前に叩く必要があるエンドポイント。ここを保護すると誰もログインできなくなる。
    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/health"})
    void 公開エンドポイントは認証せず通す(String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("POST", path), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(jwtService);
    }

    // 前方一致にすると /api/auth/login-as-admin のような別パスまで公開されてしまう。完全一致であることを固定する。
    @Test
    void 公開エンドポイントに前方一致する別のパスは保護される() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("GET", "/api/health/details"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    // --- 認証を要求する分岐 ---

    @Test
    void 有効なトークンなら利用者IDを渡して通す() throws Exception {
        when(jwtService.parseUserId(VALID_TOKEN)).thenReturn(USER_ID);
        MockHttpServletRequest request = authenticated("/api/posts");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE)).isEqualTo(USER_ID);
    }

    @Test
    void クッキーが1つも無ければ401になる() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("GET", "/api/posts"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void 別名のクッキーしか無ければ401になる() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/posts");
        request.setCookies(new Cookie("refresh_token", "some-value"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        verifyNoInteractions(jwtService);
    }

    // 期限切れ・改ざん・別鍵のいずれもJwtServiceが例外を投げる。
    // フィルタはそれを捕まえて401にし、500として漏らさない。
    @Test
    void トークンの検証に失敗したら401になる() throws Exception {
        when(jwtService.parseUserId(anyString())).thenThrow(new JwtException("expired"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(authenticated("/api/posts"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void 認証に失敗しても利用者IDは設定されない() throws Exception {
        when(jwtService.parseUserId(anyString())).thenThrow(new JwtException("invalid"));
        MockHttpServletRequest request = authenticated("/api/posts");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE)).isNull();
    }

    // --- 401応答の形式 ---

    // フロントはエラー本文を {"error":{"code","message"}} 前提で扱う。
    // ここだけ形式が違うと、認証切れのときだけエラー表示が壊れる。
    @Test
    void 認証エラーの本文は共通のエラー形式になる() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("GET", "/api/posts"), response, new MockFilterChain());

        // 文字セットが付くため application/json;charset=UTF-8 になる。型だけを見る。
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"code\":\"UNAUTHENTICATED\"")
                .contains("\"message\"");
    }
}
