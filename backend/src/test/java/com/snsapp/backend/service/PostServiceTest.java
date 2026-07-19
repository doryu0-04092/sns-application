package com.snsapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.PostResponse;
import com.snsapp.backend.dto.UpdatePostRequest;
import com.snsapp.backend.entity.Post;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.exception.InvalidFeedParameterException;
import com.snsapp.backend.exception.InvalidPostBodyException;
import com.snsapp.backend.exception.PostForbiddenException;
import com.snsapp.backend.exception.PostNotFoundException;
import com.snsapp.backend.mapper.FollowMapper;
import com.snsapp.backend.support.AbstractIntegrationTest;
import com.snsapp.backend.support.TestFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** タイムラインのカーソルページネーション境界と、投稿の所有権チェックの検証。 */
@Transactional
class PostServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PostService postService;

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private TestFixtures fixtures;

    // --- カーソルページネーション ---

    @Test
    void 取得件数がlimitちょうどのときnextCursorはnullになる() {
        User author = fixtures.user();
        createPosts(author, 3);

        CursorPage<PostResponse> page = feed(author, null, 3);

        assertThat(page.items()).hasSize(3);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void 次ページがあるときnextCursorは最後の要素のIDになる() {
        User author = fixtures.user();
        createPosts(author, 5);

        CursorPage<PostResponse> page = feed(author, null, 3);

        assertThat(page.items()).hasSize(3);
        assertThat(page.nextCursor()).isEqualTo(String.valueOf(page.items().get(2).id()));
    }

    @Test
    void nextCursorを辿ると重複や欠落なく全件を取得できる() {
        User author = fixtures.user();
        createPosts(author, 5);

        CursorPage<PostResponse> first = feed(author, null, 2);
        CursorPage<PostResponse> second = feed(author, Long.valueOf(first.nextCursor()), 2);
        CursorPage<PostResponse> third = feed(author, Long.valueOf(second.nextCursor()), 2);

        List<Long> collected = java.util.stream.Stream.of(first, second, third)
                .flatMap(p -> p.items().stream())
                .map(PostResponse::id)
                .toList();
        assertThat(collected).doesNotHaveDuplicates().hasSize(5);
        assertThat(third.nextCursor()).isNull();
    }

    @Test
    void limitは1以上50以下にクランプされる() {
        User author = fixtures.user();
        createPosts(author, 3);

        assertThat(feed(author, null, 0).items()).hasSize(1);
        assertThat(feed(author, null, -5).items()).hasSize(1);
        // 上限50なので、100000件を要求しても例外にならず最大50件で頭打ちになる
        assertThat(feed(author, null, 100000).items()).hasSizeLessThanOrEqualTo(50);
    }

    @Test
    void 不正なfeed値は例外になる() {
        User author = fixtures.user();

        assertThatThrownBy(() -> postService.listFeed(author.getId(), "bogus", null, null, 20, null))
                .isInstanceOf(InvalidFeedParameterException.class);
    }

    @Test
    void フォロー中フィードにはフォローしているユーザーの投稿だけが載る() {
        User viewer = fixtures.user();
        User followed = fixtures.user();
        User stranger = fixtures.user();
        Post followedPost = fixtures.post(followed);
        Post strangerPost = fixtures.post(stranger);
        followMapper.insertIgnoreDuplicate(viewer.getId(), followed.getId());

        List<Long> ids = postService.listFeed(viewer.getId(), "following", null, null, 50, null).items().stream()
                .map(PostResponse::id)
                .toList();

        assertThat(ids).contains(followedPost.getId()).doesNotContain(strangerPost.getId());
    }

    // --- 所有権チェック(403と404の切り分け) ---

    @Test
    void 他人の投稿を編集しようとすると403になる() {
        User author = fixtures.user();
        User other = fixtures.user();
        Post post = fixtures.post(author);

        assertThatThrownBy(() -> postService.updatePost(other.getId(), post.getId(), new UpdatePostRequest("改ざん")))
                .isInstanceOf(PostForbiddenException.class);
    }

    @Test
    void 他人の投稿を削除しようとすると403になる() {
        User author = fixtures.user();
        User other = fixtures.user();
        Post post = fixtures.post(author);

        assertThatThrownBy(() -> postService.deletePost(other.getId(), post.getId()))
                .isInstanceOf(PostForbiddenException.class);
    }

    /** 削除済みは「他人のものかどうか」より先に404にする。存在の有無を漏らさないため。 */
    @Test
    void 削除済み投稿の再削除は他人からでも404になる() {
        User author = fixtures.user();
        User other = fixtures.user();
        Post post = fixtures.post(author);
        postService.deletePost(author.getId(), post.getId());

        assertThatThrownBy(() -> postService.deletePost(author.getId(), post.getId()))
                .isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> postService.deletePost(other.getId(), post.getId()))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void 存在しない投稿の取得は404になる() {
        User user = fixtures.user();

        assertThatThrownBy(() -> postService.getPost(user.getId(), 999999999L))
                .isInstanceOf(PostNotFoundException.class);
    }

    // --- 本文のバリデーション(multipart経路) ---

    @Test
    void 本文が280文字ちょうどなら作成できる() {
        User author = fixtures.user();

        PostResponse created = postService.createPost(author.getId(), "a".repeat(280), List.of());

        assertThat(created.body()).hasSize(280);
    }

    @Test
    void 本文が281文字なら例外になる() {
        User author = fixtures.user();

        assertThatThrownBy(() -> postService.createPost(author.getId(), "a".repeat(281), List.of()))
                .isInstanceOf(InvalidPostBodyException.class);
    }

    @Test
    void 本文が空白のみなら例外になる() {
        User author = fixtures.user();

        assertThatThrownBy(() -> postService.createPost(author.getId(), "   ", List.of()))
                .isInstanceOf(InvalidPostBodyException.class);
    }

    private List<Post> createPosts(User author, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> fixtures.post(author, "投稿 " + i))
                .toList();
    }

    private CursorPage<PostResponse> feed(User viewer, Long cursor, int limit) {
        return postService.listFeed(viewer.getId(), "all", cursor, null, limit, viewer.getId());
    }
}
