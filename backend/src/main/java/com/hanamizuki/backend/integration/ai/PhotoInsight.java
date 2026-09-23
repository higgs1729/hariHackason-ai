package com.hanamizuki.backend.integration.ai;

/**
 * Per-photo copy. Everything except {@code photoId} may come back null, and
 * the screen simply hides that row.
 *
 * @param caption 2-5 characters, the label under a tile on screen 03
 * @param place   inferred from signage or landmarks when there is no GPS
 * @param weather 晴れ / 曇り / 雨 / 雪; anything else is discarded
 * @param comment one casual sentence, the コメント row on screen 08
 */
public record PhotoInsight(Long photoId, String caption, String place,
                           String weather, String comment) {
}
