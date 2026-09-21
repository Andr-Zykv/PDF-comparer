package pdfcompare.stages;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import pdfcompare.core.Difference;
import pdfcompare.core.Severity;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureStageTest {

    private final StructureStage stage = new StructureStage();

    private static PDDocument doc(PDRectangle... pageSizes) {
        PDDocument d = new PDDocument();
        for (PDRectangle size : pageSizes) {
            d.addPage(new PDPage(size));
        }
        return d;
    }

    private static void writeText(PDDocument d, int pageIndex, Standard14Fonts.FontName font) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(d, d.getPage(pageIndex))) {
            cs.beginText();
            cs.setFont(new PDType1Font(font), 12);
            cs.showText("hello");
            cs.endText();
        }
    }

    @Test
    void identicalDocumentsHaveNoDifferences() throws IOException {
        try (PDDocument a = doc(PDRectangle.A4); PDDocument b = doc(PDRectangle.A4)) {
            writeText(a, 0, Standard14Fonts.FontName.HELVETICA);
            writeText(b, 0, Standard14Fonts.FontName.HELVETICA);
            assertTrue(stage.compare(a, b).isEmpty());
        }
    }

    @Test
    void pageCountMismatchIsReportedAndSharedPagesStillCompared() throws IOException {
        try (PDDocument a = doc(PDRectangle.A4, PDRectangle.A4); PDDocument b = doc(PDRectangle.LETTER)) {
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(2, diffs.size());
            assertEquals("pages", diffs.get(0).path());
            assertEquals("2", diffs.get(0).expected());
            assertEquals("1", diffs.get(0).actual());
            assertEquals("page[1].size", diffs.get(1).path());
        }
    }

    @Test
    void rotationIsNormalized() throws IOException {
        try (PDDocument a = doc(PDRectangle.A4); PDDocument b = doc(PDRectangle.A4)) {
            a.getPage(0).setRotation(0);
            b.getPage(0).setRotation(90);
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(1, diffs.size());
            assertEquals("page[1].rotation", diffs.get(0).path());
            assertEquals(Severity.ERROR, diffs.get(0).severity());
        }
    }

    @Test
    void differentFontsAreReportedAsWarning() throws IOException {
        try (PDDocument a = doc(PDRectangle.A4); PDDocument b = doc(PDRectangle.A4)) {
            writeText(a, 0, Standard14Fonts.FontName.HELVETICA);
            writeText(b, 0, Standard14Fonts.FontName.TIMES_ROMAN);
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(1, diffs.size());
            assertEquals("page[1].fonts", diffs.get(0).path());
            assertEquals(Severity.WARNING, diffs.get(0).severity());
        }
    }
}
