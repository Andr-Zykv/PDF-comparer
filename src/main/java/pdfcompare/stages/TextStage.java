package pdfcompare.stages;

import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorN;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingColor;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingColorN;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingColorSpace;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingDeviceCMYKColor;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingDeviceGrayColor;
import org.apache.pdfbox.contentstream.operator.color.SetStrokingDeviceRGBColor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import pdfcompare.core.ComparisonStage;
import pdfcompare.core.Difference;
import pdfcompare.core.Severity;
import pdfcompare.core.Stage;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Compares the visible text of two documents, page by page, at word granularity, including how
 * each word is styled (font, weight, size, colour).
 *
 * <p>Words are extracted in reading order ({@link PDFTextStripper#setSortByPosition}) and split
 * on whitespace. Changed, added and removed words are all reported as {@link Severity#ERROR},
 * since word-level content loss is exactly what this stage exists to catch; a {@link
 * pdfcompare.core.Policy} can downgrade specific paths if a project needs to.
 *
 * <p>Words that match are then compared on style, taken from the word's first glyph. Style
 * changes are {@link Severity#WARNING}s, and a change shared by consecutive words is reported
 * once for the whole run, e.g. {@code page[2].word[5-30].font}.
 */
public final class TextStage implements ComparisonStage {

    /** Above this many (expected words * actual words), the word-level diff is skipped for that page. */
    private static final long MAX_ALIGNMENT_CELLS = 4_000_000L;

    /** Font sizes may differ by this many points before it counts as a size change. */
    private static final float SIZE_TOLERANCE = 0.5f;

    /** RGB channels (0-255) may differ by this much before it counts as a colour change. */
    private static final int COLOR_CHANNEL_TOLERANCE = 3;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Pattern BOLD_NAME = Pattern.compile("(?i).*(bold|black|heavy|demi|semibold).*");

    /** How a word is drawn. {@code color} is {@code #RRGGBB}, {@code invisible}, or a colour space and its components. */
    record Style(String font, boolean bold, float sizePt, String color) {}

    record StyledWord(String text, Style style) {}

    @Override
    public Stage stage() {
        return Stage.TEXT;
    }

    @Override
    public List<Difference> compare(PDDocument expected, PDDocument actual) throws IOException {
        List<Difference> out = new ArrayList<>();
        int pages = Math.min(expected.getNumberOfPages(), actual.getNumberOfPages());
        for (int i = 1; i <= pages; i++) {
            List<StyledWord> wordsA = words(expected, i);
            List<StyledWord> wordsB = words(actual, i);
            comparePage(i, wordsA, wordsB, out);
        }
        return out;
    }

    private void comparePage(int page, List<StyledWord> a, List<StyledWord> b, List<Difference> out) {
        if (a.equals(b)) {
            return;
        }
        List<String> textA = texts(a);
        List<String> textB = texts(b);
        if ((long) (a.size() + 1) * (b.size() + 1) > MAX_ALIGNMENT_CELLS) {
            out.add(diff(Severity.WARNING, page, "page[" + page + "].text",
                    excerpt(textA), excerpt(textB)));
            return;
        }
        StyleRuns styleRuns = new StyleRuns(page);
        for (WordDiff.Op op : WordDiff.diff(textA, textB)) {
            String path = "page[" + page + "].word[" + op.anchor() + "]";
            switch (op.type()) {
                case EQUAL -> styleRuns.add(op.expectedIndex(), a.get(op.expectedIndex()).style(), b.get(op.actualIndex()).style());
                case REPLACE -> out.add(diff(Severity.ERROR, page, path, textA.get(op.expectedIndex()), textB.get(op.actualIndex())));
                case DELETE -> out.add(diff(Severity.ERROR, page, path, textA.get(op.expectedIndex()), null));
                case INSERT -> out.add(diff(Severity.ERROR, page, path, null, textB.get(op.actualIndex())));
            }
        }
        out.addAll(styleRuns.finish());
    }

    /**
     * Collects style changes on matched words, merging a change that repeats on consecutive
     * words (same attribute, same before and after) into one difference.
     */
    private static final class StyleRuns {
        private final int page;
        private final Map<String, Run> open = new HashMap<>();
        private final List<Difference> done = new ArrayList<>();

        private static final class Run {
            final int start;
            int end;
            final String expected;
            final String actual;

            Run(int start, String expected, String actual) {
                this.start = start;
                this.end = start;
                this.expected = expected;
                this.actual = actual;
            }
        }

        StyleRuns(int page) {
            this.page = page;
        }

        void add(int index, Style a, Style b) {
            Map<String, String[]> changes = styleChanges(a, b);
            for (String attr : List.of("font", "bold", "size", "color")) {
                String[] change = changes.get(attr);
                Run run = open.get(attr);
                if (run != null && change != null && run.end == index - 1
                        && run.expected.equals(change[0]) && run.actual.equals(change[1])) {
                    run.end = index;
                    continue;
                }
                if (run != null) {
                    close(attr, run);
                    open.remove(attr);
                }
                if (change != null) {
                    open.put(attr, new Run(index, change[0], change[1]));
                }
            }
        }

        List<Difference> finish() {
            open.forEach(this::close);
            open.clear();
            return done;
        }

        private void close(String attr, Run run) {
            String words = run.start == run.end ? String.valueOf(run.start) : run.start + "-" + run.end;
            done.add(diff(Severity.WARNING, page, "page[" + page + "].word[" + words + "]." + attr, run.expected, run.actual));
        }
    }

    /** Attribute name to {expected, actual} for each style attribute that differs. */
    private static Map<String, String[]> styleChanges(Style a, Style b) {
        Map<String, String[]> changes = new LinkedHashMap<>();
        if (!Objects.equals(a.font(), b.font())) {
            changes.put("font", new String[] {a.font(), b.font()});
        } else if (a.bold() != b.bold()) {
            // Only worth its own entry when the font name did not already say it (fake or forced bold).
            changes.put("bold", new String[] {weight(a), weight(b)});
        }
        if (Math.abs(a.sizePt() - b.sizePt()) > SIZE_TOLERANCE) {
            changes.put("size", new String[] {size(a), size(b)});
        }
        if (colorsDiffer(a.color(), b.color())) {
            changes.put("color", new String[] {a.color(), b.color()});
        }
        return changes;
    }

    private static boolean colorsDiffer(String a, String b) {
        if (isRgb(a) && isRgb(b)) {
            int x = Integer.parseInt(a.substring(1), 16);
            int y = Integer.parseInt(b.substring(1), 16);
            for (int shift = 0; shift <= 16; shift += 8) {
                if (Math.abs(((x >> shift) & 0xFF) - ((y >> shift) & 0xFF)) > COLOR_CHANNEL_TOLERANCE) {
                    return true;
                }
            }
            return false;
        }
        return !Objects.equals(a, b);
    }

    private static boolean isRgb(String color) {
        return color != null && color.length() == 7 && color.charAt(0) == '#';
    }

    private static String weight(Style s) {
        return s.bold() ? "bold" : "regular";
    }

    private static String size(Style s) {
        return String.format(Locale.ROOT, "%.1fpt", s.sizePt());
    }

    private static Difference diff(Severity severity, int page, String path, String expected, String actual) {
        return new Difference(Stage.TEXT, severity, page, path, expected, actual);
    }

    private static List<String> texts(List<StyledWord> words) {
        List<String> texts = new ArrayList<>(words.size());
        for (StyledWord w : words) {
            texts.add(w.text());
        }
        return texts;
    }

    private static String excerpt(List<String> words) {
        String joined = String.join(" ", words);
        return joined.length() > 200 ? joined.substring(0, 200) + "..." : joined;
    }

    private static List<StyledWord> words(PDDocument doc, int page) throws IOException {
        StyledTextStripper stripper = new StyledTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        stripper.getText(doc);
        return stripper.words;
    }

    /**
     * Text stripper that records each word together with the style of its first glyph. Colour
     * is not part of {@link TextPosition}, so it is read from the graphics state as each glyph
     * is drawn; that needs the colour operators, which {@link PDFTextStripper} skips by default.
     */
    private static final class StyledTextStripper extends PDFTextStripper {

        /** Paint captured for each glyph while the content stream runs. */
        private record Paint(String color, boolean fakeBold) {}

        private final Map<TextPosition, Paint> paints = new IdentityHashMap<>();
        final List<StyledWord> words = new ArrayList<>();

        StyledTextStripper() {
            addOperator(new SetStrokingColorSpace(this));
            addOperator(new SetNonStrokingColorSpace(this));
            addOperator(new SetStrokingColor(this));
            addOperator(new SetNonStrokingColor(this));
            addOperator(new SetStrokingColorN(this));
            addOperator(new SetNonStrokingColorN(this));
            addOperator(new SetStrokingDeviceGrayColor(this));
            addOperator(new SetNonStrokingDeviceGrayColor(this));
            addOperator(new SetStrokingDeviceRGBColor(this));
            addOperator(new SetNonStrokingDeviceRGBColor(this));
            addOperator(new SetStrokingDeviceCMYKColor(this));
            addOperator(new SetNonStrokingDeviceCMYKColor(this));
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            PDGraphicsState gs = getGraphicsState();
            RenderingMode mode = gs.getTextState().getRenderingMode();
            paints.put(text, new Paint(colorOf(gs, mode), mode.isFill() && mode.isStroke()));
            super.processTextPosition(text);
        }

        /**
         * Called once per word (PDFBox writes word separators between calls), but {@code text}
         * can still hold whitespace when the PDF draws literal spaces, so it is split again here.
         * The word text comes from {@code text}, which is normalised (ligatures, direction) the
         * same way plain extraction is; the style comes from the glyphs.
         */
        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            List<TextPosition> firstGlyphs = firstGlyphOfEachWord(positions);
            String[] parts = WHITESPACE.split(text.trim());
            int k = 0;
            for (String part : parts) {
                if (part.isEmpty()) {
                    continue;
                }
                // If glyph grouping and text splitting disagree, fall back to the first word's glyph.
                TextPosition glyph = k < firstGlyphs.size() && parts.length == firstGlyphs.size()
                        ? firstGlyphs.get(k)
                        : (firstGlyphs.isEmpty() ? null : firstGlyphs.get(0));
                words.add(new StyledWord(part, styleOf(glyph)));
                k++;
            }
        }

        private static List<TextPosition> firstGlyphOfEachWord(List<TextPosition> positions) {
            List<TextPosition> firsts = new ArrayList<>();
            boolean inWord = false;
            for (TextPosition p : positions) {
                boolean blank = p.getUnicode() == null || p.getUnicode().isBlank();
                if (!blank && !inWord) {
                    firsts.add(p);
                }
                inWord = !blank;
            }
            return firsts;
        }

        private Style styleOf(TextPosition glyph) {
            if (glyph == null) {
                return new Style(null, false, 0f, null);
            }
            PDFont font = glyph.getFont();
            String name = font == null ? null : StructureStage.stripSubsetPrefix(font.getName());
            Paint paint = paints.get(glyph);
            boolean bold = isBold(font, name) || (paint != null && paint.fakeBold());
            return new Style(name, bold, glyph.getFontSizeInPt(), paint == null ? null : paint.color());
        }

        private static boolean isBold(PDFont font, String name) {
            if (name != null && BOLD_NAME.matcher(name).matches()) {
                return true;
            }
            PDFontDescriptor fd = font == null ? null : font.getFontDescriptor();
            return fd != null && (fd.getFontWeight() >= 700 || fd.isForceBold());
        }

        private static String colorOf(PDGraphicsState gs, RenderingMode mode) {
            PDColor color;
            if (mode.isFill()) {
                color = gs.getNonStrokingColor();
            } else if (mode.isStroke()) {
                color = gs.getStrokingColor();
            } else {
                return "invisible";
            }
            if (color == null) {
                return null;
            }
            try {
                return String.format(Locale.ROOT, "#%06X", color.toRGB() & 0xFFFFFF);
            } catch (IOException | RuntimeException e) {
                // Pattern, shading or otherwise unconvertible colour: compare it by its raw definition.
                String space = color.getColorSpace() == null ? "?" : color.getColorSpace().getName();
                return space + Arrays.toString(color.getComponents());
            }
        }
    }
}
