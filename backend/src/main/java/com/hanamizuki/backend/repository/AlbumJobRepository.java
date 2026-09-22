package com.hanamizuki.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hanamizuki.backend.domain.AlbumJob;
import com.hanamizuki.backend.domain.enums.JobStatus;

public interface AlbumJobRepository extends JpaRepository<AlbumJob, Long> {

    /** A replayed key returns the original job instead of paying Claude twice. */
    Optional<AlbumJob> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /** One generation at a time per user; a second request gets 409. */
    List<AlbumJob> findByUserIdAndStatusIn(Long userId, List<JobStatus> statuses);
}
