package com.hanamizuki.backend.domain;

import com.hanamizuki.backend.domain.enums.MemberRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Who may edit an album. Members must already be friends. */
@Entity
@Table(name = "album_member")
@Getter
@Setter
public class AlbumMember extends BaseEntity {

    @Column(nullable = false)
    private Long albumId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private MemberRole memberRole = MemberRole.EDITOR;

    @Column(length = 256)
    private String userName;

    @Column(length = 1024)
    private String userAvatar;

    /** Follows {@code album.title}, so "my albums" reads one table. */
    @Column(length = 512)
    private String albumTitle;
}
