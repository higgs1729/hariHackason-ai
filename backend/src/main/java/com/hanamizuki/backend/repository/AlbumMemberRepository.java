package com.hanamizuki.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hanamizuki.backend.domain.AlbumMember;

public interface AlbumMemberRepository extends JpaRepository<AlbumMember, Long> {

    List<AlbumMember> findByAlbumIdOrderByIdAsc(Long albumId);

    /** The album permission check: no row means 403, not 404. */
    Optional<AlbumMember> findByAlbumIdAndUserId(Long albumId, Long userId);
}
