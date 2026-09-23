package com.hanamizuki.backend.integration.image;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;

/**
 * Reads EXIF before it gets stripped.
 *
 * <p>Order matters on ingest: read, then rotate, then strip. A file whose
 * metadata is unreadable is not an error — plenty of images have none, and a
 * missing capture time just falls back to the upload time.
 */
@Component
public class ExifReader {

    private static final Logger log = LoggerFactory.getLogger(ExifReader.class);

    public ExifMetadata read(byte[] content) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(content));
            return new ExifMetadata(takenTime(metadata), latitude(metadata),
                    longitude(metadata), orientation(metadata));
        } catch (Exception e) {
            log.debug("No readable EXIF, falling back to defaults", e);
            return ExifMetadata.empty();
        }
    }

    private LocalDateTime takenTime(Metadata metadata) {
        ExifSubIFDDirectory dir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (dir == null) {
            return null;
        }
        Date date = dir.getDateOriginal();
        return date == null ? null
                : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private BigDecimal latitude(Metadata metadata) {
        GpsDirectory dir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        var location = dir == null ? null : dir.getGeoLocation();
        return location == null || location.isZero() ? null
                : BigDecimal.valueOf(location.getLatitude());
    }

    private BigDecimal longitude(Metadata metadata) {
        GpsDirectory dir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        var location = dir == null ? null : dir.getGeoLocation();
        return location == null || location.isZero() ? null
                : BigDecimal.valueOf(location.getLongitude());
    }

    private Integer orientation(Metadata metadata) {
        ExifIFD0Directory dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (dir == null || !dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
            return null;
        }
        try {
            return dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
        } catch (Exception e) {
            return null;
        }
    }
}
