package pdfcompare.stages;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import pdfcompare.core.Difference;
import pdfcompare.core.Severity;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextStageTest {

    private final TextStage stage = new TextStage();

    private static PDDocument doc(String... lines) throws IOException {
        return doc(Standard14Fonts.FontName.HELVETICA, Color.BLACK, lines);
    }

    private static PDDocument doc(Standard14Fonts.FontName font, Color color, String... lines) throws IOException {
        PDDocument d = new PDDocument();
        PDPage page = new PDPage();
        d.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(d, page)) {
            cs.beginText();
            cs.setFont(new PDType1Font(font), 12);
            cs.setNonStrokingColor(color);
            cs.newLineAtOffset(50, 700);
            cs.setLeading(14);
            for (String line : lines) {
                cs.showText(line);
                cs.newLine();
            }
            cs.endText();
        }
        return d;
    }

    @Test
    void identicalTextHasNoDifferences() throws IOException {
        try (PDDocument a = doc("the quick brown fox"); PDDocument b = doc("the quick brown fox")) {
            assertTrue(stage.compare(a, b).isEmpty());
        }
    }

    @Test
    void changedWordIsReportedAsReplace() throws IOException {
        try (PDDocument a = doc("the quick brown fox"); PDDocument b = doc("the slow brown fox")) {
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(1, diffs.size());
            assertEquals("page[1].word[1]", diffs.get(0).path());
            assertEquals("quick", diffs.get(0).expected());
            assertEquals("slow", diffs.get(0).actual());
            assertEquals(Severity.ERROR, diffs.get(0).severity());
        }
    }

    @Test
    void extraWordIsReportedAsInsert() throws IOException {
        try (PDDocument a = doc("the fox"); PDDocument b = doc("the quick fox")) {
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(1, diffs.size());
            assertEquals("page[1].word[1]", diffs.get(0).path());
            assertEquals(null, diffs.get(0).expected());
            assertEquals("quick", diffs.get(0).actual());
        }
    }

    @Test
    void missingWordIsReportedAsDelete() throws IOException {
        try (PDDocument a = doc("the quick fox"); PDDocument b = doc("the fox")) {
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(1, diffs.size());
            assertEquals("page[1].word[1]", diffs.get(0).path());
            assertEquals("quick", diffs.get(0).expected());
            assertEquals(null, diffs.get(0).actual());
        }
    }

    @Test
    void fontChangeOnMatchingWordsIsOneWarningForTheRun() throws IOException {
        try (PDDocument a = doc("the quick brown fox");
             PDDocument b = doc(Standard14Fonts.FontName.TIMES_ROMAN, Color.BLACK, "the quick brown fox")) {
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(1, diffs.size());
            assertEquals("page[1].word[0-3].font", diffs.get(0).path());
            assertEquals("Helvetica", diffs.get(0).expected());
            assertEquals("Times-Roman", diffs.get(0).actual());
            assertEquals(Severity.WARNING, diffs.get(0).severity());
        }
    }

    @Test
    void boldToRegularIsReportedAsFontChange() throws IOException {
        try (PDDocument a = doc(Standard14Fonts.FontName.HELVETICA_BOLD, Color.BLACK, "unaudited");
             PDDocument b = doc("unaudited")) {
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(1, diffs.size());
            assertEquals("page[1].word[0].font", diffs.get(0).path());
            assertEquals("Helvetica-Bold", diffs.get(0).expected());
            assertEquals("Helvetica", diffs.get(0).actual());
        }
    }

    @Test
    void colorChangeIsReported() throws IOException {
        try (PDDocument a = doc(Standard14Fonts.FontName.HELVETICA, new Color(0x1B7F3B), "Total 310,000.00");
             PDDocument b = doc(Standard14Fonts.FontName.HELVETICA, new Color(0xC62828), "Total 310,000.00")) {
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(1, diffs.size());
            assertEquals("page[1].word[0-1].color", diffs.get(0).path());
            assertEquals("#1B7F3B", diffs.get(0).expected());
            assertEquals("#C62828", diffs.get(0).actual());
            assertEquals(Severity.WARNING, diffs.get(0).severity());
        }
    }

    @Test
    void subtleColorChangeIsReportedButNoiseIsNot() throws IOException {
        try (PDDocument a = doc(Standard14Fonts.FontName.HELVETICA, new Color(0x333333), "contact us");
             PDDocument noise = doc(Standard14Fonts.FontName.HELVETICA, new Color(0x353535), "contact us");
             PDDocument subtle = doc(Standard14Fonts.FontName.HELVETICA, new Color(0x3A3A3A), "contact us")) {
            assertTrue(stage.compare(a, noise).isEmpty());
            assertEquals(1, stage.compare(a, subtle).size());
        }
    }

    @Test
    void changedWordIsNotAlsoReportedAsStyleChange() throws IOException {
        try (PDDocument a = doc("the quick fox");
             PDDocument b = doc(Standard14Fonts.FontName.HELVETICA, Color.RED, "the slow fox")) {
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(3, diffs.size());
            assertEquals("page[1].word[1]", diffs.get(0).path());
            assertEquals("page[1].word[0].color", diffs.get(1).path());
            assertEquals("page[1].word[2].color", diffs.get(2).path());
        }
    }
}
