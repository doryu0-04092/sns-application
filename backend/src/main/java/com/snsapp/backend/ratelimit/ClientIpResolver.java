package com.snsapp.backend.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * リクエスト元のIPアドレスを決める。
 *
 * <p><b>ALBの背後では getRemoteAddr() が使えない。</b> 返るのはALBのIPであり、
 * 全ての利用者が同じ値になる。そのまま制限の鍵にすると、
 * 誰か1人が上限に達した時点で<b>全員が締め出される</b>。
 *
 * <p>そこで X-Forwarded-For を見るが、<b>この値はクライアントが自由に付けられる</b>。
 * 素朴に先頭を採ると、攻撃者はリクエストごとに偽のIPを名乗るだけで制限を無効化できる。
 *
 * <p>プロキシは受け取った X-Forwarded-For の<b>末尾に、自分が観測した接続元を追記する</b>。
 * したがって信頼できるのは「自分より手前の段が書いた分」だけで、
 * それより左はクライアントが書いた可能性がある。
 *
 * <pre>
 *   クライアントが偽装        : X-Forwarded-For: 1.2.3.4
 *   CloudFront が利用者IPを追記: X-Forwarded-For: 1.2.3.4, 203.0.113.9
 *   ALB が CloudFront を追記   : X-Forwarded-For: 1.2.3.4, 203.0.113.9, 130.176.0.1
 *                                                 ^^^^^^^^ 偽装  ^^^^^^^^^^^^ 利用者
 * </pre>
 *
 * <p><b>この前提が崩れるのは、ALBへ直接到達できる場合である。</b>
 * その経路では CloudFront の1段が抜け、偽装値が「利用者」の位置に来る。
 * 現構成ではALBのリスナー既定動作が固定応答で、{@code X-Origin-Verify} が
 * 一致するリクエストだけを転送する(infra/alb.tf)。直接到達はできない。
 */
public class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final int trustedProxyHops;

    /**
     * @param trustedProxyHops 末尾から数えて何個目を「実際の接続元」とみなすか。
     *     アプリの手前にある信頼できるプロキシのうち、<b>自分自身を除いた段数</b>である。
     *     CloudFront → ALB → アプリ なら 1。ALB直結なら 0。
     *     <b>構成の段数を変えたら、必ずこの値も変える。</b>
     *     大きすぎるとクライアントが偽装できる領域に届き、
     *     小さすぎると全員が同じプロキシのIPとして1つの鍵に潰れる。
     */
    public ClientIpResolver(int trustedProxyHops) {
        if (trustedProxyHops < 0) {
            throw new IllegalArgumentException("trustedProxyHops must be >= 0");
        }
        this.trustedProxyHops = trustedProxyHops;
    }

    /**
     * 制限の鍵に使うIPを返す。
     *
     * <p>X-Forwarded-For が無い場合(ローカル開発、ヘルスチェック)は接続元をそのまま使う。
     */
    public String resolve(HttpServletRequest request) {
        String header = request.getHeader(FORWARDED_FOR);
        if (header == null || header.isBlank()) {
            return nullSafe(request.getRemoteAddr());
        }

        String[] hops = header.split(",");
        int index = hops.length - 1 - trustedProxyHops;
        if (index < 0) {
            // 想定より段数が少ない。**左に寄せるとクライアントが書いた値を掴む。**
            // 末尾はどの構成でも「直前のプロキシが自分で書いた値」であり、
            // クライアントには操作できないため、そちらへ倒す。
            index = hops.length - 1;
        }
        String ip = hops[index].trim();
        return ip.isEmpty() ? nullSafe(request.getRemoteAddr()) : ip;
    }

    private static String nullSafe(String remoteAddr) {
        return remoteAddr == null ? "unknown" : remoteAddr;
    }
}
