package com.hanamizuki.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hanamizuki.backend.domain.Album;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    /**
     * 新しい順。/ 按相册日期倒序。
     *
     * <p>Ordered by {@code albumDate} rather than {@code id}: the list is a
     * timeline of when the photos were taken, and a batch uploaded today can
     * contain last month's trip.
     */
    List<Album> findByIdInOrderByAlbumDateDescIdDesc(Collection<Long> ids);

    /** {@code album.userName} is a follow copy of the creator's name. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Album a set a.userName = :userName where a.userId = :userId")
    int renameCreator(@Param("userId") Long userId, @Param("userName") String userName);
}
