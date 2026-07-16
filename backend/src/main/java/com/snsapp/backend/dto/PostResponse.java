package com.snsapp.backend.dto;

import java.time.LocalDateTime;

public record PostResponse(
        Long id,
        String body,
        Long authorId,
        String authorDisplayName,
        String authorAvatarUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long commentCount,
        long likeCount,
        boolean isMine,
        boolean isFollowing,
        boolean isLiked,
        boolean deleted) {
}
