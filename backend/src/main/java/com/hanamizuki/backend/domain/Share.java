package com.hanamizuki.backend.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A capability URL for one album.
 *
 * <p>The primary key is a 128-bit base64url token, not the album id: the share
 * page is public and unauthenticated, so a guessable identifier would let
 * anyone walk other people's albums by incrementing a number.
 *
 * <p>Revocation is a timestamp rather than a delete, so a dead link can answer
 * 410 SHARE_REVOKED — "no longer shared" reads very differently from "not
 * found" to someone who was legitimately sent the link.
 */
@Entity
@Table(name = "shares")
@Getter
@Setter
public class Share {

    @Id
    @Column(length = 24)
    private String token;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false, unique = true)
    private Album album;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
