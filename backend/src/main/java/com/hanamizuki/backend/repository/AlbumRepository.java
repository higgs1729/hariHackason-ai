package com.hanamizuki.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hanamizuki.backend.domain.Album;

public interface AlbumRepository extends JpaRepository<Album, Long> {
}
