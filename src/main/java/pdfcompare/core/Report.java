package pdfcompare.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** All differences found, after the {@link Policy} has been applied. */
public final class Report {

    private final List<Difference> differences;

    public Report(List<Difference> differences) {
        this.differences = List.copyOf(differences);
    }

    public List<Difference> differences() {
        return differences;
    }

    public List<Difference> atLeast(Severity threshold) {
        return differences.stream().filter(d -> d.severity().atLeast(threshold)).toList();
    }

    public boolean hasAtLeast(Severity threshold) {
        return differences.stream().anyMatch(d -> d.severity().atLeast(threshold));
    }

    public Map<Severity, Long> countBySeverity() {
        Map<Severity, Long> counts = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) {
            counts.put(s, 0L);
        }
        for (Difference d : differences) {
            counts.merge(d.severity(), 1L, Long::sum);
        }
        return Collections.unmodifiableMap(counts);
    }
}
