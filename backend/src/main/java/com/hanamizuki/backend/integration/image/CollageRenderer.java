package com.hanamizuki.backend.integration.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Builds the 1200x630 image a link preview shows.
 *
 * <p>This is the picture in the LINE card, so it is the first thing anyone who
 * did not take the photos ever sees of an album. Layout follows the count
 * rather than forcing everything into a grid: one photo cropped to fill reads
 * better than one photo in a corner surrounded by background.
 */
@Component
public class CollageRenderer {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 630;
    private static final int GAP = 6;
    /** The indigo the poster uses, so an empty slot still looks deliberate. */
    private static final Color BACKDROP = new Color(0x1C, 0x3A, 0x78);

    private final ImageProcessor images;

    public CollageRenderer(ImageProcessor images) {
        this.images = images;
    }

    public byte[] render(List<byte[]> sources) {
        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(BACKDROP);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        List<BufferedImage> photos = sources.stream().limit(4).map(images::decode).toList();
        switch (photos.size()) {
            case 0 -> { }
            case 1 -> draw(g, photos.get(0), 0, 0, WIDTH, HEIGHT);
            case 2 -> {
                int half = (WIDTH - GAP) / 2;
                draw(g, photos.get(0), 0, 0, half, HEIGHT);
                draw(g, photos.get(1), half + GAP, 0, WIDTH - half - GAP, HEIGHT);
            }
            case 3 -> {
                int left = (WIDTH - GAP) / 2;
                int rowHeight = (HEIGHT - GAP) / 2;
                draw(g, photos.get(0), 0, 0, left, HEIGHT);
                draw(g, photos.get(1), left + GAP, 0, WIDTH - left - GAP, rowHeight);
                draw(g, photos.get(2), left + GAP, rowHeight + GAP,
                        WIDTH - left - GAP, HEIGHT - rowHeight - GAP);
            }
            // The cover stays top-left, so the card leads with the photo the
            // album itself leads with.
            default -> {
                int halfW = (WIDTH - GAP) / 2;
                int halfH = (HEIGHT - GAP) / 2;
                draw(g, photos.get(0), 0, 0, halfW, halfH);
                draw(g, photos.get(1), halfW + GAP, 0, WIDTH - halfW - GAP, halfH);
                draw(g, photos.get(2), 0, halfH + GAP, halfW, HEIGHT - halfH - GAP);
                draw(g, photos.get(3), halfW + GAP, halfH + GAP,
                        WIDTH - halfW - GAP, HEIGHT - halfH - GAP);
            }
        }
        g.dispose();
        return images.toJpeg(canvas);
    }

    /**
     * Fills the slot, cropping whatever does not fit.
     *
     * <p>Letterboxing would be the safe choice, but a card with bars down the
     * side looks broken rather than careful at the size LINE renders it.
     */
    private void draw(Graphics2D g, BufferedImage photo, int x, int y, int w, int h) {
        double scale = Math.max((double) w / photo.getWidth(), (double) h / photo.getHeight());
        int scaledW = (int) Math.ceil(photo.getWidth() * scale);
        int scaledH = (int) Math.ceil(photo.getHeight() * scale);
        int offsetX = x - (scaledW - w) / 2;
        int offsetY = y - (scaledH - h) / 2;

        java.awt.Shape previous = g.getClip();
        g.setClip(x, y, w, h);
        g.drawImage(photo, offsetX, offsetY, scaledW, scaledH, null);
        g.setClip(previous);
    }
}
