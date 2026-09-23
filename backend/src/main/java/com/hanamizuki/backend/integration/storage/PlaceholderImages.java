package com.hanamizuki.backend.integration.storage;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * Generated stand-ins for real photographs, used by the dev seed.
 *
 * <p>They exist so a freshly seeded database has images to serve. Rebuilding
 * demo state by hand after a restart is how demos die, and an album of broken
 * image icons is not much better than an empty one.
 */
public final class PlaceholderImages {

    private static final Color[][] PALETTE = {
            {new Color(0x8F, 0xB8, 0xEC), new Color(0x4A, 0x6E, 0xA6)},
            {new Color(0xF2, 0xC2, 0xA8), new Color(0x2F, 0x4A, 0x7A)},
            {new Color(0x7F, 0xB0, 0xEA), new Color(0xB8, 0x82, 0x5F)},
            {new Color(0xCF, 0xE3, 0xF9), new Color(0xF5, 0xB8, 0xC8)},
    };

    private PlaceholderImages() {
    }

    /**
     * One distinct image per {@code seed}, not one per palette entry.
     *
     * <p>It used to be {@code PALETTE[seed % 4]} and nothing else, so ten
     * seeded photos were four pictures repeated. That was merely repetitive to
     * look at until the seed started storing a {@code sha256}, at which point
     * several rows shared a hash — and the dedup lookup on upload is declared
     * as returning one row.
     */
    public static byte[] jpeg(int width, int height, int seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color[] pair = PALETTE[Math.floorMod(seed, PALETTE.length)];
        // The gradient runs corner to corner one way or the other, so the
        // palette repeats every eight seeds rather than every four.
        boolean flipped = Math.floorMod(seed / PALETTE.length, 2) == 1;
        g.setPaint(flipped
                ? new GradientPaint(width, 0, pair[0], 0, height, pair[1])
                : new GradientPaint(0, 0, pair[0], width, height, pair[1]));
        g.fillRect(0, 0, width, height);

        // And a mark whose position and size come from the seed, which is what
        // actually makes every image different rather than merely most of them.
        int band = Math.floorMod(seed, 7) + 1;
        g.setColor(new Color(255, 255, 255, 90));
        g.fillOval(width / 8 * band % Math.max(width - 120, 1), height / 3,
                60 + band * 12, 60 + band * 12);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpg", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
