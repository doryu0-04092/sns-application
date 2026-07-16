package com.snsapp.backend.controller;

import com.snsapp.backend.common.ApiResponse;
import com.snsapp.backend.security.JwtAuthFilter;
import com.snsapp.backend.service.FollowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/api/users/{userId}/follow")
    public ResponseEntity<ApiResponse<Void>> follow(@PathVariable Long userId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        followService.follow(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    @DeleteMapping("/api/users/{userId}/follow")
    public ResponseEntity<ApiResponse<Void>> unfollow(@PathVariable Long userId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        followService.unfollow(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE);
    }
}
