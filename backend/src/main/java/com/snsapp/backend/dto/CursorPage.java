package com.snsapp.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * カーソルベースページネーションの1ページ分。
 *
 * @param nextCursor 次ページ取得用のカーソル。null なら末尾
 */
@Schema(description = "カーソルベースページネーションの1ページ分")
public record CursorPage<T>(
        @Schema(description = "このページの要素")
        List<T> items,

        @Schema(description = """
                次ページを取得するためのカーソル。**null なら末尾に到達している**。
                この値をそのまま次回リクエストの `cursor` パラメータに渡す
                （数値だが文字列として返る点に注意）""",
                example = "37")
        String nextCursor) {
}
