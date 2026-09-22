package com.hanamizuki.backend.domain.enums;

/**
 * {@code photo.mediaType}. Stored lowercase.
 *
 * <p>VIDEO is reserved, not accepted: Claude cannot watch video, so an album
 * containing one would need frames extracted first.
 */
public enum MediaType {
    PHOTO, VIDEO
}
