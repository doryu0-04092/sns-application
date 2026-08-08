package com.snsapp.backend.controller;

import com.snsapp.backend.common.ApiResponse;
import com.snsapp.backend.config.OpenApiConfig;
import com.snsapp.backend.security.JwtAuthFilter;
import com.snsapp.backend.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "フォロー", description = "ユーザーのフォロー・解除。フォロー中の一覧取得は「ユーザー/プロフィール」にある")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @Operation(
            summary = "ユーザーをフォローする",
            description = """
                    既にフォロー済みの状態で呼んでもエラーにならない（冪等）。

                    **自分自身はフォローできない** — 400 `SELF_FOLLOW_NOT_ALLOWED`。
                    存在しないユーザーは404 `USER_NOT_FOUND`。

                    フォロー後は `GET /api/posts?feed=following` に対象ユーザーの投稿が含まれるようになる。
                    """)
    @PostMapping("/api/users/{userId}/follow")
    public ResponseEntity<ApiResponse<Void>> follow(
            @Parameter(description = "フォロー対象のユーザーID") @PathVariable Long userId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        followService.follow(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    @Operation(
            summary = "フォローを解除する",
            description = """
                    対象の存在を確認せずDELETEを実行するため、フォローしていない状態や
                    存在しないユーザーIDに対して呼んでも**エラーにならず200を返す**（冪等）。
                    """)
    @OpenApiConfig.SkipNotFound
    @DeleteMapping("/api/users/{userId}/follow")
    public ResponseEntity<ApiResponse<Void>> unfollow(
            @Parameter(description = "フォロー解除対象のユーザーID") @PathVariable Long userId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        followService.unfollow(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE);
    }
}
