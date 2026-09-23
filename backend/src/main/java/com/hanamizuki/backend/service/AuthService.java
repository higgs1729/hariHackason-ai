package com.hanamizuki.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanamizuki.backend.api.auth.AuthDtos.AuthResponse;
import com.hanamizuki.backend.api.auth.AuthDtos.LoginRequest;
import com.hanamizuki.backend.api.auth.AuthDtos.RegisterRequest;
import com.hanamizuki.backend.api.auth.AuthDtos.UserDto;
import com.hanamizuki.backend.domain.RefreshToken;
import com.hanamizuki.backend.domain.User;
import com.hanamizuki.backend.domain.enums.UserRole;
import com.hanamizuki.backend.error.ApiException;
import com.hanamizuki.backend.error.ErrorCode;
import com.hanamizuki.backend.repository.RefreshTokenRepository;
import com.hanamizuki.backend.repository.UserRepository;
import com.hanamizuki.backend.security.JwtIssuer;

/**
 * Registration, login, and refresh-token rotation.
 *
 * <p>Access tokens are short and self-contained; refresh tokens are long,
 * opaque, and stored hashed. Only the hash is persisted, so a database dump
 * does not hand over usable sessions.
 */
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final long refreshTtlDays;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer,
                       @Value("${app.jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.refreshTtlDays = refreshTtlDays;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String userAgent) {
        if (users.existsByUserAccount(request.userAccount())) {
            throw new ApiException(ErrorCode.ACCOUNT_EXISTS, "This account name is taken");
        }
        User user = new User();
        user.setUserAccount(request.userAccount());
        user.setUserPassword(passwordEncoder.encode(request.userPassword()));
        user.setUserName(request.userName() == null || request.userName().isBlank()
                ? request.userAccount()
                : request.userName());
        user.setUserRole(UserRole.USER);
        users.save(user);

        return issuePair(user, UUID.randomUUID().toString(), userAgent);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent) {
        User user = users.findByUserAccount(request.userAccount())
                .orElseThrow(() -> new ApiException(ErrorCode.CREDENTIALS_INVALID,
                        "Account or password is wrong"));

        if (!passwordEncoder.matches(request.userPassword(), user.getUserPassword())) {
            // Same code and message as an unknown account: telling the two
            // apart would confirm which account names exist.
            throw new ApiException(ErrorCode.CREDENTIALS_INVALID, "Account or password is wrong");
        }
        if (user.getUserRole() == UserRole.BAN) {
            throw new ApiException(ErrorCode.ACCOUNT_BANNED, "This account is suspended");
        }

        return issuePair(user, UUID.randomUUID().toString(), userAgent);
    }

    /**
     * Rotates: the presented token dies and a replacement is issued into the
     * same family.
     *
     * <p>A token that was already rotated away should never come back. When one
     * does, the only explanation is that a copy exists, and there is no way to
     * tell which holder is the owner — so the whole family is revoked and both
     * sides have to log in again.
     */
    @Transactional
    public AuthResponse refresh(String presented, String userAgent) {
        String hash = sha256(presented);
        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Unknown refresh token"));

        LocalDateTime now = LocalDateTime.now();

        if (stored.getRevokeTime() != null) {
            refreshTokens.revokeFamily(stored.getFamilyId(), now);
            throw new ApiException(ErrorCode.TOKEN_INVALID,
                    "This refresh token was already used; all sessions have been ended");
        }
        if (stored.getExpireTime().isBefore(now)) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Refresh token has expired");
        }

        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "User no longer exists"));
        if (user.getUserRole() == UserRole.BAN) {
            refreshTokens.revokeAllForUser(user.getId(), now);
            throw new ApiException(ErrorCode.ACCOUNT_BANNED, "This account is suspended");
        }

        stored.setRevokeTime(now);
        return issuePair(user, stored.getFamilyId(), userAgent);
    }

    @Transactional
    public void logout(String presented) {
        // An unknown or already-dead token is not an error: the caller wanted
        // the session gone, and it is gone.
        refreshTokens.findByTokenHash(sha256(presented))
                .filter(token -> token.getRevokeTime() == null)
                .ifPresent(token -> token.setRevokeTime(LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return UserDto.of(users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND)));
    }

    private AuthResponse issuePair(User user, String familyId, String userAgent) {
        String refreshValue = randomToken();

        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(sha256(refreshValue));
        token.setFamilyId(familyId);
        token.setExpireTime(LocalDateTime.now().plusDays(refreshTtlDays));
        token.setUserAgent(userAgent == null ? null
                : userAgent.substring(0, Math.min(userAgent.length(), 512)));
        refreshTokens.save(token);

        return new AuthResponse(jwtIssuer.issue(user.getId(), user.getUserRole()),
                refreshValue, UserDto.of(user));
    }

    private static String randomToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }
}
