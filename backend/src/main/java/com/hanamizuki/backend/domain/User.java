package com.hanamizuki.backend.domain;

import com.hanamizuki.backend.domain.enums.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Login is {@code userAccount} + password. There is no email column and no
 * password reset — sending mail is infrastructure this project does not have.
 *
 * <p>The four {@code *Num} columns are denormalised counters, not derived on
 * read. Their sources and the moments they change are listed in
 * 03-detailed-design section 1.2.2; if they drift,
 * {@code POST /api/dev/rebuild-denorm} recomputes them.
 */
@Entity
@Table(name = "user")
@SQLRestriction("isDelete = 0")
@Getter
@Setter
public class User extends BaseEntity {

    @Column(nullable = false, length = 256)
    private String userAccount;

    /** BCrypt cost 10. Never returned by any endpoint. */
    @Column(nullable = false, length = 512)
    private String userPassword;

    /** Reserved for LINE Login, unused for now. */
    @Column(length = 256)
    private String lineUserId;

    @Column(length = 256)
    private String userName;

    @Column(length = 1024)
    private String userAvatar;

    @Column(length = 512)
    private String userProfile;

    /** {@code BAN} blocks login with 403 ACCOUNT_BANNED. */
    @Column(nullable = false, length = 256)
    private UserRole userRole = UserRole.USER;

    @Column(nullable = false)
    private int photoNum;

    @Column(nullable = false)
    private int albumNum;

    @Column(nullable = false)
    private int friendNum;

    @Column(nullable = false)
    private int capsuleNum;

    @Column(nullable = false)
    private boolean isDelete;
}
