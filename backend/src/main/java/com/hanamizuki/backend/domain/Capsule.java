package com.hanamizuki.backend.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A sealed album with a message to the future.
 *
 * <p>While {@code openedAt} is null and {@code now < openAt}, neither
 * {@code message} nor the album contents may leave the server. That check
 * belongs in {@code CapsuleService}, which must not even assemble the payload
 * before the seal breaks — hiding the fields in the frontend is not the
 * feature, and anyone with the browser console open would see through it.
 */
@Entity
@Table(name = "capsules", indexes = @Index(name = "idx_cp_user", columnList = "user_id"))
@Getter
@Setter
public class Capsule extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false, unique = true)
    private Album album;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 500)
    private String message;

    @Column(name = "open_at", nullable = false)
    private OffsetDateTime openAt;

    @Column(name = "opened_at")
    private OffsetDateTime openedAt;
}
