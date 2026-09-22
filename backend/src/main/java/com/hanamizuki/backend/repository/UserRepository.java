package com.hanamizuki.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hanamizuki.backend.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Soft-deleted rows are excluded by the entity's @SQLRestriction. */
    Optional<User> findByUserAccount(String userAccount);

    boolean existsByUserAccount(String userAccount);
}
