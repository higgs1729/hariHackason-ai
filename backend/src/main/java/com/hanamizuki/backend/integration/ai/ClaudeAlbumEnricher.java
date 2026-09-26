package com.hanamizuki.backend.integration.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.hanamizuki.backend.domain.Photo;
import com.hanamizuki.backend.integration.image.ImageProcessor;
import com.hanamizuki.backend.integration.storage.FileStorage;

/**
 * Asks Claude to look at one afternoon's photos and name it, through
 * Anthropic's own API.
 *
 * <p>This is one of only two places the AI is visible to a user — the other is
 * the shoot hint — so what matters more than the wording is that a failure
 * here never becomes a failure the user sees. Everything this class can throw
 * is caught upstream and turned into a rule-based album.
 *
 * <p>Active when {@code app.ai.provider} is {@code anthropic}, which is the
 * default. {@link OpenRouterAlbumEnricher} is the other half of that switch.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "anthropic", matchIfMissing = true)
public class ClaudeAlbumEnricher implements AlbumEnricher {

    private final String apiKey;
    private final String model;
    private final int maxImageEdge;
    private final Duration timeout;
    private final FileStorage storage;
    private final ImageProcessor images;

    public ClaudeAlbumEnricher(@Value("${app.ai.api-key:}") String apiKey,
                               @Value("${app.ai.model}") String model,
                               @Value("${app.ai.max-image-long-edge}") int maxImageEdge,
                               @Value("${app.ai.timeout-seconds}") long timeoutSeconds,
                               FileStorage storage, ImageProcessor images) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxImageEdge = maxImageEdge;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.storage = storage;
        this.images = images;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public AlbumDraft enrich(List<Photo> cluster) {
        if (!isAvailable()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not configured");
        }

        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .build();

        List<ContentBlockParam> content = new ArrayList<>();
        for (Photo photo : cluster) {
            content.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                    .source(Base64ImageSource.builder()
                            .data(downscaledBase64(photo))
                            .build())
                    .build()));
        }
        content.add(ContentBlockParam.ofText(TextBlockParam.builder()
                .text(AlbumPrompt.describe(cluster))
                .build()));

        StructuredMessageCreateParams<AlbumDraft> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(4096L)
                // Adaptive is the only thinking mode on 4.8; a token budget
                // would be rejected outright.
                .thinking(ThinkingConfigAdaptive.builder().build())
                .system(AlbumPrompt.SYSTEM)
                .addUserMessageOfBlockParams(content)
                // The schema is derived from the record, so there is no
                // hand-written JSON schema to drift from it.
                .outputConfig(AlbumDraft.class)
                .build();

        AlbumDraft draft = client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Claude returned no structured output"));

        return AlbumPrompt.validate(draft, cluster);
    }

    @Override
    public String model() {
        return model;
    }

    /**
     * Downscales before encoding. At full resolution a single photo costs
     * roughly 4,800 tokens against about 2,000 at 1080p, and twelve of them
     * go into every request.
     */
    private String downscaledBase64(Photo photo) {
        byte[] original = storage.read(photo.getFilePath());
        byte[] small = images.toJpeg(images.scaleToFit(images.decode(original), maxImageEdge));
        return Base64.getEncoder().encodeToString(small);
    }
}
