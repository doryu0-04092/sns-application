package com.snsapp.backend.dto;

// PostImageMapper#findByPostIds のバッチ取得結果の1行分(post_idごとの画像URL)。
// PostService側でpost_idごとにグルーピングし、PostResponse#imageUrlsへ差し込む。
public record PostImageRow(Long postId, String imageUrl) {
}
