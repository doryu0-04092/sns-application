package com.snsapp.backend.controller;

import com.snsapp.backend.common.ApiResponse;
import com.snsapp.backend.config.OpenApiConfig;
import com.snsapp.backend.security.JwtAuthFilter;
import com.snsapp.backend.service.CommentLikeService;
import com.snsapp.backend.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * いいねの付け外し。
 *
 * <p>トグル1本ではなくPOST(作成)/DELETE(解除)の2本に分けている理由は
 * {@code docs/api-design.md} の「v1における簡略化」を参照。
 */
@RestController
@Tag(name = "いいね", description = "投稿・コメントへのいいね。付与はPOST、解除はDELETEで表す（トグルではない）")
public class LikeController {

    private final LikeService likeService;
    private final CommentLikeService commentLikeService;

    public LikeController(LikeService likeService, CommentLikeService commentLikeService) {
        this.likeService = likeService;
        this.commentLikeService = commentLikeService;
    }

    @Operation(
            summary = "投稿にいいねする",
            description = """
                    `data` は `null` を返す。更新後のいいね数が必要な場合は投稿を取得し直すこと。

                    **自分の投稿にはいいねできない** — 400 `POST_SELF_LIKE_NOT_ALLOWED`。
                    クライアントは `isMine` が `true` の投稿でボタンを出さないことが望ましい。
                    """)
    @PostMapping("/api/posts/{postId}/like")
    public ResponseEntity<ApiResponse<Void>> likePost(
            @Parameter(description = "投稿ID") @PathVariable Long postId, HttpServletRequest request) {
        likeService.like(currentUserId(request), postId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    @Operation(
            summary = "投稿のいいねを解除する",
            description = """
                    対象の存在を確認せずDELETEを実行するため、いいねしていない状態や
                    存在しない投稿IDに対して呼んでも**エラーにならず200を返す**（冪等）。
                    """)
    @OpenApiConfig.SkipNotFound
    @DeleteMapping("/api/posts/{postId}/like")
    public ResponseEntity<ApiResponse<Void>> unlikePost(
            @Parameter(description = "投稿ID") @PathVariable Long postId, HttpServletRequest request) {
        likeService.unlike(currentUserId(request), postId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    @Operation(
            summary = "コメントにいいねする",
            description = """
                    **自分のコメントにはいいねできない** — 400 `COMMENT_SELF_LIKE_NOT_ALLOWED`。

                    削除済みコメントへのいいねは404 `COMMENT_NOT_FOUND`。
                    """)
    @PostMapping("/api/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<Void>> likeComment(
            @Parameter(description = "コメントID") @PathVariable Long commentId, HttpServletRequest request) {
        commentLikeService.like(currentUserId(request), commentId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    @Operation(
            summary = "コメントのいいねを解除する",
            description = "投稿のいいね解除と同じく、存在確認をしないため冪等（常に200）。")
    @OpenApiConfig.SkipNotFound
    @DeleteMapping("/api/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<Void>> unlikeComment(
            @Parameter(description = "コメントID") @PathVariable Long commentId, HttpServletRequest request) {
        commentLikeService.unlike(currentUserId(request), commentId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE);
    }
}
