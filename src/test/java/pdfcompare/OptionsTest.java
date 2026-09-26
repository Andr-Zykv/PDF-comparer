package pdfcompare;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pdfcompare.core.Severity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OptionsTest {

    @TempDir
    Path dir;

    private String touch(String name) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, "x");
        return file.toString();
    }

    @Test
    void defaultsToJsonNextToActualAndFailAtError() throws IOException {
        String expected = touch("expected.pdf");
        String actual = touch("actual.pdf");
        Options options = Options.parse(new String[] {expected, actual});
        assertEquals(dir.resolve("actual.diff.json"), options.jsonPath());
        assertEquals(Severity.ERROR, options.failAt());
    }

    @Test
    void explicitJsonPathOverridesTheDefault() throws IOException {
        String expected = touch("expected.pdf");
        String actual = touch("actual.pdf");
        Path custom = dir.resolve("out/report.json");
        Options options = Options.parse(new String[] {expected, actual, "--json", custom.toString()});
        assertEquals(custom, options.jsonPath());
    }

    @Test
    void noJsonSuppressesTheReport() throws IOException {
        String expected = touch("expected.pdf");
        String actual = touch("actual.pdf");
        Options options = Options.parse(new String[] {expected, actual, "--no-json"});
        assertNull(options.jsonPath());
    }

    @Test
    void failAtIsCaseInsensitive() throws IOException {
        String expected = touch("expected.pdf");
        String actual = touch("actual.pdf");
        Options options = Options.parse(new String[] {expected, actual, "--fail-at", "warning"});
        assertEquals(Severity.WARNING, options.failAt());
    }

    @Test
    void rejectsCombiningJsonAndNoJson() throws IOException {
        String expected = touch("expected.pdf");
        String actual = touch("actual.pdf");
        assertThrows(IllegalArgumentException.class,
                () -> Options.parse(new String[] {expected, actual, "--json", "x.json", "--no-json"}));
    }

    @Test
    void rejectsWrongArgumentCount() throws IOException {
        String expected = touch("expected.pdf");
        assertThrows(IllegalArgumentException.class, () -> Options.parse(new String[] {expected}));
    }

    @Test
    void rejectsMissingFile() {
        assertThrows(IllegalArgumentException.class,
                () -> Options.parse(new String[] {"/no/such/expected.pdf", "/no/such/actual.pdf"}));
    }

    @Test
    void rejectsUnknownSeverity() throws IOException {
        String expected = touch("expected.pdf");
        String actual = touch("actual.pdf");
        assertThrows(IllegalArgumentException.class,
                () -> Options.parse(new String[] {expected, actual, "--fail-at", "CRITICAL"}));
    }
}
