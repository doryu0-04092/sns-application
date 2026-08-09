package com.snsapp.backend.web.controller;

import com.snsapp.backend.security.JwtAuthFilter;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Web層スライステスト({@code @WebMvcTest})の共通土台。
 *
 * <p>このアプリはSpring Securityを使わず、独自の {@link JwtAuthFilter} が
 * {@code FilterRegistrationBean} 経由で登録されている。{@code @WebMvcTest} はその
 * {@code @Configuration} を読み込まないためフィルタは動かず、代わりに各Controllerが読む
 * リクエスト属性 {@code currentUserId} を直接注入する必要がある。
 * {@code @WithMockUser} は(Spring Securityが無いため)使えない。
 *
 * <p>したがってこの層で検証するのは「認証済みとして到達した後の入出力の契約」であり、
 * 未認証時の401は {@link JwtAuthFilter} が返すためこの層では再現しない。
 * 401の検証はフィルタまで通る結合テスト({@code ApiContractTest})が担当する。
 */
abstract class AbstractControllerTest {

    /** 認証済みユーザーとして扱うID。 */
    protected static final Long USER_ID = 1L;

    /** JwtAuthFilterが本来セットするリクエスト属性を手で埋める。 */
    protected static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.requestAttr(JwtAuthFilter.CURRENT_USER_ID_ATTRIBUTE, USER_ID);
    }

    /**
     * Set-Cookieヘッダをまとめて取り出す。
     *
     * <p>{@code header().string(...)} は先頭の1値しか見ず、{@code header().stringValues(...)} は
     * 完全一致のStringしか受け付けない。認証系は2枚のクッキーを部分一致で確かめたいため、
     * 一覧を取り出してAssertJで検証する。
     */
    protected static List<String> setCookies(MvcResult result) {
        List<String> values = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        return values == null ? List.of() : values;
    }
}
