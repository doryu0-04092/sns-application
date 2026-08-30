package com.snsapp.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snsapp.backend.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
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
 *
 * <p>アプリの既定では仕様書を配信しない(設定漏れを安全側に倒すため)。
 * ここでは内容を検証したいので明示的に有効化する。
 * 「既定では配信しない」ことの検証は {@link ApiContractTest} 側で行う。
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
class OpenApiDocumentTest extends AbstractIntegrationTest {

    private static final String COOKIE_AUTH = "cookieAuth";

    private static final List<String> HTTP_METHODS =
            List.of("get", "post", "put", "patch", "delete", "head", "options", "trace");

    /** 認証不要のエンドポイント。JwtAuthFilter#PUBLIC_PATHS と一致していなければならない。 */
    private static final List<String> PUBLIC_OPERATIONS = List.of(
            "post /api/auth/signup",
            "post /api/auth/login",
            "post /api/auth/refresh",
            // ヘルスチェックはロードバランサが叩く。トークンを持たないため認証を要さない。
            "get /api/livez",
            "get /api/readyz",
            "get /api/health");

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
            for (Iterator<String> it = operation.path("responses").fieldNames(); it.hasNext(); ) {
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

    /**
     * nullになりうるフィールドが、スキーマ上でもnull許容として表現されていること。
     *
     * <p>Javaのrecordは型でnull可否を表現しないため、明示しない限りspringdocは非nullとして出力する。
     * 説明文に「削除済みの場合はnull」と書いてもスキーマには反映されず、
     * この仕様書から型やクライアントを生成すると誤った非null型になる(#35)。
     */
    @Test
    void nullになりうるフィールドはnull許容として定義されている() throws Exception {
        JsonNode schemas = fetchApiDocs().path("components").path("schemas");

        assertPermitsNull(schemas, "PostResponse", "body", "論理削除済みの投稿");
        assertPermitsNull(schemas, "PostResponse", "authorAvatarUrl", "アイコン未設定");
        assertPermitsNull(schemas, "CommentResponse", "body", "論理削除済みのコメント");
        assertPermitsNull(schemas, "CommentResponse", "parentCommentId", "トップレベルのコメント");
        assertPermitsNull(schemas, "CommentResponse", "authorAvatarUrl", "アイコン未設定");
        assertPermitsNull(schemas, "ProfileResponse", "bio", "自己紹介 未設定");
        assertPermitsNull(schemas, "ProfileResponse", "avatarUrl", "アイコン未設定");
        assertPermitsNull(schemas, "UserResponse", "bio", "自己紹介 未設定");
        assertPermitsNull(schemas, "UserResponse", "avatarUrl", "アイコン未設定");
        assertPermitsNull(schemas, "UserSummaryResponse", "avatarUrl", "アイコン未設定");
        assertPermitsNull(schemas, "CreateCommentRequest", "parentCommentId", "トップレベルとして投稿する場合");
        assertPermitsNull(schemas, "CreatePostRequest", "imageKeys", "画像なしの投稿");
        assertPermitsNull(schemas, "UpdateProfileRequest", "avatarKey", "アイコンを変更しない場合");

        // 逆に、常に値があるフィールドまでnull許容になっていないこと
        assertNeverNull(schemas, "PostResponse", "id");
        assertNeverNull(schemas, "PostResponse", "deleted");
        assertNeverNull(schemas, "PostResponse", "authorDisplayName");
        assertNeverNull(schemas, "CommentResponse", "postId");
        assertNeverNull(schemas, "UserResponse", "email");
    }

    /**
     * レスポンスのフィールドはすべて required に列挙されていること。
     *
     * <p>Jacksonの既定設定では値がnullのフィールドもキーごと出力されるため
     * （実際のレスポンスで {@code "bio": null} を確認済み）、レスポンスの全プロパティは常に存在する。
     * nullになりうるかどうかは型側が表す（#35）。
     *
     * <p>required が欠けていると、生成される型が {@code body?: string | null} となり、
     * 実際には起こらない undefined が混ざる。「存在するか」と「nullでないか」は別概念(#37)。
     */
    @Test
    void レスポンスのフィールドはすべてrequiredに列挙されている() throws Exception {
        JsonNode schemas = fetchApiDocs().path("components").path("schemas");

        // null許容フィールドも含めて全件が required であること
        assertRequired(schemas, "PostResponse", "id", "body", "authorId", "authorDisplayName",
                "authorAvatarUrl", "createdAt", "updatedAt", "commentCount", "likeCount",
                "isMine", "isFollowing", "isLiked", "deleted", "imageUrls");
        assertRequired(schemas, "CommentResponse", "id", "postId", "parentCommentId", "body",
                "authorId", "authorDisplayName", "authorAvatarUrl", "deleted");
        assertRequired(schemas, "UserResponse", "id", "email", "displayName", "bio", "avatarUrl");
        assertRequired(schemas, "ProfileResponse", "id", "displayName", "bio", "avatarUrl",
                "followerCount", "followingCount", "isMine", "isFollowing");
        assertRequired(schemas, "UserSummaryResponse", "id", "userId", "displayName", "avatarUrl", "isFollowing");
        assertRequired(schemas, "PresignedUpload", "key", "uploadUrl");
        assertRequired(schemas, "ApiError", "error");
    }

    /**
     * リクエストの required は「クライアントが送る義務があるか」であり、レスポンスとは意味が違う。
     * 省略できる項目まで required にしてはいけない。
     */
    @Test
    void リクエストの省略可能な項目はrequiredに含まれない() throws Exception {
        JsonNode schemas = fetchApiDocs().path("components").path("schemas");

        // Bean Validation 由来の required が保持されていること
        assertRequired(schemas, "SignupRequest", "email", "password", "displayName");
        assertRequired(schemas, "LoginRequest", "email", "password");
        assertRequired(schemas, "CreatePostRequest", "body");
        assertRequired(schemas, "UpdateProfileRequest", "displayName");

        // 省略できる項目が required に混ざっていないこと
        assertNotRequired(schemas, "CreatePostRequest", "imageKeys");
        assertNotRequired(schemas, "CreateCommentRequest", "parentCommentId");
        assertNotRequired(schemas, "UpdateProfileRequest", "avatarKey");
        assertNotRequired(schemas, "UpdateProfileRequest", "bio");
    }

    private void assertRequired(JsonNode schemas, String schemaName, String... properties) {
        JsonNode required = schemas.path(schemaName).path("required");
        List<String> actual = new ArrayList<>();
        required.forEach(node -> actual.add(node.asText()));

        assertThat(actual).as("%s の required", schemaName).contains(properties);
    }

    private void assertNotRequired(JsonNode schemas, String schemaName, String property) {
        JsonNode required = schemas.path(schemaName).path("required");
        List<String> actual = new ArrayList<>();
        required.forEach(node -> actual.add(node.asText()));

        assertThat(actual).as("%s.%s は null になりうるので required に含めない", schemaName, property)
                .doesNotContain(property);
    }

    /** CursorPage はジェネリクスのため、実体化された型（例: 投稿一覧のページ）で確認する。 */
    @Test
    void カーソルの終端はnull許容として定義されている() throws Exception {
        JsonNode schemas = fetchApiDocs().path("components").path("schemas");

        String cursorPageSchema = null;
        for (Iterator<String> it = schemas.fieldNames(); it.hasNext(); ) {
            String name = it.next();
            if (name.startsWith("CursorPage") && schemas.path(name).path("properties").has("nextCursor")) {
                cursorPageSchema = name;
                break;
            }
        }

        assertThat(cursorPageSchema).as("CursorPage のスキーマが存在すること").isNotNull();
        assertThat(permitsNull(schemas, cursorPageSchema, "nextCursor"))
                .as("%s.nextCursor は末尾で null になる", cursorPageSchema).isTrue();
    }

    /**
     * スキーマがnullを許容しているか。
     *
     * <p>OpenAPI 3.0 は {@code nullable: true}、3.1 は {@code type: ["string","null"]} と
     * 表現が異なるため、どちらでも判定できるようにしている。
     */
    private boolean permitsNull(JsonNode schemas, String schemaName, String property) {
        JsonNode field = fieldSchema(schemas, schemaName, property);

        if (field.path("nullable").asBoolean(false)) {
            return true;
        }
        JsonNode type = field.path("type");
        if (type.isArray()) {
            for (JsonNode t : type) {
                if ("null".equals(t.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private JsonNode fieldSchema(JsonNode schemas, String schemaName, String property) {
        JsonNode field = schemas.path(schemaName).path("properties").path(property);
        assertThat(field.isMissingNode())
                .as("%s.%s がスキーマに存在すること", schemaName, property).isFalse();
        return field;
    }

    /** 失敗時に実際のスキーマを表示する。どう出力されたか分からないと原因を追えないため。 */
    private void assertPermitsNull(JsonNode schemas, String schemaName, String property, String why) {
        assertThat(permitsNull(schemas, schemaName, property))
                .as("%s.%s は null になりうる(%s)。実際のスキーマ: %s",
                        schemaName, property, why, fieldSchema(schemas, schemaName, property))
                .isTrue();
    }

    private void assertNeverNull(JsonNode schemas, String schemaName, String property) {
        assertThat(permitsNull(schemas, schemaName, property))
                .as("%s.%s は常に値を持つ。実際のスキーマ: %s",
                        schemaName, property, fieldSchema(schemas, schemaName, property))
                .isFalse();
    }

    /**
     * ドキュメント内の $ref がすべて解決できること。
     *
     * <p>参照先の無い $ref があっても Swagger UI はそれらしく表示してしまうため、目視では気づけない。
     * 実際 ApiError から ErrorBody への参照が切れたまま公開されており、
     * 型生成を通して初めて発覚した(#39)。仕様を機械が利用する前提では、
     * 参照が閉じていることは最低限の前提になる。
     */
    @Test
    void すべての参照が解決できる() throws Exception {
        JsonNode doc = fetchApiDocs();
        JsonNode schemas = doc.path("components").path("schemas");

        List<String> dangling = new ArrayList<>();
        collectSchemaRefs(doc, dangling, schemas);

        assertThat(dangling).as("参照先が存在しない $ref").isEmpty();
    }

    private void collectSchemaRefs(JsonNode node, List<String> dangling, JsonNode schemas) {
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isTextual()) {
                String value = ref.asText();
                String prefix = "#/components/schemas/";
                if (value.startsWith(prefix)) {
                    String name = value.substring(prefix.length());
                    if (!schemas.has(name) && !dangling.contains(value)) {
                        dangling.add(value);
                    }
                }
            }
            node.forEach(child -> collectSchemaRefs(child, dangling, schemas));
        } else if (node.isArray()) {
            node.forEach(child -> collectSchemaRefs(child, dangling, schemas));
        }
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
        Map<String, JsonNode> operations = new LinkedHashMap<>();
        forEachOperation(doc, operations::put);
        return operations;
    }

    /**
     * 「メソッド パス」をキーに全operationを走査する。
     *
     * <p>path itemにはHTTPメソッド以外のキー(parameters/summary/servers等)も入りうるため、
     * メソッド名に限定して拾う。
     */
    private void forEachOperation(JsonNode doc, BiConsumer<String, JsonNode> consumer) {
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
