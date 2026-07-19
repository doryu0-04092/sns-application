package com.snsapp.backend.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.snsapp.backend.entity.User;
import com.snsapp.backend.security.JwtService;
import com.snsapp.backend.support.AbstractIntegrationTest;
import com.snsapp.backend.support.TestFixtures;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP境界での契約の検証。認証の要否と、エラー応答が docs/api-design.md の規約
 * (401/403/404/400 + {"error":{"code","message"}})に沿っていることを固定する。
 *
 * <p>特にSpring標準例外は、専用ハンドラが無いとcatch-allに落ちて500になる。
 * 「バリデーションエラーは400」という規約を守れているかはここでしか検出できない。
 */
@AutoConfigureMockMvc
@Transactional
class ApiContractTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TestFixtures fixtures;

    // --- 認証 ---

    @Test
    void 保護されたエンドポイントはクッキーなしで401になる() throws Exception {
        mockMvc.perform(get("/api/posts").param("feed", "all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").exists());
    }

    @Test
    void 不正なトークンでも401になる() throws Exception {
        mockMvc.perform(get("/api/posts").param("feed", "all").cookie(new Cookie("auth_token", "not-a-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 公開エンドポイントは認証なしで到達できる() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void signupは登録と同時に認証クッキーを発行する() throws Exception {
        String body = """
                {"email":"contract-%d@example.com","password":"password123","displayName":"契約テスト"}
                """.formatted(System.nanoTime());

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("auth_token"))
                .andExpect(cookie().httpOnly("auth_token", true))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    @Test
    void signupが失敗したときは認証クッキーを発行しない() throws Exception {
        String body = """
                {"email":"not-an-email","password":"short","displayName":""}
                """;

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(cookie().doesNotExist("auth_token"))
                .andExpect(cookie().doesNotExist("refresh_token"));
    }

    // --- エラー応答の規約(専用ハンドラが無いと500に落ちるもの) ---

    @Test
    void パス変数の型が不正なら400になる() throws Exception {
        mockMvc.perform(get("/api/posts/abc").cookie(authCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 必須パラメータが欠けていたら400になる() throws Exception {
        mockMvc.perform(multipart("/api/users/me")
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        })
                        .cookie(authCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 壊れたJSONボディなら400になる() throws Exception {
        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken")
                        .cookie(authCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 存在しない投稿は404になる() throws Exception {
        mockMvc.perform(get("/api/posts/999999999").cookie(authCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    @Test
    void エラーメッセージは日本語で返す() throws Exception {
        String body = """
                {"body":"%s"}
                """.formatted("a".repeat(281));

        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .cookie(authCookie()))
                .andExpect(status().isBadRequest())
                // Bean Validationの既定メッセージ(英語)が漏れていないこと
                .andExpect(jsonPath("$.error.message").value(matchesPattern(".*[ぁ-んァ-ン一-龥].*")));
    }

    // --- F-15 ユーザー一覧/検索 ---

    @Test
    void queryなしのユーザー一覧は200で返る() throws Exception {
        fixtures.user("一覧に載るユーザー");

        mockMvc.perform(get("/api/users").cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void queryありのユーザー検索は表示名で絞り込む() throws Exception {
        fixtures.user("ズミウダ固有名");

        mockMvc.perform(get("/api/users").param("query", "ズミウダ固有名").cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].displayName").value("ズミウダ固有名"));
    }

    private Cookie authCookie() {
        User user = fixtures.user();
        return new Cookie("auth_token", jwtService.issueToken(user.getId()));
    }
}
