package com.snsapp.backend.dto;

import java.time.LocalDateTime;

/**
 * コメント1件分。
 *
 * @param authorAvatarUrl 表示用の署名付きURL。
 *                        <b>MyBatisが組み立てた直後だけはS3キーが入っている</b>点に注意。
 *                        {@link com.snsapp.backend.service.CommentService} が
 *                        {@link #withAuthorAvatarUrl} で署名付きURLへ差し替えてから返す
 */
public record CommentResponse(
        Long id,
        Long postId,
        Long parentCommentId,
        String body,
        Long authorId,
        String authorDisplayName,
        String authorAvatarUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long likeCount,
        boolean isMine,
        boolean isFollowing,
        boolean isLiked,
        boolean deleted) {

    /** authorAvatarUrl だけを差し替えた新しいインスタンスを返す(キー→署名付きURLの変換用)。 */
    public CommentResponse withAuthorAvatarUrl(String newAuthorAvatarUrl) {
        return new CommentResponse(
                id, postId, parentCommentId, body, authorId, authorDisplayName, newAuthorAvatarUrl,
                createdAt, updatedAt, likeCount, isMine, isFollowing, isLiked, deleted);
    }
}
