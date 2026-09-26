package com.hanamizuki.backend.integration.ai;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link ClaudeShootHinter}'s job done through {@code claude -p}, with no tools
 * at all: the answer needs nothing but the text it is given.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "cli", matchIfMissing = true)
public class CliShootHinter implements ShootHinter {

    static final String SCHEMA = """
            {"type":"object","properties":{
              "hint":{"type":"string"},
              "poses":{"type":"array","items":{"type":"string"}}},
             "required":["hint","poses"]}""";

    private final ClaudeCli cli;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public CliShootHinter(ClaudeCli cli, ObjectMapper objectMapper,
                          @Value("${app.ai.cli.hint-timeout-seconds}") long timeoutSeconds) {
        this.cli = cli;
        this.objectMapper = objectMapper;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public boolean isAvailable() {
        return cli.isAvailable();
    }

    @Override
    public ShootHint suggest(int memberCount, List<String> memberNames, String place, String mood) {
        return objectMapper.treeToValue(
                cli.run(ClaudeShootHinter.SYSTEM,
                        ClaudeShootHinter.describe(memberCount, memberNames, place, mood),
                        SCHEMA, null, List.of(), timeout),
                ShootHint.class);
    }
}
