package com.hanamizuki.backend.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * {@code aiGenerated} records whether the title and summary came from Claude or
 * from the rule-based fallback. It is what makes graceful degradation visible:
 * when the AI call fails the album is still produced, just flagged false.
 *
 * <p>{@code version} backs the ETag / If-Match handshake; concurrent edits from
 * two album members lose with 409 VERSION_CONFLICT rather than silently
 * overwriting each other.
 */
@Entity
@Table(name = "albums")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
public class Album extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String title;

    @Column(length = 255)
    private String summary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_photo_id")
    private Photo coverPhoto;

    @Column(nullable = false)
    private LocalDate date;

    @Column(length = 64)
    private String place;

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
