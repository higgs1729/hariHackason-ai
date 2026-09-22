package com.hanamizuki.backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hanamizuki.backend.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Kills an entire rotation family at once. Used when a token that was
     * already rotated away comes back: the only way that happens is if someone
     * kept a copy, and there is no way to tell the thief from the owner, so
     * both get logged out.
     */
    @Modifying
    @Query("update RefreshToken t set t.revokeTime = :now "
            + "where t.familyId = :familyId and t.revokeTime is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update RefreshToken t set t.revokeTime = :now "
            + "where t.userId = :userId and t.revokeTime is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
