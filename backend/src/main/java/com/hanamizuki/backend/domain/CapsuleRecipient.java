package com.hanamizuki.backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Who else receives a capsule. No rows means the creator only, which is the
 * poster's original 「1年後の自分へ」.
 */
@Entity
@Table(name = "capsule_recipient")
@Getter
@Setter
public class CapsuleRecipient extends BaseEntity {

    @Column(nullable = false)
    private Long capsuleId;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 256)
    private String userName;

    @Column(length = 1024)
    private String userAvatar;

    /** Set once notified, so a retry does not send twice. */
    private LocalDateTime notifyTime;
}
