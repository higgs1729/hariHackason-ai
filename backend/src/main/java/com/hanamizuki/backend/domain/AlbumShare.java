package com.hanamizuki.backend.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A public, unauthenticated view of one album.
 *
 * <p>{@code shareToken} is random rather than the album id because this page
 * needs no login: a guessable identifier would expose every album to anyone
 * counting upwards.
 *
 * <p>The album fields here are a snapshot taken when the link was created and
 * deliberately do not follow the album afterwards. A link already sitting in a
 * LINE conversation should not change what it says. It also means the crawler's
 * two requests touch exactly one row.
 */
@Entity
@Table(name = "album_share")
@Getter
@Setter
public class AlbumShare extends BaseEntity {

    /** SecureRandom 24 bytes, base64url, 32 characters. */
    @Column(nullable = false, length = 64)
    private String shareToken;

    @Column(nullable = false)
    private Long albumId;

    @Column(nullable = false)
    private Long userId;

    /** og:title */
    @Column(length = 512)
    private String albumTitle;

    /** og:description */
    @Column(length = 1024)
    private String albumSummary;

    private LocalDate albumDate;

    @Column(length = 1024)
    private String coverPhotoUrl;

    @Column(nullable = false)
    private int photoNum;

    /** 1200x630 collage, rendered on first crawl and cached. */
    @Column(length = 1024)
    private String ogImagePath;

    @Column(nullable = false)
    private int viewNum;

    private LocalDateTime expireTime;

    /** Non-null answers 410, so the UI can say "no longer shared". */
    private LocalDateTime revokeTime;
}
