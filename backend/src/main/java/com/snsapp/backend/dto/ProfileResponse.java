package com.snsapp.backend.dto;

public record ProfileResponse(
        Long id,
        String displayName,
        String bio,
        String avatarUrl,
        long followerCount,
        long followingCount,
        boolean isMine,
        boolean isFollowing) {
}
