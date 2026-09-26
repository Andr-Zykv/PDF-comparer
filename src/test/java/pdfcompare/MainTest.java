package pdfcompare;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @TempDir
    Path dir;

    private Path savePdf(String name, int pages, String title) throws IOException {
        Path file = dir.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle(title);
            doc.setDocumentInformation(info);
            doc.save(file.toFile());
        }
        return file;
    }

    /** Captures what Main would otherwise print to the real stdout/stderr. */
    private static final class Capture {
        private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        final PrintStream out = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
        final PrintStream err = new PrintStream(errBuf, true, StandardCharsets.UTF_8);

        String out() {
            return outBuf.toString(StandardCharsets.UTF_8);
        }

        String err() {
            return errBuf.toString(StandardCharsets.UTF_8);
        }
    }

    @Test
    void identicalContentPdfsExitZeroAndWriteAJsonReport() throws IOException {
        Path expected = savePdf("expected.pdf", 1, "Report");
        Path actual = savePdf("actual.pdf", 1, "Report");
        Capture cap = new Capture();

        int code = Main.run(new String[] {expected.toString(), actual.toString()}, cap.out, cap.err);

        // not asserting zero differences outright: two independently-saved PDFs can still
        // differ in file ID/timestamps, which is exactly the INFO-level noise Policy.defaults()
        // exists to absorb - it shouldn't reach ERROR and shouldn't fail the run.
        assertEquals(0, code);
        assertTrue(cap.out().contains(": 0 error"));
        Path jsonPath = dir.resolve("actual.diff.json");
        assertTrue(Files.exists(jsonPath));
    }

    @Test
    void pageCountMismatchExitsOneAtTheDefaultThreshold() throws IOException {
        Path expected = savePdf("expected.pdf", 2, "Report");
        Path actual = savePdf("actual.pdf", 1, "Report");
        Capture cap = new Capture();

        int code = Main.run(new String[] {expected.toString(), actual.toString()}, cap.out, cap.err);

        assertEquals(1, code);
        assertTrue(cap.out().contains("[ERROR"));
        assertTrue(cap.out().contains("pages"));
    }

    @Test
    void titleChangeIsAWarningThatDoesNotFailAtTheDefaultThreshold() throws IOException {
        Path expected = savePdf("expected.pdf", 1, "Old Title");
        Path actual = savePdf("actual.pdf", 1, "New Title");
        Capture cap = new Capture();

        int code = Main.run(new String[] {expected.toString(), actual.toString()}, cap.out, cap.err);

        assertEquals(0, code);
        assertTrue(cap.out().contains("[WARNING"));
        assertTrue(cap.out().contains("info.Title"));
    }

    @Test
    void failAtWarningCatchesTheSameTitleChange() throws IOException {
        Path expected = savePdf("expected.pdf", 1, "Old Title");
        Path actual = savePdf("actual.pdf", 1, "New Title");
        Capture cap = new Capture();

        int code = Main.run(new String[] {expected.toString(), actual.toString(), "--fail-at", "WARNING"}, cap.out, cap.err);

        assertEquals(1, code);
    }

    @Test
    void withoutFailFastLaterStagesStillReportEvenAfterAnEarlierErrorStage() throws IOException {
        // page count differs (STRUCTURE, ERROR) AND the title differs (METADATA, WARNING) -
        // without --fail-fast both show up, since every stage runs regardless.
        Path expected = savePdf("expected.pdf", 1, "Old Title");
        Path actual = savePdf("actual.pdf", 2, "New Title");
        Capture cap = new Capture();

        int code = Main.run(new String[] {expected.toString(), actual.toString()}, cap.out, cap.err);

        assertEquals(1, code);
        assertTrue(cap.out().contains("[ERROR"));
        assertTrue(cap.out().contains("[WARNING"));
        assertTrue(cap.out().contains("info.Title"));
    }

    @Test
    void failFastStopsAtTheFirstStageThatReachesFailAt() throws IOException {
        // same two PDFs as above, but --fail-fast means METADATA (and everything after
        // STRUCTURE) never runs once STRUCTURE's page-count difference already reaches ERROR.
        Path expected = savePdf("expected.pdf", 1, "Old Title");
        Path actual = savePdf("actual.pdf", 2, "New Title");
        Capture cap = new Capture();

        int code = Main.run(new String[] {expected.toString(), actual.toString(), "--fail-fast"}, cap.out, cap.err);

        assertEquals(1, code);
        assertTrue(cap.out().contains("[ERROR"));
        assertFalse(cap.out().contains("info.Title"));
        assertFalse(cap.out().contains("[WARNING"));
    }

    @Test
    void noJsonFlagSkipsWritingAReportFile() throws IOException {
        Path expected = savePdf("expected.pdf", 1, "Report");
        Path actual = savePdf("actual.pdf", 1, "Report");
        Capture cap = new Capture();

        Main.run(new String[] {expected.toString(), actual.toString(), "--no-json"}, cap.out, cap.err);

        assertFalse(Files.exists(dir.resolve("actual.diff.json")));
    }

    @Test
    void missingArgumentsPrintUsageAndExitTwo() {
        Capture cap = new Capture();
        int code = Main.run(new String[] {"only-one.pdf"}, cap.out, cap.err);
        assertEquals(2, code);
        assertTrue(cap.err().contains("usage:"));
    }
}
