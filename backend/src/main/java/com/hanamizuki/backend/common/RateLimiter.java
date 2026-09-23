package com.hanamizuki.backend.common;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 呼び出し回数の上限。/ 调用频率上限。
 *
 * <p>In memory, per process. That is the right size for one server at a
 * hackathon and the wrong size for anything with two, so it is worth saying
 * plainly: this limits a user on one instance, not a user. Redis is the answer
 * if there is ever a second instance, and nothing here is shaped to make that
 * change hard.
 *
 * <p>A sliding window rather than a fixed one. A fixed window lets a user
 * spend the whole allowance at 11:59:59 and the whole next allowance at
 * 12:00:00, which for a route that costs money per call is the case worth
 * covering.
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Instant>> calls = new ConcurrentHashMap<>();

    /**
     * @param key    scopes the limit; callers pass something like {@code "hint:42"}
     * @param limit  how many calls are allowed in {@code window}
     * @return false when this call is over the limit and should be refused
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);

        // Locked on the one user's deque rather than the map: two users
        // calling at the same moment do not wait for each other.
        Deque<Instant> recent = calls.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
                recent.pollFirst();
            }
            if (recent.size() >= limit) {
                return false;
            }
            recent.addLast(now);
            return true;
        }
    }

    /**
     * Drops keys whose window has fully elapsed.
     *
     * <p>Without this the map grows by one entry per user who ever called and
     * never shrinks. Called from a scheduled sweep rather than on every
     * request, so a burst does not pay for the tidying.
     */
    public int evictIdle(Duration window) {
        Instant cutoff = Instant.now().minus(window);
        int removed = 0;
        for (Map.Entry<String, Deque<Instant>> entry : calls.entrySet()) {
            Deque<Instant> recent = entry.getValue();
            synchronized (recent) {
                while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
                    recent.pollFirst();
                }
                if (recent.isEmpty() && calls.remove(entry.getKey(), recent)) {
                    removed++;
                }
            }
        }
        return removed;
    }
}
