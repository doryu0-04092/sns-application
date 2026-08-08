package com.snsapp.backend.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3クライアントの組み立て。
 *
 * <p>クライアントとプレサイナーで<b>別のエンドポイントを指定できるようにしている</b>。
 * LocalStackをDockerで動かす場合、バックエンドコンテナからは {@code http://localstack:4566} で
 * 到達する一方、署名付きURLを実際に叩くのはブラウザなので {@code http://localhost:4566} でなければ
 * 解決できない。両者を同じ値にすると、どちらかが必ず繋がらなくなる。
 *
 * <p>実AWSに向ける場合は両方とも未設定にする(SDK既定のエンドポイントが使われる)。
 */
@Configuration
public class S3Config {

    @Value("${app.storage.s3.region}")
    private String region;

    /** コンテナ内部からS3へ到達するためのエンドポイント。空なら実AWS。 */
    @Value("${app.storage.s3.endpoint:}")
    private String endpoint;

    /** 署名付きURLに埋め込むホスト。空なら endpoint と同じものを使う。 */
    @Value("${app.storage.s3.public-endpoint:}")
    private String publicEndpoint;

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder().region(Region.of(region));
        applyEndpoint(builder::endpointOverride, builder::serviceConfiguration, endpoint);
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder().region(Region.of(region));
        String target = publicEndpoint.isBlank() ? endpoint : publicEndpoint;
        applyEndpoint(builder::endpointOverride, builder::serviceConfiguration, target);
        return builder.build();
    }

    /**
     * エンドポイントが指定されている場合のみ上書きし、あわせてパススタイルアクセスを有効にする。
     * LocalStackは仮想ホスト形式({@code bucket.localhost}) の名前解決ができないため、
     * パススタイル({@code localhost:4566/bucket}) にしないと接続できない。
     */
    private void applyEndpoint(
            java.util.function.Consumer<URI> endpointOverride,
            java.util.function.Consumer<S3Configuration> serviceConfiguration,
            String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        endpointOverride.accept(URI.create(value));
        serviceConfiguration.accept(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    }
}
