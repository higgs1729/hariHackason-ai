package com.hanamizuki.backend.integration.ai;

import java.util.List;

/**
 * 撮影の指示を考える。/ 生成拍照指示。
 *
 * <p>An interface for the same reason {@link AlbumEnricher} is one: the service
 * above it has to behave identically whether this succeeds, fails, or is not
 * configured at all, and that is only testable if it can be replaced.
 */
public interface ShootHinter {

    /** False when no API key is configured, which is not an error. */
    boolean isAvailable();

    /**
     * @param memberCount how many people are in shot
     * @param memberNames their display names, possibly empty
     * @param place       free text, may be null
     * @param mood        free text, may be null
     * @throws RuntimeException on any transport or parsing failure; the caller
     *                          is expected to fall back rather than propagate
     */
    ShootHint suggest(int memberCount, List<String> memberNames, String place, String mood);
}
