package com.snsapp.backend.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.snsapp.backend.controller.PostController;
import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.PostResponse;
import com.snsapp.backend.exception.InvalidFeedParameterException;
import com.snsapp.backend.exception.PostForbiddenException;
import com.snsapp.backend.exception.PostNotFoundException;
import com.snsapp.backend.exception.TooManyImagesException;
import com.snsapp.backend.service.PostService;
import java.time.LocalDateTime;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link PostController} のWeb層スライステスト(docs/test-plan.md 4.2)。
 *
 * <p>Serviceをモックするため、ここで見るのは入出力の契約だけ
 * (ルーティング・{@code @Valid}の境界値・ステータスコード・エラーJSONの形式)。
 * ビジネスロジックの分岐はL1の {@code PostServiceUnitTest} が担当する。
 */
@WebMvcTest(PostController.class)
class PostControllerTest extends AbstractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    // MockMvcRequestBuilders.post と名前が衝突しないよう postResponse にしている。
    private static PostResponse postResponse(long id) {
        return new PostResponse(
                id, "本文", 5L, "投稿者", null,
                LocalDateTime.now(), LocalDateTime.now(), 0, 0, false, false, false, false);
    }

    private static String body(int length) {
        return "あ".repeat(length);
    }

    // --- GET /api/posts ---

    @Test
    void タイムラインを取得できる() throws Exception {
        when(postService.listFeed(eq(USER_ID), eq("all"), any(), any(), anyInt(), any()))
                .thenReturn(new CursorPage<>(List.of(postResponse(1), postResponse(2)), "1"));

        mockMvc.perform(authenticated(get("/api/posts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.nextCursor").value("1"));
    }

    /** feed未指定なら all、limit未指定なら 20 が既定値として渡ること。 */
    @Test
    void クエリパラメータ未指定なら既定値が使われる() throws Exception {
        when(postService.listFeed(any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(new CursorPage<>(List.of(), null));

        mockMvc.perform(authenticated(get("/api/posts"))).andExpect(status().isOk());

        verify(postService).listFeed(USER_ID, "all", null, null, 20, null);
    }

    @Test
    void クエリパラメータはそのままServiceへ渡る() throws Exception {
        when(postService.listFeed(any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(new CursorPage<>(List.of(), null));

        mockMvc.perform(authenticated(
                        get("/api/posts?feed=following&cursor=100&sinceId=50&limit=30&authorId=7")))
                .andExpect(status().isOk());

        verify(postService).listFeed(USER_ID, "following", 100L, 50L, 30, 7L);
    }

    @Test
    void feedが不正なら400になる() throws Exception {
        when(postService.listFeed(any(), eq("invalid"), any(), any(), anyInt(), any()))
                .thenThrow(new InvalidFeedParameterException());

        mockMvc.perform(authenticated(get("/api/posts?feed=invalid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_FEED"));
    }

    /** 範囲外のlimitはServiceがクランプするためエラーにならない。 */
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 50, 51, 10000})
    void 範囲外のlimitでもエラーにならない(int limit) throws Exception {
        when(postService.listFeed(any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(new CursorPage<>(List.of(), null));

        mockMvc.perform(authenticated(get("/api/posts?limit=" + limit))).andExpect(status().isOk());
    }

    /** 数値パラメータに文字列を渡した場合は型変換に失敗して400。 */
    @ParameterizedTest
    @ValueSource(strings = {"cursor=abc", "sinceId=abc", "limit=abc", "authorId=abc"})
    void 数値パラメータに文字列を渡すと400になる(String query) throws Exception {
        mockMvc.perform(authenticated(get("/api/posts?" + query)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // --- POST /api/posts ---

    @Test
    void 投稿を作成すると201になる() throws Exception {
        when(postService.createPost(eq(USER_ID), any())).thenReturn(postResponse(1));

        mockMvc.perform(authenticated(post("/api/posts"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"本文\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    /** 本文の長さの境界値。280文字ちょうどは通り、281文字は弾かれる。 */
    @ParameterizedTest
    @CsvSource({"1, 201", "279, 201", "280, 201", "281, 400", "500, 400"})
    void 本文の長さの境界値を検証する(int length, int expectedStatus) throws Exception {
        when(postService.createPost(any(), any())).thenReturn(postResponse(1));

        mockMvc.perform(authenticated(post("/api/posts"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"%s\"}".formatted(body(length))))
                .andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\\n", "\\t"})
    void 本文が空や半角空白のみなら400になる(String bodyValue) throws Exception {
        mockMvc.perform(authenticated(post("/api/posts"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"%s\"}".formatted(bodyValue)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /**
     * 全角スペースだけの投稿は現状「通る」(docs/test-plan.md 6.2)。
     *
     * <p>{@code @NotBlank} は Hibernate Validator 内部で {@link String#trim()} を使っており、
     * trim() は U+0020 以下のコードポイントしか除去しない。全角スペース U+3000 は残るため
     * 空文字と判定されない。{@link String#isBlank()} は Unicode 対応で全角スペースも空白と
     * 見なすため、同じ「空白のみ」でも判定が割れる。
     *
     * <p>見た目が空の投稿を許すかは仕様判断のためここでは現状を固定するに留める。
     * 弾きたくなった場合はこのテストが落ちて変更に気づける。
     */
    @Test
    void 全角スペースのみの本文は現状では投稿できてしまう() throws Exception {
        when(postService.createPost(any(), any())).thenReturn(postResponse(1));

        mockMvc.perform(authenticated(post("/api/posts"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"　　　\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 本文キーが無いと400になる() throws Exception {
        mockMvc.perform(authenticated(post("/api/posts"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 壊れたJSONを送ると400になる() throws Exception {
        mockMvc.perform(authenticated(post("/api/posts"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /** 画像枚数の境界値。@Sizeで4枚まで、5枚目からは400。 */
    @Test
    void 画像4枚までは投稿できる() throws Exception {
        when(postService.createPost(any(), any())).thenReturn(postResponse(1));

        mockMvc.perform(authenticated(post("/api/posts"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"本文\", \"imageKeys\": [\"a\",\"b\",\"c\",\"d\"]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 画像5枚は400になる() throws Exception {
        mockMvc.perform(authenticated(post("/api/posts"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"本文\", \"imageKeys\": [\"a\",\"b\",\"c\",\"d\",\"e\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /** Service側の枚数チェックに落ちた場合は専用のエラーコードで返る。 */
    @Test
    void Serviceが画像枚数超過を投げると400になる() throws Exception {
        when(postService.createPost(any(), any())).thenThrow(new TooManyImagesException());

        mockMvc.perform(authenticated(post("/api/posts"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"本文\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_IMAGES"));
    }

    // --- GET /api/posts/{postId} ---

    @Test
    void 投稿を1件取得できる() throws Exception {
        when(postService.getPost(USER_ID, 42L)).thenReturn(postResponse(42));

        mockMvc.perform(authenticated(get("/api/posts/42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(42));
    }

    @Test
    void 存在しない投稿の取得は404になる() throws Exception {
        when(postService.getPost(any(), eq(999L))).thenThrow(new PostNotFoundException());

        mockMvc.perform(authenticated(get("/api/posts/999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    @Test
    void パスパラメータが数値でないと400になる() throws Exception {
        mockMvc.perform(authenticated(get("/api/posts/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // --- PATCH /api/posts/{postId} ---

    @Test
    void 投稿を編集できる() throws Exception {
        when(postService.updatePost(eq(USER_ID), eq(42L), any())).thenReturn(postResponse(42));

        mockMvc.perform(authenticated(patch("/api/posts/42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"編集後\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 他人の投稿の編集は403になる() throws Exception {
        when(postService.updatePost(any(), any(), any())).thenThrow(new PostForbiddenException());

        mockMvc.perform(authenticated(patch("/api/posts/42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"編集後\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_FORBIDDEN"));
    }

    @Test
    void 存在しない投稿の編集は404になる() throws Exception {
        when(postService.updatePost(any(), any(), any())).thenThrow(new PostNotFoundException());

        mockMvc.perform(authenticated(patch("/api/posts/999"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"編集後\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    /** バリデーションはServiceを呼ぶ前に効くこと。 */
    @Test
    void 編集で本文が空なら400になりServiceを呼ばない() throws Exception {
        mockMvc.perform(authenticated(patch("/api/posts/42"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"\"}"))
                .andExpect(status().isBadRequest());

        verify(postService, never()).updatePost(any(), any(), any());
    }

    // --- DELETE /api/posts/{postId} ---

    @Test
    void 投稿を削除できる() throws Exception {
        doNothing().when(postService).deletePost(USER_ID, 42L);

        mockMvc.perform(authenticated(delete("/api/posts/42"))).andExpect(status().is2xxSuccessful());

        verify(postService).deletePost(USER_ID, 42L);
    }

    @Test
    void 他人の投稿の削除は403になる() throws Exception {
        doThrow(new PostForbiddenException()).when(postService).deletePost(any(), any());

        mockMvc.perform(authenticated(delete("/api/posts/42")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_FORBIDDEN"));
    }

    @Test
    void 存在しない投稿の削除は404になる() throws Exception {
        doThrow(new PostNotFoundException()).when(postService).deletePost(any(), any());

        mockMvc.perform(authenticated(delete("/api/posts/999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    // --- 想定外の例外 ---

    /**
     * Serviceが想定外の例外を投げた場合、500になり、
     * かつ例外メッセージがクライアントへ漏れないこと(内部情報の露出防止)。
     */
    @Test
    void 想定外の例外は500になり詳細を返さない() throws Exception {
        when(postService.getPost(any(), any()))
                .thenThrow(new IllegalStateException("DBのパスワードは secret123 です"));

        mockMvc.perform(authenticated(get("/api/posts/42")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message")
                        .value(Matchers.not(Matchers.containsString("secret123"))));
    }
}
