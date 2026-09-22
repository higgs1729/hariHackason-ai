package com.hanamizuki.backend.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.hanamizuki.backend.domain.enums.NotifyType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Text and sender are frozen at the moment the notification was raised. A
 * notification describes something that happened; renaming the album afterwards
 * should not rewrite the record of it.
 *
 * <p>Type-specific extras go in {@code payload} rather than into new columns,
 * because the list of types only ever grows.
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
public class Notification extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private NotifyType notifyType;

    @Column(length = 512)
    private String title;

    @Column(length = 1024)
    private String content;

    private Long fromUserId;

    @Column(length = 256)
    private String fromUserName;

    @Column(length = 1024)
    private String fromUserAvatar;

    /** ALBUM / CAPSULE / FRIEND. */
    @Column(length = 64)
    private String targetType;

    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private LocalDateTime readTime;
}
