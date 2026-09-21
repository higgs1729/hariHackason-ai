package com.hanamizuki.backend.domain;

import com.hanamizuki.backend.domain.enums.MemberRole;

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

/** Membership is the sole authorization check for every album-scoped route. */
@Entity
@Table(name = "album_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_am_album_user",
                columnNames = {"album_id", "user_id"}),
        indexes = @Index(name = "idx_am_user", columnList = "user_id"))
@Getter
@Setter
public class AlbumMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MemberRole role = MemberRole.EDITOR;
}
