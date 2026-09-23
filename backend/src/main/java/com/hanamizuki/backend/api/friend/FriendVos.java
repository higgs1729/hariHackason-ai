package com.hanamizuki.backend.api.friend;

/** Shapes for /api/friends. */
public final class FriendVos {

    private FriendVos() {
    }

    /**
     * 受け取った申請の一行。/ 收到的一条申请。
     *
     * @param id the {@code friend} row id, which is what {@code accept} takes
     */
    public record FriendRequestVo(Long id, Long userId, String userName, String userAvatar) {
    }

    /** Add by id — the search screen resolves a name to an id first. */
    public record FriendRequestBody(Long userId) {
    }
}
