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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "ユーザー/プロフィール", description = "ユーザーの検索・プロフィール取得・自分のプロフィール編集・フォロー関係の一覧")
public class UserController {

    private final UserService userService;
    private final FollowService followService;

    public UserController(UserService userService, FollowService followService) {
        this.userService = userService;
        this.followService = followService;
    }

    // query 省略時は全ユーザーを新着順で返す(F-15の「一覧」。S-07は一覧と検索の両方を担う画面)。
    @Operation(
            summary = "ユーザーの一覧・検索",
            description = """
                    `query` を指定すると表示名の部分一致（大文字小文字を区別しない）で絞り込み、
                    省略すると全ユーザーを新着順で返す。

                    **自分自身は常に結果から除外される**（フォロー相手を探す画面向けのため）。
                    自分の情報が必要な場合は `GET /api/auth/me` を使うこと。
                    """)
    @GetMapping("/api/users")
    public ResponseEntity<ApiResponse<CursorPage<UserSummaryResponse>>> search(
            @Parameter(description = "表示名の部分一致で絞り込む。省略時は全ユーザー")
            @RequestParam(required = false) String query,
            @Parameter(description = "前回応答の `nextCursor` を渡すと続きを取得する")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "取得件数。1〜50に丸められる（範囲外を指定してもエラーにはならない）")
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        CursorPage<UserSummaryResponse> page = userService.searchUsers(currentUserId, query, cursor, limit);
        return ResponseEntity.ok(ApiResponse.of(page));
    }

    @Operation(
            summary = "プロフィール取得",
            description = """
                    表示名・自己紹介・アイコンに加え、フォロー数／フォロワー数と、
                    現在のユーザーがこのユーザーをフォローしているかを返す。

                    このユーザーの投稿一覧は含まれない。
                    `GET /api/posts?authorId={userId}` で別途取得すること。
                    """)
    @GetMapping("/api/users/{userId}")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
            @Parameter(description = "ユーザーID") @PathVariable Long userId, HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        ProfileResponse profile = userService.getProfile(currentUserId, userId);
        return ResponseEntity.ok(ApiResponse.of(profile));
    }

    @Operation(
            summary = "自分のプロフィール編集",
            description = """
                    表示名・自己紹介・アイコンを更新する。対象は常にログイン中のユーザーで、
                    他人のプロフィールを編集する手段は無い（そのためパスにユーザーIDを取らない）。

                    アイコンを変更する場合は、先に `POST /api/uploads/presign` で採番した `key` を
                    `avatarKey` に渡す。`avatarKey` を省略（`null`）すると**アイコンは変更されない**。
                    新しいアイコンを設定すると、古いアイコンはS3から削除される。

                    **注意**: メソッドはPATCHだが `displayName` は必須。
                    アイコンだけを変更する場合も、現在の表示名を一緒に送る必要がある。
                    """)
    @PatchMapping("/api/users/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(
            @Valid @RequestBody UpdateProfileRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        UserResponse user = userService.updateProfile(currentUserId, request);
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    @Operation(
            summary = "フォロワー一覧",
            description = """
                    指定ユーザーを**フォローしている**ユーザーの一覧。

                    **注意**: 各要素の `id` はフォロー関係のレコードIDであり、ユーザーIDではない。
                    プロフィールへの遷移などには必ず `userId` を使うこと
                    （`id` はページネーションの `cursor` に渡す値）。
                    """)
    @GetMapping("/api/users/{userId}/followers")
    public ResponseEntity<ApiResponse<CursorPage<UserSummaryResponse>>> followers(
            @Parameter(description = "対象ユーザーID") @PathVariable Long userId,
            @Parameter(description = "前回応答の `nextCursor` を渡すと続きを取得する")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "取得件数。1〜50に丸められる（範囲外を指定してもエラーにはならない）")
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        Long currentUserId = currentUserId(request);
        CursorPage<UserSummaryResponse> page = followService.listFollowers(currentUserId, userId, cursor, limit);
        return ResponseEntity.ok(ApiResponse.of(page));
    }

    @Operation(
            summary = "フォロー中一覧",
            description = """
                    指定ユーザーが**フォローしている**ユーザーの一覧。

                    フォロワー一覧と同じく、各要素の `id` はフォロー関係のレコードIDで、
                    ユーザーIDは `userId` のほう。
                    """)
    @GetMapping("/api/users/{userId}/following")
    public ResponseEntity<ApiResponse<CursorPage<UserSummaryResponse>>> following(
            @Parameter(description = "対象ユーザーID") @PathVariable Long userId,
            @Parameter(description = "前回応答の `nextCursor` を渡すと続きを取得する")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "取得件数。1〜50に丸められる（範囲外を指定してもエラーにはならない）")
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
