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

    /**
     * What actually wrote the copy, recorded on {@code album.aiModel}.
     *
     * <p>Asked of the implementation rather than read from a property: the
     * provider and the model are chosen together, and a service reading
     * {@code app.ai.model} while OpenRouter served the request from
     * {@code anthropic/claude-sonnet-5} would file the wrong answer under the
     * right-looking name.
     */
    String model();
}
