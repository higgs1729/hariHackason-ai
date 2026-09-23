package com.hanamizuki.backend.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanamizuki.backend.api.auth.AuthDtos.UserDto;
import com.hanamizuki.backend.api.friend.FriendVos.FriendRequestVo;
import com.hanamizuki.backend.domain.Friend;
import com.hanamizuki.backend.domain.User;
import com.hanamizuki.backend.domain.enums.FriendStatus;
import com.hanamizuki.backend.error.ApiException;
import com.hanamizuki.backend.error.ErrorCode;
import com.hanamizuki.backend.repository.FriendRepository;
import com.hanamizuki.backend.repository.UserRepository;

/**
 * 友だち。/ 好友关系。
 *
 * <p>The table holds one row per direction. A pending request is a single row
 * owned by the sender; accepting turns it into a pair. That costs one extra
 * insert and buys a friend list that is one indexed single-column read
 * ({@code idx_userId_status}) instead of an OR across two columns, which is
 * the query that actually runs on every screen.
 */
@Service
public class FriendService {

    private final FriendRepository friends;
    private final UserRepository users;

    public FriendService(FriendRepository friends, UserRepository users) {
        this.friends = friends;
        this.users = users;
    }

    /**
     * 友だち一覧。/ 好友列表。
     *
     * <p>Two queries: the friend rows, then the users in one batch. The
     * denormalised {@code friendUserName} would answer a name-and-avatar list
     * on its own, but the contract types this as {@code User[]} — which carries
     * {@code userAccount} and {@code userRole}, neither of which is copied — so
     * the rows are loaded by id rather than half-filled.
     */
    @Transactional(readOnly = true)
    public List<UserDto> list(Long userId) {
        List<Friend> rows = friends.findByUserIdAndStatus(userId, FriendStatus.ACCEPTED);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(Friend::getFriendId).toList();
        Map<Long, User> byId = users.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // Ordered by name so the list does not reshuffle between loads. Rows
        // whose user has since been soft-deleted simply drop out.
        return ids.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(user ->
                        user.getUserName() == null ? user.getUserAccount() : user.getUserName()))
                .map(UserDto::of)
                .toList();
    }

    /**
     * 受け取った申請。/ 收到的申请。
     *
     * <p>Not in the frontend contract, but {@code accept(requestId)} is in it —
     * and without a list the client has no way to learn a request id. Added
     * rather than letting accept be unreachable.
     */
    @Transactional(readOnly = true)
    public List<FriendRequestVo> incoming(Long userId) {
        return friends.findByFriendIdAndStatus(userId, FriendStatus.PENDING).stream()
                .map(row -> {
                    User sender = users.findById(row.getUserId()).orElse(null);
                    return new FriendRequestVo(row.getId(), row.getUserId(),
                            sender == null ? null : sender.getUserName(),
                            sender == null ? null : sender.getUserAvatar());
                })
                .toList();
    }

    /**
     * 友だち申請。/ 发起好友申请。
     *
     * <p>If the other side has already asked, this accepts instead of creating
     * a second pending row. Two people tapping "add" on each other should end
     * up friends, not deadlocked on two requests neither thinks to answer.
     */
    @Transactional
    public void request(Long userId, Long targetId) {
        if (userId.equals(targetId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "You cannot add yourself");
        }
        User target = users.findById(targetId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        Optional<Friend> mirrored = friends.findByUserIdAndFriendId(targetId, userId);
        if (mirrored.isPresent() && mirrored.get().getStatus() == FriendStatus.PENDING) {
            accept(userId, mirrored.get().getId());
            return;
        }
        if (friends.findByUserIdAndFriendId(userId, targetId).isPresent()) {
            throw new ApiException(ErrorCode.FRIEND_REQUEST_EXISTS,
                    "You have already added this person");
        }

        User me = users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        friends.save(link(me, target, FriendStatus.PENDING, userId));
    }

    /**
     * 申請を承認。/ 通过好友申请。
     *
     * <p>Only the recipient can accept, and a request addressed to someone else
     * is 404 rather than 403 — the same reasoning as capsules, since ids are
     * sequential and a 403 would confirm the row exists.
     */
    @Transactional
    public void accept(Long userId, Long requestId) {
        Friend incoming = friends.findById(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such request"));
        if (!incoming.getFriendId().equals(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No such request");
        }
        if (incoming.getStatus() == FriendStatus.ACCEPTED) {
            return;
        }

        User me = users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        User sender = users.findById(incoming.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        incoming.setStatus(FriendStatus.ACCEPTED);
        // Refreshed on accept as well as on rename: the request may have sat
        // unanswered long enough for the name on it to have gone stale.
        incoming.setFriendUserName(me.getUserName());
        incoming.setFriendUserAvatar(me.getUserAvatar());

        // The mirror row is what makes the friend list a single-column read.
        if (friends.findByUserIdAndFriendId(userId, sender.getId()).isEmpty()) {
            friends.save(link(me, sender, FriendStatus.ACCEPTED, sender.getId()));
        }

        me.setFriendNum(me.getFriendNum() + 1);
        sender.setFriendNum(sender.getFriendNum() + 1);
    }

    /** The display columns are follow copies, written here and kept up to date by UserService. */
    private static Friend link(User owner, User other, FriendStatus status, Long requestUserId) {
        Friend row = new Friend();
        row.setUserId(owner.getId());
        row.setFriendId(other.getId());
        row.setStatus(status);
        row.setRequestUserId(requestUserId);
        row.setFriendUserName(other.getUserName());
        row.setFriendUserAvatar(other.getUserAvatar());
        return row;
    }
}
