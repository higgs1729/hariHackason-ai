package com.hanamizuki.backend.api.user;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hanamizuki.backend.api.auth.AuthDtos.UserDto;
import com.hanamizuki.backend.api.user.UserRequests.PatchMeRequest;
import com.hanamizuki.backend.security.AuthUser;
import com.hanamizuki.backend.service.UserService;

/**
 * マイページ。/ 用户中心。
 *
 * <p>{@code GET /api/users/me} duplicates {@code /api/auth/me} on purpose: the
 * frontend contract has both, one reached through the auth store and one
 * through the profile screen, and pointing them at the same service is cheaper
 * than arguing about which is canonical.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthUser principal) {
        return userService.me(principal.userId());
    }

    @PatchMapping("/me")
    public UserDto patchMe(@RequestBody PatchMeRequest request,
                           @AuthenticationPrincipal AuthUser principal) {
        return userService.patchMe(principal.userId(), request);
    }

    /**
     * Declared after {@code /me} would not matter to Spring, which prefers the
     * literal segment over the variable one — but {@code /api/users/me} must
     * never be read as an id, so the two stay adjacent where that is visible.
     */
    @GetMapping("/search")
    public List<UserDto> search(@RequestParam("q") String query,
                                @AuthenticationPrincipal AuthUser principal) {
        return userService.search(query, principal.userId());
    }

    @GetMapping("/{userId}")
    public UserDto get(@PathVariable Long userId) {
        return userService.get(userId);
    }
}
