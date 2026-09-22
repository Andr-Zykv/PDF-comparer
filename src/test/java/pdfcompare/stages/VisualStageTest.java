package pdfcompare.stages;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import pdfcompare.core.Difference;
import pdfcompare.core.Severity;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualStageTest {

    // Low DPI keeps these tests fast; the pixel-diff logic doesn't depend on resolution.
    private final VisualStage stage = new VisualStage(72f, VisualStage.DEFAULT_CHANNEL_TOLERANCE,
            VisualStage.DEFAULT_MAX_DIFFERING_FRACTION);

    private static PDDocument blankLetterPage() throws IOException {
        PDDocument d = new PDDocument();
        d.addPage(new PDPage(PDRectangle.LETTER));
        return d;
    }

    private static void fillRect(PDDocument d, Color color, float x, float y, float w, float h) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(d, d.getPage(0), PDPageContentStream.AppendMode.APPEND, true)) {
            cs.setNonStrokingColor(color);
            cs.addRect(x, y, w, h);
            cs.fill();
        }
    }

    @Test
    void identicalPagesHaveNoDifferences() throws IOException {
        try (PDDocument a = blankLetterPage(); PDDocument b = blankLetterPage()) {
            fillRect(a, Color.BLUE, 100, 100, 50, 50);
            fillRect(b, Color.BLUE, 100, 100, 50, 50);
            assertTrue(stage.compare(a, b).isEmpty());
        }
    }

    @Test
    void aVisiblyDifferentPageIsReportedAsError() throws IOException {
        try (PDDocument a = blankLetterPage(); PDDocument b = blankLetterPage()) {
            // a good-sized block so the differing fraction clears the 0.05% threshold decisively
            fillRect(b, Color.RED, 50, 50, 200, 200);
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(1, diffs.size());
            assertEquals("page[1].visual", diffs.get(0).path());
            assertEquals(Severity.ERROR, diffs.get(0).severity());
            assertTrue(diffs.get(0).actual().contains("% differing"));
        }
    }

    @Test
    void differentPageSizeIsReportedWithoutAttemptingAPixelDiff() throws IOException {
        try (PDDocument a = new PDDocument(); PDDocument b = new PDDocument()) {
            a.addPage(new PDPage(PDRectangle.LETTER));
            b.addPage(new PDPage(PDRectangle.A4));
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(1, diffs.size());
            assertEquals("page[1].visual.size", diffs.get(0).path());
        }
    }

    @Test
    void toleratesTinyAntiAliasingLevelNoise() throws IOException {
        // A single-pixel-scale nudge should stay under the differing-pixel-fraction threshold,
        // even though anti-aliasing will shift a handful of edge pixels' colors slightly.
        try (PDDocument a = blankLetterPage(); PDDocument b = blankLetterPage()) {
            fillRect(a, Color.BLUE, 100.0f, 100.0f, 50, 50);
            fillRect(b, Color.BLUE, 100.3f, 100.0f, 50, 50);
            assertTrue(stage.compare(a, b).isEmpty());
        }
    }
}
