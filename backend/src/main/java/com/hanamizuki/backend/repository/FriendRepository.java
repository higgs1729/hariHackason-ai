package com.hanamizuki.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hanamizuki.backend.domain.Friend;
import com.hanamizuki.backend.domain.enums.FriendStatus;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    /** Single-column query, which is why accepting writes both directions. */
    List<Friend> findByUserIdAndStatus(Long userId, FriendStatus status);
}
