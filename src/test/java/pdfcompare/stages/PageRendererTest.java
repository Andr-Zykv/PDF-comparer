package pdfcompare.stages;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageRendererTest {

    @Test
    void rendersAtTheRequestedResolution() throws IOException {
        try (PDDocument d = new PDDocument()) {
            d.addPage(new PDPage(PDRectangle.LETTER));
            BufferedImage img = PageRenderer.render(d, 1, 72f);
            // at 72 DPI, 1 point == 1 pixel
            assertEquals(612, img.getWidth());
            assertEquals(792, img.getHeight());
        }
    }

    @Test
    void rejectsOutOfRangePage() throws IOException {
        try (PDDocument d = new PDDocument()) {
            d.addPage(new PDPage());
            assertThrows(IllegalArgumentException.class, () -> PageRenderer.render(d, 2, 72f));
        }
    }

    @Test
    void writesAReadablePng(@TempDir Path tempDir) throws IOException {
        try (PDDocument d = new PDDocument()) {
            d.addPage(new PDPage(PDRectangle.LETTER));
            BufferedImage img = PageRenderer.render(d, 1, 72f);
            Path out = tempDir.resolve("nested/page-1.png");
            PageRenderer.writePng(img, out);
            assertTrue(Files.exists(out));
            BufferedImage readBack = javax.imageio.ImageIO.read(out.toFile());
            assertEquals(img.getWidth(), readBack.getWidth());
            assertEquals(img.getHeight(), readBack.getHeight());
        }
    }

    @Test
    void diffHighlightsOnlyTheChangedArea() throws IOException {
        try (PDDocument a = new PDDocument(); PDDocument b = new PDDocument()) {
            PDPage pageA = new PDPage(PDRectangle.LETTER);
            a.addPage(pageA);
            PDPage pageB = new PDPage(PDRectangle.LETTER);
            b.addPage(pageB);
            try (PDPageContentStream cs = new PDPageContentStream(b, pageB)) {
                cs.setNonStrokingColor(Color.RED);
                cs.addRect(50, 50, 100, 100);
                cs.fill();
            }
            BufferedImage highlighted = PageRenderer.renderDiff(a, b, 1, 72f, 12, Color.MAGENTA.getRGB());
            // a pixel inside the red rectangle should be highlighted, one far outside should not
            assertEquals(Color.MAGENTA.getRGB(), highlighted.getRGB(100, 792 - 100));
            assertEquals(Color.WHITE.getRGB(), highlighted.getRGB(5, 5));
        }
    }

    @Test
    void refusesToDiffDifferentlySizedPages() throws IOException {
        try (PDDocument a = new PDDocument(); PDDocument b = new PDDocument()) {
            a.addPage(new PDPage(PDRectangle.LETTER));
            b.addPage(new PDPage(PDRectangle.A4));
            assertThrows(IllegalArgumentException.class, () -> PageRenderer.renderDiff(a, b, 1, 72f, 12, 0));
        }
    }
}
