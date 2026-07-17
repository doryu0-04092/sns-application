package com.snsapp.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PostResponse(
        Long id,
        String body,
        Long authorId,
        String authorDisplayName,
        String authorAvatarUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long commentCount,
        long likeCount,
        boolean isMine,
        boolean isFollowing,
        boolean isLiked,
        boolean deleted,
        List<String> imageUrls) {

    // MyBatis(PostMapper.xmlのPostResponseMap)はSQLの列から直接この13引数コンストラクタを解決する。
    // imageUrlsはpost_imagesテーブルの別クエリでPostService側がバッチ取得して差し込むため、
    // SQL経由では埋められない(空リストで補う)。
    public PostResponse(
            Long id,
            String body,
            Long authorId,
            String authorDisplayName,
            String authorAvatarUrl,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long commentCount,
            long likeCount,
            boolean isMine,
            boolean isFollowing,
            boolean isLiked,
            boolean deleted) {
        this(id, body, authorId, authorDisplayName, authorAvatarUrl, createdAt, updatedAt,
                commentCount, likeCount, isMine, isFollowing, isLiked, deleted, List.of());
    }
}
