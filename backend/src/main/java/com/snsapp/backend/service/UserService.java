package com.snsapp.backend.service;

import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.ProfileResponse;
import com.snsapp.backend.dto.UpdateProfileRequest;
import com.snsapp.backend.dto.UserResponse;
import com.snsapp.backend.dto.UserSummaryResponse;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.exception.UserNotFoundException;
import com.snsapp.backend.mapper.UserMapper;
import com.snsapp.backend.storage.StorageService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserService {

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
        // MyBatisが入れたのはS3キー。表示できるURLへ差し替える。
        return profile.withAvatarUrl(storageService.presignedGetUrl(profile.avatarUrl()));
    }

    /**
     * 自分のプロフィールを更新する(F-14)。
     *
     * <p>アイコン画像はブラウザからS3へ直接アップロード済みで、ここには {@code avatarKey} だけが渡る。
     * 実体の検証は {@link StorageService#promote} が行う。
     */
    public UserResponse updateProfile(Long currentUserId, UpdateProfileRequest request) {
        User existing = userMapper.findById(currentUserId);
        String avatarKey = existing.getAvatarKey();

        if (request.avatarKey() != null && !request.avatarKey().isBlank()) {
            String newAvatarKey = storageService.promote(request.avatarKey(), "avatars");
            storageService.delete(avatarKey);
            avatarKey = newAvatarKey;
        }

        userMapper.update(currentUserId, request.displayName(), request.bio(), avatarKey);
        User updated = userMapper.findById(currentUserId);
        return UserResponse.from(updated, storageService.presignedGetUrl(updated.getAvatarKey()));
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
        List<UserSummaryResponse> page = hasMore ? rows.subList(0, clampedLimit) : rows;
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).id()) : null;

        List<UserSummaryResponse> items = page.stream()
                .map(row -> row.withAvatarUrl(storageService.presignedGetUrl(row.avatarUrl())))
                .toList();
        return new CursorPage<>(items, nextCursor);
    }
}
