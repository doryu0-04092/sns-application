package com.snsapp.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "ログインリクエスト")
public record LoginRequest(
        @Schema(description = "登録済みのメールアドレス", example = "user@example.com")
        @NotBlank(message = "メールアドレスを入力してください")
        @Email(message = "メールアドレスの形式が正しくありません")
        String email,

        @Schema(description = "パスワード。メールアドレスとパスワードのどちらが誤っていても"
                + "同じ401 `INVALID_CREDENTIALS` を返す（どちらが存在するかを推測させないため）",
                example = "password1234")
        @NotBlank(message = "パスワードを入力してください")
        String password) {
}
