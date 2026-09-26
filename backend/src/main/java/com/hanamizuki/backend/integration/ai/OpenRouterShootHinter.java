package com.hanamizuki.backend.integration.ai;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The shoot hint, through OpenRouter.
 *
 * <p>Text only and no thinking mode, for the reason the Anthropic version
 * gives: the group is standing there holding a pose. The shorter hint timeout
 * applies here too, so a slow answer becomes the canned text rather than a
 * long wait followed by the canned text.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openrouter")
public class OpenRouterShootHinter implements ShootHinter {

    private final OpenRouterClient client;
    private final Duration timeout;

    public OpenRouterShootHinter(OpenRouterClient client,
                                 @Value("${app.ai.hint-timeout-seconds}") long timeoutSeconds) {
        this.client = client;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public boolean isAvailable() {
        return client.isAvailable();
    }

    @Override
    public ShootHint suggest(int memberCount, List<String> memberNames, String place, String mood) {
        return client.complete(
                ShootPrompt.SYSTEM,
                ShootPrompt.describe(memberCount, memberNames, place, mood),
                ShootHint.class,
                timeout);
    }
}
