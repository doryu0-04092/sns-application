package com.snsapp.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "新規登録リクエスト")
public record SignupRequest(
        @Schema(description = "メールアドレス。既に登録済みの場合は400 `EMAIL_ALREADY_EXISTS`",
                example = "user@example.com")
        @NotBlank(message = "メールアドレスを入力してください")
        @Email(message = "メールアドレスの形式が正しくありません")
        String email,

        // 上限72はBCryptが受け付けるバイト長の上限に由来する。
        @Schema(description = "パスワード（8〜72文字）。上限72はBCryptが扱えるバイト長に由来する",
                example = "password1234")
        @NotBlank(message = "パスワードを入力してください")
        @Size(min = 8, max = 72, message = "パスワードは8文字以上72文字以内で入力してください")
        String password,

        // 文言はプロフィール編集(UpdateProfileRequest)側と揃えている。
        @Schema(description = "表示名（1〜100文字）。他ユーザーに表示される名前", example = "山田太郎")
        @NotBlank(message = "表示名は1文字以上100文字以内で入力してください")
        @Size(max = 100, message = "表示名は1文字以上100文字以内で入力してください")
        String displayName) {
}
