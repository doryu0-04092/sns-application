package com.snsapp.backend.ratelimit;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snsapp.backend.common.ApiError;
import com.snsapp.backend.ratelimit.RateLimitProperties.Rule;
import com.snsapp.backend.security.JwtAuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * リクエスト数の制限。
 *
 * <p><b>置き場所は JwtAuthFilter の後ろ。</b> 認証済みの書き込みを
 * <b>ユーザー単位</b>で数えたいが、そのユーザーIDを設定するのが JwtAuthFilter だからである。
 * 公開エンドポイント(ログイン・登録・更新)はそのまま素通りしてくるので、
 * ユーザーIDが無い = 未認証として <b>IP単位</b>で数える。1つのフィルタで両方を扱える。
 *
 * <p><b>この位置の限界も書いておく。</b> 壊れたトークンを大量に送る攻撃は
 * JwtAuthFilter が先に401を返すため、ここには届かず数えられない。
 * 署名検証1回分のCPUしか使わないので実害は小さいが、
 * <b>帯域を埋める種類の攻撃はアプリでは止められない</b>。それはWAF/ALBの担当である。
 *
 * <p>制限にかかったら 429 と {@code Retry-After} を返す。
 * 本文は他のエラーと同じ形({@code {"error":{"code","message"}}})にしてある。
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String SIGNUP_PATH = "/api/auth/signup";
    private static final String REFRESH_PATH = "/api/auth/refresh";

    /** 状態を変える操作。読み取りは対象外にしている(数えるほどの負荷ではない)。 */
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PATCH", "PUT", "DELETE");

    private final RateLimitProperties properties;
    private final ClientIpResolver ipResolver;
    private final ObjectMapper objectMapper;

    private final RateLimiter loginLimiter;
    private final RateLimiter signupLimiter;
    private final RateLimiter refreshLimiter;
    private final RateLimiter writeLimiter;

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.ipResolver = new ClientIpResolver(properties.getTrustedProxyHops());
        this.loginLimiter = limiter("login", properties.getLogin());
        this.signupLimiter = limiter("signup", properties.getSignup());
        this.refreshLimiter = limiter("refresh", properties.getRefresh());
        this.writeLimiter = limiter("write", properties.getWrite());
    }

    private static RateLimiter limiter(String name, Rule rule) {
        return new RateLimiter(name, rule.getCapacity(), rule.refillPerSecond());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        Target target = resolveTarget(request);
        if (target == null) {
            chain.doFilter(request, response);
            return;
        }

        if (target.limiter().tryAcquire(target.key())) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfter = target.limiter().retryAfterSeconds(target.key());
        // **鍵そのものは出さない。** IPもユーザーIDも、そのまま残すと
        // アクセスログ以上の情報をこちらに増やすことになる。
        // 誰が叩いているかは RequestLoggingFilter の記録と時刻で突き合わせる。
        log.warn("rate limited {} {}", kv("rule", target.rule()), kv("retryAfterSeconds", retryAfter));
        writeTooManyRequests(response, retryAfter);
    }

    /** どの制限に当たるかを決める。対象外なら null。 */
    private Target resolveTarget(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method)) {
            if (LOGIN_PATH.equals(path)) {
                return new Target("login", loginLimiter, "ip:" + ipResolver.resolve(request));
            }
            if (SIGNUP_PATH.equals(path)) {
                return new Target("signup", signupLimiter, "ip:" + ipResolver.resolve(request));
            }
            if (REFRESH_PATH.equals(path)) {
                return new Target("refresh", refreshLimiter, "ip:" + ipResolver.resolve(request));
            }
        }

        if (!path.startsWith("/api/") || !WRITE_METHODS.contains(method.toUpperCase())) {
            return null;
        }

        Object userId = request.getAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE);
        if (userId == null) {
            // 認証されていない書き込みは、この後ろのフィルタが401にする。数えても意味が無い。
            return null;
        }
        return new Target("write", writeLimiter, "user:" + userId);
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1L, retryAfterSeconds)));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ApiError body = ApiError.of("RATE_LIMITED", "アクセスが多すぎます。しばらく待ってからやり直してください");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private record Target(String rule, RateLimiter limiter, String key) {
    }
}
