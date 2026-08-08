package com.snsapp.backend.controller;

import com.snsapp.backend.common.ApiResponse;
import com.snsapp.backend.config.OpenApiConfig;
import com.snsapp.backend.dto.CreatePostRequest;
import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.PostResponse;
import com.snsapp.backend.dto.UpdatePostRequest;
import com.snsapp.backend.security.JwtAuthFilter;
import com.snsapp.backend.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "タイムライン/投稿", description = "投稿の取得・作成・編集・削除。一覧はカーソルベースページネーション")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @Operation(
            summary = "タイムライン取得",
            description = """
                    投稿を新しい順に返す。`cursor` と `sinceId` は用途が逆なので注意すること。

                    - **過去へ遡る（無限スクロール）**: 前回応答の `nextCursor` を `cursor` に渡す。
                      `nextCursor` が `null` なら末尾
                    - **新着を取りに行く（ポーリング）**: 手元で最も新しい投稿のIDを `sinceId` に渡すと、
                      それより新しい投稿だけが返る

                    `feed` に `all` / `following` 以外を渡すと400 `INVALID_FEED`。

                    レスポンス内の画像URLとアイコンURLは署名付きで**有効期限24時間**。
                    """)
    @GetMapping("/api/posts")
    public ResponseEntity<ApiResponse<CursorPage<PostResponse>>> list(
            @Parameter(description = "`all`=全体タイムライン / `following`=フォロー中のみ")
            @RequestParam(defaultValue = "all") String feed,
            @Parameter(description = "この投稿IDより古い投稿を取得する。前回応答の `nextCursor` を渡す")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "この投稿IDより新しい投稿だけを取得する（新着ポーリング用）")
            @RequestParam(required = false) Long sinceId,
            @Parameter(description = "取得件数。1〜50に丸められる（範囲外を指定してもエラーにはならない）")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "指定したユーザーの投稿だけに絞る（プロフィール画面の投稿一覧用）")
            @RequestParam(required = false) Long authorId,
            HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        CursorPage<PostResponse> page = postService.listFeed(currentUserId, feed, cursor, sinceId, limit, authorId);
        return ResponseEntity.ok(ApiResponse.of(page));
    }

    @Operation(
            summary = "投稿作成",
            description = """
                    本文は280文字以内。画像を添付する場合は、先に
                    `POST /api/uploads/presign` で採番した `key` を `imageKeys` に最大4件渡す。

                    サーバーは受け取ったキーの実体をS3上で検証（サイズ・形式）してから登録するため、
                    アップロードが完了していないキーを渡すとエラーになる。

                    成功時のステータスは **201 Created**。

                    エラー: 5MB超は400 `IMAGE_TOO_LARGE`、非対応形式は400 `INVALID_IMAGE_TYPE`、
                    5枚以上は400 `TOO_MANY_IMAGES`。
                    """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "作成成功。作成された投稿を返す")
    @PostMapping("/api/posts")
    public ResponseEntity<ApiResponse<PostResponse>> create(
            @Valid @RequestBody CreatePostRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        PostResponse post = postService.createPost(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(post));
    }

    @Operation(
            summary = "投稿詳細取得",
            description = "いいね数・コメント数、および現在のユーザーから見た `isMine` / `isLiked` / `isFollowing` を含む。")
    @GetMapping("/api/posts/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> get(
            @Parameter(description = "投稿ID") @PathVariable Long postId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        PostResponse post = postService.getPost(currentUserId, postId);
        return ResponseEntity.ok(ApiResponse.of(post));
    }

    @Operation(
            summary = "投稿の編集",
            description = """
                    **本文のみ**編集できる。画像の追加・削除・並び替えはv1のスコープ外で、
                    そのためのAPIは存在しない（`docs/api-design.md` の「v1における簡略化」を参照）。

                    編集できるのは自分の投稿のみ。
                    """)
    @OpenApiConfig.ErrorResponse(
            status = "403", code = "POST_FORBIDDEN", message = "自分の投稿のみ編集・削除できます",
            description = "他人の投稿を編集しようとした")
    @PatchMapping("/api/posts/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> update(
            @Parameter(description = "投稿ID") @PathVariable Long postId,
            @Valid @RequestBody UpdatePostRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        PostResponse post = postService.updatePost(currentUserId, postId, request);
        return ResponseEntity.ok(ApiResponse.of(post));
    }

    @Operation(
            summary = "投稿の削除",
            description = """
                    **論理削除**。削除後の見え方はコメントの有無で変わるので注意すること。

                    - **コメントが無い投稿** — 一覧からも詳細からも消える。
                      以後 `GET /api/posts/{postId}` は404 `POST_NOT_FOUND`
                    - **コメントが付いている投稿** — ツリーの接続点として一覧・詳細に残る。
                      `deleted: true` / `body: null` / `imageUrls: []` になり、
                      `commentCount` は残存するコメント数を返す。この状態への新規コメントは404で拒否される

                    つまりクライアントは、削除後に404になる場合と `deleted: true` が返る場合の
                    **両方を扱う**必要がある。

                    削除できるのは自分の投稿のみ。
                    """)
    @OpenApiConfig.ErrorResponse(
            status = "403", code = "POST_FORBIDDEN", message = "自分の投稿のみ編集・削除できます",
            description = "他人の投稿を削除しようとした")
    @DeleteMapping("/api/posts/{postId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "投稿ID") @PathVariable Long postId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        postService.deletePost(currentUserId, postId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE);
    }
}
