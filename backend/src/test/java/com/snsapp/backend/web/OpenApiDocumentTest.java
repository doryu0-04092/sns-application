package com.snsapp.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snsapp.backend.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 生成されるOpenAPIドキュメント(/v3/api-docs)の検証。
 *
 * <p>パスやDTOの型はspringdocが実装から生成するためドリフトしないが、次の2つは壊れうる。
 *
 * <ul>
 *   <li>アノテーションの記述ミスでドキュメント生成そのものが失敗する</li>
 *   <li>新しいエンドポイントを追加したとき、説明を書き忘れる</li>
 * </ul>
 *
 * <p>後者を防ぐため「全エンドポイントがsummaryとtagを持つ」ことを固定する。
 * 未注釈のエンドポイントを足すとこのテストが落ちる。
 */
@AutoConfigureMockMvc
class OpenApiDocumentTest extends AbstractIntegrationTest {

    private static final String COOKIE_AUTH = "cookieAuth";

    private static final List<String> HTTP_METHODS =
            List.of("get", "post", "put", "patch", "delete", "head", "options", "trace");

    /** 認証不要のエンドポイント。JwtAuthFilter#PUBLIC_PATHS と一致していなければならない。 */
    private static final List<String> PUBLIC_OPERATIONS =
            List.of("post /api/auth/signup", "post /api/auth/login", "post /api/auth/refresh", "get /api/health");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void APIドキュメントが生成され認証なしで取得できる() throws Exception {
        JsonNode doc = fetchApiDocs();

        assertThat(doc.path("openapi").asText()).startsWith("3.");
        assertThat(doc.path("info").path("title").asText()).isEqualTo("SNS Application API");
        assertThat(doc.path("paths")).isNotEmpty();
    }

    @Test
    void 全エンドポイントに概要とタグが付いている() throws Exception {
        List<String> missingSummary = new ArrayList<>();
        List<String> missingTag = new ArrayList<>();

        forEachOperation(fetchApiDocs(), (name, operation) -> {
            if (operation.path("summary").asText("").isBlank()) {
                missingSummary.add(name);
            }
            if (operation.path("tags").isEmpty()) {
                missingTag.add(name);
            }
        });

        assertThat(missingSummary).as("@Operation(summary=...)が無いエンドポイント").isEmpty();
        assertThat(missingTag).as("@Tagが無いエンドポイント").isEmpty();
    }

    @Test
    void 全エンドポイントに共通エラーレスポンスが注入されている() throws Exception {
        List<String> missing500 = new ArrayList<>();

        forEachOperation(fetchApiDocs(), (name, operation) -> {
            if (!operation.path("responses").has("500")) {
                missing500.add(name);
            }
        });

        assertThat(missing500).as("500が定義されていないエンドポイント").isEmpty();
    }

    @Test
    void 認証の要否がJwtAuthFilterの公開パスと一致する() throws Exception {
        JsonNode doc = fetchApiDocs();

        // 「既定で認証必須」はドキュメント全体のsecurityで表現している。これが無いと
        // 個々のoperationが何も継承せず、全エンドポイントが公開扱いになってしまう。
        assertThat(declaresCookieAuth(doc.path("security")))
                .as("ドキュメント全体に既定のsecurityが宣言されていること")
                .isTrue();

        List<String> publicOperations = new ArrayList<>();
        forEachOperation(doc, (name, operation) -> {
            if (!requiresCookieAuth(doc, operation)) {
                publicOperations.add(name);
            }
        });

        assertThat(publicOperations).containsExactlyInAnyOrderElementsOf(PUBLIC_OPERATIONS);
    }

    @Test
    void 認証が必要なエンドポイントには401が定義されている() throws Exception {
        JsonNode doc = fetchApiDocs();
        List<String> missing401 = new ArrayList<>();

        forEachOperation(doc, (name, operation) -> {
            if (requiresCookieAuth(doc, operation) && !operation.path("responses").has("401")) {
                missing401.add(name);
            }
        });

        assertThat(missing401).isEmpty();
    }

