package pdfcompare.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportTest {

    private static Difference diff(Severity severity) {
        return new Difference(Stage.TEXT, severity, 1, "page[1].word[0]", "x", "y");
    }

    @Test
    void thresholdAndCounts() {
        Report report = new Report(List.of(diff(Severity.INFO), diff(Severity.INFO), diff(Severity.ERROR)));
        assertTrue(report.hasAtLeast(Severity.ERROR));
        assertEquals(1, report.atLeast(Severity.WARNING).size());
        assertEquals(2L, report.countBySeverity().get(Severity.INFO));
        assertEquals(0L, report.countBySeverity().get(Severity.WARNING));
    }

    @Test
    void emptyReportHasNothing() {
        assertFalse(new Report(List.of()).hasAtLeast(Severity.INFO));
    }
}
