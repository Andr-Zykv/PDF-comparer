package pdfcompare;

import pdfcompare.core.Comparer;
import pdfcompare.core.Difference;
import pdfcompare.core.JsonReportWriter;
import pdfcompare.core.Policy;
import pdfcompare.core.Report;
import pdfcompare.core.Severity;
import pdfcompare.stages.MetadataStage;
import pdfcompare.stages.StructureStage;
import pdfcompare.stages.TableStage;
import pdfcompare.stages.TextStage;
import pdfcompare.stages.VisualStage;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/** Command-line entry point: compares two PDFs and reports what differs. */
public final class Main {

    private static final String USAGE = """
            usage: pdf-compare <expected.pdf> <actual.pdf> [options]

            options:
              --json <path>     write the JSON report here (default: <actual>.diff.json)
              --no-json         skip writing a JSON report
              --fail-at LEVEL   exit 1 if any difference reaches this severity (default: ERROR)
                                LEVEL is one of INFO, WARNING, ERROR
              --fail-fast       stop after the first stage that reaches --fail-at; skips later,
                                more expensive stages once the documents are already known to
                                differ enough to fail
            """;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /** Package-visible for testing: runs against injected streams instead of the real ones. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            err.println("error: " + e.getMessage());
            err.println();
            err.print(USAGE);
            return 2;
        }

        Report report;
        try {
            report = defaultComparer(options).compare(options.expected(), options.actual());
        } catch (IOException e) {
            err.println("error comparing PDFs: " + e.getMessage());
            return 2;
        }

        printSummary(report, out);

        if (options.jsonPath() != null) {
            try {
                JsonReportWriter.write(report, options.jsonPath());
                out.println();
                out.println("JSON report written to " + options.jsonPath());
            } catch (IOException e) {
                err.println("error writing JSON report: " + e.getMessage());
                return 2;
            }
        }

        return report.hasAtLeast(options.failAt()) ? 1 : 0;
    }

    private static Comparer defaultComparer(Options options) {
        return new Comparer(
                List.of(new StructureStage(), new MetadataStage(), new TextStage(),
                        new TableStage(), new VisualStage()),
                Policy.defaults(),
                options.failFast() ? options.failAt() : null);
    }

    private static void printSummary(Report report, PrintStream out) {
        Map<Severity, Long> counts = report.countBySeverity();
        out.printf("%d difference(s): %d error, %d warning, %d info%n",
                report.differences().size(), counts.get(Severity.ERROR),
                counts.get(Severity.WARNING), counts.get(Severity.INFO));
        for (Difference d : report.differences()) {
            out.printf("[%-7s] %-9s %s: %s -> %s%n",
                    d.severity(), d.stage(), d.path(), display(d.expected()), display(d.actual()));
        }
    }

    private static String display(String value) {
        return value == null ? "<absent>" : value;
    }
}
