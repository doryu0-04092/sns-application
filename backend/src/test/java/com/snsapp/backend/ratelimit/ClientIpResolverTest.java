package com.snsapp.backend.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 制限の鍵に使うIPの決め方。
 *
 * <p><b>ここを誤ると、レート制限は「無い」か「全員を巻き込む」のどちらかになる。</b>
 * 左に寄せすぎるとクライアントが名乗った値を信じて制限が素通りし、
 * 右に寄せすぎると全員がプロキシのIPとして1つの鍵に潰れる。
 * どちらも表向きは動いて見えるため、テストで固定する。
 */
class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void ヘッダーが無ければ接続元をそのまま使う() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertThat(resolver.resolve(request("192.0.2.10", null))).isEqualTo("192.0.2.10");
    }

    /**
     * 本番の形。CloudFront → ALB → アプリ の2段で、
     * 利用者のIPは末尾から1つ手前に入る。
     */
    @Test
    void 二段構成では末尾から1つ手前が利用者になる() {
        ClientIpResolver resolver = new ClientIpResolver(1);
        // 先頭はクライアントが名乗った値、中央がCloudFrontの見た利用者、末尾がALBの見たCloudFront。
        String header = "1.2.3.4, 203.0.113.9, 130.176.0.1";

        assertThat(resolver.resolve(request("10.0.1.5", header))).isEqualTo("203.0.113.9");
    }

    /**
     * <b>これが最も重要な1件。</b> 攻撃者がリクエストごとに偽のIPを名乗っても、
     * 鍵が変わらないこと。変わってしまうと、制限は事実上存在しないのと同じになる。
     */
    @Test
    void 偽装した値では鍵が変わらない() {
        ClientIpResolver resolver = new ClientIpResolver(1);
        String first = resolver.resolve(request("10.0.1.5", "9.9.9.9, 203.0.113.9, 130.176.0.1"));
        String second = resolver.resolve(request("10.0.1.5", "8.8.8.8, 203.0.113.9, 130.176.0.1"));

        assertThat(first).isEqualTo(second).isEqualTo("203.0.113.9");
    }

    @Test
    void ALB直結の想定では末尾が利用者になる() {
        ClientIpResolver resolver = new ClientIpResolver(0);

        assertThat(resolver.resolve(request("10.0.1.5", "1.2.3.4, 203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    /**
     * 想定より段数が少ない場合。
     *
     * <p><b>左へ寄せない。</b> 末尾はどの構成でも直前のプロキシが自分で書いた値であり、
     * クライアントには操作できない。届かないなら安全側(末尾)へ倒す。
     */
    @Test
    void 段数が想定より少なければ末尾へ倒す() {
        ClientIpResolver resolver = new ClientIpResolver(3);

        assertThat(resolver.resolve(request("10.0.1.5", "1.2.3.4, 203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void 空白や空要素があっても壊れない() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertThat(resolver.resolve(request("10.0.1.5", "  1.2.3.4 ,  203.0.113.9  , 130.176.0.1 ")))
                .isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(request("10.0.1.5", "   "))).isEqualTo("10.0.1.5");
    }

    @Test
    void 段数に負の値は受け付けない() {
        assertThatThrownBy(() -> new ClientIpResolver(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
