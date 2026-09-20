package pdfcompare.core;

/** How much a difference matters. Declaration order is ascending importance. */
public enum Severity {
    /** Expected to change on re-save (producer, timestamps, ...); does not indicate damage. */
    INFO,
    /** Unclassified or possibly meaningful; needs a human look. */
    WARNING,
    /** Content or layout was lost or altered. */
    ERROR;

    public boolean atLeast(Severity other) {
        return compareTo(other) >= 0;
    }
}
