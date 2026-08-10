package com.snsapp.backend.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.snsapp.backend.security.JwtAuthFilter;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * アクセスログと追跡IDの検証。
 *
 * <p>「ログが出ること」自体が仕様なので、実際に出力されたログイベントを
 * {@link ListAppender} で捕まえて中身を確認する。
 */
class RequestLoggingFilterTest {

    private ch.qos.logback.classic.Logger filterLogger;
    private ListAppender<ILoggingEvent> appender;
    private RequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        filterLogger = context.getLogger(RequestLoggingFilter.class);
        appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        filterLogger.addAppender(appender);
        filterLogger.setLevel(Level.DEBUG);

        filter = new RequestLoggingFilter();
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(appender);
        filterLogger.setLevel(null);
        MDC.clear();
    }

    @Test
    void アクセスログにメソッドとパスとステータスと所要時間が出る() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("method=GET")
                .contains("path=/api/posts")
                .contains("status=200")
                .contains("durationMs=");
    }

    @Test
    void クエリ文字列はログに出さない() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        request.setQueryString("feed=all&token=secret-value");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(onlyEvent().getFormattedMessage()).doesNotContain("secret-value").doesNotContain("feed=all");
    }

    @Test
    void 追跡IDがレスポンスヘッダーに付与される() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/posts"), response, new MockFilterChain());

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void 追跡IDはリクエストごとに異なる() throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/posts"), first, new MockFilterChain());
        filter.doFilter(new MockHttpServletRequest("GET", "/api/posts"), second, new MockFilterChain());

        assertThat(first.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER))
                .isNotEqualTo(second.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER));
    }

    @Test
    void 後続処理の中では追跡IDがMDCから参照できる() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> filterLogger.info("inner");

        filter.doFilter(request, response, chain);

        ILoggingEvent inner = appender.list.get(0);
        assertThat(inner.getFormattedMessage()).isEqualTo("inner");
        assertThat(inner.getMDCPropertyMap().get(RequestLoggingFilter.MDC_REQUEST_ID))
                .isEqualTo(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER));
    }

    // スレッドプールでスレッドが使い回されるため、クリアを怠ると次のリクエストのログに
    // 前のリクエストのIDが付いてしまう。追跡が崩れる致命的な不具合になるので固定する。
    @Test
    void 処理後にMDCはクリアされる() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/posts"), new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID)).isNull();
        assertThat(MDC.get(RequestLoggingFilter.MDC_USER_ID)).isNull();
    }

    @Test
    void 後続処理が例外を投げてもアクセスログは出てMDCはクリアされる() {
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        try {
            filter.doFilter(new MockHttpServletRequest("GET", "/api/posts"), new MockHttpServletResponse(), failing);
        } catch (Exception expected) {
            // 例外はそのまま外へ伝える。ここではログとMDCの後始末だけを確認する。
        }

        assertThat(onlyEvent().getFormattedMessage()).contains("path=/api/posts");
        assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    void 認証済みリクエストではログに利用者IDが付く() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        // JwtAuthFilterが認証成功時にこの属性を設定する。
        request.setAttribute(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE, 7L);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(onlyEvent().getMDCPropertyMap().get(RequestLoggingFilter.MDC_USER_ID)).isEqualTo("7");
    }

    @Test
    void 未認証リクエストのログには利用者IDが付かない() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/posts"), new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(onlyEvent().getMDCPropertyMap()).doesNotContainKey(RequestLoggingFilter.MDC_USER_ID);
    }

    // 死活監視のポーリングとCORSプリフライトは件数が多く、1件ずつ見る価値が無い。
    // 本番(INFO)では出さず、調査時(DEBUG)には見えるようにしておく。
    @Test
    void ヘルスチェックとプリフライトはDEBUGに落とす() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/health"), new MockHttpServletResponse(),
                new MockFilterChain());
        filter.doFilter(new MockHttpServletRequest("OPTIONS", "/api/posts"), new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(appender.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.DEBUG, Level.DEBUG);
    }

    @Test
    void INFO設定ではヘルスチェックのログは出力されない() throws Exception {
        filterLogger.setLevel(Level.INFO);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/health"), new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(appender.list).isEmpty();
    }

    private ILoggingEvent onlyEvent() {
        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        return events.get(0);
    }
}
