package com.snsapp.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * コメント1件分。
 *
 * @param authorAvatarUrl 表示用の署名付きURL。
 *                        <b>MyBatisが組み立てた直後だけはS3キーが入っている</b>点に注意。
 *                        {@link com.snsapp.backend.service.CommentService} が
 *                        {@link #withAuthorAvatarUrl} で署名付きURLへ差し替えてから返す
 */
@Schema(description = """
        コメント1件。一覧は平坦な配列で返るため、ツリー構造は parentCommentId をたどって
        クライアント側で組み立てる""")
public record CommentResponse(
        @Schema(description = "コメントID", example = "101")
        Long id,

        @Schema(description = "コメント先の投稿ID", example = "42")
        Long postId,

        @Schema(description = """
                返信先のコメントID。**null ならトップレベルのコメント**。
                この値をたどってツリーを組み立てる""",
                example = "100", types = {"integer", "null"})
        Long parentCommentId,

        @Schema(description = "本文。**削除済みの場合は null**", example = "参考になりました",
                types = {"string", "null"})
        String body,

        @Schema(description = "コメント投稿者のユーザーID", example = "7")
        Long authorId,

        @Schema(description = "コメント投稿者の表示名", example = "山田太郎")
        String authorDisplayName,

        @Schema(description = "コメント投稿者アイコンの署名付きURL（有効期限24時間）。未設定なら null",
                types = {"string", "null"})
        String authorAvatarUrl,

        @Schema(description = "投稿日時", example = "2026-08-08T10:20:00")
        LocalDateTime createdAt,

        @Schema(description = "最終更新日時。未編集なら createdAt と同じ", example = "2026-08-08T10:20:00")
        LocalDateTime updatedAt,

        @Schema(description = "いいね数", example = "2")
        long likeCount,

        @Schema(description = "このコメントが自分のものか。true なら編集・削除でき、いいねはできない")
        boolean isMine,

        @Schema(description = "コメント投稿者を自分がフォローしているか")
        boolean isFollowing,

        @Schema(description = "自分がこのコメントにいいね済みか")
        boolean isLiked,

        @Schema(description = """
                論理削除済みか。true の場合 body は null。
                返信を持つコメントだけがこの状態で残り、返信の無いコメントは一覧から消える""")
        boolean deleted) {

    /** authorAvatarUrl だけを差し替えた新しいインスタンスを返す(キー→署名付きURLの変換用)。 */
    public CommentResponse withAuthorAvatarUrl(String newAuthorAvatarUrl) {
        return new CommentResponse(
                id, postId, parentCommentId, body, authorId, authorDisplayName, newAuthorAvatarUrl,
                createdAt, updatedAt, likeCount, isMine, isFollowing, isLiked, deleted);
    }
}
