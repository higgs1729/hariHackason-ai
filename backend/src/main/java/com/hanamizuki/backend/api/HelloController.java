package com.hanamizuki.backend.api;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smoke-test endpoint. The frontend (Vite) proxies /api/* to port 8080.
 * Add one controller per feature under this package.
 */
@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of(
                "message", "Hello from Spring Boot",
                "serverTime", OffsetDateTime.now().toString());
    }
}
