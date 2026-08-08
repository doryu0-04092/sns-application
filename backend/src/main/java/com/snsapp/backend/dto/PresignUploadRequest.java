package com.snsapp.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * アップロード用の署名付きURL発行リクエスト。
 *
 * @param contentTypes アップロードしたい画像のContent-Typeを、ファイルの枚数分並べたもの。
 *                     署名にContent-Typeを含めるため、1件ずつ指定する必要がある
 */
public record PresignUploadRequest(
        @NotEmpty(message = "アップロードするファイルを指定してください")
        @Size(max = 4, message = "画像は4枚まで添付できます")
        List<String> contentTypes) {
}
