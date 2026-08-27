package com.snsapp.backend.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.snsapp.backend.exception.ImageTooLargeException;
import com.snsapp.backend.exception.InvalidImageTypeException;
import com.snsapp.backend.storage.CdnProperties;
import com.snsapp.backend.storage.PresignedUpload;
import com.snsapp.backend.storage.S3StorageService;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * {@link S3StorageService} の分岐網羅(docs/test-plan.md S-1〜S-16)。
 *
 * <p>S3クライアントをモックする単体テストにした理由は、LocalStackを使う結合テストでは
 * 到達できない分岐があるため。特に「実AWSではListBucket権限が無いと存在しないオブジェクトへの
 * HeadObjectが404ではなく403を返す」というケースはLocalStackでは再現できず、
 * 接続断(SdkClientException)や5xxも意図的に起こせない。
 *
 * <p>不正なキーの検証(S-3〜S-5)はパストラバーサル対策そのものなので、
 * 「S3を一切呼ばずに弾く」ところまで確認する。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class S3StorageServiceTest {

    private static final String BUCKET = "test-bucket";

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    /**
     * CDN設定はモックではなく実物を使う。{@code base-url} の有無で表示用URLの分岐が変わることが
     * このクラスの検証対象で、モックにするとその分岐条件自体を書き換えてしまうため。
     * 既定は未設定(CDN無効)で、CDNを使う分岐のテストだけが値を入れる。
     */
    private final CdnProperties cdnProperties = new CdnProperties();

    private S3StorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new S3StorageService(s3Client, s3Presigner, cdnProperties);
        // @Valueで注入されるフィールドはコンストラクタ経由では設定されないため、直接埋める。
        ReflectionTestUtils.setField(storageService, "bucket", BUCKET);
        ReflectionTestUtils.setField(storageService, "presignExpiry", Duration.ofHours(24));
    }

    private static URL url(String value) {
        try {
            return URI.create(value).toURL();
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private void stubPresignPut(String presignedUrl) {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(url(presignedUrl));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);
    }

    private void stubHeadObject(String contentType, Long contentLength) {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .build());
    }

    private void stubHeadObjectFailure(int statusCode) {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Error(statusCode));
    }

    private static S3Exception s3Error(int statusCode) {
        return (S3Exception) S3Exception.builder().statusCode(statusCode).message("s3 error").build();
    }

    // --- S-1, S-2: createUploadUrl ---

    @ParameterizedTest
    @CsvSource({"image/jpeg, .jpg", "image/png, .png", "image/webp, .webp", "image/gif, .gif"})
    void 許可された画像形式ならアップロード用の署名付きURLを発行する(String contentType, String extension) {
        stubPresignPut("https://s3.example.com/upload");

        PresignedUpload upload = storageService.createUploadUrl(contentType);

        assertThat(upload.key()).startsWith("pending/").endsWith(extension);
        assertThat(upload.uploadUrl()).isEqualTo("https://s3.example.com/upload");
    }

    /**
     * Content-Typeを署名に含めることで、クライアントが別の値で送るとS3が署名不一致で拒否する。
     * 「jpegと偽って実行ファイルを置く」単純な偽装をここで防いでいる。
     */
    @Test
    void アップロード用URLの署名にはContentTypeが含まれる() {
        stubPresignPut("https://s3.example.com/upload");

        storageService.createUploadUrl("image/png");

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        PutObjectRequest putRequest = captor.getValue().putObjectRequest();
        assertThat(putRequest.contentType()).isEqualTo("image/png");
        assertThat(putRequest.bucket()).isEqualTo(BUCKET);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"application/pdf", "text/html", "image/svg+xml", "application/octet-stream", ""})
    void 許可されない形式ではアップロード用URLを発行しない(String contentType) {
        assertThatThrownBy(() -> storageService.createUploadUrl(contentType))
                .isInstanceOf(InvalidImageTypeException.class);

        verifyNoInteractions(s3Presigner);
    }

    @Test
    void アップロードキーは毎回異なる() {
        stubPresignPut("https://s3.example.com/upload");

        assertThat(storageService.createUploadUrl("image/jpeg").key())
                .isNotEqualTo(storageService.createUploadUrl("image/jpeg").key());
    }

    // --- S-3〜S-5: promote のキー検証(S3に触れずに弾く) ---

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "posts/other-user.jpg", // pending/ 以外を指して他人のオブジェクトを触ろうとする
                "avatars/someone.jpg",
                "abc.jpg",
                "/pending/abc.jpg", // 先頭が pending/ ではない
                "pending/../posts/other.jpg", // パストラバーサル
                "pending/a/../../secret.jpg"
            })
    void 不正なキーはS3に触れずに拒否される(String pendingKey) {
        assertThatThrownBy(() -> storageService.promote(pendingKey, "posts"))
                .isInstanceOf(InvalidImageTypeException.class);

        verifyNoInteractions(s3Client);
    }

    // --- S-6〜S-8: HeadObject の失敗 ---

    /** アップロードされていないキーを指定された場合(投稿だけ先に送られた等)。 */
    @Test
    void アップロードされていないキーは拒否される() {
        stubHeadObjectFailure(404);

        assertThatThrownBy(() -> storageService.promote("pending/missing.jpg", "posts"))
                .isInstanceOf(InvalidImageTypeException.class);
    }

    /**
     * 実AWSではs3:ListBucketが無い場合、存在しないオブジェクトへのHeadObjectは
     * 404ではなく403を返す(存在有無を漏らさないS3の仕様)。LocalStackでは再現できない分岐。
     */
    @Test
    void HeadObjectが403を返す場合も拒否される() {
        stubHeadObjectFailure(403);

        assertThatThrownBy(() -> storageService.promote("pending/forbidden.jpg", "posts"))
                .isInstanceOf(InvalidImageTypeException.class);
    }

    /** 404/403以外のS3エラーは「不正なキー」ではないので握りつぶさずそのまま伝播させる。 */
    @Test
    void HeadObjectが500を返す場合は例外がそのまま伝播する() {
        stubHeadObjectFailure(500);

        assertThatThrownBy(() -> storageService.promote("pending/abc.jpg", "posts"))
                .isInstanceOf(S3Exception.class)
                .isNotInstanceOf(InvalidImageTypeException.class);
    }

    // --- S-9: 実体の形式検証 ---

    /** 拡張子ではなくS3が保持する実際のContent-Typeで判定していること(拡張子偽装の検出)。 */
    @ParameterizedTest
    @ValueSource(strings = {"application/pdf", "text/html", "application/octet-stream"})
    void 実体のContentTypeが許可外なら拒否される(String actualContentType) {
        stubHeadObject(actualContentType, 1024L);

        assertThatThrownBy(() -> storageService.promote("pending/fake.jpg", "posts"))
                .isInstanceOf(InvalidImageTypeException.class);

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
    }

    // --- S-10〜S-12: サイズの境界値 ---

    @Test
    void ちょうど5MBの画像は許可される() {
        stubHeadObject("image/jpeg", 5L * 1024 * 1024);

        assertThat(storageService.promote("pending/abc.jpg", "posts")).isEqualTo("posts/abc.jpg");
    }

    @Test
    void サイズが5MBを1バイト超える画像は拒否される() {
        stubHeadObject("image/jpeg", 5L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> storageService.promote("pending/big.jpg", "posts"))
                .isInstanceOf(ImageTooLargeException.class);
    }

    /** 検証に落ちたオブジェクトはライフサイクル任せにせず、その場で消していること。 */
    @Test
    void サイズ超過で拒否した画像はその場で削除される() {
        stubHeadObject("image/jpeg", 10L * 1024 * 1024);

        assertThatThrownBy(() -> storageService.promote("pending/big.jpg", "posts"))
                .isInstanceOf(ImageTooLargeException.class);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("pending/big.jpg");
        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    void ContentLengthが不明な場合はサイズ検証をスキップする() {
        stubHeadObject("image/jpeg", null);

        assertThat(storageService.promote("pending/abc.jpg", "posts")).isEqualTo("posts/abc.jpg");
    }

    // --- S-13: promote 正常系 ---

    @Test
    void 検証を通った画像は正式な場所へ移される() {
        stubHeadObject("image/png", 1024L);

        assertThat(storageService.promote("pending/abc-123.png", "avatars")).isEqualTo("avatars/abc-123.png");
    }

    /** コピーしてから元を消す順序。逆にすると失敗時に画像が失われる。 */
    @Test
    void promoteはコピーしてからpendingを削除する() {
        stubHeadObject("image/jpeg", 1024L);

        storageService.promote("pending/abc.jpg", "posts");

        InOrder inOrder = inOrder(s3Client);
        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        inOrder.verify(s3Client).copyObject(copy.capture());
        inOrder.verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        assertThat(copy.getValue().sourceKey()).isEqualTo("pending/abc.jpg");
        assertThat(copy.getValue().destinationKey()).isEqualTo("posts/abc.jpg");
    }

    // --- S-14: viewUrl ---

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void キーが未設定なら表示用URLはnullになる(String key) {
        assertThat(storageService.viewUrl(key)).isNull();

        verifyNoInteractions(s3Presigner);
    }

    /** CDNが無効な環境(ローカル開発・E2E)ではS3の署名付きURLにフォールバックする。 */
    @Test
    void CDNが無効なら表示用の署名付きURLを返す() {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(url("https://s3.example.com/signed-get"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        assertThat(storageService.viewUrl("posts/abc.jpg")).isEqualTo("https://s3.example.com/signed-get");
    }

    /**
     * CDN有効時は署名付きURLを作らない。署名がクエリ文字列に入ると発行のたびにURLが変わり、
     * CloudFrontのキャッシュキーも毎回変わってヒットしなくなるため。
     */
    @Test
    void CDNが有効なら署名を作らず固定URLを返す() {
        cdnProperties.setBaseUrl("https://dxxxx.cloudfront.net");

        assertThat(storageService.viewUrl("posts/abc.jpg"))
                .isEqualTo("https://dxxxx.cloudfront.net/images/posts/abc.jpg");

        verifyNoInteractions(s3Presigner);
    }

    /** 同じキーからは毎回同じURLが返る。これが成り立たないとCDNもブラウザもキャッシュできない。 */
    @Test
    void CDNが有効なら同じキーからは毎回同じURLが返る() {
        cdnProperties.setBaseUrl("https://dxxxx.cloudfront.net");

        assertThat(storageService.viewUrl("posts/abc.jpg")).isEqualTo(storageService.viewUrl("posts/abc.jpg"));
    }

    // --- S-15, S-16: delete ---

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void キーが未設定ならS3を呼ばずに何もしない(String key) {
        storageService.delete(key);

        verifyNoInteractions(s3Client);
    }

    @Test
    void キーを指定すればS3のオブジェクトを削除する() {
        storageService.delete("posts/abc.jpg");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("posts/abc.jpg");
    }

    /**
     * 画像の削除失敗で投稿削除やプロフィール更新まで巻き込まないこと。
     * S3側のエラーだけでなく接続失敗(SdkClientException)も握る必要がある。
     */
    @Test
    void S3の削除に失敗しても例外を投げない() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("接続に失敗しました"));

        assertThatCode(() -> storageService.delete("posts/abc.jpg")).doesNotThrowAnyException();
    }

    @Test
    void S3がサービスエラーを返しても削除は例外を投げない() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(s3Error(500));

        assertThatCode(() -> storageService.delete("posts/abc.jpg")).doesNotThrowAnyException();
    }
}
