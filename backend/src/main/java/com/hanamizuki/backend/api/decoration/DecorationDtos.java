package com.hanamizuki.backend.api.decoration;

import java.time.OffsetDateTime;

import tools.jackson.databind.JsonNode;

public final class DecorationDtos {

    private DecorationDtos() {
    }

    /**
     * {@code elements} is passed through untouched. Strokes, text, stickers and
     * filters are the frontend's vocabulary; the backend only has to store the
     * array and hand it back.
     */
    public record DecorationVo(JsonNode elements, Long overlayUserId,
                               OffsetDateTime overlayUpdateTime, int version) {
    }

    public record DecorationPutRequest(JsonNode elements) {
    }
}
