package com.hanamizuki.backend.integration.ai;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * Asks Claude to look at one afternoon's photos and name it.
 *
 * <p>This is one of only two places the AI is visible to a user — the other is
 * the shoot hint — so what matters more than the wording is that a failure
 * here never becomes a failure the user sees. Everything this class can throw
 * is caught upstream and turned into a rule-based album.
 */
@Component
public class ClaudeAlbumEnricher implements AlbumEnricher {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAlbumEnricher.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> WEATHER = Set.of("晴れ", "曇り", "雨", "雪");
    private static final int CAPTION_MAX = 10;
    private static final int COMMENT_MAX = 255;

    private static final String SYSTEM = """
            あなたは高校生の写真アルバムに言葉を添える編集者です。
            渡された写真は同じ日の同じ時間帯に撮られたものです。

            - タイトルは10文字以内、日本語、その日の気分が伝わるもの
            - caption は2〜5文字の短いラベル（例:「放課後」「夕日」）
            - weather は 晴れ / 曇り / 雨 / 雪 のいずれか。判断できなければ null
            - place は看板や風景から推測できる場合のみ。できなければ null
            - comment は友達に話しかけるような1文
            - 事実を作らないこと。写真から読み取れないことは null にする
            """;

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
                .text(describe(cluster))
                .build()));

        StructuredMessageCreateParams<AlbumDraft> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(4096L)
                // Adaptive is the only thinking mode on 4.8; a token budget
                // would be rejected outright.
                .thinking(ThinkingConfigAdaptive.builder().build())
                .system(SYSTEM)
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

        return validate(draft, cluster);
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

    /** Ids and timestamps, so the model can tie its answers back to photos. */
    private String describe(List<Photo> cluster) {
        StringBuilder text = new StringBuilder("写真一覧:\n");
        for (Photo photo : cluster) {
            text.append("- photoId=").append(photo.getId())
                    .append(" 撮影=").append(photo.getTakenTime().format(STAMP));
            if (photo.getLatitude() != null) {
                text.append(" 位置=").append(photo.getLatitude())
                        .append(',').append(photo.getLongitude());
            }
            text.append('\n');
        }
        text.append("\n画像は上の順番と同じです。この日のアルバムを作ってください。");
        return text.toString();
    }

    /**
     * Never trusts the ids that come back.
     *
     * <p>A hallucinated {@code photoId} would attach a caption to someone
     * else's photo, and an out-of-range {@code coverPhotoId} would leave the
     * album with a broken cover. Both are cheap to check and expensive to miss.
     */
    private AlbumDraft validate(AlbumDraft draft, List<Photo> cluster) {
        Set<Long> known = cluster.stream().map(Photo::getId).collect(java.util.stream.Collectors.toSet());

        List<PhotoInsight> kept = new ArrayList<>();
        for (PhotoInsight insight : draft.photos() == null ? List.<PhotoInsight>of() : draft.photos()) {
            if (insight.photoId() == null || !known.contains(insight.photoId())) {
                log.warn("Dropping insight for unknown photoId {}", insight.photoId());
                continue;
            }
            kept.add(new PhotoInsight(insight.photoId(),
                    clip(insight.caption(), CAPTION_MAX),
                    clip(insight.place(), 64),
                    WEATHER.contains(insight.weather()) ? insight.weather() : null,
                    clip(insight.comment(), COMMENT_MAX)));
        }

        Long cover = known.contains(draft.coverPhotoId())
                ? draft.coverPhotoId()
                : cluster.get(0).getId();
        String title = draft.title() == null || draft.title().isBlank() ? null : draft.title();

        return new AlbumDraft(title, cover, clip(draft.summary(), 255), kept);
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
