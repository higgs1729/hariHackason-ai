package com.hanamizuki.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hanamizuki.backend.domain.AlbumPhoto;

public interface AlbumPhotoRepository extends JpaRepository<AlbumPhoto, Long> {

    List<AlbumPhoto> findByAlbumIdOrderByPositionAsc(Long albumId);

    /** Scoped by album so a row from another album cannot be addressed by id alone. */
    Optional<AlbumPhoto> findByIdAndAlbumId(Long id, Long albumId);

    /**
     * Whether this photo is visible to this user through album membership.
     *
     * <p>Owning the photo is checked separately; this covers the case where a
     * friend added you to an album containing someone else's photo.
     */
    @Query("select count(ap) > 0 from AlbumPhoto ap, AlbumMember m "
            + "where ap.photoId = :photoId and m.albumId = ap.albumId and m.userId = :userId")
    boolean isVisibleThroughAlbum(@Param("photoId") Long photoId, @Param("userId") Long userId);
}
