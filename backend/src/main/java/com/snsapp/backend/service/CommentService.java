package com.snsapp.backend.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

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
import com.snsapp.backend.storage.StorageService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CommentService {

    // 状態を変える操作だけをINFOで残す。識別子のみを記録し、コメント本文は載せない。
    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final StorageService storageService;

    public CommentService(CommentMapper commentMapper, PostMapper postMapper, StorageService storageService) {
        this.commentMapper = commentMapper;
        this.postMapper = postMapper;
        this.storageService = storageService;
    }

    public List<CommentResponse> listComments(Long currentUserId, Long postId) {
        // 削除済みでも返信を保持している投稿(ツームストーン)はコメント一覧を取得できる。
        if (postMapper.findById(postId, currentUserId) == null) {
            throw new PostNotFoundException();
        }
        return commentMapper.findByPostId(postId, currentUserId).stream()
                .map(this::withAvatarUrl)
                .toList();
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
        log.info("comment created {} {} {}",
                kv("commentId", comment.getId()), kv("postId", postId),
                kv("isReply", request.parentCommentId() != null));
        return withAvatarUrl(commentMapper.findById(comment.getId(), currentUserId));
    }

    public CommentResponse updateComment(Long currentUserId, Long commentId, UpdateCommentRequest request) {
        Comment raw = requireOwnedComment(currentUserId, commentId);
        commentMapper.updateBody(raw.getId(), request.body());
        log.info("comment updated {}", kv("commentId", raw.getId()));
        return withAvatarUrl(commentMapper.findById(commentId, currentUserId));
    }

    public void deleteComment(Long currentUserId, Long commentId) {
        Comment raw = requireOwnedComment(currentUserId, commentId);
        commentMapper.softDelete(raw.getId());
        log.info("comment deleted {}", kv("commentId", raw.getId()));
    }

    // MyBatisが入れたのは投稿者アイコンのS3キー。表示できる署名付きURLへ差し替える。
    private CommentResponse withAvatarUrl(CommentResponse comment) {
        return comment.withAuthorAvatarUrl(storageService.presignedGetUrl(comment.authorAvatarUrl()));
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
