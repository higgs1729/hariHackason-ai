package com.hanamizuki.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hanamizuki.backend.domain.Friend;
import com.hanamizuki.backend.domain.enums.FriendStatus;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    /** Single-column query, which is why accepting writes both directions. */
    List<Friend> findByUserIdAndStatus(Long userId, FriendStatus status);

    /**
     * 受け取った申請。/ 收到的好友申请。
     *
     * <p>A pending request exists as one row owned by the sender, so the
     * recipient finds it by {@code friendId}, not {@code userId}.
     */
    List<Friend> findByFriendIdAndStatus(Long friendId, FriendStatus status);

    Optional<Friend> findByUserIdAndFriendId(Long userId, Long friendId);

    /** {@code friend.friendUserName} follows the source, on both halves of the pair. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Friend f set f.friendUserName = :userName, f.friendUserAvatar = :userAvatar "
            + "where f.friendId = :userId")
    int renameFriend(@Param("userId") Long userId,
                     @Param("userName") String userName,
                     @Param("userAvatar") String userAvatar);
}
