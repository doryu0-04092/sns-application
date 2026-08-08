package com.snsapp.backend.service;

import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.UserSummaryResponse;
import com.snsapp.backend.exception.SelfFollowException;
import com.snsapp.backend.exception.UserNotFoundException;
import com.snsapp.backend.mapper.FollowMapper;
import com.snsapp.backend.mapper.UserMapper;
import com.snsapp.backend.storage.StorageService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FollowService {

    private static final int MAX_LIST_LIMIT = 50;

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final StorageService storageService;

    public FollowService(FollowMapper followMapper, UserMapper userMapper, StorageService storageService) {
        this.followMapper = followMapper;
        this.userMapper = userMapper;
        this.storageService = storageService;
    }

    public void follow(Long currentUserId, Long targetUserId) {
        if (targetUserId.equals(currentUserId)) {
            throw new SelfFollowException();
        }
        if (userMapper.findById(targetUserId) == null) {
            throw new UserNotFoundException();
        }
        followMapper.insertIgnoreDuplicate(currentUserId, targetUserId);
    }

    public void unfollow(Long currentUserId, Long targetUserId) {
        followMapper.delete(currentUserId, targetUserId);
    }

    public CursorPage<UserSummaryResponse> listFollowers(Long currentUserId, Long targetUserId, Long cursor, int limit) {
        requireUserExists(targetUserId);
        int clampedLimit = clampLimit(limit);
        List<UserSummaryResponse> rows = followMapper.findFollowers(targetUserId, currentUserId, cursor, clampedLimit + 1);
        return toPage(rows, clampedLimit);
    }

    public CursorPage<UserSummaryResponse> listFollowing(Long currentUserId, Long targetUserId, Long cursor, int limit) {
        requireUserExists(targetUserId);
        int clampedLimit = clampLimit(limit);
        List<UserSummaryResponse> rows = followMapper.findFollowing(targetUserId, currentUserId, cursor, clampedLimit + 1);
        return toPage(rows, clampedLimit);
    }

    private void requireUserExists(Long userId) {
        if (userMapper.findById(userId) == null) {
            throw new UserNotFoundException();
        }
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIST_LIMIT));
    }

    private CursorPage<UserSummaryResponse> toPage(List<UserSummaryResponse> rows, int limit) {
        boolean hasMore = rows.size() > limit;
        List<UserSummaryResponse> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).id()) : null;

        // MyBatisが入れたのはS3キー。表示できるURLへ差し替える。
        List<UserSummaryResponse> items = page.stream()
                .map(row -> row.withAvatarUrl(storageService.presignedGetUrl(row.avatarUrl())))
                .toList();
        return new CursorPage<>(items, nextCursor);
    }
}
