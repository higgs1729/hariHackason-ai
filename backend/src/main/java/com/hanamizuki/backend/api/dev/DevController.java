package com.hanamizuki.backend.api.dev;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanamizuki.backend.service.DevSeedService;

/**
 * Development helpers.
 *
 * <p>{@code @Profile("dev")} is what makes the open {@code /api/dev/**} rule in
 * the security config safe: outside dev this bean does not exist, so the path
 * has nothing behind it and simply 404s.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class DevController {

    private final DevSeedService devSeedService;

    public DevController(DevSeedService devSeedService) {
        this.devSeedService = devSeedService;
    }

    /** Idempotent by account name: running it twice does not duplicate users. */
    @PostMapping("/seed")
    public Map<String, Object> seed() {
        return devSeedService.seed();
    }
}
