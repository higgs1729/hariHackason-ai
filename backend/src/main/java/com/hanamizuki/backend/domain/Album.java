package com.hanamizuki.backend.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * {@code aiGenerated} says whether the title and summary came from Claude or
 * from the rule-based fallback. It is what makes graceful degradation visible:
 * a failed AI call still produces an album, just flagged false.
 *
 * <p>The cover URLs, the counters and {@code shareToken} are copies. They exist
 * so the album list — the most frequent query in the app — reads one table.
 * Sources and sync points are in 03-detailed-design section 1.2.2.
 */
@Entity
@Table(name = "album")
@SQLRestriction("isDelete = 0")
@Getter
@Setter
public class Album extends BaseEntity {

    @Column(length = 512)
    private String title;

    @Column(length = 1024)
    private String summary;

    private Long coverPhotoId;

    /** Follows {@code coverPhotoId}. */
    @Column(length = 1024)
    private String coverPhotoUrl;

    @Column(length = 1024)
    private String coverThumbUrl;

    private LocalDate albumDate;

    @Column(length = 256)
    private String place;

    @Column(nullable = false)
    private boolean aiGenerated;

    /** Which model wrote the copy, for later comparison. */
    @Column(length = 64)
    private String aiModel;

    /** Returned as the ETag; a stale If-Match is rejected with 409. */
    @Version
    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 256)
    private String userName;

    @Column(nullable = false)
    private int photoNum;

    @Column(nullable = false)
    private int memberNum = 1;

    @Column(nullable = false)
    private int viewNum;

    /** Copy of the live share token, or null when the album is not shared. */
    @Column(length = 64)
    private String shareToken;

    @Column(nullable = false)
    private boolean isDelete;
}
