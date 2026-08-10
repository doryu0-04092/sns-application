package com.snsapp.backend.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.snsapp.backend.entity.Comment;
import com.snsapp.backend.exception.CommentNotFoundException;
import com.snsapp.backend.exception.CommentSelfLikeException;
import com.snsapp.backend.mapper.CommentLikeMapper;
import com.snsapp.backend.mapper.CommentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CommentLikeService {

    // LikeServiceと同じ理由でDEBUG。
    private static final Logger log = LoggerFactory.getLogger(CommentLikeService.class);

    private final CommentLikeMapper commentLikeMapper;
    private final CommentMapper commentMapper;

    public CommentLikeService(CommentLikeMapper commentLikeMapper, CommentMapper commentMapper) {
        this.commentLikeMapper = commentLikeMapper;
        this.commentMapper = commentMapper;
    }

    public void like(Long currentUserId, Long commentId) {
        Comment comment = requireActiveComment(commentId);
        if (comment.getUserId().equals(currentUserId)) {
            throw new CommentSelfLikeException();
        }
        commentLikeMapper.insertIgnoreDuplicate(commentId, currentUserId);
        log.debug("comment liked {}", kv("commentId", commentId));
    }

    public void unlike(Long currentUserId, Long commentId) {
        commentLikeMapper.delete(commentId, currentUserId);
        log.debug("comment unliked {}", kv("commentId", commentId));
    }

    private Comment requireActiveComment(Long commentId) {
        Comment comment = commentMapper.findRawById(commentId);
        if (comment == null || comment.getDeletedAt() != null) {
            throw new CommentNotFoundException();
        }
        return comment;
    }
}
