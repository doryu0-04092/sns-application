package com.snsapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snsapp.backend.entity.Comment;
import com.snsapp.backend.entity.Post;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.exception.CommentNotFoundException;
import com.snsapp.backend.exception.CommentSelfLikeException;
import com.snsapp.backend.exception.PostNotFoundException;
import com.snsapp.backend.exception.PostSelfLikeException;
import com.snsapp.backend.support.AbstractIntegrationTest;
import com.snsapp.backend.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 投稿・コメントへのいいねの検証(F-10)。
 *
 * <p>自己いいねの禁止はDB制約ではなくアプリケーション層でのみ担保されており
 * (docs/er-diagram.md 補足)、重複防止はDBのUNIQUE制約に依存している。
 * どちらもここで挙動を固定する。
 */
@Transactional
class LikeServiceTest extends AbstractIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private CommentLikeService commentLikeService;

    @Autowired
    private PostService postService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private TestFixtures fixtures;

    @Test
    void 他人の投稿にいいねできる() {
        User author = fixtures.user();
        User liker = fixtures.user();
        Post post = fixtures.post(author);

        likeService.like(liker.getId(), post.getId());

        assertThat(postService.getPost(liker.getId(), post.getId()).likeCount()).isEqualTo(1);
        assertThat(postService.getPost(liker.getId(), post.getId()).isLiked()).isTrue();
    }

    @Test
    void 自分の投稿にはいいねできない() {
        User author = fixtures.user();
        Post post = fixtures.post(author);

        assertThatThrownBy(() -> likeService.like(author.getId(), post.getId()))
                .isInstanceOf(PostSelfLikeException.class);
    }

    /** 二重送信やリトライで重複行が増えないこと(UNIQUE制約 + insertIgnoreDuplicate)。 */
    @Test
    void 同じ投稿に二重にいいねしてもカウントは1のまま() {
        User author = fixtures.user();
        User liker = fixtures.user();
        Post post = fixtures.post(author);

        likeService.like(liker.getId(), post.getId());
        likeService.like(liker.getId(), post.getId());

        assertThat(postService.getPost(liker.getId(), post.getId()).likeCount()).isEqualTo(1);
    }

    @Test
    void いいね解除するとカウントが戻る() {
        User author = fixtures.user();
        User liker = fixtures.user();
        Post post = fixtures.post(author);
        likeService.like(liker.getId(), post.getId());

        likeService.unlike(liker.getId(), post.getId());

        assertThat(postService.getPost(liker.getId(), post.getId()).likeCount()).isZero();
        assertThat(postService.getPost(liker.getId(), post.getId()).isLiked()).isFalse();
    }

    @Test
    void いいねしていない投稿の解除は例外にならない() {
        User author = fixtures.user();
        User liker = fixtures.user();
        Post post = fixtures.post(author);

        likeService.unlike(liker.getId(), post.getId());

        assertThat(postService.getPost(liker.getId(), post.getId()).likeCount()).isZero();
    }

    @Test
    void 削除済み投稿にはいいねできない() {
        User author = fixtures.user();
        User liker = fixtures.user();
        Post post = fixtures.post(author);
        postService.deletePost(author.getId(), post.getId());

        assertThatThrownBy(() -> likeService.like(liker.getId(), post.getId()))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void 他人のコメントにいいねできる() {
        User author = fixtures.user();
        User commenter = fixtures.user();
        Post post = fixtures.post(author);
        Comment comment = fixtures.comment(post, commenter);

        commentLikeService.like(author.getId(), comment.getId());

        assertThat(commentService.listComments(author.getId(), post.getId()).get(0).likeCount()).isEqualTo(1);
    }

    @Test
    void 自分のコメントにはいいねできない() {
        User author = fixtures.user();
        User commenter = fixtures.user();
        Post post = fixtures.post(author);
        Comment comment = fixtures.comment(post, commenter);

        assertThatThrownBy(() -> commentLikeService.like(commenter.getId(), comment.getId()))
                .isInstanceOf(CommentSelfLikeException.class);
    }

    @Test
    void 削除済みコメントにはいいねできない() {
        User author = fixtures.user();
        User commenter = fixtures.user();
        Post post = fixtures.post(author);
        Comment comment = fixtures.comment(post, commenter);
        commentService.deleteComment(commenter.getId(), comment.getId());

        assertThatThrownBy(() -> commentLikeService.like(author.getId(), comment.getId()))
                .isInstanceOf(CommentNotFoundException.class);
    }
}
