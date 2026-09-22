package com.hanamizuki.backend.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Identity and timestamps, shared by every table.
 *
 * <p>Ids are {@code bigint auto_increment}, not UUIDs. InnoDB clusters on the
 * primary key and repeats it in every secondary index leaf, so a 36-byte key
 * would inflate all three indexes on {@code album_photo}; a random UUID would
 * also scatter inserts across the tree instead of appending to one hot page.
 * The cost is that ids are guessable, which is why public entry points use a
 * random token and every authenticated endpoint checks ownership
 * (03-detailed-design section 1.1).
 *
 * <p>The DDL also defaults these two columns, but Hibernate fills them so the
 * value is present on the object that just got saved, without a re-read.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;
}
