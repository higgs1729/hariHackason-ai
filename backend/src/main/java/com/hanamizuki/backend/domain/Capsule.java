package com.hanamizuki.backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * An album sealed until a date.
 *
 * <p>While sealed, the API must not return {@code capsuleMsg} or the album.
 * That check belongs on the server: hiding the fields in the frontend is not
 * the feature, and anyone with dev tools would see through it.
 *
 * <p>The album fields are a snapshot — what was sealed is the album as it was
 * that day, not whatever it has been renamed to since.
 */
@Entity
@Table(name = "capsule")
@SQLRestriction("isDelete = 0")
@Getter
@Setter
public class Capsule extends BaseEntity {

    @Column(nullable = false)
    private Long albumId;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 512)
    private String albumTitle;

    /** Only used to render the blurred teaser while sealed. */
    @Column(length = 1024)
    private String coverPhotoUrl;

    @Column(nullable = false)
    private int photoNum;

    /** Never leaves the server before {@link #openedTime} is set. */
    @Column(length = 2048)
    private String capsuleMsg;

    @Column(nullable = false)
    private LocalDateTime openTime;

    private LocalDateTime openedTime;

    /**
     * Reserved for "friends must be present": a json array of user ids who have
     * to be scanned in before the capsule opens. Null means time alone decides.
     */
    @Column(length = 1024)
    private String requiredUserIds;

    @Column(nullable = false)
    private int recipientNum;

    @Column(nullable = false)
    private boolean isDelete;
}
