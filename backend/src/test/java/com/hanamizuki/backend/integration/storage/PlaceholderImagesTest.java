package com.hanamizuki.backend.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * Different seeds have to produce different bytes.
 *
 * <p>They did not. The generator was {@code PALETTE[seed % 4]} and nothing
 * else, so ten seeded photos were four pictures repeated — which looked merely
 * lazy until the seed began storing a {@code sha256}. Then several rows shared
 * a hash, the dedup lookup on upload is declared as returning one row, and the
 * next upload of one of those images failed.
 *
 * <p>Worth a test rather than a comment because the failure surfaced three
 * layers away from the cause, as a rejected file on an endpoint that never
 * touches this class.
 */
class PlaceholderImagesTest {

    private static String digest(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void everySeedInTheRangeTheSeedUsesGivesADistinctImage() {
        Set<String> hashes = new HashSet<>();
        for (int seed = 0; seed < 32; seed++) {
            hashes.add(digest(PlaceholderImages.jpeg(800, 600, seed)));
        }
        assertThat(hashes).hasSize(32);
    }

    /** The same seed twice is the same bytes; re-seeding must not churn. */
    @Test
    void oneSeedIsStable() {
        assertThat(digest(PlaceholderImages.jpeg(800, 600, 3)))
                .isEqualTo(digest(PlaceholderImages.jpeg(800, 600, 3)));
    }

    /** Still a decodable JPEG of the size asked for, mark and all. */
    @Test
    void theResultIsAnImage() throws Exception {
        for (int seed : new int[] {0, 7, 15, 31}) {
            var image = ImageIO.read(new ByteArrayInputStream(
                    PlaceholderImages.jpeg(800, 600, seed)));
            assertThat(image).as("seed %d", seed).isNotNull();
            assertThat(image.getWidth()).isEqualTo(800);
            assertThat(image.getHeight()).isEqualTo(600);
        }
    }

    /** Thumbnails go through the same call at a different size. */
    @Test
    void aDifferentSizeStillWorks() throws Exception {
        var thumb = ImageIO.read(new ByteArrayInputStream(
                PlaceholderImages.jpeg(400, 300, 9)));
        assertThat(thumb).isNotNull();
        assertThat(thumb.getWidth()).isEqualTo(400);
    }
}
