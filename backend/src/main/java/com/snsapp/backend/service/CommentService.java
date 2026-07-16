package com.snsapp.backend.service;

import com.snsapp.backend.dto.CommentResponse;
import com.snsapp.backend.dto.CreateCommentRequest;
import com.snsapp.backend.dto.UpdateCommentRequest;
import com.snsapp.backend.entity.Comment;
import com.snsapp.backend.entity.Post;
import com.snsapp.backend.exception.CommentForbiddenException;
import com.snsapp.backend.exception.CommentNotFoundException;
import com.snsapp.backend.exception.PostNotFoundException;
import com.snsapp.backend.mapper.CommentMapper;
import com.snsapp.backend.mapper.PostMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;

    public CommentService(CommentMapper commentMapper, PostMapper postMapper) {
        this.commentMapper = commentMapper;
        this.postMapper = postMapper;
    }

    public List<CommentResponse> listComments(Long currentUserId, Long postId) {
        // 削除済みでも返信を保持している投稿(ツームストーン)はコメント一覧を取得できる。
        if (postMapper.findById(postId, currentUserId) == null) {
            throw new PostNotFoundException();
        }
        return commentMapper.findByPostId(postId, currentUserId);
    }

    public CommentResponse createComment(Long currentUserId, Long postId, CreateCommentRequest request) {
        requireActivePost(postId);

        if (request.parentCommentId() != null) {
            // 返信先コメントが存在しない/既に削除(ツームストーン化)済み/別投稿のものなら 404。
            // 削除済みコメントへの新規返信を防ぐことで、ツームストーンへの追記を防止している。
            Comment parent = commentMapper.findRawById(request.parentCommentId());
            if (parent == null || parent.getDeletedAt() != null || !parent.getPostId().equals(postId)) {
                throw new CommentNotFoundException();
            }
        }

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(currentUserId);
        comment.setParentCommentId(request.parentCommentId());
        comment.setBody(request.body());
        commentMapper.insert(comment);
        return commentMapper.findById(comment.getId(), currentUserId);
    }

    public CommentResponse updateComment(Long currentUserId, Long commentId, UpdateCommentRequest request) {
        Comment raw = requireOwnedComment(currentUserId, commentId);
        commentMapper.updateBody(raw.getId(), request.body());
        return commentMapper.findById(commentId, currentUserId);
    }

    public void deleteComment(Long currentUserId, Long commentId) {
        Comment raw = requireOwnedComment(currentUserId, commentId);
        commentMapper.softDelete(raw.getId());
    }

    // コメント新規作成の前段チェック。投稿が削除済み(ツームストーン含む)なら常に404にし、
    // 新規コメントの追加だけは一律ブロックする(既存コメントの閲覧は listComments 側で別途許可)。
    private void requireActivePost(Long postId) {
        Post post = postMapper.findRawById(postId);
        if (post == null || post.getDeletedAt() != null) {
            throw new PostNotFoundException();
        }
    }

    // 更新・削除の前段チェック。存在しない/既に削除済み -> 404、他人のコメント -> 403 で区別する。
    private Comment requireOwnedComment(Long currentUserId, Long commentId) {
        Comment raw = commentMapper.findRawById(commentId);
        if (raw == null || raw.getDeletedAt() != null) {
            throw new CommentNotFoundException();
        }
        if (!raw.getUserId().equals(currentUserId)) {
            throw new CommentForbiddenException();
        }
        return raw;
    }
}
