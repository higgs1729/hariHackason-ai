package com.hanamizuki.backend.integration.ai;

import java.util.List;

import com.hanamizuki.backend.domain.Photo;

/**
 * Writes the words for an album.
 *
 * <p>Implementations throw on failure rather than returning something empty.
 * The caller is expected to catch and fall back to rule-based titles — an
 * album still gets made, it just says less.
 */
public interface AlbumEnricher {

    /** @throws RuntimeException on any failure; the caller degrades */
    AlbumDraft enrich(List<Photo> cluster);

    /** False when there is no API key, so the caller can skip the attempt. */
    boolean isAvailable();

    /** Stored in {@code album.aiModel}; null means the configured {@code app.ai.model}. */
    default String modelLabel() {
        return null;
    }
}
