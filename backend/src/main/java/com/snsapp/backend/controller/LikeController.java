package com.snsapp.backend.controller;

import com.snsapp.backend.common.ApiResponse;
import com.snsapp.backend.security.JwtAuthFilter;
import com.snsapp.backend.service.CommentLikeService;
import com.snsapp.backend.service.LikeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LikeController {

    private final LikeService likeService;
    private final CommentLikeService commentLikeService;

    public LikeController(LikeService likeService, CommentLikeService commentLikeService) {
        this.likeService = likeService;
        this.commentLikeService = commentLikeService;
    }

    @PostMapping("/api/posts/{postId}/like")
    public ResponseEntity<ApiResponse<Void>> likePost(@PathVariable Long postId, HttpServletRequest request) {
        likeService.like(currentUserId(request), postId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    @DeleteMapping("/api/posts/{postId}/like")
    public ResponseEntity<ApiResponse<Void>> unlikePost(@PathVariable Long postId, HttpServletRequest request) {
        likeService.unlike(currentUserId(request), postId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    @PostMapping("/api/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<Void>> likeComment(@PathVariable Long commentId, HttpServletRequest request) {
        commentLikeService.like(currentUserId(request), commentId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    @DeleteMapping("/api/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<Void>> unlikeComment(@PathVariable Long commentId, HttpServletRequest request) {
        commentLikeService.unlike(currentUserId(request), commentId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE);
    }
}
