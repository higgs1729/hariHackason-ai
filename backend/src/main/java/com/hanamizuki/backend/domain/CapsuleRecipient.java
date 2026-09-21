package com.hanamizuki.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Who else gets notified when a capsule becomes openable. */
@Entity
@Table(name = "capsule_recipients",
        uniqueConstraints = @UniqueConstraint(name = "uk_cr_capsule_user",
                columnNames = {"capsule_id", "user_id"}))
@Getter
@Setter
public class CapsuleRecipient extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "capsule_id", nullable = false)
    private Capsule capsule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
