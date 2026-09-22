package com.hanamizuki.backend.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.hanamizuki.backend.domain.enums.MediaType;
import com.hanamizuki.backend.domain.enums.TakenTimeSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * The photo itself. What a photo means inside one particular album — its
 * caption, its position, its drawing — lives on {@link AlbumPhoto}, because the
 * same shot can appear in two albums decorated differently.
 *
 * <p>{@code filePath} points at a file that has already been rotated to match
 * its EXIF orientation and then stripped of EXIF entirely. Doing it once on
 * ingest means thumbnails, Claude, the decoration canvas and the collage all
 * see upright pixels; leaving the flag in place would mean four consumers each
 * having to honour it, and one of them eventually not doing so. Stripping also
 * keeps a shared photo from carrying the photographer's home coordinates.
 */
@Entity
@Table(name = "photo")
@SQLRestriction("isDelete = 0")
@Getter
@Setter
public class Photo extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    /** Follows {@code user.userName}. */
    @Column(length = 256)
    private String userName;

    @Column(length = 512)
    private String picName;

    @Column(nullable = false, length = 1024)
    private String filePath;

    @Column(length = 1024)
    private String thumbPath;

    private Integer picWidth;

    private Integer picHeight;

    /** width / height, stored so layout code does not recompute it per tile. */
    private Double picScale;

    private Long picSize;

    @Column(length = 32)
    private String picFormat;

    /** Same user + same hash means a repeat upload. */
    @Column(length = 64)
    private String sha256;

    /** VIDEO is reserved; uploads are rejected for now. */
    @Column(nullable = false, length = 32)
    private MediaType mediaType = MediaType.PHOTO;

    private Integer durationSec;

    /** EXIF DateTimeOriginal, or the upload time when there is none. */
    private LocalDateTime takenTime;

    @Column(nullable = false, length = 32)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private TakenTimeSource takenTimeSource = TakenTimeSource.UPLOAD;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    /** Kept for the record only. The pixels are already upright. */
    private Integer exifOrientation;

    /** How many albums use this photo. Zero is the definition of "unassigned". */
    @Column(nullable = false)
    private int albumNum;

    @Column(nullable = false)
    private boolean isDelete;
}
