package com.snsapp.backend.controller;

import com.snsapp.backend.common.ApiResponse;
import com.snsapp.backend.dto.PresignUploadRequest;
import com.snsapp.backend.storage.PresignedUpload;
import com.snsapp.backend.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "画像アップロード", description = "S3への直接アップロード用の署名付きURL発行。画像本体はバックエンドを経由しない")
public class UploadController {

    private final StorageService storageService;

    public UploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @Operation(
            summary = "アップロード用の署名付きURLを発行する",
            description = """
                    アップロードしたい画像の Content-Type を枚数分の配列で渡すと、
                    同じ順序で `{key, uploadUrl}` の配列が返る。

                    ### 手順

                    1. 本APIで `key` と `uploadUrl` を取得する
                    2. `uploadUrl` へブラウザから直接 `PUT` する。
                       このとき**署名時と同じ `Content-Type` ヘッダを必ず付ける**
                       （異なるとS3が署名不一致で拒否する）。
                       このリクエストは本APIのオリジンではなくS3へ送るため、認証クッキーは不要
                    3. 得られた `key` を `POST /api/posts` の `imageKeys`、
                       または `PATCH /api/users/me` の `avatarKey` に渡す
                    4. サーバーが実体（サイズ・形式）を検証し、正式な場所へ移動してからDBに登録する

                    `key` はサーバー側でUUID採番するため、クライアントが任意のキーを指定して
                    他人のオブジェクトを上書きすることはできない。

                    ### 制約

                    1回のリクエストで1〜4件。1ファイル5MBまで。形式は jpeg / png / webp / gif のみ。
                    サイズ・形式の検証は**この時点ではなく、手順4の登録時**に行われる
                    （400 `IMAGE_TOO_LARGE` / `INVALID_IMAGE_TYPE`）。
                    """)
    @PostMapping("/api/uploads/presign")
    public ResponseEntity<ApiResponse<List<PresignedUpload>>> presign(
            @Valid @RequestBody PresignUploadRequest request) {
        List<PresignedUpload> uploads = request.contentTypes().stream()
                .map(storageService::createUploadUrl)
                .toList();
        return ResponseEntity.ok(ApiResponse.of(uploads));
    }
}
