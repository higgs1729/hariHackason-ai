package com.hanamizuki.backend.domain.enums;

/**
 * {@code album_job.status}. Stored as the constant name, so no converter.
 *
 * <p>A failed Claude call does not reach FAILED. It falls back to rule-based
 * titles and still ends READY with {@code aiGenerated = false} — the demo must
 * not be able to show an error screen (01-requirements NFR-02).
 */
public enum JobStatus {
    PENDING, CLUSTERING, ENRICHING, READY, FAILED, CANCELLED
}
