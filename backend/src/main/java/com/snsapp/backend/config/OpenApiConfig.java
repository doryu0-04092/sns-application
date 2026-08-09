package com.snsapp.backend.config;

import com.snsapp.backend.common.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

/**
 * OpenAPI(Swagger UI)の定義。
 *
 * <p>ここが持つ責務は3つ。
 * <ol>
 *   <li>API全体のメタ情報(タイトル・バージョン・利用手順)</li>
 *   <li>認証スキームの宣言と、「既定で認証必須」というモデルの表明</li>
 *   <li>全エンドポイント共通のエラーレスポンスの注入({@link #commonErrorResponsesCustomizer()})</li>
 * </ol>
 *
 * <p><b>なぜ認証を「既定で必須」にするか</b>:
 * 実装側の {@link com.snsapp.backend.security.JwtAuthFilter} は
 * PUBLIC_PATHS に列挙した4本だけを公開し、残りをすべて認証必須として扱う。
 * 仕様側でエンドポイントごとに「認証要」を23回書くと、この判定がフィルタと仕様の2箇所に
 * 分かれてしまい、フィルタを直した時に仕様の書き換え漏れが起きる。
 * 「既定で必須・公開だけ例外指定」という同じ構造を写すことで、認証要否の所有者を
 * フィルタ1箇所に保つ。
 *
 * <p><b>なぜ共通エラーをアノテーションではなくCustomizerで入れるか</b>:
 * 401/500などは全27エンドポイントに共通で、アノテーションで書くと27回の重複になる。
 * 加えて、このプロジェクトの {@link com.snsapp.backend.common.ApiResponse} は
 * Swaggerの {@code io.swagger.v3.oas.annotations.responses.ApiResponse} と同名のため、
 * コントローラ側でSwagger版を使うには完全修飾名が必要になり記述が読みにくくなる。
 * Customizerに寄せることで、コントローラ側で完全修飾名が要るのは
 * エンドポイント固有の403だけになる。
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "SNS Application API",
                version = "v1",
                description = OpenApiConfig.API_DESCRIPTION),
        servers = @Server(url = "http://localhost:8080", description = "ローカル開発環境"),
        security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
@SecurityScheme(
        name = OpenApiConfig.COOKIE_AUTH,
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = "auth_token",
        description = "httpOnlyクッキーに格納されたJWT。ログイン系APIがSet-Cookieで発行する。")
public class OpenApiConfig {

    static final String COOKIE_AUTH = "cookieAuth";

    private static final String API_ERROR_SCHEMA_REF = "#/components/schemas/ApiError";

    static final String API_DESCRIPTION =
            """
            社内向けSNSアプリケーションのREST API。

            ## レスポンスの形

            成功時は `data` で包まれる。エラー時は `error` に `code` と `message` が入る。
            `code` はクライアントの分岐用、`message` はそのまま画面に出せる日本語。

            ```json
            { "data": { } }
            { "error": { "code": "POST_NOT_FOUND", "message": "投稿が見つかりません" } }
            ```

            ## 認証（Swagger UI で試す手順）

            認証はhttpOnlyクッキー `auth_token` で行う。httpOnlyのためJavaScriptから読めず、
            **画面右上の Authorize ボタンは使えない**。代わりに次の手順で試す。

            1. `POST /api/auth/signup` または `POST /api/auth/login` を Try it out で実行する
            2. ブラウザがクッキーを保存する（このページはAPIと同じ `localhost:8080` から配信されているため）
            3. 以降のTry it outには自動でクッキーが乗る。Authorizeの操作は不要

            アクセストークンは15分で失効する。失効後は `POST /api/auth/refresh` を1回叩けば
            再発行される（リフレッシュトークンのクッキーは `Path=/api/auth` に限定されている）。

            ## ページネーション

            一覧系はカーソルベース。`cursor` に前回応答の `nextCursor` を渡す。
            `nextCursor` が `null` なら末尾に到達している。

            ## 画像

            レスポンスに含まれる画像URLはすべて**署名付きURLで有効期限は24時間**。
            URLそのものを永続化・長期キャッシュしないこと。

            ## 関連ドキュメント

            設計の背景・採用理由・v1での割り切りは `docs/api-design.md` を参照。
            こちらは「何を・どんな型で」、api-design.mdは「なぜ」を扱う。
            """;

    /**
     * ApiErrorスキーマをcomponentsへ登録する。
     *
     * <p>ApiErrorはGlobalExceptionHandlerが返す型でコントローラの戻り値型には現れないため、
     * springdocの自動走査では拾われない。{@link #commonErrorResponses()} が
     * {@code $ref} で参照するので明示的に登録しておく。
     */
    @Bean
    public OpenApiCustomizer apiErrorSchemaCustomizer() {
        return this::registerApiErrorSchema;
    }

    /**
     * レスポンススキーマの全プロパティを required として列挙する。
     *
     * <p>JSON Schema では required に無いプロパティは「省略されうる」を意味する。
     * Javaのrecordには検証注釈が無いためレスポンスDTOには何も出力されず、
     * 「PostResponse.id は存在しないかもしれない」と主張する仕様になってしまう(#37)。
     *
     * <p><b>「存在するか」と「nullでないか」は別の概念。</b>
     * このAPIはJacksonの既定設定で応答を組み立てており、値がnullのフィールドも
     * {@code "bio": null} のようにキーごと出力される。つまりレスポンスの全プロパティは
     * <b>常に存在し</b>、nullになりうるかどうかは型側({@code types = {"string","null"}}・#35)が表す。
     * 両方を正しく表現して初めて、生成される型が {@code body: string | null} になる
     * (requiredが無いと {@code body?: string | null} となり、ありえない undefined が混ざる)。
     *
     * <p>リクエストスキーマは対象外。こちらの required は「クライアントが送る義務があるか」を
     * 意味しており、Bean Validation({@code @NotBlank} 等)から既に正しく生成されている。
     * 省略可能な項目(parentCommentId 等)を required にしてはいけない。
     *
     * <p>リクエストかどうかは<b>クラス名ではなくドキュメントの構造から判定する</b>
     * ({@link #requestBodySchemaNames})。命名規約に依存すると、規約から外れた時に
     * 黙って誤判定するため。
     */
    @Bean
    public OpenApiCustomizer requiredPropertiesCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            Set<String> requestSchemas = requestBodySchemaNames(openApi);
            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                if (!requestSchemas.contains(name)) {
                    markAllPropertiesRequired(schema);
                }
            });
        };
    }

    private void markAllPropertiesRequired(Schema<?> schema) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        properties.keySet().forEach(name -> {
            if (schema.getRequired() == null || !schema.getRequired().contains(name)) {
                schema.addRequiredItem(name);
            }
        });
    }

    /** リクエストボディとして参照されているスキーマ名を、ドキュメントから収集する。 */
    private Set<String> requestBodySchemaNames(OpenAPI openApi) {
        Set<String> names = new HashSet<>();
        if (openApi.getPaths() == null) {
            return names;
        }
        openApi.getPaths().values().stream()
                .flatMap(pathItem -> pathItem.readOperations().stream())
                .map(Operation::getRequestBody)
                .filter(Objects::nonNull)
                .map(RequestBody::getContent)
                .filter(Objects::nonNull)
                .flatMap(content -> content.values().stream())
                .map(MediaType::getSchema)
                .filter(Objects::nonNull)
                .map(Schema::get$ref)
                .filter(Objects::nonNull)
                .map(ref -> ref.substring(ref.lastIndexOf('/') + 1))
                .forEach(names::add);
        return names;
    }

    /**
     * 全エンドポイントに共通するエラーレスポンスを注入する。
     *
     * <p>一律に全部を足すのではなく、そのエンドポイントが<b>実際に返し得るもの</b>だけを載せる。
     * 返らないステータスを仕様に書くと、フロントエンドが起きない分岐を実装することになるため。
     *
     * <ul>
     *   <li>500 — どのエンドポイントでも起こりうる（DB停止・S3停止も
     *       GlobalExceptionHandlerのcatch-allで500になる）</li>
     *   <li>401 — 認証が必要なエンドポイント。公開エンドポイントは
     *       {@code @SecurityRequirements} を付けてあるので、その有無で判定する。
     *       login/refreshは公開だが資格情報不正で401を返すため、各メソッドで個別に宣言している</li>
     *   <li>400 — 入力（リクエストボディまたはパラメータ）を持つエンドポイントのみ</li>
     *   <li>404 — パスパラメータでリソースを指すエンドポイントのうち、
     *       実際に存在チェックを行うもの。{@link SkipNotFound} が付いたものは除外する</li>
     * </ul>
     *
     * <p>すでにコントローラ側で同じステータスを宣言している場合は上書きしない
     * （個別の記述のほうが具体的なため）。
     *
     * <p>{@link OperationCustomizer} を使うのは、Javaメソッドのアノテーション
     * （{@code @SecurityRequirements} / {@link SkipNotFound}）を参照して判定するため。
     * OpenAPIモデルだけを見る {@link OpenApiCustomizer} では、この2つを区別できない。
     */
    @Bean
    public OperationCustomizer commonErrorResponses() {
        return this::addCommonErrorResponses;
    }

    private Operation addCommonErrorResponses(Operation operation, HandlerMethod handlerMethod) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            return operation;
        }

        for (ErrorResponse declared : handlerMethod.getMethod().getAnnotationsByType(ErrorResponse.class)) {
            putIfAbsent(responses, declared.status(),
                    errorResponse(declared.description(), declared.code(), declared.message()));
        }

        if (hasInput(operation)) {
            putIfAbsent(responses, "400", errorResponse(
                    "リクエストが不正（バリデーション違反、型不一致、壊れたJSON）",
                    "VALIDATION_ERROR", "本文は280文字以内で入力してください"));
        }
        if (requiresAuthentication(handlerMethod)) {
            putIfAbsent(responses, "401", errorResponse(
                    "未認証。auth_tokenクッキーが無い、または失効している",
                    "UNAUTHENTICATED", "認証が必要です"));
        }

        if (looksUpResource(operation, handlerMethod)) {
            putIfAbsent(responses, "404", errorResponse(
                    "指定されたリソースが存在しない（論理削除済みを含む）",
                    "NOT_FOUND", "リソースが見つかりません"));
        }
        putIfAbsent(responses, "500", errorResponse(
                "サーバー側の想定外エラー。詳細はクライアントへ返さずサーバーログに記録される",
                "INTERNAL_ERROR", "予期しないエラーが発生しました"));
        return operation;
    }

    /** 公開エンドポイントには {@code @SecurityRequirements}(空)が付いている。それ以外は認証必須。 */
    private boolean requiresAuthentication(HandlerMethod handlerMethod) {
        return !handlerMethod.hasMethodAnnotation(SecurityRequirements.class);
    }

    /**
     * 400を返しうるか。
     *
     * <p>クッキー・ヘッダは除外する。400になるのはボディの検証違反、必須クエリの欠落、
     * パス変数やクエリの型不一致であり、クッキーの有無では400にならないため
     * （例: リフレッシュはクッキーのみを受け取るので400は返らず、無効なら401になる）。
     */
    private boolean hasInput(Operation operation) {
        if (operation.getRequestBody() != null) {
            return true;
        }
        return operation.getParameters() != null
                && operation.getParameters().stream()
                        .map(Parameter::getIn)
                        .anyMatch(in -> "query".equals(in) || "path".equals(in));
    }

    private boolean looksUpResource(Operation operation, HandlerMethod handlerMethod) {
        if (handlerMethod.hasMethodAnnotation(SkipNotFound.class)) {
            return false;
        }
        return operation.getParameters() != null
                && operation.getParameters().stream()
                        .map(Parameter::getIn)
                        .anyMatch("path"::equals);
    }

    /**
     * パスパラメータを持つが404を返さないエンドポイントに付ける。
     *
     * <p>いいね解除・フォロー解除は対象の存在を確認せずDELETE文を実行するだけなので、
     * 存在しないIDを指定しても200が返る（冪等）。共通404の自動付与から除外するために使う。
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface SkipNotFound {
    }

    /**
     * そのエンドポイント固有のエラーを宣言する。
     *
     * <p><b>Swaggerの {@code @ApiResponse} を直接使ってはいけない。</b>
     * springdocは自動生成した成功レスポンスを、メソッドに宣言された {@code @ApiResponse} の
     * ステータスコードへ<b>改名</b>する。そのため403を宣言すると成功レスポンス(200)が消え、
     * 403がレスポンスボディのスキーマを持つ誤った仕様になる（実際にこの不具合を踏んだ）。
     *
     * <p>この注釈は {@link #commonErrorResponses()} が読み取り、成功レスポンスに触れずに
     * エラーだけを追加する。ステータスが201になるような<b>成功</b>コードの変更は、
     * 改名の挙動がそのまま望ましい結果になるのでSwaggerの {@code @ApiResponse} を使ってよい。
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(ErrorResponses.class)
    public @interface ErrorResponse {

        /** HTTPステータスコード。例: "403"。 */
        String status();

        /** ApiErrorのcode。クライアントはこれで分岐する。 */
        String code();

        /** ApiErrorのmessage。実装が返す文言をそのまま書く。 */
        String message();

        /** どんな状況で起きるか。Swagger UIに表示される。 */
        String description();
    }

    /** {@link ErrorResponse} を複数付けるためのコンテナ。直接書く必要はない。 */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ErrorResponses {
        ErrorResponse[] value();
    }

    private void putIfAbsent(ApiResponses responses, String statusCode, ApiResponse response) {
        if (!responses.containsKey(statusCode)) {
            responses.addApiResponse(statusCode, response);
        }
    }

    private ApiResponse errorResponse(String description, String exampleCode, String exampleMessage) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", exampleCode);
        error.put("message", exampleMessage);
        body.put("error", error);

        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new MediaType()
                                .schema(new Schema<>().$ref(API_ERROR_SCHEMA_REF))
                                .example(body)));
    }

    private void registerApiErrorSchema(OpenAPI openApi) {
        ModelConverters.getInstance().read(ApiError.class)
                .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
    }
}
