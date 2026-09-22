package com.hanamizuki.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One-way, unlike {@link Friend}: the blocked user is not told.
 *
 * <p>Blocking also deletes both friendship rows, and the blocked user has to be
 * filtered out of user search, friend requests and album invitations.
 */
@Entity
@Table(name = "block")
@Getter
@Setter
public class Block extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long blockedUserId;

    @Column(length = 256)
    private String blockedUserName;
}
