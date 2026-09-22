package com.hanamizuki.backend.domain;

import com.hanamizuki.backend.domain.enums.FriendStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Accepting writes both directions, so the friend list stays a single-column
 * query: {@code where userId = ? and status = 1}. While pending there is only
 * the requester's row.
 *
 * <p>{@code friendUserName} and {@code friendUserAvatar} follow
 * {@code user}: renaming a user updates every row that names them.
 */
@Entity
@Table(name = "friend")
@Getter
@Setter
public class Friend extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long friendId;

    @Column(nullable = false)
    private FriendStatus status = FriendStatus.PENDING;

    /** Which side asked. Both rows carry the same value. */
    @Column(nullable = false)
    private Long requestUserId;

    @Column(length = 256)
    private String friendUserName;

    @Column(length = 1024)
    private String friendUserAvatar;
}
