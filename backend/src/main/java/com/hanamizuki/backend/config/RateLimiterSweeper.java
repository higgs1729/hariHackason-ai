package com.hanamizuki.backend.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hanamizuki.backend.common.RateLimiter;

/**
 * Drops rate-limit entries for users who have stopped calling.
 *
 * <p>{@link RateLimiter} keeps one deque per key and nothing removes them, so
 * without this the map grows by one entry for every user who ever hit the
 * route and never shrinks. Small, but it is a leak, and a leak with no upper
 * bound is worth eight lines.
 *
 * <p>On a timer rather than inside {@code tryAcquire}: a burst of requests is
 * exactly when the tidying should not be happening.
 */
@Component
@EnableScheduling
public class RateLimiterSweeper {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterSweeper.class);

    private final RateLimiter rateLimiter;
    private final Duration window;

    public RateLimiterSweeper(RateLimiter rateLimiter,
                              @Value("${app.ai.hint-rate-window-seconds}") long windowSeconds) {
        this.rateLimiter = rateLimiter;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    /**
     * Five minutes, independent of the window. The sweep only has to keep the
     * map from growing without bound; running it exactly one window apart would
     * be false precision, and deriving the interval from the property by
     * string-concatenating a "000" onto it would be worse.
     */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void sweep() {
        int removed = rateLimiter.evictIdle(window);
        if (removed > 0) {
            log.debug("Rate limiter: forgot {} idle keys", removed);
        }
    }
}
