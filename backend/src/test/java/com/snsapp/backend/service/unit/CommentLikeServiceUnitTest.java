package com.snsapp.backend.service.unit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.snsapp.backend.entity.Comment;
import com.snsapp.backend.exception.CommentNotFoundException;
import com.snsapp.backend.exception.CommentSelfLikeException;
import com.snsapp.backend.mapper.CommentLikeMapper;
import com.snsapp.backend.mapper.CommentMapper;
import com.snsapp.backend.service.CommentLikeService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link CommentLikeService} の分岐網羅(docs/test-plan.md CL-1〜CL-3)。
 *
 * <p>構造は {@link LikeServiceUnitTest} と同型。unlike が存在確認をしない非対称も同じ。
 */
@ExtendWith(MockitoExtension.class)
class CommentLikeServiceUnitTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final Long OTHER_USER_ID = 5L;

    @Mock
    private CommentLikeMapper commentLikeMapper;

    @Mock
    private CommentMapper commentMapper;

    @InjectMocks
    private CommentLikeService commentLikeService;

    private static Comment rawComment(Long id, Long authorId, LocalDateTime deletedAt) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setPostId(42L);
        comment.setUserId(authorId);
        comment.setDeletedAt(deletedAt);
        return comment;
    }

    // --- CL-1, CL-2: like ---

    @Test
    void 他人のコメントにいいねできる() {
        when(commentMapper.findRawById(101L)).thenReturn(rawComment(101L, OTHER_USER_ID, null));

        commentLikeService.like(CURRENT_USER_ID, 101L);

        verify(commentLikeMapper).insertIgnoreDuplicate(101L, CURRENT_USER_ID);
    }

    @Test
    void 自分のコメントにはいいねできない() {
        when(commentMapper.findRawById(101L)).thenReturn(rawComment(101L, CURRENT_USER_ID, null));

        assertThatThrownBy(() -> commentLikeService.like(CURRENT_USER_ID, 101L))
                .isInstanceOf(CommentSelfLikeException.class);

        verify(commentLikeMapper, never()).insertIgnoreDuplicate(any(), any());
    }

    @Test
    void 存在しないコメントにはいいねできない() {
        when(commentMapper.findRawById(999L)).thenReturn(null);

        assertThatThrownBy(() -> commentLikeService.like(CURRENT_USER_ID, 999L))
                .isInstanceOf(CommentNotFoundException.class);

        verify(commentLikeMapper, never()).insertIgnoreDuplicate(any(), any());
    }

    @Test
    void 削除済みのコメントにはいいねできない() {
        when(commentMapper.findRawById(101L)).thenReturn(rawComment(101L, OTHER_USER_ID, LocalDateTime.now()));

        assertThatThrownBy(() -> commentLikeService.like(CURRENT_USER_ID, 101L))
                .isInstanceOf(CommentNotFoundException.class);
    }

    // --- CL-3: unlike(現仕様の固定) ---

    @Test
    void コメントのいいねを解除できる() {
        commentLikeService.unlike(CURRENT_USER_ID, 101L);

        verify(commentLikeMapper).delete(101L, CURRENT_USER_ID);
    }

    /** 投稿側と同じ冪等な契約。存在確認をしないため常に成功する(docs/test-plan.md 6.2)。 */
    @Test
    void 存在しないコメントのいいね解除でも例外にならない() {
        assertThatCode(() -> commentLikeService.unlike(CURRENT_USER_ID, 999L)).doesNotThrowAnyException();

        verifyNoInteractions(commentMapper);
        verify(commentLikeMapper).delete(999L, CURRENT_USER_ID);
    }

    @Test
    void 削除済みコメントのいいね解除でも例外にならない() {
        assertThatCode(() -> commentLikeService.unlike(CURRENT_USER_ID, 101L)).doesNotThrowAnyException();

        verifyNoInteractions(commentMapper);
    }
}
