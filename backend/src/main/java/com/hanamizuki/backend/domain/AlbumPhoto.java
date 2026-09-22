package com.hanamizuki.backend.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * One photo as it appears in one album. Not a join table: the caption, the
 * ordering and the drawing belong to the pairing, not to the photo, so the same
 * shot can be decorated one way here and another way in a second album.
 *
 * <p>Every decoration and metadata endpoint addresses this id, never
 * {@code photoId}.
 *
 * <p>The photo's own display fields are copied in at insert time, which is what
 * lets a whole album render from two queries and no joins. They are a snapshot
 * rather than a follow because a stored file path never changes.
 */
@Entity
@Table(name = "album_photo")
@SQLRestriction("isDelete = 0")
@Getter
@Setter
public class AlbumPhoto extends BaseEntity {

    @Column(nullable = false)
    private Long albumId;

    @Column(nullable = false)
    private Long photoId;

    @Column(nullable = false)
    private int position;

    @Column(length = 1024)
    private String photoUrl;

    @Column(length = 1024)
    private String thumbUrl;

    private Integer picWidth;

    private Integer picHeight;

    private Double picScale;

    /** Screen 08's 時間 row. */
    private LocalDateTime takenTime;

    /** Screen 03's tile label, 10 characters at most. */
    @Column(length = 256)
    private String caption;

    @Column(length = 256)
    private String place;

    /** 晴れ / 曇り / 雨 / 雪. Anything else from the model is stored as null. */
    @Column(length = 64)
    private String weather;

    @Column(length = 1024)
    private String photoComment;

    /** No automatic source exists; the user types it or the row stays hidden. */
    @Column(length = 512)
    private String music;

    /**
     * The drawing itself: strokes, text, stickers and filters, with every
     * coordinate normalised to 0..1.
     *
     * <p>This is the record, and the two paths below are renderings of it. A
     * flattened PNG on its own cannot be undone one stroke at a time, cannot
     * have a sticker dragged, and cannot have its text retyped — and screen 04
     * offers all three. Normalised coordinates matter because the phone canvas
     * is 390px wide and the OG image is 1200px.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private String overlayData;

    /** Transparent PNG of {@link #overlayData}. A cache; rebuildable. */
    @Column(length = 1024)
    private String overlayPath;

    /** Photo and drawing flattened. What sharing and the OG collage use. */
    @Column(length = 1024)
    private String compositePath;

    private Long overlayUserId;

    @Column(length = 256)
    private String overlayUserName;

    /** Also the cache-busting parameter on the composite URL. */
    private LocalDateTime overlayUpdateTime;

    /** Reserved for the five-second voice memo. Not implemented. */
    @Column(length = 1024)
    private String audioPath;

    private Integer audioDurationSec;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private boolean isDelete;
}
