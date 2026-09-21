package com.hanamizuki.backend.domain;

import java.time.OffsetDateTime;

import com.hanamizuki.backend.domain.enums.JobStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * One album-generation run.
 *
 * <p>The client polls this rather than an album, because a single request can
 * produce several albums — photos spanning two afternoons cluster into two.
 * {@code albumIds} is therefore a JSON array, not a foreign key.
 *
 * <p>{@code idempotencyKey} is unique per user so a retried POST returns the
 * original job instead of starting a second, expensive Claude run.
 */
@Entity
@Table(name = "generate_jobs",
        uniqueConstraints = @UniqueConstraint(name = "uk_gj_user_idem",
                columnNames = {"user_id", "idempotency_key"}),
        indexes = @Index(name = "idx_gj_user_status", columnList = "user_id, status"))
@Getter
@Setter
public class GenerateJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status = JobStatus.PENDING;

    @Column(nullable = false)
    private int progress;

    @Lob
    @Column(name = "photo_ids", nullable = false)
    private String photoIds;

    @Lob
    @Column(name = "album_ids")
    private String albumIds;

    @Column(name = "error_code", length = 32)
    private String errorCode;

    @Column(name = "idempotency_key", nullable = false, length = 36)
    private String idempotencyKey;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;
}
