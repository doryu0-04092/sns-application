package com.snsapp.backend.service.unit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.snsapp.backend.entity.Post;
import com.snsapp.backend.exception.PostNotFoundException;
import com.snsapp.backend.exception.PostSelfLikeException;
import com.snsapp.backend.mapper.LikeMapper;
import com.snsapp.backend.mapper.PostMapper;
import com.snsapp.backend.service.LikeService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link LikeService} の分岐網羅(docs/test-plan.md LK-1〜LK-3)。
 *
 * <p>いいねは「like は投稿の存在・削除状態・自己いいねを検証するが、unlike は何も検証しない」
 * という非対称な作りになっている。存在しない投稿IDに対して like は404、unlike は200を返す。
 * これは実装漏れではなく、LikeController の説明と {@code @SkipNotFound} で
 * 「エラーにならず200を返す(冪等)」と明示されている意図的な設計(docs/test-plan.md 6.2)。
 * 契約が崩れていないことをここで固定する。
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceUnitTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final Long OTHER_USER_ID = 5L;

    @Mock
    private LikeMapper likeMapper;

    @Mock
    private PostMapper postMapper;

    @InjectMocks
    private LikeService likeService;

    private static Post rawPost(Long id, Long authorId, LocalDateTime deletedAt) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(authorId);
        post.setDeletedAt(deletedAt);
        return post;
    }

    // --- LK-1, LK-2: like ---

    @Test
    void 他人の投稿にいいねできる() {
        when(postMapper.findRawById(42L)).thenReturn(rawPost(42L, OTHER_USER_ID, null));

        likeService.like(CURRENT_USER_ID, 42L);

        verify(likeMapper).insertIgnoreDuplicate(42L, CURRENT_USER_ID);
    }

    @Test
    void 自分の投稿にはいいねできない() {
        when(postMapper.findRawById(42L)).thenReturn(rawPost(42L, CURRENT_USER_ID, null));

        assertThatThrownBy(() -> likeService.like(CURRENT_USER_ID, 42L))
                .isInstanceOf(PostSelfLikeException.class);

        verify(likeMapper, never()).insertIgnoreDuplicate(any(), any());
    }

    @Test
    void 存在しない投稿にはいいねできない() {
        when(postMapper.findRawById(999L)).thenReturn(null);

        assertThatThrownBy(() -> likeService.like(CURRENT_USER_ID, 999L))
                .isInstanceOf(PostNotFoundException.class);

        verify(likeMapper, never()).insertIgnoreDuplicate(any(), any());
    }

    @Test
    void 削除済みの投稿にはいいねできない() {
        when(postMapper.findRawById(42L)).thenReturn(rawPost(42L, OTHER_USER_ID, LocalDateTime.now()));

        assertThatThrownBy(() -> likeService.like(CURRENT_USER_ID, 42L))
                .isInstanceOf(PostNotFoundException.class);
    }

    /** 重複の吸収はSQL側の ON CONFLICT DO NOTHING に委ねている(実挙動はL3で検証)。 */
    @Test
    void 二重いいねでもServiceは例外を出さない() {
        when(postMapper.findRawById(42L)).thenReturn(rawPost(42L, OTHER_USER_ID, null));

        likeService.like(CURRENT_USER_ID, 42L);
        likeService.like(CURRENT_USER_ID, 42L);

        verify(likeMapper, times(2)).insertIgnoreDuplicate(42L, CURRENT_USER_ID);
    }

    // --- LK-3: unlike(現仕様の固定) ---

    @Test
    void いいねを解除できる() {
        likeService.unlike(CURRENT_USER_ID, 42L);

        verify(likeMapper).delete(42L, CURRENT_USER_ID);
    }

    /**
     * 存在しない投稿IDでもunlikeは成功する。likeが同じ状況で404を返すのに対し非対称だが、
     * DELETEを冪等に保つための意図的な仕様(docs/test-plan.md 6.2)。変更する場合このテストが落ちる。
     */
    @Test
    void 存在しない投稿のいいね解除でも例外にならない() {
        assertThatCode(() -> likeService.unlike(CURRENT_USER_ID, 999L)).doesNotThrowAnyException();

        verifyNoInteractions(postMapper);
        verify(likeMapper).delete(999L, CURRENT_USER_ID);
    }

    /** 削除済み投稿でも解除できる。likeでは404になるのと対照的。 */
    @Test
    void 削除済み投稿のいいね解除でも例外にならない() {
        assertThatCode(() -> likeService.unlike(CURRENT_USER_ID, 42L)).doesNotThrowAnyException();

        verifyNoInteractions(postMapper);
    }

    /** 自分の投稿へのいいね解除もブロックされない(likeでは400になる)。 */
    @Test
    void 自分の投稿のいいね解除も例外にならない() {
        assertThatCode(() -> likeService.unlike(CURRENT_USER_ID, 42L)).doesNotThrowAnyException();

        verify(likeMapper).delete(42L, CURRENT_USER_ID);
    }
}
