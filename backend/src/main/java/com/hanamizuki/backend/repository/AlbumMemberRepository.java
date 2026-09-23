package com.hanamizuki.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hanamizuki.backend.domain.AlbumMember;

public interface AlbumMemberRepository extends JpaRepository<AlbumMember, Long> {

    List<AlbumMember> findByAlbumIdOrderByIdAsc(Long albumId);

    /** The album permission check: no row means 403, not 404. */
    Optional<AlbumMember> findByAlbumIdAndUserId(Long albumId, Long userId);

    boolean existsByAlbumIdAndUserId(Long albumId, Long userId);

    /**
     * 自分が入っているアルバム。/ 我参与的相册。
     *
     * <p>The album list is driven off this table rather than {@code album.userId}
     * so an album someone added me to appears on my home screen too.
     */
    List<AlbumMember> findByUserIdOrderByIdDesc(Long userId);

    /**
     * {@code album_member.userName} is a follow copy, so a rename has to reach
     * every row that carries it. A bulk update rather than a load-and-set loop:
     * a user in twenty albums would otherwise be twenty selects.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AlbumMember m set m.userName = :userName, m.userAvatar = :userAvatar "
            + "where m.userId = :userId")
    int renameMember(@Param("userId") Long userId,
                     @Param("userName") String userName,
                     @Param("userAvatar") String userAvatar);

    /** Same, for the album title copied onto every member row. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AlbumMember m set m.albumTitle = :title where m.albumId = :albumId")
    int retitle(@Param("albumId") Long albumId, @Param("title") String title);
}
