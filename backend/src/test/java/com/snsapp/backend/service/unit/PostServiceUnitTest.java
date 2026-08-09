package com.snsapp.backend.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.snsapp.backend.dto.CreatePostRequest;
import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.PostImageRow;
import com.snsapp.backend.dto.PostResponse;
import com.snsapp.backend.dto.UpdatePostRequest;
import com.snsapp.backend.entity.Post;
import com.snsapp.backend.exception.InvalidFeedParameterException;
import com.snsapp.backend.exception.InvalidImageTypeException;
import com.snsapp.backend.exception.PostForbiddenException;
import com.snsapp.backend.exception.PostNotFoundException;
import com.snsapp.backend.exception.TooManyImagesException;
import com.snsapp.backend.mapper.PostImageMapper;
import com.snsapp.backend.mapper.PostMapper;
import com.snsapp.backend.service.PostService;
import com.snsapp.backend.storage.StorageService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PostService} の分岐網羅(docs/test-plan.md P-1〜P-13)。
 *
 * <p>実DBを使う {@code PostServiceTest}(L3)と役割を分ける。あちらはSQLが意図した行を返すことを
 * 検証し、こちらはMapperへ渡す引数・呼び出し回数・副作用の順序といった「Serviceの判断」を検証する。
 * N+1対策(P-6/P-7)や、画像の検証に失敗した時にDBへ書かないこと(P-9)は、
 * 呼び出し回数を数えられるモックでないと確認できない。
 */
@ExtendWith(MockitoExtension.class)
class PostServiceUnitTest {

    private static final Long CURRENT_USER_ID = 1L;

    @Mock
    private PostMapper postMapper;

    @Mock
    private PostImageMapper postImageMapper;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private PostService postService;

