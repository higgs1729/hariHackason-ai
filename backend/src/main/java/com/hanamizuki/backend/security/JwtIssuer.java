package com.hanamizuki.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.hanamizuki.backend.domain.enums.UserRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Mints and verifies access tokens. Refresh tokens are opaque and live in the database. */
@Component
public class JwtIssuer {

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtIssuer(@Value("${app.jwt.secret}") String secret,
                     @Value("${app.jwt.access-ttl-minutes}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = ttlMinutes;
    }

    public String issue(Long userId, UserRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlMinutes * 60)))
                .signWith(key)
                .compact();
    }

    /**
     * @throws io.jsonwebtoken.ExpiredJwtException when only the clock is the problem —
     *         the caller turns that into TOKEN_EXPIRED so the client refreshes
     *         rather than logging the user out
     * @throws io.jsonwebtoken.JwtException for anything else
     */
    public AuthUser verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthUser(Long.valueOf(claims.getSubject()),
                UserRole.valueOf(claims.get("role", String.class)));
    }
}
