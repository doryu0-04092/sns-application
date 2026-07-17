package com.snsapp.backend.dto;

public record UserSummaryResponse(
        Long id,
        Long userId,
        String displayName,
        String avatarUrl,
        boolean isFollowing) {
}
