package pdfcompare;

import pdfcompare.core.Severity;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parsed command-line arguments for {@link Main}. Package-private: an implementation detail.
 *
 * @param jsonPath where to write the JSON report, or {@code null} to skip it ({@code --no-json})
 * @param failAt   the severity {@link Main} exits non-zero at if the report reaches it
 * @param failFast if true, stop after the first stage whose own findings reach {@code failAt} -
 *                 once we already know the documents aren't equal, later stages are redundant
 */
record Options(File expected, File actual, Path jsonPath, Severity failAt, boolean failFast) {

    static Options parse(String[] args) {
        List<String> positional = new ArrayList<>();
        Path explicitJson = null;
        boolean noJson = false;
        Severity failAt = Severity.ERROR;
        boolean failFast = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--json" -> explicitJson = Path.of(requireValue(args, ++i, "--json"));
                case "--no-json" -> noJson = true;
                case "--fail-at" -> failAt = parseSeverity(requireValue(args, ++i, "--fail-at"));
                case "--fail-fast" -> failFast = true;
                default -> positional.add(args[i]);
            }
        }

        if (positional.size() != 2) {
            throw new IllegalArgumentException("expected exactly 2 file arguments, got " + positional.size());
        }
        if (noJson && explicitJson != null) {
            throw new IllegalArgumentException("cannot combine --json and --no-json");
        }

        File expected = new File(positional.get(0));
        File actual = new File(positional.get(1));
        requireReadableFile(expected, "expected");
        requireReadableFile(actual, "actual");

        Path jsonPath = noJson ? null : explicitJson != null ? explicitJson : defaultJsonPath(actual);
        return new Options(expected, actual, jsonPath, failAt, failFast);
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flag + " requires a value");
        }
        return args[index];
    }

    private static Severity parseSeverity(String value) {
        try {
            return Severity.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--fail-at must be one of INFO, WARNING, ERROR (got '" + value + "')");
        }
    }

    private static void requireReadableFile(File file, String label) {
        if (!file.isFile()) {
            throw new IllegalArgumentException(label + " PDF not found: " + file);
        }
    }

    /** {@code <actual's directory>/<actual's base name>.diff.json}. */
    private static Path defaultJsonPath(File actual) {
        String name = actual.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        File parent = actual.getAbsoluteFile().getParentFile();
        Path parentPath = parent != null ? parent.toPath() : Path.of(".");
        return parentPath.resolve(base + ".diff.json");
    }
}
