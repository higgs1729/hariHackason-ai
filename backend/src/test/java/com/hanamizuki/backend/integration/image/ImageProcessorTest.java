package com.hanamizuki.backend.integration.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Orientation is the one piece of ingest that is easy to get wrong and
 * invisible when you do: the file opens, it is just sideways.
 *
 * <p>All eight values are covered because implementations that handle only
 * 3, 6 and 8 look correct until someone uploads a mirrored photo.
 */
class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    /** Deliberately not square, so a transposed result is detectable. */
    private BufferedImage landscape() {
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 10, 10);   // marks the top-left corner
        g.dispose();
        return image;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void keepsDimensionsForUprightAndMirroredOrientations(int orientation) {
        BufferedImage out = processor.applyOrientation(landscape(), orientation);
        assertThat(out.getWidth()).isEqualTo(40);
        assertThat(out.getHeight()).isEqualTo(20);
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6, 7, 8})
    void swapsDimensionsForQuarterTurns(int orientation) {
        BufferedImage out = processor.applyOrientation(landscape(), orientation);
        assertThat(out.getWidth()).isEqualTo(20);
        assertThat(out.getHeight()).isEqualTo(40);
    }

    @Test
    void movesTheCornerMarkWhenRotatedAQuarterTurn() {
        BufferedImage out = processor.applyOrientation(landscape(), 6);
        // A quarter turn clockwise puts the old top-left at the top-right.
        assertThat(new Color(out.getRGB(out.getWidth() - 1, 0))).isEqualTo(Color.RED);
        assertThat(new Color(out.getRGB(0, 0))).isNotEqualTo(Color.RED);
    }

    @Test
    void treatsAbsentOrientationAsUpright() {
        BufferedImage source = landscape();
        assertThat(processor.applyOrientation(source, null)).isSameAs(source);
        assertThat(processor.applyOrientation(source, 1)).isSameAs(source);
    }

    @Test
    void neverEnlargesWhenScaling() {
        BufferedImage source = landscape();
        assertThat(processor.scaleToFit(source, 100)).isSameAs(source);

        BufferedImage shrunk = processor.scaleToFit(source, 20);
        assertThat(shrunk.getWidth()).isEqualTo(20);
        assertThat(shrunk.getHeight()).isEqualTo(10);
    }
}
