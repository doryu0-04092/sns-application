package com.snsapp.backend.dto;

import com.snsapp.backend.common.ContentLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "投稿編集リクエスト。編集できるのは本文のみで、画像は変更できない")
public record UpdatePostRequest(
        @Schema(description = "新しい本文（280文字以内）", example = "内容を修正しました")
        @NotBlank(message = ContentLimits.BODY_MESSAGE)
        @Size(max = ContentLimits.MAX_BODY_LENGTH, message = ContentLimits.BODY_MESSAGE)
        String body) {
}
