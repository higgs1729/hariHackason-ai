package com.hanamizuki.backend.domain.enums;

/**
 * Whether {@code Photo.takenAt} came from EXIF or was backfilled from the upload
 * time. Clustering treats UPLOAD-sourced timestamps as less trustworthy.
 */
public enum TakenAtSource {
    EXIF,
    UPLOAD
}
