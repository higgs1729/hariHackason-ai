package com.hanamizuki.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final Duration WINDOW = Duration.ofMillis(200);

    @Test
    void theLimitIsTheLimit() {
        RateLimiter limiter = new RateLimiter();

        assertThat(limiter.tryAcquire("a", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("a", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("a", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("a", 3, WINDOW)).isFalse();
    }

    @Test
    void keysDoNotShareAnAllowance() {
        RateLimiter limiter = new RateLimiter();

        assertThat(limiter.tryAcquire("a", 1, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("a", 1, WINDOW)).isFalse();
        assertThat(limiter.tryAcquire("b", 1, WINDOW)).isTrue();
    }

    /**
     * The reason this is a sliding window and not a fixed one. Under a fixed
     * window a user spends the whole allowance at the end of one bucket and the
     * whole next allowance at the start of the next, which is double the limit
     * back to back — and every call here costs money.
     */
    @Test
    void callsExpireOneAtATimeRatherThanAllAtOnce() throws InterruptedException {
        RateLimiter limiter = new RateLimiter();

        assertThat(limiter.tryAcquire("a", 2, WINDOW)).isTrue();
        Thread.sleep(150);
        assertThat(limiter.tryAcquire("a", 2, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("a", 2, WINDOW)).isFalse();

        // Only the first of the two has aged out by now.
        Thread.sleep(100);
        assertThat(limiter.tryAcquire("a", 2, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("a", 2, WINDOW)).isFalse();
    }

    @Test
    void anIdleKeyIsForgotten() throws InterruptedException {
        RateLimiter limiter = new RateLimiter();
        limiter.tryAcquire("a", 1, WINDOW);
        limiter.tryAcquire("b", 1, WINDOW);

        assertThat(limiter.evictIdle(WINDOW)).isZero();

        Thread.sleep(250);
        assertThat(limiter.evictIdle(WINDOW)).isEqualTo(2);
    }

    /**
     * Two requests from one user can land on two threads. Without the lock the
     * read-then-add is a race and the limit leaks.
     */
    @Test
    void concurrentCallersCannotExceedTheLimit() throws Exception {
        RateLimiter limiter = new RateLimiter();
        int limit = 50;
        int attempts = 400;
        AtomicInteger granted = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<Callable<Void>> work = java.util.stream.IntStream.range(0, attempts)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        if (limiter.tryAcquire("shared", limit, Duration.ofMinutes(1))) {
                            granted.incrementAndGet();
                        }
                        return null;
                    })
                    .toList();
            for (Future<Void> future : pool.invokeAll(work)) {
                future.get();
            }
        }

        assertThat(granted.get()).isEqualTo(limit);
    }
}
