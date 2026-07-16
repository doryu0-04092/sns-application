package com.snsapp.backend.service;

import com.snsapp.backend.entity.Comment;
import com.snsapp.backend.exception.CommentNotFoundException;
import com.snsapp.backend.exception.CommentSelfLikeException;
import com.snsapp.backend.mapper.CommentLikeMapper;
import com.snsapp.backend.mapper.CommentMapper;
import org.springframework.stereotype.Service;

@Service
public class CommentLikeService {

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
    }

    public void unlike(Long currentUserId, Long commentId) {
        commentLikeMapper.delete(commentId, currentUserId);
    }

    private Comment requireActiveComment(Long commentId) {
        Comment comment = commentMapper.findRawById(commentId);
        if (comment == null || comment.getDeletedAt() != null) {
            throw new CommentNotFoundException();
        }
        return comment;
    }
}
