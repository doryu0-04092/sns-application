package com.snsapp.backend.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.UserSummaryResponse;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.exception.SelfFollowException;
import com.snsapp.backend.exception.UserNotFoundException;
import com.snsapp.backend.mapper.FollowMapper;
import com.snsapp.backend.mapper.UserMapper;
import com.snsapp.backend.service.FollowService;
import com.snsapp.backend.storage.StorageService;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FollowService} の分岐網羅(docs/test-plan.md FL-1〜FL-8)。
 *
 * <p>フォロー系は「follow は厳密に検証するが unfollow は何も検証しない」という非対称な作りに
 * なっている。これは実装漏れではなく、FollowController の説明と {@code @SkipNotFound} で
 * 「エラーにならず200を返す(冪等)」と明示されている意図的な設計(docs/test-plan.md 6.2)。
 * 契約が崩れていないことをここで固定し、変更時に気づけるようにしている。
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowMapper followMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private FollowService followService;

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static List<UserSummaryResponse> summaries(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new UserSummaryResponse((long) i, i + 100L, "ユーザー" + i, null, false))
                .toList();
    }

    // --- FL-1〜FL-4: follow ---

    @Test
    void 他人をフォローできる() {
        when(userMapper.findById(7L)).thenReturn(user(7L));

        followService.follow(1L, 7L);

        verify(followMapper).insertIgnoreDuplicate(1L, 7L);
    }

    @Test
    void 自分自身はフォローできない() {
        assertThatThrownBy(() -> followService.follow(1L, 1L)).isInstanceOf(SelfFollowException.class);

        verify(followMapper, never()).insertIgnoreDuplicate(any(), any());
    }

    /** 自己フォロー判定はユーザー存在確認より先。DBに触れずに弾く。 */
    @Test
    void 自己フォローの判定はユーザー存在確認より先に行われる() {
        assertThatThrownBy(() -> followService.follow(1L, 1L)).isInstanceOf(SelfFollowException.class);

        verifyNoInteractions(userMapper);
    }

    @Test
    void 存在しないユーザーはフォローできない() {
        when(userMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> followService.follow(1L, 999L)).isInstanceOf(UserNotFoundException.class);

        verify(followMapper, never()).insertIgnoreDuplicate(any(), any());
    }

    /**
     * 既にフォロー済みでもServiceは素通しする。重複の吸収はSQL側の
     * ON CONFLICT DO NOTHING に委ねている(実挙動はL3の結合テストで検証)。
     */
    @Test
    void 既にフォロー済みでも例外にならない() {
        when(userMapper.findById(7L)).thenReturn(user(7L));

        followService.follow(1L, 7L);
        followService.follow(1L, 7L);

        verify(followMapper, times(2)).insertIgnoreDuplicate(1L, 7L);
    }

    // --- FL-5: unfollow(現仕様の固定) ---

    @Test
    void フォロー解除できる() {
        followService.unfollow(1L, 7L);

        verify(followMapper).delete(1L, 7L);
    }

    /**
     * 存在しないユーザーIDでもunfollowは成功する(200)。
     * follow が同じ状況で404を返すのに対し非対称だが、これが現在の仕様。
     * 変更する場合はこのテストが落ちるので気づける。
     */
    @Test
    void 存在しないユーザーのフォロー解除でも例外にならない() {
        assertThatCode(() -> followService.unfollow(1L, 999L)).doesNotThrowAnyException();

        verifyNoInteractions(userMapper);
        verify(followMapper).delete(1L, 999L);
    }

    /** 自己フォロー解除もブロックされない(followでは400になるのと対照的)。 */
    @Test
    void 自分自身のフォロー解除も例外にならない() {
        assertThatCode(() -> followService.unfollow(1L, 1L)).doesNotThrowAnyException();
    }

    // --- FL-6: 一覧の存在確認 ---

    @Test
    void 存在しないユーザーのフォロワー一覧は取得できない() {
        when(userMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> followService.listFollowers(1L, 999L, null, 20))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void 存在しないユーザーのフォロー中一覧は取得できない() {
        when(userMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> followService.listFollowing(1L, 999L, null, 20))
                .isInstanceOf(UserNotFoundException.class);
    }

    // --- FL-7: limit のクランプ ---

    @ParameterizedTest
    @CsvSource({"-5, 2", "0, 2", "1, 2", "20, 21", "50, 51", "51, 51", "1000, 51"})
    void フォロワー一覧のlimitは1から50に丸められる(int requested, int expectedMapperLimit) {
        when(userMapper.findById(7L)).thenReturn(user(7L));
        when(followMapper.findFollowers(eq(7L), eq(1L), isNull(), anyInt())).thenReturn(List.of());

        followService.listFollowers(1L, 7L, null, requested);

        verify(followMapper).findFollowers(eq(7L), eq(1L), isNull(), eq(expectedMapperLimit));
    }

    // --- FL-8: ページング ---

    @Test
    void フォロワー一覧で次ページがある場合はnextCursorが入る() {
        when(userMapper.findById(7L)).thenReturn(user(7L));
        when(followMapper.findFollowers(eq(7L), eq(1L), isNull(), eq(4))).thenReturn(summaries(4));

        CursorPage<UserSummaryResponse> page = followService.listFollowers(1L, 7L, null, 3);

        assertThat(page.items()).hasSize(3);
        assertThat(page.nextCursor()).isEqualTo("3");
    }

    /**
     * nextCursorに入るのは UserSummaryResponse#id で、フォロー関係のレコードIDである。
     * ユーザーID(userId)ではない点を明示する(UserSummaryResponseのSchema説明どおり)。
     */
    @Test
    void フォロワー一覧のnextCursorはユーザーIDではなくレコードIDが入る() {
        when(userMapper.findById(7L)).thenReturn(user(7L));
        when(followMapper.findFollowers(eq(7L), eq(1L), isNull(), eq(2))).thenReturn(summaries(2));

        CursorPage<UserSummaryResponse> page = followService.listFollowers(1L, 7L, null, 1);

        assertThat(page.items().get(0).id()).isEqualTo(1L);
        assertThat(page.items().get(0).userId()).isEqualTo(101L);
        assertThat(page.nextCursor()).isEqualTo("1");
    }

    @Test
    void フォロー中一覧で次ページがない場合はnextCursorがnullになる() {
        when(userMapper.findById(7L)).thenReturn(user(7L));
        when(followMapper.findFollowing(eq(7L), eq(1L), isNull(), eq(4))).thenReturn(summaries(2));

        CursorPage<UserSummaryResponse> page = followService.listFollowing(1L, 7L, null, 3);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void フォロワーが0人でも例外にならない() {
        when(userMapper.findById(7L)).thenReturn(user(7L));
        when(followMapper.findFollowers(eq(7L), eq(1L), isNull(), anyInt())).thenReturn(List.of());

        CursorPage<UserSummaryResponse> page = followService.listFollowers(1L, 7L, null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void 一覧のアイコンは署名付きURLに差し替わる() {
        when(userMapper.findById(7L)).thenReturn(user(7L));
        when(followMapper.findFollowers(eq(7L), eq(1L), isNull(), anyInt()))
                .thenReturn(List.of(new UserSummaryResponse(1L, 101L, "山田", "avatars/a.jpg", false)));
        when(storageService.presignedGetUrl("avatars/a.jpg")).thenReturn("https://s3.example.com/signed");

        CursorPage<UserSummaryResponse> page = followService.listFollowers(1L, 7L, null, 20);

        assertThat(page.items().get(0).avatarUrl()).isEqualTo("https://s3.example.com/signed");
    }
}
