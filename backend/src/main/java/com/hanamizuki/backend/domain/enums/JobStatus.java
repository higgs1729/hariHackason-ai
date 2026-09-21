package com.hanamizuki.backend.domain.enums;

/**
 * Album generation states.
 *
 * <p>CLUSTERING is pure EXIF work and always succeeds. ENRICHING is the Claude
 * call; when it fails the job still reaches READY, with the album flagged
 * {@code aiGenerated = false} and rule-based titles. FAILED is reserved for
 * losing the photos themselves, not for losing the AI.
 *
 * <p>Note: {@code 03-detailed-design.md} cites "section 3.1" for these values,
 * but that section is the conventions table and does not list them. These are
 * reconstructed from ENRICHING and READY as used elsewhere in the document —
 * confirm before relying on the exact spelling.
 */
public enum JobStatus {
    PENDING,
    CLUSTERING,
    ENRICHING,
    READY,
    FAILED
}
