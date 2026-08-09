package com.snsapp.backend.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.snsapp.backend.controller.LikeController;
import com.snsapp.backend.exception.CommentNotFoundException;
import com.snsapp.backend.exception.CommentSelfLikeException;
import com.snsapp.backend.exception.PostNotFoundException;
import com.snsapp.backend.exception.PostSelfLikeException;
import com.snsapp.backend.service.CommentLikeService;
import com.snsapp.backend.service.LikeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link LikeController} のWeb層スライステスト(docs/test-plan.md 4.2)。
 *
 * <p>いいねの付与と解除は非対称な契約になっている。付与は対象の存在・削除状態・自己いいねを
 * 検証して404/400を返すが、解除は存在確認をせず常に200を返す(冪等)。
 * これはOpenAPIの説明と {@code @SkipNotFound} で意図的に文書化されている設計であり、
 * 契約として崩れていないことをここで固定する。
 */
@WebMvcTest(LikeController.class)
class LikeControllerTest extends AbstractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LikeService likeService;

    @MockitoBean
    private CommentLikeService commentLikeService;

    // --- 投稿へのいいね ---

    @Test
    void 投稿にいいねできる() throws Exception {
        mockMvc.perform(authenticated(post("/api/posts/42/like")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(likeService).like(USER_ID, 42L);
    }

    @Test
    void 自分の投稿へのいいねは400になる() throws Exception {
        doThrow(new PostSelfLikeException()).when(likeService).like(any(), any());

        mockMvc.perform(authenticated(post("/api/posts/42/like")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("POST_SELF_LIKE_NOT_ALLOWED"));
    }

    @Test
    void 存在しない投稿へのいいねは404になる() throws Exception {
        doThrow(new PostNotFoundException()).when(likeService).like(any(), any());

        mockMvc.perform(authenticated(post("/api/posts/999/like")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    // --- 投稿のいいね解除(冪等) ---

    @Test
    void 投稿のいいねを解除できる() throws Exception {
        mockMvc.perform(authenticated(delete("/api/posts/42/like"))).andExpect(status().isOk());

        verify(likeService).unlike(USER_ID, 42L);
    }

    /**
     * 付与は404を返す状況でも、解除は200を返す(冪等)。
     * Serviceが存在確認をしないためControllerまでエラーが上がってこない。
     */
    @Test
    void 存在しない投稿のいいね解除でも200になる() throws Exception {
        mockMvc.perform(authenticated(delete("/api/posts/999/like"))).andExpect(status().isOk());

        verify(likeService).unlike(USER_ID, 999L);
    }

    // --- コメントへのいいね ---

    @Test
    void コメントにいいねできる() throws Exception {
        mockMvc.perform(authenticated(post("/api/comments/101/like"))).andExpect(status().isOk());

        verify(commentLikeService).like(USER_ID, 101L);
    }

    @Test
    void 自分のコメントへのいいねは400になる() throws Exception {
        doThrow(new CommentSelfLikeException()).when(commentLikeService).like(any(), any());

        mockMvc.perform(authenticated(post("/api/comments/101/like")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMENT_SELF_LIKE_NOT_ALLOWED"));
    }

    @Test
    void 削除済みコメントへのいいねは404になる() throws Exception {
        doThrow(new CommentNotFoundException()).when(commentLikeService).like(any(), any());

        mockMvc.perform(authenticated(post("/api/comments/101/like")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMENT_NOT_FOUND"));
    }

    // --- コメントのいいね解除(冪等) ---

    @Test
    void コメントのいいねを解除できる() throws Exception {
        mockMvc.perform(authenticated(delete("/api/comments/101/like"))).andExpect(status().isOk());

        verify(commentLikeService).unlike(USER_ID, 101L);
    }

    @Test
    void 存在しないコメントのいいね解除でも200になる() throws Exception {
        mockMvc.perform(authenticated(delete("/api/comments/999/like"))).andExpect(status().isOk());

        verify(commentLikeService).unlike(USER_ID, 999L);
    }

    // --- パスパラメータ ---

    @Test
    void 投稿IDが数値でないと400になる() throws Exception {
        mockMvc.perform(authenticated(post("/api/posts/abc/like")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void コメントIDが数値でないと400になる() throws Exception {
        mockMvc.perform(authenticated(post("/api/comments/abc/like")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
