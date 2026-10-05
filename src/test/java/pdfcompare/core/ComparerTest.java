package pdfcompare.core;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparerTest {

    /** A fake stage that always reports one difference of a fixed severity, and counts calls. */
    private static final class FakeStage implements ComparisonStage {
        private final Stage stage;
        private final Severity severity;
        final AtomicInteger calls = new AtomicInteger();

        FakeStage(Stage stage, Severity severity) {
            this.stage = stage;
            this.severity = severity;
        }

        @Override
        public Stage stage() {
            return stage;
        }

        @Override
        public List<Difference> compare(PDDocument expected, PDDocument actual) {
            calls.incrementAndGet();
            return List.of(new Difference(stage, severity, null, stage.name().toLowerCase(), "a", "b"));
        }
    }

    @Test
    void withoutAStopThresholdEveryStageRuns() throws IOException {
        FakeStage first = new FakeStage(Stage.STRUCTURE, Severity.ERROR);
        FakeStage second = new FakeStage(Stage.VISUAL, Severity.ERROR);
        Comparer comparer = new Comparer(List.of(first, second), new Policy());

        try (PDDocument a = new PDDocument(); PDDocument b = new PDDocument()) {
            Report report = comparer.compare(a, b);
            assertEquals(2, report.differences().size());
        }
        assertEquals(1, first.calls.get());
        assertEquals(1, second.calls.get());
    }

    @Test
    void stopAtSkipsLaterStagesOnceReached() throws IOException {
        FakeStage first = new FakeStage(Stage.STRUCTURE, Severity.ERROR);
        FakeStage second = new FakeStage(Stage.VISUAL, Severity.ERROR);
        Comparer comparer = new Comparer(List.of(first, second), new Policy(), Severity.ERROR);

        try (PDDocument a = new PDDocument(); PDDocument b = new PDDocument()) {
            Report report = comparer.compare(a, b);
            // the first stage's difference is still in the report; the second never ran
            assertEquals(1, report.differences().size());
            assertEquals(Stage.STRUCTURE, report.differences().get(0).stage());
        }
        assertEquals(1, first.calls.get());
        assertEquals(0, second.calls.get());
    }

    @Test
    void stopAtChecksSeverityAfterThePolicyReclassifiesIt() throws IOException {
        // the stage emits ERROR, but the policy downgrades it to INFO - that should NOT trigger
        // an early stop, since stopAt looks at what actually lands in the report.
        FakeStage first = new FakeStage(Stage.STRUCTURE, Severity.ERROR);
        FakeStage second = new FakeStage(Stage.VISUAL, Severity.ERROR);
        Policy policy = new Policy().rule("structure", Severity.INFO);
        Comparer comparer = new Comparer(List.of(first, second), policy, Severity.ERROR);

        try (PDDocument a = new PDDocument(); PDDocument b = new PDDocument()) {
            Report report = comparer.compare(a, b);
            assertEquals(2, report.differences().size());
            assertEquals(Severity.INFO, report.differences().get(0).severity());
        }
        assertEquals(1, first.calls.get());
        assertEquals(1, second.calls.get());
    }

    @Test
    void stageThatNeverReachesTheThresholdLetsLaterStagesRun() throws IOException {
        FakeStage first = new FakeStage(Stage.STRUCTURE, Severity.WARNING);
        FakeStage second = new FakeStage(Stage.VISUAL, Severity.ERROR);
        Comparer comparer = new Comparer(List.of(first, second), new Policy(), Severity.ERROR);

        try (PDDocument a = new PDDocument(); PDDocument b = new PDDocument()) {
            Report report = comparer.compare(a, b);
            assertEquals(2, report.differences().size());
        }
        assertTrue(second.calls.get() > 0);
    }
}
