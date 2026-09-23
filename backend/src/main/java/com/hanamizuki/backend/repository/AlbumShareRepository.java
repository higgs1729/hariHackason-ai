package com.hanamizuki.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hanamizuki.backend.domain.AlbumShare;

public interface AlbumShareRepository extends JpaRepository<AlbumShare, Long> {

    Optional<AlbumShare> findByShareToken(String shareToken);

    /** One live link per album; revoking clears it and a new POST mints another. */
    Optional<AlbumShare> findByAlbumIdAndRevokeTimeIsNull(Long albumId);
}
