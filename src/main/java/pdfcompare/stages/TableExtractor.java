package pdfcompare.stages;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Heuristically finds grid-shaped tables on a page from glyph positions. Package-private:
 * an implementation detail of {@link TableStage}.
 *
 * <p>Two-step heuristic:
 * <ol>
 *   <li>Split each line into "cells" wherever the accumulated gap between glyphs (real
 *       positional gaps, plus the width of any run of space glyphs) exceeds a threshold
 *       scaled to the current font's space width. A run of lines is read from glyph
 *       positions rather than the stripper's reconstructed text, since PDF producers that
 *       lay out text with {@code TJ} offsets instead of literal space glyphs would otherwise
 *       leave the text string and its positions misaligned.</li>
 *   <li>A maximal run of &ge;2 consecutive lines that all split into the same number of
 *       cells (&ge;2) is treated as one table.</li>
 * </ol>
 *
 * <p>This intentionally does not attempt real column-boundary alignment across rows (e.g. via
 * x-coordinate clustering): columns are assigned positionally, cell index to column index. That
 * misreads a table whose row shapes genuinely vary, such as a wrapped multi-line cell or a
 * merged header cell - an accepted limitation for a first cut.
 */
final class TableExtractor {

    /** Gap, in multiples of the current font's space width, that starts a new cell. */
    private static final float GAP_THRESHOLD_SPACES = 3f;
    private static final float MIN_GAP_THRESHOLD = 4f;

    record Table(List<List<String>> rows) {
        int columns() {
            return rows.isEmpty() ? 0 : rows.get(0).size();
        }
    }

    List<Table> tablesOn(PDDocument doc, int page) throws IOException {
        return groupIntoTables(new RowStripper().rowsOf(doc, page));
    }

    private static List<Table> groupIntoTables(List<List<String>> rows) {
        List<Table> tables = new ArrayList<>();
        List<List<String>> block = new ArrayList<>();
        int blockColumns = -1;
        for (List<String> row : rows) {
            if (row.size() >= 2 && row.size() == blockColumns) {
                block.add(row);
                continue;
            }
            flushBlock(block, tables);
            block = new ArrayList<>();
            if (row.size() >= 2) {
                block.add(row);
                blockColumns = row.size();
            } else {
                blockColumns = -1;
            }
        }
        flushBlock(block, tables);
        return tables;
    }

    private static void flushBlock(List<List<String>> block, List<Table> tables) {
        if (block.size() >= 2) {
            tables.add(new Table(List.copyOf(block)));
        }
    }

    /**
     * Reads one page as a list of rows (one per visual line), each a list of cell strings, by
     * walking glyph positions directly. A cell boundary within a line is wherever the
     * accumulated whitespace since the last glyph exceeds {@link #GAP_THRESHOLD_SPACES} times
     * that glyph's space width; a new row starts at every {@link #writeLineSeparator()}, which
     * the base stripper emits between visual lines regardless of how many font runs (and so how
     * many {@code writeString} calls) made up the line.
     */
    private static final class RowStripper extends PDFTextStripper {

        private final List<List<String>> rows = new ArrayList<>();
        private List<String> currentRow;
        private final StringBuilder cell = new StringBuilder();
        private Float prevEnd;
        private float whitespaceRun;

        RowStripper() throws IOException {
            setSortByPosition(true);
        }

        List<List<String>> rowsOf(PDDocument doc, int page) throws IOException {
            rows.clear();
            startNewRow();
            setStartPage(page);
            setEndPage(page);
            getText(doc);
            flushRow();
            return rows;
        }

        private void startNewRow() {
            currentRow = new ArrayList<>();
            cell.setLength(0);
            prevEnd = null;
            whitespaceRun = 0;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            for (TextPosition tp : textPositions) {
                accept(tp);
            }
        }

        private void accept(TextPosition tp) {
            float x = tp.getXDirAdj();
            float width = tp.getWidthDirAdj();
            if (prevEnd != null) {
                whitespaceRun += Math.max(0, x - prevEnd);
            }
            String unicode = tp.getUnicode();
            if (unicode == null || unicode.isBlank()) {
                whitespaceRun += width;
                prevEnd = x + width;
                return;
            }
            float threshold = Math.max(GAP_THRESHOLD_SPACES * tp.getWidthOfSpace(), MIN_GAP_THRESHOLD);
            if (cell.length() > 0 && whitespaceRun > threshold) {
                flushCell();
            } else if (cell.length() > 0 && whitespaceRun > 0) {
                cell.append(' ');
            }
            cell.append(unicode);
            whitespaceRun = 0;
            prevEnd = x + width;
        }

        private void flushCell() {
            if (cell.length() > 0) {
                currentRow.add(cell.toString());
                cell.setLength(0);
            }
        }

        @Override
        protected void writeLineSeparator() throws IOException {
            flushRow();
            super.writeLineSeparator();
        }

        private void flushRow() {
            flushCell();
            if (!currentRow.isEmpty()) {
                rows.add(currentRow);
            }
            startNewRow();
        }
    }
}
