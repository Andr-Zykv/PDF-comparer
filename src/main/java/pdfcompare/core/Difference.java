package pdfcompare.core;

import java.util.Objects;

/**
 * One observed difference between the two documents.
 *
 * @param stage    stage that found it
 * @param severity severity as emitted by the stage; a {@link Policy} may reclassify it
 * @param page     1-based page number, or {@code null} for document-level differences
 * @param path     dotted location, e.g. {@code info.Title}, {@code page[3].fonts}, {@code page[2].word[41]}
 * @param expected value in the first (reference) document, or {@code null} if absent
 * @param actual   value in the second document, or {@code null} if absent
 */
public record Difference(Stage stage, Severity severity, Integer page, String path,
                         String expected, String actual) {

    public Difference {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(path, "path");
    }

    public Difference withSeverity(Severity newSeverity) {
        return new Difference(stage, newSeverity, page, path, expected, actual);
    }
}
