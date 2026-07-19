package com.snsapp.backend.service;

import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.ProfileResponse;
import com.snsapp.backend.dto.UserResponse;
import com.snsapp.backend.dto.UserSummaryResponse;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.exception.InvalidBioException;
import com.snsapp.backend.exception.InvalidDisplayNameException;
import com.snsapp.backend.exception.UserNotFoundException;
import com.snsapp.backend.mapper.UserMapper;
import com.snsapp.backend.storage.StorageService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {

    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final int MAX_BIO_LENGTH = 500;
    private static final int MAX_SEARCH_LIMIT = 50;

    private final UserMapper userMapper;
    private final StorageService storageService;

    public UserService(UserMapper userMapper, StorageService storageService) {
        this.userMapper = userMapper;
        this.storageService = storageService;
    }

    public ProfileResponse getProfile(Long currentUserId, Long targetUserId) {
        ProfileResponse profile = userMapper.findProfileById(targetUserId, currentUserId);
        if (profile == null) {
            throw new UserNotFoundException();
        }
        return profile;
    }

    public UserResponse updateProfile(Long currentUserId, String displayName, String bio, MultipartFile avatar) {
        if (displayName == null || displayName.isBlank() || displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new InvalidDisplayNameException();
        }
        if (bio != null && bio.length() > MAX_BIO_LENGTH) {
            throw new InvalidBioException();
        }

        User existing = userMapper.findById(currentUserId);
        String avatarUrl = existing.getAvatarUrl();
        if (avatar != null && !avatar.isEmpty()) {
            String newAvatarUrl = storageService.store(avatar, "avatars");
            storageService.delete(avatarUrl);
            avatarUrl = newAvatarUrl;
        }

        userMapper.update(currentUserId, displayName, bio, avatarUrl);
        return UserResponse.from(userMapper.findById(currentUserId));
    }

    /**
     * ユーザーを一覧・検索する(F-15)。
     *
     * @param query 表示名の部分一致条件。null または空文字なら絞り込まず全ユーザーを新着順で返す
     */
    public CursorPage<UserSummaryResponse> searchUsers(Long currentUserId, String query, Long cursor, int limit) {
        String normalizedQuery = (query == null || query.isBlank()) ? null : query.trim();

        int clampedLimit = Math.max(1, Math.min(limit, MAX_SEARCH_LIMIT));
        List<UserSummaryResponse> rows =
                userMapper.searchByDisplayName(currentUserId, normalizedQuery, cursor, clampedLimit + 1);

        boolean hasMore = rows.size() > clampedLimit;
        List<UserSummaryResponse> items = hasMore ? rows.subList(0, clampedLimit) : rows;
        String nextCursor = hasMore ? String.valueOf(items.get(items.size() - 1).id()) : null;
        return new CursorPage<>(items, nextCursor);
    }
}
