package com.hanamizuki.backend.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * The hand-drawn layer for one album photo.
 *
 * <p>{@code elementsJson} is the source of truth: the stroke and sticker
 * elements the client drew, kept as vector data so the drawing stays editable
 * and can be re-rendered at any resolution. {@code renderedPath} is a flattened
 * PNG derived from it — cache, not data. Deleting it costs a re-render, nothing
 * more.
 *
 * <p>The primary key is borrowed from {@link AlbumPhoto} via {@code @MapsId},
 * which enforces at most one decoration per album photo without a second
 * unique constraint.
 */
@Entity
@Table(name = "decorations")
@Getter
@Setter
public class Decoration {

    @Id
    @Column(name = "album_photo_id", length = 36)
    private String albumPhotoId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "album_photo_id")
    private AlbumPhoto albumPhoto;

    @Lob
    @Column(name = "elements_json", nullable = false)
    private String elementsJson;

    @Column(name = "rendered_path", length = 255)
    private String renderedPath;

    /** Doubles as the optimistic-lock counter behind ETag / If-Match. */
    @Version
    @Column(nullable = false)
    private int revision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
