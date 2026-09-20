package pdfcompare.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Maps difference paths to severities. Rules are checked in the order they were added and the
 * first match wins. A difference that matches no rule keeps the severity its stage gave it, so
 * nothing is silently ignored.
 *
 * <p>Patterns are matched against {@link Difference#path()}; {@code *} matches any run of
 * characters (including dots), everything else is literal.
 */
public final class Policy {

    private record Rule(Pattern pattern, Severity severity) {}

    private final List<Rule> rules = new ArrayList<>();

    /** Rules for changes that routinely happen when a PDF is re-saved and say nothing about content. */
    public static Policy defaults() {
        return new Policy()
                .rule("info.Producer", Severity.INFO)
                .rule("info.Creator", Severity.INFO)
                .rule("info.CreationDate", Severity.INFO)
                .rule("info.ModDate", Severity.INFO)
                .rule("trailer.ID", Severity.INFO)
                .rule("xmp.*Date", Severity.INFO)
                .rule("xmp.*InstanceID", Severity.INFO)
                .rule("xmp.*DocumentID", Severity.INFO)
                .rule("xmp.*Producer", Severity.INFO)
                .rule("header.version", Severity.INFO);
    }

    public Policy rule(String globPattern, Severity severity) {
        rules.add(new Rule(compile(globPattern), severity));
        return this;
    }

    public Severity classify(Difference difference) {
        for (Rule rule : rules) {
            if (rule.pattern().matcher(difference.path()).matches()) {
                return rule.severity();
            }
        }
        return difference.severity();
    }

    public Difference apply(Difference difference) {
        Severity severity = classify(difference);
        return severity == difference.severity() ? difference : difference.withSeverity(severity);
    }

    private static Pattern compile(String glob) {
        StringBuilder regex = new StringBuilder();
        int start = 0;
        for (int star; (star = glob.indexOf('*', start)) >= 0; start = star + 1) {
            regex.append(Pattern.quote(glob.substring(start, star))).append(".*");
        }
        regex.append(Pattern.quote(glob.substring(start)));
        return Pattern.compile(regex.toString());
    }
}
