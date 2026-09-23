package com.hanamizuki.backend.common;

import java.util.List;

/**
 * The list envelope every paginated endpoint returns.
 *
 * @param nextCursor null when there is nothing after this page
 * @param total      how many rows match, not how many are in {@code items}
 */
public record PageVo<T>(List<T> items, String nextCursor, long total) {

    public static <T> PageVo<T> of(List<T> items, long total) {
        return new PageVo<>(items, null, total);
    }
}
