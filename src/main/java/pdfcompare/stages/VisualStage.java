package pdfcompare.stages;

import org.apache.pdfbox.pdmodel.PDDocument;

import pdfcompare.core.ComparisonStage;
import pdfcompare.core.Difference;
import pdfcompare.core.Severity;
import pdfcompare.core.Stage;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compares each shared page as a rendered image, catching differences the other stages can't
 * see by construction - font rendering, vector art, embedded images, positioning that survives
 * every other check yet still looks wrong.
 *
 * <p>Both documents render to RGB bitmaps at {@link #dpi} and are compared pixel by pixel, with
 * {@link #channelTolerance} absorbing anti-aliasing jitter and {@link #maxDifferingFraction}
 * absorbing whatever's left. The defaults are deliberately tight: these are machine-generated
 * reports, expected to render near-identically run to run, so a real content change should stand
 * out clearly above the noise floor. Loosen them (via the constructor) for documents where that
 * assumption doesn't hold, e.g. scanned or hand-edited PDFs.
 *
 * <p>When a page is flagged, {@link PageRenderer#renderDiff} can produce a highlighted image
 * pinpointing which pixels differ.
 */
public final class VisualStage implements ComparisonStage {

    public static final float DEFAULT_DPI = 150f;
    /** Per-channel (0-255) tolerance before a pixel counts as differing. */
    public static final int DEFAULT_CHANNEL_TOLERANCE = 12;
    /** Fraction of differing pixels above which a page counts as visually different. */
    public static final double DEFAULT_MAX_DIFFERING_FRACTION = 0.0005; // 0.05%

    private final float dpi;
    private final int channelTolerance;
    private final double maxDifferingFraction;

    public VisualStage() {
        this(DEFAULT_DPI, DEFAULT_CHANNEL_TOLERANCE, DEFAULT_MAX_DIFFERING_FRACTION);
    }

    public VisualStage(float dpi, int channelTolerance, double maxDifferingFraction) {
        this.dpi = dpi;
        this.channelTolerance = channelTolerance;
        this.maxDifferingFraction = maxDifferingFraction;
    }

    @Override
    public Stage stage() {
        return Stage.VISUAL;
    }

    @Override
    public List<Difference> compare(PDDocument expected, PDDocument actual) throws IOException {
        List<Difference> out = new ArrayList<>();
        int pages = Math.min(expected.getNumberOfPages(), actual.getNumberOfPages());
        for (int page = 1; page <= pages; page++) {
            comparePage(page, expected, actual, out);
        }
        return out;
    }

    private void comparePage(int page, PDDocument expected, PDDocument actual, List<Difference> out) throws IOException {
        BufferedImage imageA = PageRenderer.render(expected, page, dpi);
        BufferedImage imageB = PageRenderer.render(actual, page, dpi);
        String path = "page[" + page + "].visual";

        if (imageA.getWidth() != imageB.getWidth() || imageA.getHeight() != imageB.getHeight()) {
            out.add(diff(page, path + ".size",
                    imageA.getWidth() + "x" + imageA.getHeight(),
                    imageB.getWidth() + "x" + imageB.getHeight()));
            return;
        }

        ImageDiff.Result result = ImageDiff.compare(imageA, imageB, channelTolerance);
        if (result.differingFraction() > maxDifferingFraction) {
            out.add(diff(page, path, "0.000% differing",
                    String.format(Locale.ROOT, "%.3f%% differing (%d/%d px)",
                            result.differingFraction() * 100, result.differingPixels(), result.totalPixels())));
        }
    }

    private static Difference diff(int page, String path, String expected, String actual) {
        return new Difference(Stage.VISUAL, Severity.ERROR, page, path, expected, actual);
    }
}
