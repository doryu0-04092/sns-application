package com.snsapp.backend.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQLコンテナに対して実行する統合テストの基底クラス。
 *
 * <p>MyBatisのXML SQLはPostgreSQL固有の構文(GENERATED ALWAYS AS IDENTITY、ILIKE、相関サブクエリ)
 * に依存しているため、インメモリDBではSQLの正しさを検証できない。実際のPostgreSQLに
 * Flywayでマイグレーションを流した状態でテストする。
 *
 * <p>コンテナはstaticフィールドで保持し、JVMごとに1度だけ起動する(いわゆるsingleton container
 * パターン)。{@code @Testcontainers}によるクラス単位のライフサイクル管理を使うとテストクラスの
 * 数だけコンテナが起動して遅くなるため、あえて手動で起動している。JVM終了時にTestcontainersの
 * Ryukコンテナが後始末するため、明示的なstopは不要。
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
