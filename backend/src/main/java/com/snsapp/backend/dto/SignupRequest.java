package com.snsapp.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "メールアドレスを入力してください")
        @Email(message = "メールアドレスの形式が正しくありません")
        String email,

        // 上限72はBCryptが受け付けるバイト長の上限に由来する。
        @NotBlank(message = "パスワードを入力してください")
        @Size(min = 8, max = 72, message = "パスワードは8文字以上72文字以内で入力してください")
        String password,

        // 文言はUserService(multipart経路)のInvalidDisplayNameExceptionと揃えている。
        @NotBlank(message = "表示名は1文字以上100文字以内で入力してください")
        @Size(max = 100, message = "表示名は1文字以上100文字以内で入力してください")
        String displayName) {
}
