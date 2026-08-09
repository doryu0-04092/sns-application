package com.snsapp.backend.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.snsapp.backend.controller.FollowController;
import com.snsapp.backend.exception.SelfFollowException;
import com.snsapp.backend.exception.UserNotFoundException;
import com.snsapp.backend.service.FollowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link FollowController} のWeb層スライステスト(docs/test-plan.md 4.2)。
 *
 * <p>いいねと同じく、フォローは検証あり・解除は冪等という非対称な契約
 * ({@code @SkipNotFound} で意図的に文書化されている)。
 */
@WebMvcTest(FollowController.class)
class FollowControllerTest extends AbstractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FollowService followService;

    @Test
    void ユーザーをフォローできる() throws Exception {
        mockMvc.perform(authenticated(post("/api/users/7/follow")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(followService).follow(USER_ID, 7L);
    }

    @Test
    void 自分自身のフォローは400になる() throws Exception {
        doThrow(new SelfFollowException()).when(followService).follow(any(), any());

        mockMvc.perform(authenticated(post("/api/users/1/follow")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SELF_FOLLOW_NOT_ALLOWED"));
    }

    @Test
    void 存在しないユーザーのフォローは404になる() throws Exception {
        doThrow(new UserNotFoundException()).when(followService).follow(any(), any());

        mockMvc.perform(authenticated(post("/api/users/999/follow")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    void フォローを解除できる() throws Exception {
        mockMvc.perform(authenticated(delete("/api/users/7/follow"))).andExpect(status().isOk());

        verify(followService).unfollow(USER_ID, 7L);
    }

    /** フォローは404を返す状況でも、解除は200を返す(冪等)。 */
    @Test
    void 存在しないユーザーのフォロー解除でも200になる() throws Exception {
        mockMvc.perform(authenticated(delete("/api/users/999/follow"))).andExpect(status().isOk());

        verify(followService).unfollow(USER_ID, 999L);
    }

    @Test
    void ユーザーIDが数値でないと400になる() throws Exception {
        mockMvc.perform(authenticated(post("/api/users/abc/follow")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
