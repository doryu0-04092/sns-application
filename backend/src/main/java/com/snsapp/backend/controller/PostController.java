package com.snsapp.backend.controller;

import com.snsapp.backend.common.ApiResponse;
import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.PostResponse;
import com.snsapp.backend.dto.UpdatePostRequest;
import com.snsapp.backend.security.JwtAuthFilter;
import com.snsapp.backend.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/api/posts")
    public ResponseEntity<ApiResponse<CursorPage<PostResponse>>> list(
            @RequestParam(defaultValue = "all") String feed,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Long sinceId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long authorId,
            HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        CursorPage<PostResponse> page = postService.listFeed(currentUserId, feed, cursor, sinceId, limit, authorId);
        return ResponseEntity.ok(ApiResponse.of(page));
    }

    @PostMapping(value = "/api/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PostResponse>> create(
            @RequestParam String body,
            @RequestParam(required = false) List<MultipartFile> images,
            HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        PostResponse post = postService.createPost(currentUserId, body, images == null ? List.of() : images);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(post));
    }

    @GetMapping("/api/posts/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> get(@PathVariable Long postId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        PostResponse post = postService.getPost(currentUserId, postId);
        return ResponseEntity.ok(ApiResponse.of(post));
    }

    @PatchMapping("/api/posts/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> update(
            @PathVariable Long postId, @Valid @RequestBody UpdatePostRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        PostResponse post = postService.updatePost(currentUserId, postId, request);
        return ResponseEntity.ok(ApiResponse.of(post));
    }

    @DeleteMapping("/api/posts/{postId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long postId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        postService.deletePost(currentUserId, postId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE);
    }
}
