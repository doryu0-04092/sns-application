package com.snsapp.backend.dto;

import com.snsapp.backend.common.ContentLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "コメント編集リクエスト")
public record UpdateCommentRequest(
        @Schema(description = "新しい本文（280文字以内）", example = "誤字を直しました")
        @NotBlank(message = ContentLimits.BODY_MESSAGE)
        @Size(max = ContentLimits.MAX_BODY_LENGTH, message = ContentLimits.BODY_MESSAGE)
        String body) {
}
