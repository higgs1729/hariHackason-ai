package com.hanamizuki.backend.domain;

import java.time.OffsetDateTime;

import com.hanamizuki.backend.domain.enums.TakenAtSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * A photo as uploaded. Everything here describes the file itself.
 *
 * <p>Per-album presentation — caption, place, weather, the hand-drawn layer —
 * lives on {@link AlbumPhoto}, because the same photo can appear in two albums
 * with different captions and different decoration.
 *
 * <p>{@code width}/{@code height} are stored after rotation is applied, so
 * consumers never have to read the EXIF orientation again.
 */
@Entity
@Table(name = "photos",
        uniqueConstraints = @UniqueConstraint(name = "uk_photos_owner_sha",
                columnNames = {"owner_id", "sha256"}),
        indexes = @Index(name = "idx_photos_owner_taken", columnList = "owner_id, taken_at"))
@SQLRestriction("deleted_at is null")
@Getter
@Setter
public class Photo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "storage_path", nullable = false, length = 255)
    private String storagePath;

    /** Content hash; the unique constraint with owner_id makes re-upload a no-op. */
    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(nullable = false)
    private long bytes;

    @Column(name = "taken_at", nullable = false)
    private OffsetDateTime takenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "taken_at_source", nullable = false, length = 8)
    private TakenAtSource takenAtSource = TakenAtSource.UPLOAD;

    @Column
    private Double lat;

    @Column
    private Double lng;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
