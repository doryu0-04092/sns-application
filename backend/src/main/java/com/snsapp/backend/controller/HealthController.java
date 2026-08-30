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

/**
 * 運用・動作確認用のエンドポイント。機能要件ではない。
 *
 * <p><b>「生きているか」と「使えるか」を分けている。</b>
 *
 * <p>元は {@code /api/health} 1本で、DBへの疎通まで見て200を返していた。
 * これをロードバランサのヘルスチェックに向けていたため、
 * <b>RDSが一時的に不調になると全タスクが同時にunhealthyと判定され、
 * 一斉に置き換えが走る</b>構造だった。置き換えてもRDSは回復しないので、
 * 動いているタスクを失うぶん状況が悪化するだけである。
 * （この懸念は docs/retrospective.md に「残した課題」として記録していた。）
 *
 * <p>タスクを入れ替えるべき理由になるのは「プロセスが生きていないこと」だけである。
 * そこで次のように分けた。
 *
 * <ul>
 *   <li>{@code /api/livez} — <b>依存先を一切見ない。</b> ロードバランサはこちらを見る
 *   <li>{@code /api/readyz} — DBへの疎通を含む。デプロイ時の投入判定と状況確認に使う
 * </ul>
 *
 * <p>{@code /api/health} は {@code /api/readyz} と同じ内容で残してある。
 * 監視やスクリプトから参照されている可能性があり、黙って消すと
 * 「200が返らなくなった」という形で表面化するためである。
 */
@RestController
@Tag(name = "ヘルスチェック", description = "運用・動作確認用。機能要件ではない")
public class HealthController {

    private final UserMapper userMapper;

    public HealthController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Operation(
            summary = "プロセスの生存確認",
            description = """
                    **依存先を一切見ない。** ロードバランサのヘルスチェックはこちらへ向ける。

                    DBの状態に関わらず、プロセスが動いてリクエストを処理できていれば200を返す。

                    依存先まで確認するヘルスチェックをロードバランサに向けると、
                    DBが一時的に不調になったときに全タスクが同時にunhealthyと判定されて
                    一斉に置き換えられる。置き換えてもDBは回復しないため、
                    動いているタスクを失うぶん状況が悪化するだけである。

                    認証不要。
                    """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "プロセスは動作している",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            { "data": { "status": "ok" } }
                            """)))
    @SecurityRequirements
    @GetMapping("/api/livez")
    public Map<String, Object> livez() {
        return Map.of("data", Map.of("status", "ok"));
    }

    @Operation(
            summary = "依存先を含む疎通確認",
            description = """
                    DBへの疎通（ユーザー数を取得できるか）を含めて確認する。

                    DBに到達できない場合は200ではなく500 `INTERNAL_ERROR` になる。
                    つまり「200が返ること」がアプリ＋DBの両方が生きている証拠になる。

                    **ロードバランサのヘルスチェックには使わない。**
                    デプロイ時の投入判定と、運用中の状況確認に使う。

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
    @GetMapping("/api/readyz")
    public Map<String, Object> readyz() {
        long userCount = userMapper.countUsers();
        return Map.of("data", Map.of("status", "ok", "userCount", userCount));
    }

    @Operation(
            summary = "依存先を含む疎通確認（後方互換）",
            description = """
                    `/api/readyz` と同じ内容を返す。

                    **分離する前からある名前であり、監視やスクリプトから
                    参照されている可能性があるため残している。**
                    新しく参照する場合は `/api/livez` か `/api/readyz` を使うこと。
                    """)
    @SecurityRequirements
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return readyz();
    }
}
