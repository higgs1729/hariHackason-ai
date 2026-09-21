package com.hanamizuki.backend.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Table is {@code users}, not {@code user} — the latter is reserved in H2.
 *
 * <p>Soft-deleted. The {@link SQLRestriction} makes every query and association
 * load skip deleted rows automatically, so callers cannot forget the condition.
 */
@Entity
@Table(name = "users", indexes = @Index(name = "idx_users_email", columnList = "email"))
@SQLRestriction("deleted_at is null")
@Getter
@Setter
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt output is always 60 characters. */
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 32)
    private String displayName;

    @Column(name = "avatar_path", length = 255)
    private String avatarPath;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
