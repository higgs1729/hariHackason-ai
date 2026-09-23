package com.hanamizuki.backend.api.capsule;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanamizuki.backend.api.capsule.CapsuleVos.CapsuleVo;
import com.hanamizuki.backend.security.AuthUser;
import com.hanamizuki.backend.service.CapsuleService;

/**
 * Brings a capsule forward so the demo can show what opening one looks like.
 *
 * <p>Its own class, because a profile applies to a bean and not to a method,
 * and this must not exist outside dev. The path stays under /api/capsules
 * because that is what the frontend calls; only the bean is conditional.
 */
@RestController
@RequestMapping("/api/capsules")
@Profile("dev")
public class DevCapsuleController {

    private final CapsuleService capsuleService;

    public DevCapsuleController(CapsuleService capsuleService) {
        this.capsuleService = capsuleService;
    }

    @PostMapping("/{capsuleId}/unseal-now")
    public CapsuleVo unsealNow(@PathVariable Long capsuleId,
                               @AuthenticationPrincipal AuthUser principal) {
        return capsuleService.unsealNow(capsuleId, principal.userId());
    }
}
