package com.snsapp.backend.controller;

import com.snsapp.backend.common.ApiResponse;
import com.snsapp.backend.dto.CommentResponse;
import com.snsapp.backend.dto.CreateCommentRequest;
import com.snsapp.backend.dto.UpdateCommentRequest;
import com.snsapp.backend.security.JwtAuthFilter;
import com.snsapp.backend.service.CommentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> list(
            @PathVariable Long postId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        List<CommentResponse> comments = commentService.listComments(currentUserId, postId);
        return ResponseEntity.ok(ApiResponse.of(comments));
    }

    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> create(
            @PathVariable Long postId, @Valid @RequestBody CreateCommentRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        CommentResponse comment = commentService.createComment(currentUserId, postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(comment));
    }

    @PatchMapping("/api/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> update(
            @PathVariable Long commentId, @Valid @RequestBody UpdateCommentRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        CommentResponse comment = commentService.updateComment(currentUserId, commentId, request);
        return ResponseEntity.ok(ApiResponse.of(comment));
    }

    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long commentId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        commentService.deleteComment(currentUserId, commentId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE);
    }
}
