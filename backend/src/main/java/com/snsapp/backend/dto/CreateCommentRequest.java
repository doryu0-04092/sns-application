package com.snsapp.backend.dto;

import com.snsapp.backend.common.ContentLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "コメント投稿リクエスト")
public record CreateCommentRequest(
        @Schema(description = "本文（280文字以内）", example = "参考になりました")
        @NotBlank(message = ContentLimits.BODY_MESSAGE)
        @Size(max = ContentLimits.MAX_BODY_LENGTH, message = ContentLimits.BODY_MESSAGE)
        String body,

        @Schema(description = """
                返信先のコメントID。指定するとそのコメントへの返信になり、
                **省略（null）するとトップレベルのコメント**になる。
                削除済みコメントを指定すると404 `COMMENT_NOT_FOUND`""",
                example = "100", types = {"integer", "null"})
        Long parentCommentId) {
}
