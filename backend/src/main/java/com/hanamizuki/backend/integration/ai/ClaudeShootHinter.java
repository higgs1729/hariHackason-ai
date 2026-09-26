package com.hanamizuki.backend.integration.ai;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;

/**
 * Asks Claude what this group should do in front of the camera.
 *
 * <p>The counterpart to {@link ClaudeAlbumEnricher}: that one looks backwards
 * at photos already taken, this one looks forward at a photo about to be. They
 * are the only two places the AI is visible, and this is the one the user is
 * standing still waiting for.
 *
 * <p>Three differences from the album call, all of them about that wait:
 *
 * <ul>
 *   <li>no images — nothing has been shot yet, so the request is a few hundred
 *       tokens instead of twenty-odd thousand
 *   <li>no thinking — a one-line instruction does not need it, and it is pure
 *       latency here
 *   <li>a shorter timeout than the album's, because a group holding a pose for
 *       twenty-five seconds has already stopped enjoying it
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "anthropic", matchIfMissing = true)
public class ClaudeShootHinter implements ShootHinter {

    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public ClaudeShootHinter(@Value("${app.ai.api-key:}") String apiKey,
                             @Value("${app.ai.model}") String model,
                             @Value("${app.ai.hint-timeout-seconds}") long timeoutSeconds) {
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public ShootHint suggest(int memberCount, List<String> memberNames, String place, String mood) {
        if (!isAvailable()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not configured");
        }

        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .build();

        StructuredMessageCreateParams<ShootHint> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L)
                .system(ShootPrompt.SYSTEM)
                .addUserMessage(ShootPrompt.describe(memberCount, memberNames, place, mood))
                .outputConfig(ShootHint.class)
                .build();

        return client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Claude returned no structured output"));
    }

}
