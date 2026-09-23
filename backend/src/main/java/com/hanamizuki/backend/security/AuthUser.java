package com.hanamizuki.backend.security;

import com.hanamizuki.backend.domain.enums.UserRole;

/**
 * What the JWT carries and what controllers read. Deliberately just an id and a
 * role: anything else would go stale between the token being minted and used.
 */
public record AuthUser(Long userId, UserRole role) {
}
