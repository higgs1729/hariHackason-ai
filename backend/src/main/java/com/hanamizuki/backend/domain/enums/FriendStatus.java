package com.hanamizuki.backend.domain.enums;

/** {@code friend.status}, stored as tinyint 0/1. */
public enum FriendStatus {
    PENDING(0), ACCEPTED(1);

    private final int code;

    FriendStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static FriendStatus of(int code) {
        return code == 1 ? ACCEPTED : PENDING;
    }
}
