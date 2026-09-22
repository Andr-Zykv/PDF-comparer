package pdfcompare.stages;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Rasterizes PDF pages to images, and images to PNG files. Used by {@link VisualStage}, and
 * useful standalone for eyeballing a page or a flagged difference.
 */
public final class PageRenderer {

    private PageRenderer() {
    }

    /** Renders {@code page} (1-based) of {@code doc} at {@code dpi} dots per inch, in RGB. */
    public static BufferedImage render(PDDocument doc, int page, float dpi) throws IOException {
        int count = doc.getNumberOfPages();
        if (page < 1 || page > count) {
            throw new IllegalArgumentException("page " + page + " out of range 1.." + count);
        }
        return new PDFRenderer(doc).renderImageWithDPI(page - 1, dpi, ImageType.RGB);
    }

    /** Writes {@code image} as a PNG to {@code file}, creating parent directories as needed. */
    public static void writePng(BufferedImage image, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!ImageIO.write(image, "png", file.toFile())) {
            throw new IOException("no PNG writer registered for this image");
        }
    }

    /**
     * Renders {@code actual}'s page with every pixel that differs from {@code expected}'s
     * corresponding page (by more than {@code channelTolerance} on any RGB channel) painted
     * {@code highlightRgb}, for visually locating what a {@link VisualStage} difference refers
     * to. Both documents' pages must render to the same pixel size.
     */
    public static BufferedImage renderDiff(PDDocument expected, PDDocument actual, int page, float dpi,
                                            int channelTolerance, int highlightRgb) throws IOException {
        BufferedImage a = render(expected, page, dpi);
        BufferedImage b = render(actual, page, dpi);
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            throw new IllegalArgumentException("page " + page + " renders to different sizes: "
                    + a.getWidth() + "x" + a.getHeight() + " vs " + b.getWidth() + "x" + b.getHeight());
        }
        return ImageDiff.highlight(a, b, channelTolerance, highlightRgb);
    }
}
