package com.snsapp.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "投稿1件。削除済み(deleted=true)の場合は本文と画像が伏せられる")
public record PostResponse(
        @Schema(description = "投稿ID。タイムラインの cursor / sinceId にはこの値を渡す", example = "42")
        Long id,

        @Schema(description = "本文（280文字以内）。**削除済みの場合は null**",
                example = "今日から新しいプロジェクトが始まりました", types = {"string", "null"})
        String body,

        @Schema(description = "投稿者のユーザーID", example = "7")
        Long authorId,

        @Schema(description = "投稿者の表示名", example = "山田太郎")
        String authorDisplayName,

        @Schema(description = "投稿者アイコンの署名付きURL（有効期限24時間）。未設定なら null",
                types = {"string", "null"})
        String authorAvatarUrl,

        @Schema(description = "投稿日時（UTC。末尾の Z がタイムゾーンを表す）",
                example = "2026-08-08T10:15:30.000000Z")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = JsonFormats.UTC_DATE_TIME)
        LocalDateTime createdAt,

        @Schema(description = "最終更新日時（UTC）。未編集なら createdAt と同じ",
                example = "2026-08-08T10:15:30.000000Z")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = JsonFormats.UTC_DATE_TIME)
        LocalDateTime updatedAt,

        @Schema(description = "コメント数（削除済み投稿では残存しているコメントのみ数える）", example = "3")
        long commentCount,

        @Schema(description = "いいね数", example = "12")
        long likeCount,

        @Schema(description = "この投稿が自分のものか。true なら編集・削除でき、いいねはできない")
        boolean isMine,

        @Schema(description = "投稿者を自分がフォローしているか")
        boolean isFollowing,

        @Schema(description = "自分がこの投稿にいいね済みか")
        boolean isLiked,

        @Schema(description = """
                論理削除済みか。true の場合 body は null、imageUrls は空になる。
                コメントが付いている投稿だけがこの状態で残り、コメントの無い投稿は一覧から消える""")
        boolean deleted,

        @Schema(description = """
                添付画像の署名付きURL（最大4件・**有効期限24時間**）。
                レスポンスを長期キャッシュするとURLだけ先に失効して画像が壊れるため、
                URL自体を永続化しないこと""")
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
