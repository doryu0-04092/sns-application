package com.snsapp.backend.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * トークンバケットの挙動。
 *
 * <p><b>時刻を引数で渡す設計にしているのは、ここを待たずに検証するためである。</b>
 * 実時間に依存させると「1分あたり」の回復を確かめるのに1分かかり、
 * しかも遅いマシンでは falsely 失敗する。
 */
class TokenBucketTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void 容量までは連続して通る() {
        TokenBucket bucket = new TokenBucket(3, 1, 0);

        assertThat(bucket.tryConsume(0)).isTrue();
        assertThat(bucket.tryConsume(0)).isTrue();
        assertThat(bucket.tryConsume(0)).isTrue();
    }

    @Test
    void 容量を超えると落ちる() {
        TokenBucket bucket = new TokenBucket(2, 1, 0);
        bucket.tryConsume(0);
        bucket.tryConsume(0);

        assertThat(bucket.tryConsume(0)).isFalse();
    }

    @Test
    void 時間の経過で回復する() {
        TokenBucket bucket = new TokenBucket(2, 1, 0);
        bucket.tryConsume(0);
        bucket.tryConsume(0);
        assertThat(bucket.tryConsume(0)).isFalse();

        assertThat(bucket.tryConsume(SECOND)).isTrue();
    }

    @Test
    void 回復は容量を超えて貯まらない() {
        TokenBucket bucket = new TokenBucket(2, 1, 0);

        // 100秒放置しても、貯まるのは容量の2つまで。
        assertThat(bucket.tryConsume(100 * SECOND)).isTrue();
        assertThat(bucket.tryConsume(100 * SECOND)).isTrue();
        assertThat(bucket.tryConsume(100 * SECOND)).isFalse();
    }

    /**
     * <b>固定窓との違いがここに出る。</b> 固定窓は窓の境目で容量の2倍が連続して通るが、
     * トークンバケットは経過時間に比例してしか回復しないため、その段差が無い。
     */
    @Test
    void 窓の境目のような段差が無い() {
        TokenBucket bucket = new TokenBucket(5, 5, 0);
        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryConsume(0)).isTrue();
        }

        // 1秒後に回復するのは5つではなく5つ分の補充(=5/秒 × 1秒)。
        // 補充速度をそのまま超える連続消費はできない。
        assertThat(bucket.tryConsume(SECOND)).isTrue();
        assertThat(bucket.tryConsume(SECOND)).isTrue();
        assertThat(bucket.tryConsume(SECOND)).isTrue();
        assertThat(bucket.tryConsume(SECOND)).isTrue();
        assertThat(bucket.tryConsume(SECOND)).isTrue();
        assertThat(bucket.tryConsume(SECOND)).isFalse();
    }

    @Test
    void 再試行までの秒数は切り上げる() {
        // 1秒あたり2つ補充 = 1つ回復するのに0.5秒。切り捨てると0秒になり、
        // 「Retry-Afterの直後に再送してまた429」になる。
        TokenBucket bucket = new TokenBucket(1, 2, 0);
        bucket.tryConsume(0);

        assertThat(bucket.secondsUntilAvailable(0)).isEqualTo(1L);
    }

    @Test
    void 通せる状態なら再試行までの秒数は0() {
        TokenBucket bucket = new TokenBucket(1, 1, 0);

        assertThat(bucket.secondsUntilAvailable(0)).isZero();
    }

    @Test
    void 満杯かどうかを判定できる() {
        TokenBucket bucket = new TokenBucket(2, 1, 0);
        assertThat(bucket.isFull(0)).isTrue();

        bucket.tryConsume(0);
        assertThat(bucket.isFull(0)).isFalse();

        // 使われないまま時間が経てば満杯に戻る = 掃除してよい状態。
        assertThat(bucket.isFull(10 * SECOND)).isTrue();
    }
}
