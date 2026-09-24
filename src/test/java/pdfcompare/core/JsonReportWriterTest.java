package pdfcompare.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonReportWriterTest {

    @Test
    void emptyReportHasAnEmptyDifferencesArray() {
        String json = JsonReportWriter.toJson(new Report(List.of()));
        assertTrue(json.contains("\"differences\": []"));
        assertTrue(json.contains("\"INFO\": 0"));
    }

    @Test
    void differenceFieldsRoundTripAsText() {
        Difference d = new Difference(Stage.TEXT, Severity.ERROR, 3, "page[3].word[1]", "quick", "slow");
        String json = JsonReportWriter.toJson(new Report(List.of(d)));
        assertTrue(json.contains("\"stage\": \"TEXT\""));
        assertTrue(json.contains("\"severity\": \"ERROR\""));
        assertTrue(json.contains("\"page\": 3"));
        assertTrue(json.contains("\"path\": \"page[3].word[1]\""));
        assertTrue(json.contains("\"expected\": \"quick\""));
        assertTrue(json.contains("\"actual\": \"slow\""));
    }

    @Test
    void nullFieldsBecomeJsonNull() {
        Difference d = new Difference(Stage.METADATA, Severity.WARNING, null, "info.Author", null, "New");
        String json = JsonReportWriter.toJson(new Report(List.of(d)));
        assertTrue(json.contains("\"page\": null"));
        assertTrue(json.contains("\"expected\": null"));
    }

    @Test
    void specialCharactersAreEscaped() {
        Difference d = new Difference(Stage.TEXT, Severity.ERROR, 1, "page[1].word[0]",
                "line one\nline \"two\"\\three", "tab\there");
        String json = JsonReportWriter.toJson(new Report(List.of(d)));
        assertTrue(json.contains("line one\\nline \\\"two\\\"\\\\three"));
        assertTrue(json.contains("tab\\there"));
    }

    @Test
    void writeCreatesParentDirectoriesAndPersistsValidJson(@TempDir Path tempDir) throws IOException {
        Path out = tempDir.resolve("nested/report.json");
        Report report = new Report(List.of(new Difference(Stage.STRUCTURE, Severity.ERROR, 1, "pages", "2", "1")));
        JsonReportWriter.write(report, out);
        assertTrue(Files.exists(out));
        assertEquals(JsonReportWriter.toJson(report), Files.readString(out));
    }
}
