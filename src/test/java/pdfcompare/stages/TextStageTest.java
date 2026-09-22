package pdfcompare.stages;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import pdfcompare.core.Difference;
import pdfcompare.core.Severity;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextStageTest {

    private final TextStage stage = new TextStage();

    private static PDDocument doc(String... lines) throws IOException {
        PDDocument d = new PDDocument();
        PDPage page = new PDPage();
        d.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(d, page)) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
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
}
