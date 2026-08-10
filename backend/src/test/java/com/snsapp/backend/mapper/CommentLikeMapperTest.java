package com.snsapp.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.snsapp.backend.dto.CommentResponse;
import com.snsapp.backend.entity.Comment;
import com.snsapp.backend.entity.Post;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.support.AbstractIntegrationTest;
import com.snsapp.backend.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * CommentLikeMapper.xml のSQLの検証。
 *
 * <p>いいねの結果は {@code comment_likes} を直接読むのではなく、
 * CommentMapper 側の相関サブクエリ（{@code like_count} / {@code is_liked}）に現れる。
 * 書き込みと読み出しが別のSQLに分かれているため、<strong>両方を通して初めて成立を確認できる</strong>。
 *
 * <p>解除（{@code delete}）はServiceの結合テストから一度も呼ばれておらず、未検証だった。
 */
@Transactional
class CommentLikeMapperTest extends AbstractIntegrationTest {

    @Autowired
    private CommentLikeMapper commentLikeMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private TestFixtures fixtures;

    private CommentResponse readBack(Comment comment, User viewer) {
        return commentMapper.findById(comment.getId(), viewer.getId());
    }

    @Test
    void いいねするとカウントが増える() {
        User author = fixtures.user();
        User liker = fixtures.user();
        Post post = fixtures.post(author);
        Comment comment = fixtures.comment(post, author);

        commentLikeMapper.insertIgnoreDuplicate(comment.getId(), liker.getId());

        assertThat(readBack(comment, liker).likeCount()).isEqualTo(1);
    }

    // ON CONFLICT DO NOTHING が効いていないと、連打で一意制約違反(500)になる。
    @Test
    void 二重いいねでもカウントは1のままになる() {
        User author = fixtures.user();
        User liker = fixtures.user();
        Comment comment = fixtures.comment(fixtures.post(author), author);

        commentLikeMapper.insertIgnoreDuplicate(comment.getId(), liker.getId());
        assertThatCode(() -> commentLikeMapper.insertIgnoreDuplicate(comment.getId(), liker.getId()))
                .doesNotThrowAnyException();

        assertThat(readBack(comment, liker).likeCount()).isEqualTo(1);
    }

    @Test
    void いいねを解除するとカウントが戻る() {
        User author = fixtures.user();
        User liker = fixtures.user();
        Comment comment = fixtures.comment(fixtures.post(author), author);
        commentLikeMapper.insertIgnoreDuplicate(comment.getId(), liker.getId());

        commentLikeMapper.delete(comment.getId(), liker.getId());

        assertThat(readBack(comment, liker).likeCount()).isZero();
    }

    // Serviceは存在確認をせずDELETEを投げる(冪等な設計)。0件削除でエラーにならないことを固定する。
    @Test
    void いいねしていない状態で解除しても何も起きない() {
        User author = fixtures.user();
        User other = fixtures.user();
        Comment comment = fixtures.comment(fixtures.post(author), author);

        assertThatCode(() -> commentLikeMapper.delete(comment.getId(), other.getId()))
                .doesNotThrowAnyException();
    }

    // WHERE句からuser_idが抜けると、1人の解除で全員のいいねが消える。
    @Test
    void 解除は自分のいいねだけを消す() {
        User author = fixtures.user();
        User leaving = fixtures.user();
        User staying = fixtures.user();
        Comment comment = fixtures.comment(fixtures.post(author), author);
        commentLikeMapper.insertIgnoreDuplicate(comment.getId(), leaving.getId());
        commentLikeMapper.insertIgnoreDuplicate(comment.getId(), staying.getId());

        commentLikeMapper.delete(comment.getId(), leaving.getId());

        assertThat(readBack(comment, staying).likeCount()).isEqualTo(1);
    }

    // is_liked は閲覧者ごとの判定。誤ると他人のいいねが自分のものとして表示される。
    @Test
    void いいねの有無は閲覧者ごとに判定される() {
        User author = fixtures.user();
        User liker = fixtures.user();
        User viewer = fixtures.user();
        Comment comment = fixtures.comment(fixtures.post(author), author);
        commentLikeMapper.insertIgnoreDuplicate(comment.getId(), liker.getId());

        assertThat(readBack(comment, liker).isLiked()).isTrue();
        assertThat(readBack(comment, viewer).isLiked()).isFalse();
    }
}
