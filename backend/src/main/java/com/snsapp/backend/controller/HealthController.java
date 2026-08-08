package com.snsapp.backend.controller;

import com.snsapp.backend.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "ヘルスチェック", description = "運用・動作確認用。機能要件ではない")
public class HealthController {

    private final UserMapper userMapper;

    public HealthController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Operation(
            summary = "死活監視",
            description = """
                    アプリの起動状態と、DB接続の疎通（ユーザー数を取得できるか）を確認する。

                    DBに到達できない場合は200ではなく500 `INTERNAL_ERROR` になる。
                    つまり「200が返ること」がアプリ＋DBの両方が生きている証拠になる。

                    認証不要。
                    """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "アプリとDBが正常",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            { "data": { "status": "ok", "userCount": 3 } }
                            """)))
    @SecurityRequirements
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        long userCount = userMapper.countUsers();
        return Map.of("data", Map.of("status", "ok", "userCount", userCount));
    }
}
