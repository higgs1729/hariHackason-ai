package com.hanamizuki.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** One-way block. Checked before friend requests and album invitations. */
@Entity
@Table(name = "blocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_bl_user_blocked",
                columnNames = {"user_id", "blocked_user_id"}))
@Getter
@Setter
public class Block extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_user_id", nullable = false)
    private User blockedUser;
}
