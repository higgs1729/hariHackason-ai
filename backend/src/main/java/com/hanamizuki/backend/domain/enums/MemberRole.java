package com.hanamizuki.backend.domain.enums;

/** VIEWER attempting a write yields ROLE_INSUFFICIENT (403). */
public enum MemberRole {
    OWNER,
    EDITOR,
    VIEWER
}
