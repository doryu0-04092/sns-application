package com.snsapp.backend.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snsapp.backend.ratelimit.RateLimitProperties.Rule;
import com.snsapp.backend.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * レート制限フィルタの分岐。
 *
 * <p>Springを立てずにフィルタ単体で検証する。見たいのは「どのリクエストを、どの鍵で数え、
 * 超えたら何を返すか」であり、DIやDBは関係しないため({@code JwtAuthFilterTest} と同じ方針)。
 *
 * <p><b>1回ごとに新しい MockFilterChain を使う。</b> 使い回すと2回目以降に
 * 「既にdoFilter済み」の状態から始まり、通過したかどうかを判定できなくなる。
 */
class RateLimitFilterTest {

    private static RateLimitProperties properties(int capacity) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustedProxyHops(0);
        properties.setLogin(new Rule(capacity, 60));
        properties.setSignup(new Rule(capacity, 60));
        properties.setRefresh(new Rule(capacity, 60));
        properties.setWrite(new Rule(capacity, 60));
        return properties;
    }

    private static RateLimitFilter filter(RateLimitProperties properties) {
        return new RateLimitFilter(properties, new ObjectMapper());
    }

    private static MockHttpServletRequest request(String method, String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        return request;
    }

    /** 1回通し、通過したか(=チェーンが呼ばれたか)と応答を返す。 */
    private static MockHttpServletResponse call(RateLimitFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void 上限までは通り超えると429になる() throws Exception {
        RateLimitFilter filter = filter(properties(2));

        assertThat(call(filter, request("POST", "/api/auth/login", "1.1.1.1")).getStatus()).isEqualTo(200);
        assertThat(call(filter, request("POST", "/api/auth/login", "1.1.1.1")).getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = call(filter, request("POST", "/api/auth/login", "1.1.1.1"));
        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    /**
     * <b>Retry-After が無いと、クライアントは当てずっぽうに再送するしかない。</b>
     * 制限しているのに負荷が減らない状態になるため、必ず返す。
     */
    @Test
    void 制限時はRetryAfterを返す() throws Exception {
        RateLimitFilter filter = filter(properties(1));
        call(filter, request("POST", "/api/auth/login", "1.1.1.1"));

        MockHttpServletResponse blocked = call(filter, request("POST", "/api/auth/login", "1.1.1.1"));

        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(Long.parseLong(blocked.getHeader("Retry-After"))).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void 制限時の本文は他のエラーと同じ形になる() throws Exception {
        RateLimitFilter filter = filter(properties(1));
        call(filter, request("POST", "/api/auth/login", "1.1.1.1"));

        MockHttpServletResponse blocked = call(filter, request("POST", "/api/auth/login", "1.1.1.1"));

        assertThat(blocked.getContentType()).contains("application/json");
        assertThat(blocked.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
    }

    /** IPが違えば別の鍵になる。ここが壊れると、誰か1人の超過で全員が止まる。 */
    @Test
    void 送信元が違えば互いに影響しない() throws Exception {
        RateLimitFilter filter = filter(properties(1));
        call(filter, request("POST", "/api/auth/login", "1.1.1.1"));

        assertThat(call(filter, request("POST", "/api/auth/login", "1.1.1.1")).getStatus()).isEqualTo(429);
        assertThat(call(filter, request("POST", "/api/auth/login", "2.2.2.2")).getStatus()).isEqualTo(200);
    }

    /** ログインと登録は別の制限。片方を使い切っても、もう片方は通る。 */
    @Test
    void 経路ごとに別々に数える() throws Exception {
        RateLimitFilter filter = filter(properties(1));
        call(filter, request("POST", "/api/auth/login", "1.1.1.1"));

        assertThat(call(filter, request("POST", "/api/auth/login", "1.1.1.1")).getStatus()).isEqualTo(429);
        assertThat(call(filter, request("POST", "/api/auth/signup", "1.1.1.1")).getStatus()).isEqualTo(200);
    }

    @Test
    void 読み取りは制限しない() throws Exception {
        RateLimitFilter filter = filter(properties(1));

        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, request("GET", "/api/posts", "1.1.1.1")).getStatus()).isEqualTo(200);
        }
    }

    @Test
    void ヘルスチェックは制限しない() throws Exception {
        RateLimitFilter filter = filter(properties(1));

        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, request("GET", "/api/livez", "1.1.1.1")).getStatus()).isEqualTo(200);
        }
    }

    /** 認証済みの書き込みはユーザー単位。IPを変えても同じ利用者なら同じ鍵になる。 */
    @Test
    void 認証済みの書き込みはユーザー単位で数える() throws Exception {
        RateLimitFilter filter = filter(properties(1));

        MockHttpServletRequest first = request("POST", "/api/posts", "1.1.1.1");
        first.setAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE, 42L);
        assertThat(call(filter, first).getStatus()).isEqualTo(200);

        MockHttpServletRequest fromAnotherIp = request("POST", "/api/posts", "9.9.9.9");
        fromAnotherIp.setAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE, 42L);
        assertThat(call(filter, fromAnotherIp).getStatus()).isEqualTo(429);

        MockHttpServletRequest anotherUser = request("POST", "/api/posts", "1.1.1.1");
        anotherUser.setAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE, 43L);
        assertThat(call(filter, anotherUser).getStatus()).isEqualTo(200);
    }

    /**
     * 未認証の書き込みは数えない。
     *
     * <p>この後ろのフィルタが401にするため、数えても意味が無い。
     * むしろIP単位で数えると、共有IPの利用者を巻き込む。
     */
    @Test
    void 未認証の書き込みは数えない() throws Exception {
        RateLimitFilter filter = filter(properties(1));

        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, request("POST", "/api/posts", "1.1.1.1")).getStatus()).isEqualTo(200);
        }
    }

    /** 障害時に落とせること。設定を誤って利用者を締め出した時、再デプロイを待たずに戻せる必要がある。 */
    @Test
    void 無効にすると素通りする() throws Exception {
        RateLimitProperties properties = properties(1);
        properties.setEnabled(false);
        RateLimitFilter filter = filter(properties);

        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, request("POST", "/api/auth/login", "1.1.1.1")).getStatus()).isEqualTo(200);
        }
    }

    /** ALBの背後を想定。X-Forwarded-For を見ずに接続元で数えると、全員が1つの鍵に潰れる。 */
    @Test
    void プロキシ経由でも利用者ごとに分かれる() throws Exception {
        RateLimitProperties properties = properties(1);
        properties.setTrustedProxyHops(0);
        RateLimitFilter filter = filter(properties);

        MockHttpServletRequest userA = request("POST", "/api/auth/login", "10.0.0.1");
        userA.addHeader("X-Forwarded-For", "203.0.113.1");
        assertThat(call(filter, userA).getStatus()).isEqualTo(200);

        // 同じALB(接続元は同じ)だが、利用者が違う。
        MockHttpServletRequest userB = request("POST", "/api/auth/login", "10.0.0.1");
        userB.addHeader("X-Forwarded-For", "203.0.113.2");
        assertThat(call(filter, userB).getStatus()).isEqualTo(200);

        MockHttpServletRequest userAAgain = request("POST", "/api/auth/login", "10.0.0.1");
        userAAgain.addHeader("X-Forwarded-For", "203.0.113.1");
        assertThat(call(filter, userAAgain).getStatus()).isEqualTo(429);
    }
}
