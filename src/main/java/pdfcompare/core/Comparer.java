package pdfcompare.core;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Runs the configured stages over two documents and applies the policy to what they find. */
public final class Comparer {

    private final List<ComparisonStage> stages;
    private final Policy policy;

    public Comparer(List<ComparisonStage> stages, Policy policy) {
        this.stages = List.copyOf(stages);
        this.policy = policy;
    }

    public Report compare(File expected, File actual) throws IOException {
        try (PDDocument a = Loader.loadPDF(expected); PDDocument b = Loader.loadPDF(actual)) {
            return compare(a, b);
        }
    }

    public Report compare(PDDocument expected, PDDocument actual) throws IOException {
        List<Difference> all = new ArrayList<>();
        for (ComparisonStage stage : stages) {
            for (Difference d : stage.compare(expected, actual)) {
                all.add(policy.apply(d));
            }
        }
        return new Report(all);
    }
}
