package com.snsapp.backend.controller;

import com.snsapp.backend.common.ApiResponse;
import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.ProfileResponse;
import com.snsapp.backend.dto.UserResponse;
import com.snsapp.backend.dto.UserSummaryResponse;
import com.snsapp.backend.security.JwtAuthFilter;
import com.snsapp.backend.service.FollowService;
import com.snsapp.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UserController {

    private final UserService userService;
    private final FollowService followService;

    public UserController(UserService userService, FollowService followService) {
        this.userService = userService;
        this.followService = followService;
    }

    // query 省略時は全ユーザーを新着順で返す(F-15の「一覧」。S-07は一覧と検索の両方を担う画面)。
    @GetMapping("/api/users")
    public ResponseEntity<ApiResponse<CursorPage<UserSummaryResponse>>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        CursorPage<UserSummaryResponse> page = userService.searchUsers(currentUserId, query, cursor, limit);
        return ResponseEntity.ok(ApiResponse.of(page));
    }

    @GetMapping("/api/users/{userId}")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(@PathVariable Long userId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        ProfileResponse profile = userService.getProfile(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.of(profile));
    }

    @PatchMapping(value = "/api/users/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(
            @RequestParam String displayName,
            @RequestParam(required = false) String bio,
            @RequestParam(required = false) MultipartFile avatar,
            HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        UserResponse user = userService.updateProfile(currentUserId, displayName, bio, avatar);
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    @GetMapping("/api/users/{userId}/followers")
    public ResponseEntity<ApiResponse<CursorPage<UserSummaryResponse>>> followers(
            @PathVariable Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        CursorPage<UserSummaryResponse> page = followService.listFollowers(currentUserId, userId, cursor, limit);
        return ResponseEntity.ok(ApiResponse.of(page));
    }

    @GetMapping("/api/users/{userId}/following")
    public ResponseEntity<ApiResponse<CursorPage<UserSummaryResponse>>> following(
            @PathVariable Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        CursorPage<UserSummaryResponse> page = followService.listFollowing(currentUserId, userId, cursor, limit);
        return ResponseEntity.ok(ApiResponse.of(page));
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE);
    }
}
