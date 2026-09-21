package com.hanamizuki.backend.domain;

import java.time.OffsetDateTime;

import com.hanamizuki.backend.domain.enums.FriendshipStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * While PENDING there is one row, requester to target. On accept the reversed
 * row is inserted too, so listing a user's friends is a single-column indexed
 * query rather than {@code where user_id = ? or friend_id = ?}, which cannot
 * use an index.
 */
@Entity
@Table(name = "friendships",
        uniqueConstraints = @UniqueConstraint(name = "uk_fs_user_friend",
                columnNames = {"user_id", "friend_id"}),
        indexes = @Index(name = "idx_fs_user_status", columnList = "user_id, status"))
@Getter
@Setter
public class Friendship extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "friend_id", nullable = false)
    private User friend;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FriendshipStatus status = FriendshipStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;
}
