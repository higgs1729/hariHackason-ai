package com.hanamizuki.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * A photo's placement and description inside one album.
 *
 * <p>Not a plain join table. Everything the user or the AI writes about a photo
 * in album context lands here, which is what lets the same {@link Photo} carry
 * different captions and different decoration in two albums.
 *
 * <p>Ids are exposed over the wire with an {@code ap_} prefix so a client
 * cannot mistake one for a photo id — see section 3.1 of the detailed design.
 */
@Entity
@Table(name = "album_photos",
        uniqueConstraints = @UniqueConstraint(name = "uk_ap_album_photo",
                columnNames = {"album_id", "photo_id"}),
        indexes = @Index(name = "idx_ap_album_pos", columnList = "album_id, position"))
@Getter
@Setter
public class AlbumPhoto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photo_id", nullable = false)
    private Photo photo;

    @Column(nullable = false)
    private int position;

    /** The short tile label, e.g. 「放課後」. Truncated to 10 characters on intake. */
    @Column(length = 16)
    private String caption;

    @Column(length = 64)
    private String place;

    /** One of 晴れ / 曇り / 雨 / 雪, or null when the AI could not tell. */
    @Column(length = 8)
    private String weather;

    @Column(length = 255)
    private String comment;

    /** No automatic source exists for this — user input only. */
    @Column(length = 128)
    private String music;

    @Version
    @Column(nullable = false)
    private int version;
}
