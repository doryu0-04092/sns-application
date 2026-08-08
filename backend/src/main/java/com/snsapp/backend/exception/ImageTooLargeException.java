package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// ブラウザがS3へ直接アップロードした画像が上限(5MB)を超えていた場合にスロー。
// 署名付きPUTではサイズを事前に強制できないため、S3StorageService#promote の
// HeadObject による事後検証で検出する。
public class ImageTooLargeException extends ApiException {

    public ImageTooLargeException() {
        super(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE", "画像は1枚あたり5MBまでです");
    }
}
