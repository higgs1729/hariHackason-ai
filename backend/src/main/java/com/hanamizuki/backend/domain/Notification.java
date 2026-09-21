package com.hanamizuki.backend.domain;

import java.time.OffsetDateTime;

import com.hanamizuki.backend.domain.enums.NotificationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The index is on (user_id, read_at) because the unread badge — the only query
 * that runs on every screen — filters on exactly that pair.
 */
@Entity
@Table(name = "notifications",
        indexes = @Index(name = "idx_nt_user_read", columnList = "user_id, read_at"))
@Getter
@Setter
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Lob
    @Column(name = "payload_json")
    private String payloadJson;

    @Column(name = "read_at")
    private OffsetDateTime readAt;
}
