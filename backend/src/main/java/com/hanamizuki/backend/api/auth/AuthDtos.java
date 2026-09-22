package com.hanamizuki.backend.api.auth;

import com.hanamizuki.backend.domain.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response shapes for /api/auth, per 03-detailed-design section 4.1. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 4, max = 256) String userAccount,
            @NotBlank @Size(min = 8, max = 128) String userPassword,
            @Size(max = 256) String userName) {
    }

    public record LoginRequest(
            @NotBlank String userAccount,
            @NotBlank String userPassword) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record AuthResponse(String accessToken, String refreshToken, UserDto user) {
    }

    /**
     * {@code userPassword} is absent by construction rather than by annotation —
     * the field cannot leak if the type has nowhere to put it.
     *
     * <p>{@code userRole} goes out lowercase to match the column.
     */
    public record UserDto(Long id, String userAccount, String userName,
                          String userAvatar, String userProfile, String userRole) {

        public static UserDto of(User user) {
            return new UserDto(user.getId(), user.getUserAccount(), user.getUserName(),
                    user.getUserAvatar(), user.getUserProfile(),
                    user.getUserRole().name().toLowerCase());
        }
    }
}
