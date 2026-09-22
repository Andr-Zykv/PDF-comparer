package pdfcompare.stages;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import pdfcompare.core.ComparisonStage;
import pdfcompare.core.Difference;
import pdfcompare.core.Severity;
import pdfcompare.core.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Compares the visible text of two documents, page by page, at word granularity. Says nothing
 * about fonts, layout or images; see {@link StructureStage} for those.
 *
 * <p>Words are extracted in reading order ({@link PDFTextStripper#setSortByPosition}) and split
 * on whitespace. Changed, added and removed words are all reported as {@link Severity#ERROR},
 * since word-level content loss is exactly what this stage exists to catch; a {@link
 * pdfcompare.core.Policy} can downgrade specific paths if a project needs to.
 */
public final class TextStage implements ComparisonStage {

    /** Above this many (expected words * actual words), the word-level diff is skipped for that page. */
    private static final long MAX_ALIGNMENT_CELLS = 4_000_000L;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    public Stage stage() {
        return Stage.TEXT;
    }

    @Override
    public List<Difference> compare(PDDocument expected, PDDocument actual) throws IOException {
        List<Difference> out = new ArrayList<>();
        int pages = Math.min(expected.getNumberOfPages(), actual.getNumberOfPages());
        for (int i = 1; i <= pages; i++) {
            List<String> wordsA = words(expected, i);
            List<String> wordsB = words(actual, i);
            comparePage(i, wordsA, wordsB, out);
        }
        return out;
    }

    private void comparePage(int page, List<String> a, List<String> b, List<Difference> out) {
        if (a.equals(b)) {
            return;
        }
        if ((long) (a.size() + 1) * (b.size() + 1) > MAX_ALIGNMENT_CELLS) {
            out.add(diff(Severity.WARNING, page, "page[" + page + "].text",
                    excerpt(a), excerpt(b)));
            return;
        }
        for (WordDiff.Op op : WordDiff.diff(a, b)) {
            String path = "page[" + page + "].word[" + op.anchor() + "]";
            switch (op.type()) {
                case REPLACE -> out.add(diff(Severity.ERROR, page, path, a.get(op.expectedIndex()), b.get(op.actualIndex())));
                case DELETE -> out.add(diff(Severity.ERROR, page, path, a.get(op.expectedIndex()), null));
                case INSERT -> out.add(diff(Severity.ERROR, page, path, null, b.get(op.actualIndex())));
            }
        }
    }

    private static Difference diff(Severity severity, int page, String path, String expected, String actual) {
        return new Difference(Stage.TEXT, severity, page, path, expected, actual);
    }

    private static String excerpt(List<String> words) {
        String joined = String.join(" ", words);
        return joined.length() > 200 ? joined.substring(0, 200) + "..." : joined;
    }

    private static List<String> words(PDDocument doc, int page) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        String text = stripper.getText(doc);
        List<String> words = new ArrayList<>();
        for (String w : WHITESPACE.split(text.trim())) {
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        return words;
    }
}
