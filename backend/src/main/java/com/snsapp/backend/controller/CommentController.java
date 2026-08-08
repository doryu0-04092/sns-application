package com.snsapp.backend.controller;

import com.snsapp.backend.common.ApiResponse;
import com.snsapp.backend.config.OpenApiConfig;
import com.snsapp.backend.dto.CommentResponse;
import com.snsapp.backend.dto.CreateCommentRequest;
import com.snsapp.backend.dto.UpdateCommentRequest;
import com.snsapp.backend.security.JwtAuthFilter;
import com.snsapp.backend.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "コメント", description = "投稿へのコメントと、コメントへの返信（ネスト）")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(
            summary = "コメント一覧取得",
            description = """
                    **平坦な配列**を返す。返信も含めて全件が同じ階層に並び、
                    親子関係は各要素の `parentCommentId` で表現される
                    （`null` ならトップレベルのコメント）。
                    **ツリーへの組み立てはクライアント側の責務**。

                    ページネーションは無く、常に全件を返す（v1の割り切り）。

                    削除済みコメントの扱い:

                    - 返信を持たない削除済みコメントは**一覧に現れない**
                    - 返信を持つ削除済みコメントはツリーの接続点として残り、
                      `deleted: true` / `body: null` で返る

                    投稿自体が削除済み（ツームストーン）でも、コメント一覧は取得できる。
                    """)
    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> list(
            @Parameter(description = "投稿ID") @PathVariable Long postId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        List<CommentResponse> comments = commentService.listComments(currentUserId, postId);
        return ResponseEntity.ok(ApiResponse.of(comments));
    }

    @Operation(
            summary = "コメント投稿",
            description = """
                    `parentCommentId` を指定すると、そのコメントへの**返信**になる。
                    省略（`null`）するとトップレベルのコメントになる。

                    成功時のステータスは **201 Created**。

                    削除済みの投稿・コメントには追加できない
                    （それぞれ404 `POST_NOT_FOUND` / `COMMENT_NOT_FOUND`）。
                    """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "作成成功。作成されたコメントを返す")
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> create(
            @Parameter(description = "コメント先の投稿ID") @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        CommentResponse comment = commentService.createComment(currentUserId, postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(comment));
    }

    @Operation(summary = "コメントの編集", description = "本文を編集する。編集できるのは自分のコメントのみ。")
    @OpenApiConfig.ErrorResponse(
            status = "403", code = "COMMENT_FORBIDDEN", message = "自分のコメントのみ編集・削除できます",
            description = "他人のコメントを編集しようとした")
    @PatchMapping("/api/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> update(
            @Parameter(description = "コメントID") @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        CommentResponse comment = commentService.updateComment(currentUserId, commentId, request);
        return ResponseEntity.ok(ApiResponse.of(comment));
    }

    @Operation(
            summary = "コメントの削除",
            description = """
                    **論理削除**。投稿の削除と同じく、返信の有無で見え方が変わる。

                    - **返信が無いコメント** — 一覧から消える
                    - **返信を持つコメント** — ツリーの接続点として残り、
                      `deleted: true` / `body: null` で返る。このコメントへの新規返信は404で拒否される

                    削除できるのは自分のコメントのみ。
                    """)
    @OpenApiConfig.ErrorResponse(
            status = "403", code = "COMMENT_FORBIDDEN", message = "自分のコメントのみ編集・削除できます",
            description = "他人のコメントを削除しようとした")
    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "コメントID") @PathVariable Long commentId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        commentService.deleteComment(currentUserId, commentId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE);
    }
}
