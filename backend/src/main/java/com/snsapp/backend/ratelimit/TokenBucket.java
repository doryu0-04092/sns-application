package com.snsapp.backend.ratelimit;

/**
 * トークンバケット。1つの鍵(IPやユーザーID)あたりの許容量を持つ。
 *
 * <p><b>固定窓ではなくトークンバケットにした理由。</b>
 * 「1分あたりN回」を固定窓で数えると、窓の境目で 2N 回が連続して通る。
 * 59秒目にN回、61秒目にN回である。ログイン試行の制限としては、
 * <b>まさに短時間に集中させたい攻撃者に都合がよい</b>。
 *
 * <p>トークンバケットは経過時間に比例してトークンを補充するため、この境目が存在しない。
 * 一方でバースト(貯まった分の一気使い)は capacity までは許す。
 * これは意図した挙動で、画面を開いた瞬間に数本のリクエストが並ぶ通常の利用を通すために要る。
 *
 * <p><b>スレッド安全性</b>: 同じ鍵に同時にリクエストが来るため、
 * {@link #tryConsume(long)} は synchronized にしている。
 * 鍵ごとに別インスタンスなので、競合するのは同一利用者の同時リクエストだけである。
 */
class TokenBucket {

    private final double capacity;
    private final double refillPerNano;

    private double tokens;
    private long lastRefillNanos;

    /**
     * @param capacity バケットの容量。連続して通せる上限(バースト)
     * @param refillPerSecond 1秒あたりの補充量
     * @param nowNanos 現在時刻({@link System#nanoTime()})
     */
    TokenBucket(double capacity, double refillPerSecond, long nowNanos) {
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / 1_000_000_000d;
        this.tokens = capacity;
        this.lastRefillNanos = nowNanos;
    }

    /**
     * トークンを1つ消費する。足りなければ消費せずに false を返す。
     *
     * @param nowNanos 現在時刻。テストから時刻を差し込めるように引数で受ける
     */
    synchronized boolean tryConsume(long nowNanos) {
        refill(nowNanos);
        if (tokens < 1d) {
            return false;
        }
        tokens -= 1d;
        return true;
    }

    /** 次に1つ通るまでの秒数。すでに通せるなら0。応答の Retry-After に使う。 */
    synchronized long secondsUntilAvailable(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1d) {
            return 0L;
        }
        double needed = 1d - tokens;
        double seconds = needed / (refillPerNano * 1_000_000_000d);
        // 切り上げる。切り捨てると「Retry-After の直後に再送してまた429」になる。
        return (long) Math.ceil(seconds);
    }

    /** 満杯かどうか。満杯 = しばらく使われていない = 破棄してよい。 */
    synchronized boolean isFull(long nowNanos) {
        refill(nowNanos);
        return tokens >= capacity;
    }

    private void refill(long nowNanos) {
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
        lastRefillNanos = nowNanos;
    }
}
