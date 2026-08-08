package com.snsapp.backend.support;

import java.io.IOException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL と LocalStack(S3)のコンテナに対して実行する統合テストの基底クラス。
 *
 * <p>MyBatisのXML SQLはPostgreSQL固有の構文(GENERATED ALWAYS AS IDENTITY、ILIKE、相関サブクエリ)
 * に依存しているため、インメモリDBではSQLの正しさを検証できない。実際のPostgreSQLに
 * Flywayでマイグレーションを流した状態でテストする。
 *
 * <p>画像はS3前提になったため、S3のAPIも必要になる。モックで代用すると署名の組み立てや
 * キーの扱いの誤りを検出できないため、LocalStackで実際に動かす。
 *
 * <p>コンテナはstaticフィールドで保持し、JVMごとに1度だけ起動する(いわゆるsingleton container
 * パターン)。{@code @Testcontainers}によるクラス単位のライフサイクル管理を使うとテストクラスの
 * 数だけコンテナが起動して遅くなるため、あえて手動で起動している。JVM終了時にTestcontainersの
 * Ryukコンテナが後始末するため、明示的なstopは不要。
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    protected static final String TEST_BUCKET = "test-images";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:4"))
                    .withServices(LocalStackContainer.Service.S3);

    static {
        POSTGRES.start();
        LOCALSTACK.start();
        createTestBucket();
    }

    private static void createTestBucket() {
        try {
            LOCALSTACK.execInContainer("awslocal", "s3", "mb", "s3://" + TEST_BUCKET);
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("テスト用S3バケットの作成に失敗しました", ex);
        }
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("app.storage.s3.bucket", () -> TEST_BUCKET);
        registry.add("app.storage.s3.region", LOCALSTACK::getRegion);
        registry.add("app.storage.s3.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("app.storage.s3.public-endpoint", () -> "");

        // 開発マシンの実AWS認証情報を拾って本物のS3へ接続しにいくのを防ぐため、
        // LocalStack用のダミー認証情報を明示的に指定する(LocalStackは値を検証しない)。
        System.setProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
        System.setProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
    }
}
