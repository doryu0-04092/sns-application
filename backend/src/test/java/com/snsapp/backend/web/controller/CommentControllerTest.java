package com.snsapp.backend.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.snsapp.backend.controller.CommentController;
import com.snsapp.backend.dto.CommentResponse;
import com.snsapp.backend.exception.CommentForbiddenException;
import com.snsapp.backend.exception.CommentNotFoundException;
import com.snsapp.backend.exception.PostNotFoundException;
import com.snsapp.backend.service.CommentService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** {@link CommentController} のWeb層スライステスト(docs/test-plan.md 4.2)。 */
@WebMvcTest(CommentController.class)
class CommentControllerTest extends AbstractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    private static CommentResponse comment(long id, Long parentCommentId, boolean deleted) {
        return new CommentResponse(
                id, 42L, parentCommentId, deleted ? null : "コメント本文", 5L, "投稿者", null,
                LocalDateTime.now(), LocalDateTime.now(), 0, false, false, false, deleted);
    }

    // --- GET /api/posts/{postId}/comments ---

    @Test
    void コメント一覧を取得できる() throws Exception {
        when(commentService.listComments(USER_ID, 42L))
                .thenReturn(List.of(comment(101, null, false), comment(102, 101L, false)));

        mockMvc.perform(authenticated(get("/api/posts/42/comments")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].parentCommentId").value(101));
    }

    /** 一覧はツリーではなく平坦な配列で返る(ツリー化はクライアント側の責務)。 */
    @Test
    void コメント一覧は平坦な配列で返る() throws Exception {
        when(commentService.listComments(any(), any())).thenReturn(List.of(comment(101, null, false)));

        mockMvc.perform(authenticated(get("/api/posts/42/comments")))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].children").doesNotExist());
    }

    /** ツームストーン化したコメントは body が null で deleted が true。 */
    @Test
    void 削除済みコメントは本文がnullで返る() throws Exception {
        when(commentService.listComments(any(), any())).thenReturn(List.of(comment(101, null, true)));

        mockMvc.perform(authenticated(get("/api/posts/42/comments")))
                .andExpect(jsonPath("$.data[0].deleted").value(true))
                .andExpect(jsonPath("$.data[0].body").doesNotExist());
    }

    @Test
    void 存在しない投稿のコメント一覧は404になる() throws Exception {
        when(commentService.listComments(any(), eq(999L))).thenThrow(new PostNotFoundException());

        mockMvc.perform(authenticated(get("/api/posts/999/comments")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    // --- POST /api/posts/{postId}/comments ---

    @Test
    void コメントを投稿すると201になる() throws Exception {
        when(commentService.createComment(eq(USER_ID), eq(42L), any())).thenReturn(comment(101, null, false));

        mockMvc.perform(authenticated(post("/api/posts/42/comments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"コメント\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(101));
    }

    @Test
    void 返信を投稿できる() throws Exception {
        when(commentService.createComment(any(), any(), any())).thenReturn(comment(102, 101L, false));

        mockMvc.perform(authenticated(post("/api/posts/42/comments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"返信\", \"parentCommentId\": 101}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parentCommentId").value(101));
    }

    @Test
    void 削除済み投稿へのコメントは404になる() throws Exception {
        when(commentService.createComment(any(), any(), any())).thenThrow(new PostNotFoundException());

        mockMvc.perform(authenticated(post("/api/posts/42/comments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"コメント\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    @Test
    void 存在しないコメントへの返信は404になる() throws Exception {
        when(commentService.createComment(any(), any(), any())).thenThrow(new CommentNotFoundException());

        mockMvc.perform(authenticated(post("/api/posts/42/comments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"返信\", \"parentCommentId\": 999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMENT_NOT_FOUND"));
    }

    /** 本文の長さの境界値。投稿と同じ280文字制限。 */
    @ParameterizedTest
    @CsvSource({"1, 201", "279, 201", "280, 201", "281, 400"})
    void コメント本文の長さの境界値を検証する(int length, int expectedStatus) throws Exception {
        when(commentService.createComment(any(), any(), any())).thenReturn(comment(101, null, false));

        mockMvc.perform(authenticated(post("/api/posts/42/comments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"%s\"}".formatted("あ".repeat(length))))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void コメント本文が空なら400になる() throws Exception {
        mockMvc.perform(authenticated(post("/api/posts/42/comments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(commentService, never()).createComment(any(), any(), any());
    }

    // --- PATCH /api/comments/{commentId} ---

    @Test
    void コメントを編集できる() throws Exception {
        when(commentService.updateComment(eq(USER_ID), eq(101L), any())).thenReturn(comment(101, null, false));

        mockMvc.perform(authenticated(patch("/api/comments/101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"編集後\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 他人のコメントの編集は403になる() throws Exception {
        when(commentService.updateComment(any(), any(), any())).thenThrow(new CommentForbiddenException());

        mockMvc.perform(authenticated(patch("/api/comments/101"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"編集後\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMENT_FORBIDDEN"));
    }

    @Test
    void 存在しないコメントの編集は404になる() throws Exception {
        when(commentService.updateComment(any(), any(), any())).thenThrow(new CommentNotFoundException());

        mockMvc.perform(authenticated(patch("/api/comments/999"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"編集後\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMENT_NOT_FOUND"));
    }

    // --- DELETE /api/comments/{commentId} ---

    @Test
    void コメントを削除できる() throws Exception {
        mockMvc.perform(authenticated(delete("/api/comments/101"))).andExpect(status().isOk());

        verify(commentService).deleteComment(USER_ID, 101L);
    }

    @Test
    void 他人のコメントの削除は403になる() throws Exception {
        doThrow(new CommentForbiddenException()).when(commentService).deleteComment(any(), any());

        mockMvc.perform(authenticated(delete("/api/comments/101")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMENT_FORBIDDEN"));
    }

    @Test
    void 存在しないコメントの削除は404になる() throws Exception {
        doThrow(new CommentNotFoundException()).when(commentService).deleteComment(any(), any());

        mockMvc.perform(authenticated(delete("/api/comments/999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    void コメントIDが数値でないと400になる() throws Exception {
        mockMvc.perform(authenticated(delete("/api/comments/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