    private static Post rawPost(Long id, Long authorId, LocalDateTime deletedAt) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(authorId);
        post.setBody("本文");
        post.setDeletedAt(deletedAt);
        return post;
    }

    private static PostResponse response(long id, boolean deleted) {
        return new PostResponse(
                id, deleted ? null : "本文", 5L, "投稿者", null,
                LocalDateTime.now(), LocalDateTime.now(), 0, 0, false, false, false, deleted);
    }

    private static List<PostResponse> responses(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> response(i, false)).toList();
    }

    // --- P-1: feed の同値分割 ---

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ALL", "All", "followers", "invalid", "全部"})
    void feedの値が不正なら例外になる(String feed) {
        assertThatThrownBy(() -> postService.listFeed(CURRENT_USER_ID, feed, null, null, 20, null))
                .isInstanceOf(InvalidFeedParameterException.class);

        verifyNoInteractions(postMapper);
    }

    // --- P-3〜P-5: feed と authorId によるクエリの切り替え ---

    @Test
    void feedがallなら全体タイムラインを取得する() {
        when(postMapper.findFeedAll(eq(CURRENT_USER_ID), isNull(), isNull(), anyInt())).thenReturn(List.of());

        postService.listFeed(CURRENT_USER_ID, "all", null, null, 20, null);

        verify(postMapper).findFeedAll(eq(CURRENT_USER_ID), isNull(), isNull(), anyInt());
        verify(postMapper, never()).findFeedFollowing(any(), any(), any(), anyInt());
    }

    @Test
    void feedがfollowingならフォロー中タイムラインを取得する() {
        when(postMapper.findFeedFollowing(eq(CURRENT_USER_ID), isNull(), isNull(), anyInt())).thenReturn(List.of());

        postService.listFeed(CURRENT_USER_ID, "following", null, null, 20, null);

        verify(postMapper).findFeedFollowing(eq(CURRENT_USER_ID), isNull(), isNull(), anyInt());
        verify(postMapper, never()).findFeedAll(any(), any(), any(), anyInt());
    }

    /** authorIdが指定されるとfeedの値に関わらず著者絞り込みが優先される。 */
    @ParameterizedTest
    @ValueSource(strings = {"all", "following"})
    void authorIdを指定するとfeedの値に関わらず著者で絞り込む(String feed) {
        when(postMapper.findByAuthor(eq(CURRENT_USER_ID), eq(7L), isNull(), isNull(), anyInt()))
                .thenReturn(List.of());

        postService.listFeed(CURRENT_USER_ID, feed, null, null, 20, 7L);

        verify(postMapper).findByAuthor(eq(CURRENT_USER_ID), eq(7L), isNull(), isNull(), anyInt());
        verify(postMapper, never()).findFeedAll(any(), any(), any(), anyInt());
        verify(postMapper, never()).findFeedFollowing(any(), any(), any(), anyInt());
    }

    // --- P-2: limit のクランプ ---

    /** 範囲外のlimitは例外にせず1〜50へ丸める。Mapperには次ページ判定用の1件を足した値が渡る。 */
    @ParameterizedTest
    @CsvSource({"-100, 2", "-1, 2", "0, 2", "1, 2", "20, 21", "49, 50", "50, 51", "51, 51", "10000, 51"})
    void limitは1から50に丸められる(int requested, int expectedMapperLimit) {
        when(postMapper.findFeedAll(eq(CURRENT_USER_ID), isNull(), isNull(), anyInt())).thenReturn(List.of());

        postService.listFeed(CURRENT_USER_ID, "all", null, null, requested, null);

        verify(postMapper).findFeedAll(eq(CURRENT_USER_ID), isNull(), isNull(), eq(expectedMapperLimit));
    }

    // --- ページング ---

    @Test
    void 次ページがある場合はnextCursorに最後の投稿IDが入る() {
        when(postMapper.findFeedAll(eq(CURRENT_USER_ID), isNull(), isNull(), eq(4))).thenReturn(responses(4));

        CursorPage<PostResponse> page = postService.listFeed(CURRENT_USER_ID, "all", null, null, 3, null);

        assertThat(page.items()).hasSize(3);
        assertThat(page.nextCursor()).isEqualTo("3");
    }

    @Test
    void 次ページがない場合はnextCursorがnullになる() {
        when(postMapper.findFeedAll(eq(CURRENT_USER_ID), isNull(), isNull(), eq(4))).thenReturn(responses(2));

        CursorPage<PostResponse> page = postService.listFeed(CURRENT_USER_ID, "all", null, null, 3, null);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void cursorとsinceIdはそのままMapperへ渡される() {
        when(postMapper.findFeedAll(eq(CURRENT_USER_ID), eq(100L), eq(50L), anyInt())).thenReturn(List.of());

        postService.listFeed(CURRENT_USER_ID, "all", 100L, 50L, 20, null);

        verify(postMapper).findFeedAll(eq(CURRENT_USER_ID), eq(100L), eq(50L), anyInt());
    }

    // --- P-6, P-7: N+1 対策 ---

    /** 投稿が0件なら画像取得クエリを発行しないこと(早期returnの分岐)。 */
    @Test
    void 投稿が0件なら画像取得クエリを発行しない() {
        when(postMapper.findFeedAll(eq(CURRENT_USER_ID), isNull(), isNull(), anyInt())).thenReturn(List.of());

        CursorPage<PostResponse> page = postService.listFeed(CURRENT_USER_ID, "all", null, null, 20, null);

        assertThat(page.items()).isEmpty();
        verifyNoInteractions(postImageMapper);
    }

    /** 投稿がN件でも画像取得は1クエリにまとまること。件数分クエリが飛ぶとN+1になる。 */
    @Test
    void 複数投稿でも画像取得は1クエリにまとまる() {
        when(postMapper.findFeedAll(eq(CURRENT_USER_ID), isNull(), isNull(), anyInt())).thenReturn(responses(10));
        when(postImageMapper.findByPostIds(any())).thenReturn(List.of());

        postService.listFeed(CURRENT_USER_ID, "all", null, null, 20, null);

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.captor();
        verify(postImageMapper).findByPostIds(captor.capture());
        assertThat(captor.getValue()).hasSize(10);
    }

    // --- P-8: 画像枚数の境界値 ---

    @Test
    void 画像4枚までは投稿できる() {
        when(storageService.promote(anyString(), eq("posts"))).thenReturn("posts/x.jpg");
        when(postMapper.findById(any(), any())).thenReturn(response(1, false));

        List<String> keys = List.of("pending/1.jpg", "pending/2.jpg", "pending/3.jpg", "pending/4.jpg");
        postService.createPost(CURRENT_USER_ID, new CreatePostRequest("本文", keys));

        verify(postMapper).insert(any(Post.class));
    }

    @Test
    void 画像5枚は投稿できない() {
        List<String> keys =
                List.of("pending/1.jpg", "pending/2.jpg", "pending/3.jpg", "pending/4.jpg", "pending/5.jpg");

        assertThatThrownBy(() -> postService.createPost(CURRENT_USER_ID, new CreatePostRequest("本文", keys)))
                .isInstanceOf(TooManyImagesException.class);

        verifyNoInteractions(storageService);
        verify(postMapper, never()).insert(any());
    }

    @Test
    void 画像なしでも投稿できる() {
        when(postMapper.findById(any(), any())).thenReturn(response(1, false));

        postService.createPost(CURRENT_USER_ID, new CreatePostRequest("本文", null));

        verify(postMapper).insert(any(Post.class));
        verifyNoInteractions(postImageMapper);
    }

    // --- P-9: 画像検証の失敗でDBに書かない ---

    /**
     * 2枚目の検証に落ちた時点で投稿ごと失敗させる。ここでinsertが走ると、
     * 画像が1枚だけ付いた中途半端な投稿が残ってしまう。
     */
    @Test
    void 途中の画像の検証に失敗したら投稿を保存しない() {
        when(storageService.promote("pending/ok.jpg", "posts")).thenReturn("posts/ok.jpg");
        when(storageService.promote("pending/bad.exe", "posts")).thenThrow(new InvalidImageTypeException());

        assertThatThrownBy(() -> postService.createPost(
                        CURRENT_USER_ID, new CreatePostRequest("本文", List.of("pending/ok.jpg", "pending/bad.exe"))))
                .isInstanceOf(InvalidImageTypeException.class);

        verify(postMapper, never()).insert(any());
        verifyNoInteractions(postImageMapper);
    }

    // --- P-10: 画像の表示順 ---

    @Test
    void 画像は指定した順序でdisplayOrderが振られる() {
        when(storageService.promote("pending/a.jpg", "posts")).thenReturn("posts/a.jpg");
        when(storageService.promote("pending/b.jpg", "posts")).thenReturn("posts/b.jpg");
        when(storageService.promote("pending/c.jpg", "posts")).thenReturn("posts/c.jpg");
        when(postMapper.findById(any(), any())).thenReturn(response(1, false));

        postService.createPost(
                CURRENT_USER_ID,
                new CreatePostRequest("本文", List.of("pending/a.jpg", "pending/b.jpg", "pending/c.jpg")));

        InOrder inOrder = inOrder(postImageMapper);
        inOrder.verify(postImageMapper).insert(any(), eq("posts/a.jpg"), eq(0));
        inOrder.verify(postImageMapper).insert(any(), eq("posts/b.jpg"), eq(1));
        inOrder.verify(postImageMapper).insert(any(), eq("posts/c.jpg"), eq(2));
    }

    // --- getPost ---

    @Test
    void 投稿を取得できる() {
        when(postMapper.findById(42L, CURRENT_USER_ID)).thenReturn(response(42, false));
        when(postImageMapper.findByPostIds(List.of(42L))).thenReturn(List.of());

        assertThat(postService.getPost(CURRENT_USER_ID, 42L).id()).isEqualTo(42L);
    }

    @Test
    void 存在しない投稿は取得できない() {
        when(postMapper.findById(999L, CURRENT_USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> postService.getPost(CURRENT_USER_ID, 999L))
                .isInstanceOf(PostNotFoundException.class);
    }

    // --- P-11: 所有権チェックの3分岐 ---

    @Test
    void 自分の投稿を編集できる() {
        when(postMapper.findRawById(42L)).thenReturn(rawPost(42L, CURRENT_USER_ID, null));
        when(postMapper.findById(42L, CURRENT_USER_ID)).thenReturn(response(42, false));
        when(postImageMapper.findByPostIds(List.of(42L))).thenReturn(List.of());

        postService.updatePost(CURRENT_USER_ID, 42L, new UpdatePostRequest("編集後"));

        verify(postMapper).updateBody(42L, "編集後");
    }

    @Test
    void 存在しない投稿は編集できない() {
        when(postMapper.findRawById(999L)).thenReturn(null);

        assertThatThrownBy(() -> postService.updatePost(CURRENT_USER_ID, 999L, new UpdatePostRequest("編集後")))
                .isInstanceOf(PostNotFoundException.class);

        verify(postMapper, never()).updateBody(anyLong(), anyString());
    }

    @Test
    void 削除済みの投稿は編集できない() {
        when(postMapper.findRawById(42L)).thenReturn(rawPost(42L, CURRENT_USER_ID, LocalDateTime.now()));

        assertThatThrownBy(() -> postService.updatePost(CURRENT_USER_ID, 42L, new UpdatePostRequest("編集後")))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void 他人の投稿は編集できない() {
        when(postMapper.findRawById(42L)).thenReturn(rawPost(42L, 999L, null));

        assertThatThrownBy(() -> postService.updatePost(CURRENT_USER_ID, 42L, new UpdatePostRequest("編集後")))
                .isInstanceOf(PostForbiddenException.class);

        verify(postMapper, never()).updateBody(anyLong(), anyString());
    }

    /**
     * 「存在しない/削除済み」は404、「他人のもの」は403。一律にすると
     * 投稿IDの総当たりで存在有無を判定できたり、本人が権限エラーに気づけなくなったりする。
     */
    @Test
    void 存在しない投稿と他人の投稿で異なる例外になる() {
        when(postMapper.findRawById(999L)).thenReturn(null);
        when(postMapper.findRawById(42L)).thenReturn(rawPost(42L, 999L, null));

        assertThatThrownBy(() -> postService.deletePost(CURRENT_USER_ID, 999L))
                .isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> postService.deletePost(CURRENT_USER_ID, 42L))
                .isInstanceOf(PostForbiddenException.class);
    }

    @Test
    void 削除済みの投稿は再度削除できない() {
        when(postMapper.findRawById(42L)).thenReturn(rawPost(42L, CURRENT_USER_ID, LocalDateTime.now()));

        assertThatThrownBy(() -> postService.deletePost(CURRENT_USER_ID, 42L))
                .isInstanceOf(PostNotFoundException.class);

        verify(postMapper, never()).softDelete(anyLong());
    }

    // --- P-12: 削除時の副作用 ---

    /**
     * 投稿本体は論理削除だが、添付画像はS3の実体もpost_images行も物理削除する。
     * 行を残すと、ツームストーンとして残り続ける投稿に画像URLが乗り続けてしまう。
     */
    @Test
    void 投稿を削除すると画像の実体と行を消してから論理削除する() {
        when(postMapper.findRawById(42L)).thenReturn(rawPost(42L, CURRENT_USER_ID, null));
        when(postImageMapper.findByPostIds(List.of(42L)))
                .thenReturn(List.of(new PostImageRow(42L, "posts/a.jpg"), new PostImageRow(42L, "posts/b.jpg")));

        postService.deletePost(CURRENT_USER_ID, 42L);

        InOrder inOrder = inOrder(storageService, postImageMapper, postMapper);
        inOrder.verify(storageService).delete("posts/a.jpg");
        inOrder.verify(storageService).delete("posts/b.jpg");
        inOrder.verify(postImageMapper).deleteByPostId(42L);
        inOrder.verify(postMapper).softDelete(42L);
    }

    // --- P-13: ツームストーンから画像を漏らさない ---

    /**
     * 削除済み投稿は本文がSQLでNULL化されるのに合わせ、画像URLも必ず空で返す。
     * deletePostが行を消すため通常は空だが、修正前に削除された投稿の行が残っていても漏らさない。
     */
    @Test
    void 削除済み投稿の画像URLは必ず空になる() {
        when(postMapper.findById(42L, CURRENT_USER_ID)).thenReturn(response(42, true));
        when(postImageMapper.findByPostIds(List.of(42L)))
                .thenReturn(List.of(new PostImageRow(42L, "posts/leftover.jpg")));

        PostResponse post = postService.getPost(CURRENT_USER_ID, 42L);

        assertThat(post.deleted()).isTrue();
        assertThat(post.imageUrls()).isEmpty();
        verify(storageService, never()).presignedGetUrl("posts/leftover.jpg");
    }
}
