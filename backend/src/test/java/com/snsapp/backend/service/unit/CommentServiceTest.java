package com.snsapp.backend.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.snsapp.backend.dto.CommentResponse;
import com.snsapp.backend.dto.CreateCommentRequest;
import com.snsapp.backend.dto.PostResponse;
import com.snsapp.backend.dto.UpdateCommentRequest;
import com.snsapp.backend.entity.Comment;
import com.snsapp.backend.entity.Post;
import com.snsapp.backend.exception.CommentForbiddenException;
import com.snsapp.backend.exception.CommentNotFoundException;
import com.snsapp.backend.exception.PostNotFoundException;
import com.snsapp.backend.mapper.CommentMapper;
import com.snsapp.backend.mapper.PostMapper;
import com.snsapp.backend.service.CommentService;
import com.snsapp.backend.storage.StorageService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link CommentService} の分岐網羅(docs/test-plan.md C-1〜C-11)。
 *
 * <p>要点は「ツームストーン(削除済みだが返信を持つ投稿)の扱いが読み取りと書き込みで異なる」こと。
 * 既存コメントの閲覧は許可されるが、新規コメントの追加は一律ブロックされる。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final Long POST_ID = 42L;
    private static final Long CURRENT_USER_ID = 1L;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private PostMapper postMapper;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private CommentService commentService;

    private static Post rawPost(Long id, Long authorId, LocalDateTime deletedAt) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(authorId);
        post.setBody("本文");
        post.setDeletedAt(deletedAt);
        return post;
    }

    private static Comment rawComment(Long id, Long postId, Long authorId, LocalDateTime deletedAt) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setPostId(postId);
        comment.setUserId(authorId);
        comment.setBody("コメント本文");
        comment.setDeletedAt(deletedAt);
        return comment;
    }

    private static CommentResponse response(Long id, String avatarKey) {
        return new CommentResponse(
                id, POST_ID, null, "コメント本文", 1L, "山田", avatarKey,
                LocalDateTime.now(), LocalDateTime.now(), 0, true, false, false, false);
    }

    private static PostResponse postResponse() {
        return new PostResponse(
                POST_ID, "本文", 5L, "投稿者", null,
                LocalDateTime.now(), LocalDateTime.now(), 0, 0, false, false, false, false);
    }

    // --- C-1, C-2: listComments ---

    @Test
    void 投稿のコメント一覧を取得できる() {
        when(postMapper.findById(POST_ID, CURRENT_USER_ID)).thenReturn(postResponse());
        when(commentMapper.findByPostId(POST_ID, CURRENT_USER_ID))
                .thenReturn(List.of(response(101L, null), response(102L, null)));

        assertThat(commentService.listComments(CURRENT_USER_ID, POST_ID)).hasSize(2);
    }

    @Test
    void 存在しない投稿のコメント一覧は取得できない() {
        when(postMapper.findById(999L, CURRENT_USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> commentService.listComments(CURRENT_USER_ID, 999L))
                .isInstanceOf(PostNotFoundException.class);
    }

    /**
     * 削除済みでも返信を持つ投稿(ツームストーン)は findById が行を返すため、
     * 既存コメントの閲覧はできる。新規追加を禁じる createComment とは判定が違う。
     */
    @Test
    void ツームストーン投稿でもコメント一覧は取得できる() {
        when(postMapper.findById(POST_ID, CURRENT_USER_ID)).thenReturn(postResponse());
        when(commentMapper.findByPostId(POST_ID, CURRENT_USER_ID)).thenReturn(List.of(response(101L, null)));

        assertThat(commentService.listComments(CURRENT_USER_ID, POST_ID)).hasSize(1);
    }

    @Test
    void コメント一覧のアイコンは署名付きURLに差し替わる() {
        when(postMapper.findById(POST_ID, CURRENT_USER_ID)).thenReturn(postResponse());
        when(commentMapper.findByPostId(POST_ID, CURRENT_USER_ID))
                .thenReturn(List.of(response(101L, "avatars/a.jpg")));
        when(storageService.viewUrl("avatars/a.jpg")).thenReturn("https://s3.example.com/signed");

        assertThat(commentService.listComments(CURRENT_USER_ID, POST_ID).get(0).authorAvatarUrl())
                .isEqualTo("https://s3.example.com/signed");
    }

    // --- C-3: createComment の投稿チェック ---

    @Test
    void 存在しない投稿にはコメントできない() {
        when(postMapper.findRawById(999L)).thenReturn(null);

        assertThatThrownBy(() -> commentService.createComment(
                        CURRENT_USER_ID, 999L, new CreateCommentRequest("コメント", null)))
                .isInstanceOf(PostNotFoundException.class);

        verify(commentMapper, never()).insert(any());
    }

    /** 閲覧はできるツームストーンでも、新規コメントの追加はできない。listComments との差が要点。 */
    @Test
    void 削除済み投稿には新規コメントできない() {
        when(postMapper.findRawById(POST_ID)).thenReturn(rawPost(POST_ID, 5L, LocalDateTime.now()));

        assertThatThrownBy(() -> commentService.createComment(
                        CURRENT_USER_ID, POST_ID, new CreateCommentRequest("コメント", null)))
                .isInstanceOf(PostNotFoundException.class);

        verify(commentMapper, never()).insert(any());
    }

    // --- C-4〜C-7: 返信先の検証 ---

    @Test
    void トップレベルのコメントを作成できる() {
        when(postMapper.findRawById(POST_ID)).thenReturn(rawPost(POST_ID, 5L, null));
        when(commentMapper.findById(any(), any())).thenReturn(response(101L, null));

        commentService.createComment(CURRENT_USER_ID, POST_ID, new CreateCommentRequest("コメント", null));

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).insert(captor.capture());
        assertThat(captor.getValue().getParentCommentId()).isNull();
        assertThat(captor.getValue().getPostId()).isEqualTo(POST_ID);
        assertThat(captor.getValue().getUserId()).isEqualTo(CURRENT_USER_ID);
    }

    @Test
    void 既存コメントへの返信を作成できる() {
        when(postMapper.findRawById(POST_ID)).thenReturn(rawPost(POST_ID, 5L, null));
        when(commentMapper.findRawById(100L)).thenReturn(rawComment(100L, POST_ID, 5L, null));
        when(commentMapper.findById(any(), any())).thenReturn(response(101L, null));

        commentService.createComment(CURRENT_USER_ID, POST_ID, new CreateCommentRequest("返信", 100L));

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).insert(captor.capture());
        assertThat(captor.getValue().getParentCommentId()).isEqualTo(100L);
    }

    @Test
    void 存在しないコメントには返信できない() {
        when(postMapper.findRawById(POST_ID)).thenReturn(rawPost(POST_ID, 5L, null));
        when(commentMapper.findRawById(999L)).thenReturn(null);

        assertThatThrownBy(() -> commentService.createComment(
                        CURRENT_USER_ID, POST_ID, new CreateCommentRequest("返信", 999L)))
                .isInstanceOf(CommentNotFoundException.class);

        verify(commentMapper, never()).insert(any());
    }

    /** 削除済みコメント(ツームストーン)へ返信を足せると、削除したはずの枝が伸び続けてしまう。 */
    @Test
    void 削除済みコメントには返信できない() {
        when(postMapper.findRawById(POST_ID)).thenReturn(rawPost(POST_ID, 5L, null));
        when(commentMapper.findRawById(100L)).thenReturn(rawComment(100L, POST_ID, 5L, LocalDateTime.now()));

        assertThatThrownBy(() -> commentService.createComment(
                        CURRENT_USER_ID, POST_ID, new CreateCommentRequest("返信", 100L)))
                .isInstanceOf(CommentNotFoundException.class);

        verify(commentMapper, never()).insert(any());
    }

    /** 別投稿のコメントIDを指定して、無関係なツリーにぶら下げられないこと。 */
    @Test
    void 別の投稿のコメントには返信できない() {
        when(postMapper.findRawById(POST_ID)).thenReturn(rawPost(POST_ID, 5L, null));
        when(commentMapper.findRawById(100L)).thenReturn(rawComment(100L, 999L, 5L, null));

        assertThatThrownBy(() -> commentService.createComment(
                        CURRENT_USER_ID, POST_ID, new CreateCommentRequest("返信", 100L)))
                .isInstanceOf(CommentNotFoundException.class);

        verify(commentMapper, never()).insert(any());
    }

    // --- C-8〜C-10: 所有権チェック(更新) ---

    @Test
    void 自分のコメントを編集できる() {
        when(commentMapper.findRawById(101L)).thenReturn(rawComment(101L, POST_ID, CURRENT_USER_ID, null));
        when(commentMapper.findById(101L, CURRENT_USER_ID)).thenReturn(response(101L, null));

        commentService.updateComment(CURRENT_USER_ID, 101L, new UpdateCommentRequest("編集後"));

        verify(commentMapper).updateBody(101L, "編集後");
    }

    @Test
    void 存在しないコメントは編集できない() {
        when(commentMapper.findRawById(999L)).thenReturn(null);

        assertThatThrownBy(() -> commentService.updateComment(
                        CURRENT_USER_ID, 999L, new UpdateCommentRequest("編集後")))
                .isInstanceOf(CommentNotFoundException.class);

        verify(commentMapper, never()).updateBody(anyLong(), any());
    }

    /** 削除済みは「存在しない」と同じ404にする。403との使い分けが要点。 */
    @Test
    void 削除済みのコメントは編集できない() {
        when(commentMapper.findRawById(101L))
                .thenReturn(rawComment(101L, POST_ID, CURRENT_USER_ID, LocalDateTime.now()));

        assertThatThrownBy(() -> commentService.updateComment(
                        CURRENT_USER_ID, 101L, new UpdateCommentRequest("編集後")))
                .isInstanceOf(CommentNotFoundException.class);
    }

    @Test
    void 他人のコメントは編集できない() {
        when(commentMapper.findRawById(101L)).thenReturn(rawComment(101L, POST_ID, 999L, null));

        assertThatThrownBy(() -> commentService.updateComment(
                        CURRENT_USER_ID, 101L, new UpdateCommentRequest("編集後")))
                .isInstanceOf(CommentForbiddenException.class);

        verify(commentMapper, never()).updateBody(anyLong(), any());
    }

    // --- C-8〜C-11: 所有権チェック(削除) ---

    /** 論理削除であり物理削除ではないこと。返信を持つコメントをツームストーンとして残すため。 */
    @Test
    void 自分のコメントを削除すると論理削除される() {
        when(commentMapper.findRawById(101L)).thenReturn(rawComment(101L, POST_ID, CURRENT_USER_ID, null));

        commentService.deleteComment(CURRENT_USER_ID, 101L);

        verify(commentMapper).softDelete(101L);
    }

    @Test
    void 存在しないコメントは削除できない() {
        when(commentMapper.findRawById(999L)).thenReturn(null);

        assertThatThrownBy(() -> commentService.deleteComment(CURRENT_USER_ID, 999L))
                .isInstanceOf(CommentNotFoundException.class);

        verify(commentMapper, never()).softDelete(anyLong());
    }

    /** 二重削除は成功扱いにせず404にする。 */
    @Test
    void 削除済みのコメントは再度削除できない() {
        when(commentMapper.findRawById(101L))
                .thenReturn(rawComment(101L, POST_ID, CURRENT_USER_ID, LocalDateTime.now()));

        assertThatThrownBy(() -> commentService.deleteComment(CURRENT_USER_ID, 101L))
                .isInstanceOf(CommentNotFoundException.class);

        verify(commentMapper, never()).softDelete(anyLong());
    }

    @Test
    void 他人のコメントは削除できない() {
        when(commentMapper.findRawById(101L)).thenReturn(rawComment(101L, POST_ID, 999L, null));

        assertThatThrownBy(() -> commentService.deleteComment(CURRENT_USER_ID, 101L))
                .isInstanceOf(CommentForbiddenException.class);

        verify(commentMapper, never()).softDelete(anyLong());
    }

    /**
     * 「存在しない/削除済み」は404、「他人のもの」は403。
     * 一律403にすると、他人のコメントIDを総当たりして存在有無を判定できてしまう一方、
     * 一律404にすると本人が権限エラーに気づけない。区別されていることを固定する。
     */
    @Test
    void 存在しないコメントと他人のコメントで異なる例外になる() {
        when(commentMapper.findRawById(999L)).thenReturn(null);
        when(commentMapper.findRawById(101L)).thenReturn(rawComment(101L, POST_ID, 999L, null));

        assertThatThrownBy(() -> commentService.deleteComment(CURRENT_USER_ID, 999L))
                .isInstanceOf(CommentNotFoundException.class);
        assertThatThrownBy(() -> commentService.deleteComment(CURRENT_USER_ID, 101L))
                .isInstanceOf(CommentForbiddenException.class);
    }
}
