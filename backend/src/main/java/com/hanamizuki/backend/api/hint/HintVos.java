package com.hanamizuki.backend.api.hint;

import java.util.List;

/** Shapes for /api/hints, per 05-backend-answers section 3.1. */
public final class HintVos {

    private HintVos() {
    }

    /**
     * @param memberCount how many people will be in shot; falls back to the
     *                    length of {@code memberNames} when absent
     * @param memberNames display names, optional — the hint works without them
     * @param place       free text from the camera screen, optional
     * @param mood        free text from the camera screen, optional
     */
    public record ShootHintRequest(Integer memberCount, List<String> memberNames,
                                   String place, String mood) {
    }

    /**
     * @param aiGenerated 1 when Claude wrote this, 0 for the canned text.
     *                    Present for logs and nothing else — the camera screen
     *                    must render both identically. The whole point of the
     *                    fallback is that the AI moment never becomes the
     *                    moment the AI fell over.
     */
    public record ShootHintVo(String hint, List<String> poses, int aiGenerated) {
    }
}
