package com.snsapp.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 投稿1件分。
 *
 * @param authorAvatarUrl 表示用の署名付きURL。
 *                        <b>MyBatisが組み立てた直後だけはS3キーが入っている</b>点に注意
 * @param imageUrls       添付画像の署名付きURL。SQLでは埋められず、
 *                        {@link com.snsapp.backend.service.PostService} が別クエリで取得して差し込む
 */
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

    /** 画像URLと投稿者アイコンURLだけを差し替えた新しいインスタンスを返す(キー→署名付きURLの変換用)。 */
    public PostResponse withImageUrls(String newAuthorAvatarUrl, List<String> newImageUrls) {
        return new PostResponse(id, body, authorId, authorDisplayName, newAuthorAvatarUrl, createdAt, updatedAt,
                commentCount, likeCount, isMine, isFollowing, isLiked, deleted, newImageUrls);
    }
}
