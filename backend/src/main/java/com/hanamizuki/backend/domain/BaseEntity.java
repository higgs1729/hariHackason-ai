package com.hanamizuki.backend.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

/**
 * Shared identity and creation timestamp.
 *
 * <p>Ids are UUID v4 strings stored as CHAR(36) rather than a database sequence,
 * so a row can be referenced before it is flushed. {@code Decoration} and
 * {@code Share} do not extend this — their primary keys are borrowed from
 * another table and a share token respectively.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void assignIdAndTimestamp() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
