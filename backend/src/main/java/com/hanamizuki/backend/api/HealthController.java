package com.hanamizuki.backend.api;

import java.io.File;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/health} — is this process actually usable right now.
 *
 * <p>Deliberately does not call Anthropic. A health check that costs money and
 * takes seconds gets polled in a loop by something and then nobody trusts it;
 * {@code aiReachable} reports whether the key is configured, which is the part
 * that actually differs between machines.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final DataSource dataSource;
    private final String storageRoot;
    private final String anthropicKey;

    public HealthController(DataSource dataSource,
                            @Value("${app.storage.root}") String storageRoot,
                            @Value("${ANTHROPIC_API_KEY:}") String anthropicKey) {
        this.dataSource = dataSource;
        this.storageRoot = storageRoot;
        this.anthropicKey = anthropicKey;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean dbUp = isDatabaseUp();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbUp ? "UP" : "DOWN");
        body.put("db", dbUp ? "UP" : "DOWN");
        body.put("aiReachable", !anthropicKey.isBlank());
        body.put("diskFreeMb", diskFreeMb());
        return body;
    }

    private boolean isDatabaseUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1);
        } catch (Exception e) {
            return false;
        }
    }

    /** Falls back to the working directory before the storage root exists. */
    private long diskFreeMb() {
        File probe = new File(storageRoot);
        if (!probe.exists()) {
            probe = new File(".");
        }
        return probe.getUsableSpace() / (1024 * 1024);
    }
}
