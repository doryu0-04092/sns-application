package com.snsapp.backend.service;

import com.snsapp.backend.dto.ProfileResponse;
import com.snsapp.backend.dto.UpdateProfileRequest;
import com.snsapp.backend.dto.UserResponse;
import com.snsapp.backend.exception.UserNotFoundException;
import com.snsapp.backend.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public ProfileResponse getProfile(Long currentUserId, Long targetUserId) {
        ProfileResponse profile = userMapper.findProfileById(targetUserId, currentUserId);
        if (profile == null) {
            throw new UserNotFoundException();
        }
        return profile;
    }

    public UserResponse updateProfile(Long currentUserId, UpdateProfileRequest request) {
        userMapper.update(currentUserId, request.displayName(), request.bio(), request.avatarUrl());
        return UserResponse.from(userMapper.findById(currentUserId));
    }
}
