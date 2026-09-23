package com.hanamizuki.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hanamizuki.backend.domain.Capsule;

public interface CapsuleRepository extends JpaRepository<Capsule, Long> {

    List<Capsule> findByUserIdOrderByOpenTimeAsc(Long userId);

    /** One capsule per album: sealing the same memories twice means nothing. */
    Optional<Capsule> findByAlbumId(Long albumId);
}
