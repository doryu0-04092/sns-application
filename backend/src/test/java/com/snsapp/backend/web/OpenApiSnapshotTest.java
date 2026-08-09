package com.snsapp.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snsapp.backend.support.AbstractIntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * リポジトリにコミットしたOpenAPI仕様({@code docs/openapi.json})が、実装と一致していることの検証。
 *
 * <p><b>なぜファイルとして持つか</b>: フロントエンドの型をこの仕様から生成するため。
 * 仕様がアプリ起動中しか存在しないと、型生成のたびにバックエンドを起動する必要があり、
 * CIで「生成された型が最新か」を検証することもできない。
 *
 * <p><b>なぜテストで書き出さず、比較するか</b>: テストがリポジトリのファイルを書き換えると、
 * 「実装を変えたらテストを流すだけで仕様が黙って更新される」状態になり、
 * APIの変更がレビューを素通りする。ここでは比較のみ行い、更新は明示的な操作にしている。
 *
 * <p><b>仕様を更新する手順</b>:
 *
 * <pre>{@code
 * mvn test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true
 * }</pre>
 *
 * 更新後は差分をコミットすること。差分がそのままAPIの変更点になる。
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
class OpenApiSnapshotTest extends AbstractIntegrationTest {

    /** 更新モードの切り替え。既定(未指定)は比較のみ。 */
    private static final String UPDATE_PROPERTY = "openapi.snapshot.update";

    /** backend/ から見た相対パス。リポジトリ直下の docs/ に置く。 */
    private static final Path SNAPSHOT = Path.of("..", "docs", "openapi.json");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void コミットされた仕様が実装と一致している() throws Exception {
        String current = currentSpec();

        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, current, StandardCharsets.UTF_8);
            return;
        }

        assertThat(Files.exists(SNAPSHOT))
                .as("%s が存在すること。無い場合は -D%s=true で生成する", SNAPSHOT, UPDATE_PROPERTY)
                .isTrue();

        assertThat(readSnapshot())
                .as("""
                        コミットされた %s が実装と一致していません。
                        APIを変更したなら、次のコマンドで仕様を更新してコミットしてください。
                          mvn test -Dtest=OpenApiSnapshotTest -D%s=true
                        """, SNAPSHOT, UPDATE_PROPERTY)
                .isEqualTo(current);
    }

    /**
     * 実装から生成された現在の仕様。
     *
     * <p>差分を読める形にするため、キー順を整えて整形出力する。springdocの出力順は
     * 実行ごとに揺れうるため、そのまま比較すると意味のない差分が出る。
     */
    private String currentSpec() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return normalize(objectMapper.readTree(json));
    }

    private String readSnapshot() throws IOException {
        return normalize(objectMapper.readTree(Files.readString(SNAPSHOT, StandardCharsets.UTF_8)));
    }

    private String normalize(JsonNode node) throws IOException {
        return objectMapper
                .writerWithDefaultPrettyPrinter()
                .withFeatures(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(node) + "\n";
    }
}
