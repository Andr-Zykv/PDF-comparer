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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableStageTest {

    private final TableStage stage = new TableStage();

    private static PDDocument doc(Consumer<PDPageContentStream> writer) throws IOException {
        PDDocument d = new PDDocument();
        PDPage page = new PDPage();
        d.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(d, page)) {
            writer.accept(cs);
        }
        return d;
    }

    /** Three-column table: a header row plus two data rows, each column ~150pt apart. */
    private static PDDocument table(String[] header, String[] r1, String[] r2) throws IOException {
        return doc(cs -> {
            try {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.setLeading(20);
                writeGridLine(cs, header);
                cs.newLine();
                writeGridLine(cs, r1);
                cs.newLine();
                writeGridLine(cs, r2);
                cs.endText();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    /** Writes one line of columns, each column padded out to a fixed width with spaces. */
    private static void writeGridLine(PDPageContentStream cs, String[] cols) throws IOException {
        StringBuilder line = new StringBuilder();
        for (String col : cols) {
            line.append(col);
            while (line.length() % 20 != 0) {
                line.append(' ');
            }
        }
        cs.showText(line.toString());
    }

    @Test
    void identicalTablesHaveNoDifferences() throws IOException {
        try (PDDocument a = table(new String[] {"Name", "Age", "City"},
                new String[] {"Alice", "30", "Paris"},
                new String[] {"Bob", "25", "Lyon"});
             PDDocument b = table(new String[] {"Name", "Age", "City"},
                     new String[] {"Alice", "30", "Paris"},
                     new String[] {"Bob", "25", "Lyon"})) {
            assertTrue(stage.compare(a, b).isEmpty());
        }
    }

    @Test
    void changedCellIsReported() throws IOException {
        try (PDDocument a = table(new String[] {"Name", "Age", "City"},
                new String[] {"Alice", "30", "Paris"},
                new String[] {"Bob", "25", "Lyon"});
             PDDocument b = table(new String[] {"Name", "Age", "City"},
                     new String[] {"Alice", "31", "Paris"},
                     new String[] {"Bob", "25", "Lyon"})) {
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(1, diffs.size());
            assertEquals("page[1].table[0].cell[1][1]", diffs.get(0).path());
            assertEquals("30", diffs.get(0).expected());
            assertEquals("31", diffs.get(0).actual());
            assertEquals(Severity.ERROR, diffs.get(0).severity());
        }
    }

    @Test
    void extraDataRowChangesRowCount() throws IOException {
        try (PDDocument a = table(new String[] {"Name", "Age", "City"},
                new String[] {"Alice", "30", "Paris"},
                new String[] {"Bob", "25", "Lyon"})) {
            PDDocument b = doc(cs -> {
                try {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.setLeading(20);
                    writeGridLine(cs, new String[] {"Name", "Age", "City"});
                    cs.newLine();
                    writeGridLine(cs, new String[] {"Alice", "30", "Paris"});
                    cs.newLine();
                    writeGridLine(cs, new String[] {"Bob", "25", "Lyon"});
                    cs.newLine();
                    writeGridLine(cs, new String[] {"Cara", "40", "Nice"});
                    cs.endText();
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
            List<Difference> diffs = stage.compare(a, b);
            b.close();
            assertEquals(1, diffs.size());
            assertEquals("page[1].table[0].rows", diffs.get(0).path());
            assertEquals("3", diffs.get(0).expected());
            assertEquals("4", diffs.get(0).actual());
        }
    }

    @Test
    void plainProseIsNotDetectedAsATable() throws IOException {
        try (PDDocument a = doc(cs -> {
            try {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.setLeading(14);
                cs.showText("This is an ordinary paragraph of text.");
                cs.newLine();
                cs.showText("It just wraps onto a second line normally.");
                cs.endText();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }); PDDocument b = doc(cs -> {
            try {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.setLeading(14);
                cs.showText("This is an ordinary paragraph of text.");
                cs.newLine();
                cs.showText("It just wraps onto a second line normally.");
                cs.endText();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        })) {
            assertTrue(stage.compare(a, b).isEmpty());
        }
    }

    @Test
    void multiWordCellStaysOneCell() throws IOException {
        try (PDDocument a = table(new String[] {"Name", "Age", "City"},
                new String[] {"Alice", "30", "New York"},
                new String[] {"Bob", "25", "Lyon"});
             PDDocument b = table(new String[] {"Name", "Age", "City"},
                     new String[] {"Alice", "30", "New York"},
                     new String[] {"Bob", "25", "Lyon"})) {
            assertTrue(stage.compare(a, b).isEmpty());
        }
    }

    @Test
    void midCellFontChangeDoesNotSplitTheCell() throws IOException {
        // "Total: 42" where "42" is set in a different font mid-line, still one cell per row.
        try (PDDocument a = doc(cs -> {
            try {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.setLeading(20);
                cs.showText("Label               Total: ");
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.showText("42");
                cs.newLine();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.showText("Label               Total: ");
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.showText("43");
                cs.endText();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        })) {
            List<TableExtractor.Table> tables = new TableExtractor().tablesOn(a, 1);
            assertEquals(1, tables.size());
            assertEquals(2, tables.get(0).columns());
            assertEquals("Total: 42", tables.get(0).rows().get(0).get(1));
            assertEquals("Total: 43", tables.get(0).rows().get(1).get(1));
        }
    }
}
