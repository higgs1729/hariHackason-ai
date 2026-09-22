package com.hanamizuki.backend.integration.image;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What we take from EXIF, and nothing else.
 *
 * @param takenTime   DateTimeOriginal, or null when the file has none
 * @param latitude    null unless the camera recorded a position
 * @param longitude   as above
 * @param orientation 1..8, or null. Only kept for the record: the pixels are
 *                    rotated on ingest, so nothing downstream should consult it
 */
public record ExifMetadata(LocalDateTime takenTime, BigDecimal latitude,
                           BigDecimal longitude, Integer orientation) {

    public static ExifMetadata empty() {
        return new ExifMetadata(null, null, null, null);
    }
}
