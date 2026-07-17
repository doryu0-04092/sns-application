package com.snsapp.backend.controller;

import com.snsapp.backend.common.ApiResponse;
import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.ProfileResponse;
import com.snsapp.backend.dto.UpdateProfileRequest;
import com.snsapp.backend.dto.UserResponse;
import com.snsapp.backend.dto.UserSummaryResponse;
import com.snsapp.backend.security.JwtAuthFilter;
import com.snsapp.backend.service.FollowService;
import com.snsapp.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;
    private final FollowService followService;

    public UserController(UserService userService, FollowService followService) {
        this.userService = userService;
        this.followService = followService;
    }

    @GetMapping("/api/users/{userId}")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(@PathVariable Long userId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        ProfileResponse profile = userService.getProfile(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.of(profile));
    }

    @PatchMapping("/api/users/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(
            @Valid @RequestBody UpdateProfileRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        UserResponse user = userService.updateProfile(currentUserId, request);
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
