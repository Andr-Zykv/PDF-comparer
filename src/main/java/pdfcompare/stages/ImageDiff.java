package pdfcompare.stages;

import java.awt.image.BufferedImage;

/**
 * Pixel-level comparison between two equally-sized images. Package-private: an implementation
 * detail of {@link VisualStage} and {@link PageRenderer#renderDiff}.
 */
final class ImageDiff {

    record Result(int width, int height, long totalPixels, long differingPixels) {
        double differingFraction() {
            return totalPixels == 0 ? 0 : (double) differingPixels / totalPixels;
        }
    }

    private ImageDiff() {
    }

    /** A pixel counts as differing if any RGB channel is more than {@code channelTolerance} apart. */
    static Result compare(BufferedImage a, BufferedImage b, int channelTolerance) {
        int w = a.getWidth();
        int h = a.getHeight();
        int[] pa = a.getRGB(0, 0, w, h, null, 0, w);
        int[] pb = b.getRGB(0, 0, w, h, null, 0, w);
        long differing = 0;
        for (int i = 0; i < pa.length; i++) {
            if (differs(pa[i], pb[i], channelTolerance)) {
                differing++;
            }
        }
        return new Result(w, h, (long) w * h, differing);
    }

    /** {@code b} with every differing pixel replaced by {@code highlightRgb}, for manual review. */
    static BufferedImage highlight(BufferedImage a, BufferedImage b, int channelTolerance, int highlightRgb) {
        int w = a.getWidth();
        int h = a.getHeight();
        int[] pa = a.getRGB(0, 0, w, h, null, 0, w);
        int[] pb = b.getRGB(0, 0, w, h, null, 0, w);
        int[] out = new int[pa.length];
        for (int i = 0; i < pa.length; i++) {
            out[i] = differs(pa[i], pb[i], channelTolerance) ? highlightRgb : pb[i];
        }
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        result.setRGB(0, 0, w, h, out, 0, w);
        return result;
    }

    private static boolean differs(int rgb1, int rgb2, int tolerance) {
        int r1 = (rgb1 >> 16) & 0xFF;
        int g1 = (rgb1 >> 8) & 0xFF;
        int b1 = rgb1 & 0xFF;
        int r2 = (rgb2 >> 16) & 0xFF;
        int g2 = (rgb2 >> 8) & 0xFF;
        int b2 = rgb2 & 0xFF;
        return Math.abs(r1 - r2) > tolerance || Math.abs(g1 - g2) > tolerance || Math.abs(b1 - b2) > tolerance;
    }
}
