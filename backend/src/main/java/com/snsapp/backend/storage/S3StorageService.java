package com.snsapp.backend.storage;

import com.snsapp.backend.exception.ImageTooLargeException;
import com.snsapp.backend.exception.InvalidImageTypeException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3に画像を保存する {@link StorageService} 実装。バケットは非公開で、
 * アップロード・表示ともに署名付きURLを介して行う。
 */
@Service
public class S3StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private static final String PENDING_PREFIX = "pending/";
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    /** アップロード用URLの有効期限。ユーザーがファイルを選んでから送るまでの猶予として十分な長さ。 */
    private static final Duration UPLOAD_EXPIRY = Duration.ofMinutes(15);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.storage.s3.bucket}")
    private String bucket;

    /**
     * 表示用URLの有効期限。フロントエンドは TanStack Query でレスポンスをキャッシュするため、
     * 短すぎるとキャッシュ内のURLだけ先に失効して画像が壊れる。既定は24時間。
     */
    @Value("${app.storage.s3.presign-expiry}")
    private Duration presignExpiry;

    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    public PresignedUpload createUploadUrl(String contentType) {
        String extension = ALLOWED_CONTENT_TYPES.get(contentType);
        if (extension == null) {
            throw new InvalidImageTypeException();
        }

        String key = PENDING_PREFIX + UUID.randomUUID() + extension;

        // Content-Type を署名に含める。クライアントが別の値で送るとS3側が署名不一致で拒否するため、
        // 「jpegと偽って実行ファイルを置く」といった単純な偽装をここで防げる。
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        String url = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(UPLOAD_EXPIRY)
                        .putObjectRequest(putRequest)
                        .build())
                .url()
                .toString();

        return new PresignedUpload(key, url);
    }

    @Override
    public String promote(String pendingKey, String category) {
        // 呼び出し側から渡ってくるキーはクライアント由来。pending/配下以外を指定して
        // 他人のオブジェクトを移動・参照させられないよう、まず形式を検証する。
        if (pendingKey == null || !pendingKey.startsWith(PENDING_PREFIX) || pendingKey.contains("..")) {
            throw new InvalidImageTypeException();
        }

        HeadObjectResponse head;
        try {
            head = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(pendingKey).build());
        } catch (S3Exception ex) {
            // アップロードされていないキーを指定された(投稿だけ先に送られた等)。
            //
            // 実AWSでは s3:ListBucket を持たない場合、存在しないオブジェクトへのHeadObjectは
            // 404ではなく403を返す(存在有無を漏らさないというS3の仕様)。LocalStackは404を返すため、
            // 環境によって例外が変わる。どちらも「このキーは使えない」であり区別する意味がないので、
            // まとめて不正なキーとして扱う。
            if (ex.statusCode() == 404 || ex.statusCode() == 403) {
                throw new InvalidImageTypeException();
            }
            throw ex;
        }

        String extension = ALLOWED_CONTENT_TYPES.get(head.contentType());
        if (extension == null) {
            throw new InvalidImageTypeException();
        }
        if (head.contentLength() != null && head.contentLength() > MAX_IMAGE_BYTES) {
            // 検証に落ちたオブジェクトはライフサイクル任せにせずその場で消す
            delete(pendingKey);
            throw new ImageTooLargeException();
        }

        String finalKey = category + "/" + pendingKey.substring(PENDING_PREFIX.length());
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(pendingKey)
                .destinationBucket(bucket)
                .destinationKey(finalKey)
                .build());
        delete(pendingKey);

        return finalKey;
    }

    @Override
    public String presignedGetUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucket).key(key).build();

        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(presignExpiry)
                        .getObjectRequest(getRequest)
                        .build())
                .url()
                .toString();
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException ex) {
            // 画像の削除失敗で投稿削除やプロフィール更新まで失敗させたくないため、記録のみに留める。
            // S3側のエラー(AwsServiceException)だけでなく、接続失敗(SdkClientException)も
            // 握る必要があるため、共通の親であるSdkExceptionで受ける。
            log.warn("Failed to delete S3 object: {}", key, ex);
        }
    }
}
