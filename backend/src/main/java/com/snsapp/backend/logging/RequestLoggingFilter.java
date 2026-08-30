package com.snsapp.backend.logging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.snsapp.backend.security.JwtAuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * リクエスト単位の追跡ID(requestId)を発行し、アクセスログを1行出力する。
 *
 * <p>複数の利用者のリクエストが同時に処理されるため、requestIdが無いと
 * 「どのログがどのリクエストのものか」を後から追えない。MDCに載せることで、
 * このフィルタ以降で出る全てのログ(サービス層のINFO、例外ハンドラのERROR)に
 * 自動で同じrequestIdが付く。呼び出し元が問い合わせに使えるよう、
 * レスポンスヘッダー {@value #REQUEST_ID_HEADER} でも返す。
 *
 * <p><strong>ログに載せるのはメタデータのみ</strong>(メソッド/パス/ステータス/所要時間)。
 * ヘッダー・Cookie・リクエストボディ・クエリ文字列は一切渡さない。マスキング処理で後から
 * 消すのではなく、認証トークンやパスワードがログに入る経路自体を作らない方針にしている
 * (一度ログに残った秘密情報は取り消せないため)。
 *
 * <p><strong>保守上の注意</strong>: このフィルタは全フィルタの最外周(HIGHEST_PRECEDENCE、
 * 登録は {@code CorsConfig})である必要がある。内側に置くと、JwtAuthFilterが返す401が
 * アクセスログに残らなくなる。また finally での {@link MDC#clear()} は必須で、
 * これを外すとスレッドプールの使い回しにより別のリクエストのログへ前の値が漏れる。
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    static final String MDC_REQUEST_ID = "requestId";
    static final String MDC_USER_ID = "userId";

    // 死活監視のポーリングで埋め尽くされるのを避けるため、DEBUGへ落とす(出力自体は止めない)。
    // ヘルスチェックは30秒ごとに来る。全部記録すると、
    // アクセスログのほとんどがヘルスチェックで埋まる。
    private static final Set<String> QUIET_PATHS =
            Set.of("/api/livez", "/api/readyz", "/api/health");

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            try {
                logAccess(request, response, elapsedMillis(startedAt));
            } finally {
                MDC.clear();
            }
        }
    }

    private void logAccess(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        // JwtAuthFilterが認証成功時に設定する。未認証・公開エンドポイントでは付かない。
        Object userId = request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE);
        if (userId != null) {
            MDC.put(MDC_USER_ID, String.valueOf(userId));
        }

        boolean quiet = "OPTIONS".equalsIgnoreCase(request.getMethod())
                || QUIET_PATHS.contains(request.getRequestURI());
        if (quiet && !log.isDebugEnabled()) {
            return;
        }

        // kv(...) は人が読む形式では "key=value"、JSON形式では独立したフィールドになる。
        Object[] fields = {
            kv("method", request.getMethod()),
            // クエリ文字列は含めない(将来トークン等が紛れ込んでもログに出ないようにするため)。
            kv("path", request.getRequestURI()),
            kv("status", response.getStatus()),
            kv("durationMs", durationMs)
        };
        if (quiet) {
            log.debug("access {} {} {} {}", fields);
        } else {
            log.info("access {} {} {} {}", fields);
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
