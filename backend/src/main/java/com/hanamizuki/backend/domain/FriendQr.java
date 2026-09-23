package com.hanamizuki.backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A short-lived code shown on screen so a friend standing next to you can scan
 * it. Web Bluetooth and Web NFC are unavailable on iOS Safari, so proximity had
 * to become something the camera can read.
 *
 * <p>The payload is this random token rather than the user id: a photographed
 * QR containing an id would let anyone add that person forever. Ten minutes and
 * single use keep it to the moment it was shown.
 */
@Entity
@Table(name = "friend_qr")
@Getter
@Setter
public class FriendQr extends BaseEntity {

    /** SecureRandom 16 bytes, base64url, 22 characters. */
    @Column(nullable = false, length = 64)
    private String qrToken;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime expireTime;

    /** Non-null means spent; a second scan gets 410. */
    private LocalDateTime usedTime;

    private Long usedUserId;
}
