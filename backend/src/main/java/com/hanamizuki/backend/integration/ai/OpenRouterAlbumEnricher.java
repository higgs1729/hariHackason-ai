package com.hanamizuki.backend.integration.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.hanamizuki.backend.domain.Photo;
import com.hanamizuki.backend.integration.image.ImageProcessor;
import com.hanamizuki.backend.integration.storage.FileStorage;

/**
 * The same album request, sent through OpenRouter instead of Anthropic.
 *
 * <p>Prompt and validation come from {@link AlbumPrompt}, shared with
 * {@link ClaudeAlbumEnricher}. What differs is only the wire format: OpenRouter
 * speaks the OpenAI chat shape, where an image is a content part carrying a
 * {@code data:} URI rather than a base64 source block.
 *
 * <p>Active when {@code app.ai.provider=openrouter}.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openrouter")
public class OpenRouterAlbumEnricher implements AlbumEnricher {

    private final OpenRouterClient client;
    private final int maxImageEdge;
    private final Duration timeout;
    private final FileStorage storage;
    private final ImageProcessor images;

    public OpenRouterAlbumEnricher(OpenRouterClient client,
                                   @Value("${app.ai.max-image-long-edge}") int maxImageEdge,
                                   @Value("${app.ai.timeout-seconds}") long timeoutSeconds,
                                   FileStorage storage, ImageProcessor images) {
        this.client = client;
        this.maxImageEdge = maxImageEdge;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.storage = storage;
        this.images = images;
    }

    @Override
    public boolean isAvailable() {
        return client.isAvailable();
    }

    @Override
    public String model() {
        return client.primaryModel();
    }

    @Override
    public AlbumDraft enrich(List<Photo> cluster) {
        List<Map<String, Object>> content = new ArrayList<>();
        for (Photo photo : cluster) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", dataUri(photo))));
        }
        content.add(Map.of("type", "text", "text", AlbumPrompt.describe(cluster)));

        AlbumDraft draft = client.complete(AlbumPrompt.SYSTEM, content, AlbumDraft.class, timeout);
        return AlbumPrompt.validate(draft, cluster);
    }

    /**
     * Downscaled first, same as the Anthropic path: at full resolution a photo
     * costs roughly 4,800 tokens against about 2,000 at 1080p, and twelve go
     * into every request.
     *
     * <p>A {@code data:} URI rather than a link, because the photos are behind
     * an authenticated route and handing a provider a URL it cannot fetch
     * fails later and less clearly than not trying.
     */
    private String dataUri(Photo photo) {
        byte[] original = storage.read(photo.getFilePath());
        byte[] small = images.toJpeg(images.scaleToFit(images.decode(original), maxImageEdge));
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(small);
    }
}
