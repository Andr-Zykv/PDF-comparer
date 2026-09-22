package pdfcompare.stages;

import org.apache.pdfbox.pdmodel.PDDocument;

import pdfcompare.core.ComparisonStage;
import pdfcompare.core.Difference;
import pdfcompare.core.Severity;
import pdfcompare.core.Stage;
import pdfcompare.stages.TableExtractor.Table;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares grid-shaped tables found on each page. See {@link TableExtractor} for how a "table"
 * is recognized in the first place, and its documented limitations - notably that a table's
 * rows must all detect the same number of cells, and columns are matched positionally rather
 * than by x-coordinate alignment.
 */
public final class TableStage implements ComparisonStage {

    @Override
    public Stage stage() {
        return Stage.TABLE;
    }

    @Override
    public List<Difference> compare(PDDocument expected, PDDocument actual) throws IOException {
        List<Difference> out = new ArrayList<>();
        TableExtractor extractor = new TableExtractor();
        int pages = Math.min(expected.getNumberOfPages(), actual.getNumberOfPages());
        for (int page = 1; page <= pages; page++) {
            List<Table> tablesA = extractor.tablesOn(expected, page);
            List<Table> tablesB = extractor.tablesOn(actual, page);
            compareTables(page, tablesA, tablesB, out);
        }
        return out;
    }

    private void compareTables(int page, List<Table> a, List<Table> b, List<Difference> out) {
        if (a.size() != b.size()) {
            out.add(diff(page, "page[" + page + "].tables", String.valueOf(a.size()), String.valueOf(b.size())));
        }
        for (int t = 0; t < Math.min(a.size(), b.size()); t++) {
            compareTable(page, t, a.get(t), b.get(t), out);
        }
    }

    private void compareTable(int page, int index, Table a, Table b, List<Difference> out) {
        String prefix = "page[" + page + "].table[" + index + "].";

        int rowsA = a.rows().size();
        int rowsB = b.rows().size();
        if (rowsA != rowsB) {
            out.add(diff(page, prefix + "rows", String.valueOf(rowsA), String.valueOf(rowsB)));
        }

        int colsA = a.columns();
        int colsB = b.columns();
        if (colsA != colsB) {
            out.add(diff(page, prefix + "cols", String.valueOf(colsA), String.valueOf(colsB)));
        }

        int rows = Math.min(rowsA, rowsB);
        int cols = Math.min(colsA, colsB);
        for (int r = 0; r < rows; r++) {
            List<String> rowA = a.rows().get(r);
            List<String> rowB = b.rows().get(r);
            for (int c = 0; c < cols; c++) {
                String va = rowA.get(c);
                String vb = rowB.get(c);
                if (!va.equals(vb)) {
                    out.add(diff(page, prefix + "cell[" + r + "][" + c + "]", va, vb));
                }
            }
        }
    }

    private static Difference diff(int page, String path, String expected, String actual) {
        return new Difference(Stage.TABLE, Severity.ERROR, page, path, expected, actual);
    }
}
