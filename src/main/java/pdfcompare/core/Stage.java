package pdfcompare.core;

/** The comparison stage that produced a {@link Difference}. */
public enum Stage {
    STRUCTURE,
    METADATA,
    TEXT,
    TABLE,
    VISUAL
}
