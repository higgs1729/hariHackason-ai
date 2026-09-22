package com.hanamizuki.backend.domain.enums;

/**
 * {@code photo.takenTimeSource}. Stored as the constant name.
 *
 * <p>UPLOAD means the EXIF timestamp was missing and the upload time stood in,
 * which makes the clustering boundary a guess. Screen 08 marks it as such.
 */
public enum TakenTimeSource {
    EXIF, UPLOAD
}
