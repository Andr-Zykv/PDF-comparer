package pdfcompare.core;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.List;

/** One step of the comparison pipeline (structure, metadata, text, ...). */
public interface ComparisonStage {

    Stage stage();

    /**
     * Compares {@code expected} (the reference) with {@code actual}.
     *
     * @return every difference found; empty if the documents match as far as this stage can tell
     */
    List<Difference> compare(PDDocument expected, PDDocument actual) throws IOException;
}
