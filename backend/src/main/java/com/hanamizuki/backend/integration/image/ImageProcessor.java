package com.hanamizuki.backend.integration.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

/**
 * Rotation, scaling and JPEG encoding. Stateless.
 *
 * <p>Rotation happens once, on ingest, and the EXIF flag is then discarded.
 * Four things read these files afterwards — thumbnails, Claude, the decoration
 * canvas and the share collage — and if each had to honour the flag itself,
 * one of them would eventually forget and show a sideways photo.
 */
@Component
public class ImageProcessor {

    public BufferedImage decode(byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new IllegalArgumentException("Not an image this JDK can read");
            }
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Applies an EXIF orientation so the stored pixels are upright.
     *
     * <p>All eight values are handled. Implementations that cover only 3, 6 and
     * 8 look fine until someone hands them a mirrored photo.
     */
    public BufferedImage applyOrientation(BufferedImage image, Integer orientation) {
        if (orientation == null || orientation == 1) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swapsAxes = orientation >= 5;

        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.scale(-1, 1); t.translate(-w, 0); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.scale(1, -1); t.translate(0, -h); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { return image; }
        }

        BufferedImage out = new BufferedImage(swapsAxes ? h : w, swapsAxes ? w : h,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, t, null);
        g.dispose();
        return out;
    }

    /** Scales so the longest edge is at most {@code maxEdge}. Never enlarges. */
    public BufferedImage scaleToFit(BufferedImage image, int maxEdge) {
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest <= maxEdge) {
            return image;
        }
        double factor = (double) maxEdge / longest;
        int w = Math.max(1, (int) Math.round(image.getWidth() * factor));
        int h = Math.max(1, (int) Math.round(image.getHeight() * factor));

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /**
     * Re-encodes as JPEG, which also drops every metadata block the source had.
     * That is the point: a shared photo should not carry the coordinates of the
     * house it was taken in.
     */
    public byte[] toJpeg(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpg", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
