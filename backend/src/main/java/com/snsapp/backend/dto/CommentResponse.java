package com.snsapp.backend.dto;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long postId,
        Long parentCommentId,
        String body,
        Long authorId,
        String authorDisplayName,
        String authorAvatarUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long likeCount,
        boolean isMine,
        boolean isFollowing,
        boolean isLiked,
        boolean deleted) {
}
