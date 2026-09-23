package com.hanamizuki.backend.error;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The single response body for every failure.
 *
 * @param code    stable identifier the frontend branches on
 * @param message human-readable, for logs and developers — not for branching
 * @param details field-level messages for VALIDATION_FAILED, or {@code current}
 *                for VERSION_CONFLICT; null otherwise
 * @param traceId correlates the response with the server log line
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, Object> details, String traceId) {
}
