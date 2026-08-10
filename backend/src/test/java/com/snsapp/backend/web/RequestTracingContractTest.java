package com.snsapp.backend.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.snsapp.backend.logging.RequestLoggingFilter;
import com.snsapp.backend.support.AbstractIntegrationTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 追跡ID(X-Request-Id)がフィルタチェーン全体で機能することの検証。
 *
 * <p>単体テストはフィルタ単体の振る舞いしか見ない。ここでは実際の登録順序で組み上がった状態を確認する。
 * 特に、RequestLoggingFilterを最外周へ入れたことでCORSと認証の相対順序が崩れていないこと
 * (401応答にCORSヘッダーが付くこと)を固定する。順序を誤るとブラウザ側では
 * 認証エラーではなく原因不明のCORSエラーとして見えてしまう。
 */
@AutoConfigureMockMvc
class RequestTracingContractTest extends AbstractIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 認証なしで到達できる応答にも追跡IDが付く() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestLoggingFilter.REQUEST_ID_HEADER));
    }

    @Test
    void 認証エラーの応答にも追跡IDが付く() throws Exception {
        mockMvc.perform(get("/api/posts").param("feed", "all"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestLoggingFilter.REQUEST_ID_HEADER));
    }

    // /api/ 配下は認証フィルタが先に401を返すため404にならない。404を通すには配下の外を叩く。
    @Test
    void 存在しないパスの応答にも追跡IDが付く() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists(RequestLoggingFilter.REQUEST_ID_HEADER));
    }

    @Test
    void 追跡IDはリクエストごとに異なる() throws Exception {
        String first = requestIdOfHealthCheck();
        String second = requestIdOfHealthCheck();

        Assertions.assertThat(first).isNotBlank().isNotEqualTo(second);
    }

    @Test
    void 認証エラーの応答にもCORSヘッダーが付く() throws Exception {
        mockMvc.perform(get("/api/posts").param("feed", "all").header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    void 追跡IDはブラウザのスクリプトから読める() throws Exception {
        mockMvc.perform(get("/api/health").header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", RequestLoggingFilter.REQUEST_ID_HEADER));
    }

    private String requestIdOfHealthCheck() throws Exception {
        return mockMvc.perform(get("/api/health"))
                .andReturn()
                .getResponse()
                .getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
    }
}
