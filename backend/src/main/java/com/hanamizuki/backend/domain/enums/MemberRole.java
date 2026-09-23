package com.hanamizuki.backend.domain.enums;

/**
 * {@code album_member.memberRole}. Stored lowercase.
 *
 * <p>There is no VIEWER: screen 05 has no read-only member, the share link
 * plays that role instead.
 */
public enum MemberRole {
    OWNER, EDITOR
}
