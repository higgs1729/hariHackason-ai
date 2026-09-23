package com.hanamizuki.backend.domain;

import java.time.LocalDateTime;

import com.hanamizuki.backend.domain.enums.JobStatus;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One run of "make albums out of these photos".
 *
 * <p>The client polls this rather than an album, because one request can
 * produce several: thirty photos spanning two afternoons are two albums, not
 * one.
 *
 * <p>A failed Claude call does not fail the job. It falls back to rule-based
 * titles and still finishes READY with {@code aiGenerated = false}. FAILED is
 * reserved for bad input.
 */
@Entity
@Table(name = "album_job")
@Getter
@Setter
public class AlbumJob extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    /** json array. */
    @Column(nullable = false, length = 4096)
    private String photoIds;

    /** Copy of the array length, so a job list does not parse json. */
    @Column(nullable = false)
    private int photoNum;

    /** json array, filled in on completion. */
    @Column(length = 1024)
    private String albumIds;

    @Column(nullable = false)
    private int albumNum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobStatus status = JobStatus.PENDING;

    /** 0..100. Without it the client can only spin for up to thirty seconds. */
    @Column(nullable = false)
    private int progress;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private boolean aiGenerated;

    @Column(length = 64)
    private String errorCode;

    /** Kept for the log, not sent to the client. */
    @Column(length = 1024)
    private String errorMsg;

    /**
     * Supplied by the client and unique per user. A request that times out at
     * the network layer may never have arrived, so there is no running job for
     * the concurrency check to catch; replaying the same key returns the
     * original job instead of starting a second Claude run.
     */
    @Column(length = 64)
    private String idempotencyKey;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    private Long costMs;
}
