package com.snsapp.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snsapp.backend.dto.CommentResponse;
import com.snsapp.backend.dto.CreateCommentRequest;
import com.snsapp.backend.dto.PostResponse;
import com.snsapp.backend.entity.Comment;
import com.snsapp.backend.entity.Post;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.exception.CommentNotFoundException;
import com.snsapp.backend.exception.PostNotFoundException;
import com.snsapp.backend.support.AbstractIntegrationTest;
import com.snsapp.backend.support.TestFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 論理削除された投稿・コメントの可視性ルール(ツームストーン)の検証。
 *
 * <p>「削除済みでも返信を保持している限りツリーの接続点として残す」という判断は
 * {@code PostMapper.xml}に4箇所、{@code CommentMapper.xml}に1箇所ある
 * {@code deleted_at IS NULL OR EXISTS(...)}という述語に散らばっており、
 * 1箇所だけ書き換えても他の一覧が矛盾した状態になりうる。ここで挙動を明示的に固定する。
 */
@Transactional
class TombstoneVisibilityTest extends AbstractIntegrationTest {

    @Autowired
    private PostService postService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private TestFixtures fixtures;

    @Test
    void 返信のない削除済み投稿は一覧からも詳細からも消える() {
        User author = fixtures.user();
        Post post = fixtures.post(author);

        postService.deletePost(author.getId(), post.getId());

        assertThatThrownBy(() -> postService.getPost(author.getId(), post.getId()))
                .isInstanceOf(PostNotFoundException.class);
        assertThat(feedIds(author)).doesNotContain(post.getId());
    }

    @Test
    void コメントを持つ削除済み投稿はツームストーンとして残る() {
        User author = fixtures.user();
        User commenter = fixtures.user();
        Post post = fixtures.post(author);
        fixtures.comment(post, commenter);

        postService.deletePost(author.getId(), post.getId());

        PostResponse found = postService.getPost(author.getId(), post.getId());
        assertThat(found.deleted()).isTrue();
        assertThat(found.commentCount()).isEqualTo(1);
        assertThat(feedIds(author)).contains(post.getId());
    }

    @Test
    void ツームストーン化した投稿は本文と画像を返さない() {
        User author = fixtures.user();
        User commenter = fixtures.user();
        Post post = fixtures.postWithImage(author, "http://localhost:8080/uploads/posts/dummy.png");
        fixtures.comment(post, commenter);

        assertThat(postService.getPost(author.getId(), post.getId()).imageUrls()).hasSize(1);

        postService.deletePost(author.getId(), post.getId());

        PostResponse found = postService.getPost(author.getId(), post.getId());
        assertThat(found.body()).isNull();
        assertThat(found.imageUrls()).isEmpty();
    }

    @Test
    void ツームストーン化した投稿には新規コメントを追加できない() {
        User author = fixtures.user();
        User commenter = fixtures.user();
        Post post = fixtures.post(author);
        fixtures.comment(post, commenter);
        postService.deletePost(author.getId(), post.getId());

        assertThatThrownBy(() -> commentService.createComment(
                        commenter.getId(), post.getId(), new CreateCommentRequest("追記", null)))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void ツームストーン化した投稿でもコメント一覧は取得できる() {
        User author = fixtures.user();
        User commenter = fixtures.user();
        Post post = fixtures.post(author);
        fixtures.comment(post, commenter);
        postService.deletePost(author.getId(), post.getId());

        assertThat(commentService.listComments(author.getId(), post.getId())).hasSize(1);
    }

    @Test
    void 返信のない削除済みコメントは一覧から消える() {
        User author = fixtures.user();
        User commenter = fixtures.user();
        Post post = fixtures.post(author);
        Comment comment = fixtures.comment(post, commenter);

        commentService.deleteComment(commenter.getId(), comment.getId());

        assertThat(commentService.listComments(author.getId(), post.getId())).isEmpty();
    }

    @Test
    void 返信を持つ削除済みコメントはツリーの接続点として残る() {
        User author = fixtures.user();
        User commenter = fixtures.user();
        User replier = fixtures.user();
        Post post = fixtures.post(author);
        Comment parent = fixtures.comment(post, commenter);
        Comment reply = fixtures.comment(post, replier, parent);

        commentService.deleteComment(commenter.getId(), parent.getId());

        List<CommentResponse> comments = commentService.listComments(author.getId(), post.getId());
        assertThat(comments).hasSize(2);
        CommentResponse tombstone = comments.stream()
                .filter(c -> c.id().equals(parent.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(tombstone.deleted()).isTrue();
        assertThat(tombstone.body()).isNull();
        assertThat(comments).anyMatch(c -> c.id().equals(reply.getId()) && !c.deleted());
    }

    @Test
    void 削除済みコメントには返信できない() {
        User author = fixtures.user();
        User commenter = fixtures.user();
        User replier = fixtures.user();
        Post post = fixtures.post(author);
        Comment parent = fixtures.comment(post, commenter);
        fixtures.comment(post, replier, parent);
        commentService.deleteComment(commenter.getId(), parent.getId());

        assertThatThrownBy(() -> commentService.createComment(
                        replier.getId(), post.getId(), new CreateCommentRequest("追記", parent.getId())))
                .isInstanceOf(CommentNotFoundException.class);
    }

    @Test
    void 削除済み投稿のコメント数は返信を持つコメントのみ数える() {
        User author = fixtures.user();
        User commenter = fixtures.user();
        User replier = fixtures.user();
        Post post = fixtures.post(author);
        Comment withReply = fixtures.comment(post, commenter);
        fixtures.comment(post, replier, withReply);
        Comment withoutReply = fixtures.comment(post, commenter);

        // 返信を持つコメントと持たないコメントを両方削除する
        commentService.deleteComment(commenter.getId(), withReply.getId());
        commentService.deleteComment(commenter.getId(), withoutReply.getId());

        // 残るのは「ツームストーン化した親」と「その返信」の2件
        assertThat(postService.getPost(author.getId(), post.getId()).commentCount()).isEqualTo(2);
    }

    private List<Long> feedIds(User viewer) {
        return postService.listFeed(viewer.getId(), "all", null, null, 50, null).items().stream()
                .map(PostResponse::id)
                .toList();
    }
}