    /**
     * 全エンドポイントに成功レスポンスがあること。
     *
     * <p>springdocは自動生成した成功レスポンスを、メソッドに付けた {@code @ApiResponse} の
     * ステータスへ改名する。エラー用に403等を宣言すると成功レスポンスが消える
     * （実際に踏んだ不具合）。エラーは OpenApiConfig.ErrorResponse で宣言すること。
     */
    @Test
    void 全エンドポイントに成功レスポンスが定義されている() throws Exception {
        List<String> missingSuccess = new ArrayList<>();

        forEachOperation(fetchApiDocs(), (name, operation) -> {
            boolean hasSuccess = false;
            for (java.util.Iterator<String> it = operation.path("responses").fieldNames(); it.hasNext(); ) {
                if (it.next().startsWith("2")) {
                    hasSuccess = true;
                }
            }
            if (!hasSuccess) {
                missingSuccess.add(name);
            }
        });

        assertThat(missingSuccess).as("2xxレスポンスが無いエンドポイント").isEmpty();
    }

    /**
     * いいね解除・フォロー解除は対象の存在を確認せず冪等に成功するため、404を載せてはいけない。
     * OpenApiConfig.SkipNotFound の付け忘れ・外し忘れを検出する。
     */
    @Test
    void 冪等な解除系エンドポイントには404が定義されていない() throws Exception {
        Map<String, JsonNode> operations = collectOperations(fetchApiDocs());

        assertThat(operations.get("delete /api/posts/{postId}/like").path("responses").has("404")).isFalse();
        assertThat(operations.get("delete /api/comments/{commentId}/like").path("responses").has("404")).isFalse();
        assertThat(operations.get("delete /api/users/{userId}/follow").path("responses").has("404")).isFalse();

        // 対になる付与系は存在チェックをするので404が要る
        assertThat(operations.get("post /api/posts/{postId}/like").path("responses").has("404")).isTrue();
    }

    /** Bean Validationの制約がスキーマへ反映されていること(springdocを入れた主な利点のひとつ)。 */
    @Test
    void バリデーション制約がスキーマに反映されている() throws Exception {
        JsonNode schemas = fetchApiDocs().path("components").path("schemas");

        JsonNode createPost = schemas.path("CreatePostRequest");
        assertThat(createPost.path("properties").path("body").path("maxLength").asInt()).isEqualTo(280);
        assertThat(createPost.path("properties").path("imageKeys").path("maxItems").asInt()).isEqualTo(4);
        assertThat(createPost.path("required")).isNotEmpty();

        // エラー応答の型もドキュメントに載っていること(OpenApiConfigで明示登録している)
        assertThat(schemas.has("ApiError")).isTrue();
    }

    /**
     * OpenAPIのsecurityは継承する。operationに security が無ければドキュメント全体の宣言が適用され、
     * 空配列で上書きされていれば認証不要。この違いを取り違えると全エンドポイントが公開に見える。
     */
    private boolean requiresCookieAuth(JsonNode doc, JsonNode operation) {
        JsonNode security = operation.get("security");
        return security == null ? declaresCookieAuth(doc.path("security")) : declaresCookieAuth(security);
    }

    private boolean declaresCookieAuth(JsonNode security) {
        for (JsonNode requirement : security) {
            if (requirement.has(COOKIE_AUTH)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode fetchApiDocs() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(json);
    }

    private Map<String, JsonNode> collectOperations(JsonNode doc) {
        Map<String, JsonNode> operations = new java.util.LinkedHashMap<>();
        forEachOperation(doc, operations::put);
        return operations;
    }

    /**
     * 「メソッド パス」をキーに全operationを走査する。
     *
     * <p>path itemにはHTTPメソッド以外のキー(parameters/summary/servers等)も入りうるため、
     * メソッド名に限定して拾う。
     */
    private void forEachOperation(JsonNode doc, java.util.function.BiConsumer<String, JsonNode> consumer) {
        JsonNode paths = doc.path("paths");
        paths.fieldNames().forEachRemaining(path -> {
            JsonNode pathItem = paths.path(path);
            for (String method : HTTP_METHODS) {
                if (pathItem.has(method)) {
                    consumer.accept(method + " " + path, pathItem.path(method));
                }
            }
        });
    }
}
