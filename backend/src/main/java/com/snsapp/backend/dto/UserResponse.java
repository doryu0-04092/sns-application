package com.snsapp.backend.dto;

import com.snsapp.backend.entity.User;

public record UserResponse(
        Long id,
        String email,
        String displayName,
        String bio,
        String avatarUrl) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                user.getAvatarUrl());
    }
}
