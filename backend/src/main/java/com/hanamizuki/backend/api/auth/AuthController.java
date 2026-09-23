package com.hanamizuki.backend.api.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanamizuki.backend.api.auth.AuthDtos.AuthResponse;
import com.hanamizuki.backend.api.auth.AuthDtos.LoginRequest;
import com.hanamizuki.backend.api.auth.AuthDtos.RefreshRequest;
import com.hanamizuki.backend.api.auth.AuthDtos.RegisterRequest;
import com.hanamizuki.backend.api.auth.AuthDtos.UserDto;
import com.hanamizuki.backend.security.AuthUser;
import com.hanamizuki.backend.service.AuthService;

import jakarta.validation.Valid;

/**
 * Login is account name plus password. There is no email and no password
 * reset — sending mail is infrastructure this project does not have.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request,
                                 @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.register(request, userAgent);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.login(request, userAgent);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request,
                                @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.refresh(request.refreshToken(), userAgent);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Reached with a valid access token only, so {@code principal} is never
     * null here — {@link com.hanamizuki.backend.security.RestAuthEntryPoint}
     * has already answered 401 otherwise.
     */
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthUser principal) {
        return authService.me(principal.userId());
    }
}
