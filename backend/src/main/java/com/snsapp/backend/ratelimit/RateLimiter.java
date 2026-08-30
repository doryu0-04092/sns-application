package com.snsapp.backend.ratelimit;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 鍵ごとのトークンバケットを保持する。
 *
 * <p><b>プロセス内に持つ。</b> 外部ストア(Redis等)を使わない理由は2つある。
 *
 * <ol>
 *   <li>現在のECSサービスは {@code desired_count = 1}(infra/variables.tf)であり、
 *       プロセスが1つしか無い。今この時点では厳密に効く</li>
 *   <li>この規模で ElastiCache を足すと、月額が増えるうえに
 *       <b>「Redisが落ちたらログインできない」という新しい障害経路</b>が増える</li>
 * </ol>
 *
 * <p><b>したがって、これは台数を増やすと緩む。</b>
 * タスクをN台にすると、利用者から見た実効上限はおよそN倍になる。
 * 台数を増やす時は、この制限を外部ストアへ移すか、
 * AWS WAF のレートベースルール(ALB/CloudFrontに付けられる)へ寄せる判断が要る。
 * この割り切りは docs/operations.md にも記載している。
 *
 * <p><b>件数の上限を持つ理由</b>: 鍵はIPやユーザーIDなので、
 * 攻撃者がIPを変えながら叩くと際限なく増える。<b>制限のための仕組みが
 * メモリを食い潰す</b>という本末転倒を避けるため、上限を設けて掃除する。
 */
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    /**
     * 保持する鍵の最大数。
     *
     * <p>1件あたり数十バイト程度なので、10万件でも実測に影響しない範囲に収まる。
     * これを超えたら、使われていないバケット(満杯のもの)を掃除する。
     */
    private static final int MAX_ENTRIES = 100_000;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final double capacity;
    private final double refillPerSecond;
    private final String name;
    private final AtomicLong lastOverflowWarnNanos = new AtomicLong(Long.MIN_VALUE);

    public RateLimiter(String name, double capacity, double refillPerSecond) {
        this.name = name;
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    /** 1回分を消費できたら true。できなければ false(=制限にかかった)。 */
    public boolean tryAcquire(String key) {
        long now = System.nanoTime();
        if (buckets.size() >= MAX_ENTRIES && !buckets.containsKey(key)) {
            sweep(now);
            if (buckets.size() >= MAX_ENTRIES) {
                // **通す(fail-open)。** 閉じると、鍵を大量に作るだけで
                // 全利用者を締め出せてしまい、防ぎたいものより悪い状態になる。
                // 代わりに気づけるようにする。
                warnOverflow();
                return true;
            }
        }
        return bucket(key, now).tryConsume(now);
    }

    /** Retry-After に載せる秒数。 */
    public long retryAfterSeconds(String key) {
        long now = System.nanoTime();
        TokenBucket bucket = buckets.get(key);
        return bucket == null ? 0L : bucket.secondsUntilAvailable(now);
    }

    private TokenBucket bucket(String key, long now) {
        return buckets.computeIfAbsent(key, ignored -> new TokenBucket(capacity, refillPerSecond, now));
    }

    /** 満杯のバケットは「しばらく使われていない」と同義なので落とす。 */
    private void sweep(long now) {
        buckets.entrySet().removeIf(entry -> entry.getValue().isFull(now));
    }

    /** 溢れた事実は残すが、毎リクエスト出すとログ自体が負荷になるため1分に1回に抑える。 */
    private void warnOverflow() {
        long now = System.nanoTime();
        long last = lastOverflowWarnNanos.get();
        if (now - last < 60_000_000_000L) {
            return;
        }
        if (!lastOverflowWarnNanos.compareAndSet(last, now)) {
            return;
        }
        log.warn(
                "rate limiter entries exhausted, passing requests through {} {}",
                kv("limiter", name),
                kv("entries", buckets.size()));
    }
}
