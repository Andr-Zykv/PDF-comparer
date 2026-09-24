package pdfcompare.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Serializes a {@link Report} to JSON. Hand-rolled rather than pulling in a library: every field
 * is a known string, enum or nullable integer, so there's no arbitrary object graph to walk.
 *
 * <pre>{@code
 * {
 *   "summary": { "INFO": 2, "WARNING": 1, "ERROR": 0 },
 *   "differences": [
 *     { "stage": "TEXT", "severity": "ERROR", "page": 1,
 *       "path": "page[1].word[1]", "expected": "quick", "actual": "slow" },
 *     ...
 *   ]
 * }
 * }</pre>
 */
public final class JsonReportWriter {

    private JsonReportWriter() {
    }

    public static void write(Report report, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, toJson(report), StandardCharsets.UTF_8);
    }

    public static String toJson(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"summary\": {\n");
        Map<Severity, Long> counts = report.countBySeverity();
        Severity[] severities = Severity.values();
        for (int i = 0; i < severities.length; i++) {
            sb.append("    \"").append(severities[i].name()).append("\": ").append(counts.get(severities[i]));
            sb.append(i < severities.length - 1 ? ",\n" : "\n");
        }
        sb.append("  },\n  \"differences\": [");

        List<Difference> diffs = report.differences();
        for (int i = 0; i < diffs.size(); i++) {
            sb.append(i == 0 ? "\n" : ",\n");
            appendDifference(sb, diffs.get(i));
        }
        sb.append(diffs.isEmpty() ? "]\n" : "\n  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendDifference(StringBuilder sb, Difference d) {
        sb.append("    {\n")
                .append("      \"stage\": \"").append(d.stage().name()).append("\",\n")
                .append("      \"severity\": \"").append(d.severity().name()).append("\",\n")
                .append("      \"page\": ").append(d.page() == null ? "null" : d.page()).append(",\n")
                .append("      \"path\": ").append(quote(d.path())).append(",\n")
                .append("      \"expected\": ").append(quote(d.expected())).append(",\n")
                .append("      \"actual\": ").append(quote(d.actual())).append("\n")
                .append("    }");
    }

    private static String quote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
