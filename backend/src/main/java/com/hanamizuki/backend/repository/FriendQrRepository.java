package com.hanamizuki.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hanamizuki.backend.domain.FriendQr;

public interface FriendQrRepository extends JpaRepository<FriendQr, Long> {

    Optional<FriendQr> findByQrToken(String qrToken);
}
