package com.hanamizuki.backend.integration.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.hanamizuki.backend.domain.Photo;
import com.hanamizuki.backend.integration.image.ImageProcessor;
import com.hanamizuki.backend.integration.storage.FileStorage;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link ClaudeAlbumEnricher}'s job done through {@code claude -p}.
 *
 * <p>The photos go in as files, not bytes: each one is downscaled into a
 * throwaway directory, the CLI runs with that directory as its working
 * directory and only the {@code Read} tool, and the prompt names the files.
 * Same system prompt, same validation of the ids that come back.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "cli", matchIfMissing = true)
public class CliAlbumEnricher implements AlbumEnricher {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    static final String SCHEMA = """
            {"type":"object","properties":{
              "title":{"type":"string"},
              "coverPhotoId":{"type":"integer"},
              "summary":{"type":"string"},
              "photos":{"type":"array","items":{"type":"object","properties":{
                "photoId":{"type":"integer"},
                "caption":{"type":"string"},
                "place":{"type":["string","null"]},
                "weather":{"type":["string","null"]},
                "comment":{"type":"string"}},
                "required":["photoId","caption","comment"]}}},
             "required":["title","coverPhotoId","summary","photos"]}""";

    private final ClaudeCli cli;
    private final FileStorage storage;
    private final ImageProcessor images;
    private final ObjectMapper objectMapper;
    private final int maxImageEdge;
    private final Duration timeout;

    public CliAlbumEnricher(ClaudeCli cli, FileStorage storage, ImageProcessor images,
                            ObjectMapper objectMapper,
                            @Value("${app.ai.max-image-long-edge}") int maxImageEdge,
                            @Value("${app.ai.cli.timeout-seconds}") long timeoutSeconds) {
        this.cli = cli;
        this.storage = storage;
        this.images = images;
        this.objectMapper = objectMapper;
        this.maxImageEdge = maxImageEdge;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public boolean isAvailable() {
        return cli.isAvailable();
    }

    @Override
    public String modelLabel() {
        return cli.modelLabel();
    }

    @Override
    public AlbumDraft enrich(List<Photo> cluster) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("album-ai-");
            StringBuilder prompt = new StringBuilder(
                    "写真一覧（このフォルダの画像ファイル。Read で1枚ずつ見てください）:\n");
            for (Photo photo : cluster) {
                byte[] small = images.toJpeg(images.scaleToFit(
                        images.decode(storage.read(photo.getFilePath())), maxImageEdge));
                String name = photo.getId() + ".jpg";
                Files.write(dir.resolve(name), small);
                prompt.append("- photoId=").append(photo.getId())
                        .append(" file=").append(name)
                        .append(" 撮影=").append(photo.getTakenTime().format(STAMP));
                if (photo.getLatitude() != null) {
                    prompt.append(" 位置=").append(photo.getLatitude())
                            .append(',').append(photo.getLongitude());
                }
                prompt.append('\n');
            }
            prompt.append("\nすべての画像を見てから、この日のアルバムを作ってください。");

            AlbumDraft draft = objectMapper.treeToValue(
                    cli.run(ClaudeAlbumEnricher.SYSTEM, prompt.toString(), SCHEMA,
                            dir, List.of("Read"), timeout),
                    AlbumDraft.class);
            return ClaudeAlbumEnricher.validate(draft, cluster);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(dir);
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // A temp directory left behind is not worth failing an album over.
        }
    }
}
