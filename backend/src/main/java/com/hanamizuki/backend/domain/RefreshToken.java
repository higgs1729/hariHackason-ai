package com.hanamizuki.backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One row per issued refresh token, stored hashed.
 *
 * <p>Refreshing rotates: the used row gets a {@code revokeTime} and a new row
 * inherits the {@code familyId}. If a token that is already revoked comes back,
 * it was copied, so the whole family is revoked rather than just that row.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
public class RefreshToken extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    /** SHA-256 of the token. The plaintext only ever exists in the response. */
    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 64)
    private String familyId;

    @Column(nullable = false)
    private LocalDateTime expireTime;

    /** Non-null means dead: rotated away, logged out, or revoked for reuse. */
    private LocalDateTime revokeTime;

    @Column(length = 512)
    private String userAgent;
}
