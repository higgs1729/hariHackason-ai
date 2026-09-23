package com.hanamizuki.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hanamizuki.backend.domain.Photo;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    List<Photo> findByUserIdOrderByTakenTimeAsc(Long userId, Limit limit);

    /** albumNum = 0 is the definition of "not in any album yet" (screen 03). */
    List<Photo> findByUserIdAndAlbumNumOrderByTakenTimeAsc(Long userId, int albumNum, Limit limit);

    /** Same uploader, same bytes: a repeat upload, not a new photo. */
    Optional<Photo> findByUserIdAndSha256(Long userId, String sha256);

    /** {@code photo.userName} is a follow copy of the uploader's name. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Photo p set p.userName = :userName where p.userId = :userId")
    int renameUploader(@Param("userId") Long userId, @Param("userName") String userName);
}
