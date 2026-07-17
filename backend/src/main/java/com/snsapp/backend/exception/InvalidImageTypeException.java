package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// アップロードされたファイルのcontent-typeが対応形式(jpeg/png/webp/gif)以外の場合にスロー。
// LocalDiskStorageService#store から使用。
public class InvalidImageTypeException extends ApiException {

    public InvalidImageTypeException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_TYPE", "対応していない画像形式です(jpeg/png/webp/gifのみ)");
    }
}
