package com.snsapp.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snsapp.backend.logging.RequestLoggingFilter;
import com.snsapp.backend.ratelimit.RateLimitFilter;
import com.snsapp.backend.ratelimit.RateLimitProperties;
import com.snsapp.backend.security.JwtAuthFilter;
import com.snsapp.backend.security.JwtService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * サーブレットフィルタの登録と実行順序を1か所で決める。
 *
 * <p>順序: RequestLoggingFilter → CorsFilter → JwtAuthFilter → RateLimitFilter。
 * <ul>
 *   <li>RequestLoggingFilterが最外周なのは、CORS拒否やJwtAuthFilterが返す401も含めて
 *       全てのリクエストをアクセスログに残すため。</li>
 *   <li>CorsFilterがJwtAuthFilterより先なのは、順序を誤ると401応答にCORSヘッダーが
 *       付与されず、ブラウザ側で意味不明なCORSエラーとして扱われてしまうため。</li>
 *   <li>RateLimitFilterがJwtAuthFilterの<b>後ろ</b>なのは、認証済みの書き込みを
 *       ユーザー単位で数えるため。そのユーザーIDを設定するのがJwtAuthFilterである。
 *       公開エンドポイント(ログイン・登録・更新)はJwtAuthFilterを素通りしてくるので、
 *       ユーザーIDが無い=未認証として、そちらはIP単位で数える。</li>
 * </ul>
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration =
                new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        // 追跡IDをブラウザ側のスクリプトからも読めるようにする(問い合わせ時の照合用)。
        // 明示しないとレスポンスに載っていてもJSからは参照できない。
        configuration.setExposedHeaders(List.of(RequestLoggingFilter.REQUEST_ID_HEADER));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        FilterRegistrationBean<JwtAuthFilter> registration =
                new FilterRegistrationBean<>(new JwtAuthFilter(jwtService, objectMapper));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimitProperties properties, ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(properties, objectMapper));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        return registration;
    }
}
