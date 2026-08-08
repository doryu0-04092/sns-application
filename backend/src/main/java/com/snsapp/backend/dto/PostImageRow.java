package com.snsapp.backend.dto;

// PostImageMapper#findByPostIds のバッチ取得結果の1行分(post_idごとのS3キー)。
// PostService側でpost_idごとにグルーピングし、署名付きURLへ変換してPostResponse#imageUrlsへ差し込む。
public record PostImageRow(Long postId, String imageKey) {
}
