package com.snsapp.backend.controller;

import com.snsapp.backend.common.ApiResponse;
import com.snsapp.backend.dto.PresignUploadRequest;
import com.snsapp.backend.storage.PresignedUpload;
import com.snsapp.backend.storage.StorageService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 画像アップロード用の署名付きURLを発行する。
 *
 * <p>画像本体はこのバックエンドを経由せず、ブラウザからS3へ直接送られる。
 * ここで発行するキーはサーバー側でUUID採番するため、クライアントが任意のキーを
 * 指定して他人のオブジェクトを上書きすることはできない。
 */
@RestController
public class UploadController {

    private final StorageService storageService;

    public UploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/api/uploads/presign")
    public ResponseEntity<ApiResponse<List<PresignedUpload>>> presign(
            @Valid @RequestBody PresignUploadRequest request) {
        List<PresignedUpload> uploads = request.contentTypes().stream()
                .map(storageService::createUploadUrl)
                .toList();
        return ResponseEntity.ok(ApiResponse.of(uploads));
    }
}
