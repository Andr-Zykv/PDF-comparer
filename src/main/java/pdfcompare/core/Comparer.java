package pdfcompare.core;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the configured stages over two documents and applies the policy to what they find.
 *
 * <p>Stages run in the given order, and that order matters when {@code stopAt} is set: the first
 * stage whose (post-policy) differences reach it ends the run there, so put cheap, foundational
 * checks - page count, metadata - before expensive ones like rendering a page to compare it as
 * an image. The stages already offered by {@code pdfcompare.stages} are declared in roughly that
 * order.
 */
public final class Comparer {

    private final List<ComparisonStage> stages;
    private final Policy policy;
    private final Severity stopAt;

    /** Runs every stage regardless of what earlier ones find. */
    public Comparer(List<ComparisonStage> stages, Policy policy) {
        this(stages, policy, null);
    }

    /**
     * @param stopAt severity at which a stage's own findings stop the run before any later
     *               stage executes, or {@code null} to always run every stage. Earlier stages'
     *               differences are kept in the {@link Report} either way; only later stages are
     *               skipped.
     */
    public Comparer(List<ComparisonStage> stages, Policy policy, Severity stopAt) {
        this.stages = List.copyOf(stages);
        this.policy = policy;
        this.stopAt = stopAt;
    }

    public Report compare(File expected, File actual) throws IOException {
        try (PDDocument a = Loader.loadPDF(expected); PDDocument b = Loader.loadPDF(actual)) {
            return compare(a, b);
        }
    }

    public Report compare(PDDocument expected, PDDocument actual) throws IOException {
        List<Difference> all = new ArrayList<>();
        for (ComparisonStage stage : stages) {
            boolean stageReachedStopThreshold = false;
            for (Difference d : stage.compare(expected, actual)) {
                Difference classified = policy.apply(d);
                all.add(classified);
                if (stopAt != null && classified.severity().atLeast(stopAt)) {
                    stageReachedStopThreshold = true;
                }
            }
            if (stageReachedStopThreshold) {
                break;
            }
        }
        return new Report(all);
    }
}
