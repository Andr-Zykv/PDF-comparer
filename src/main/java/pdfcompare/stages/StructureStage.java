package pdfcompare.stages;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import pdfcompare.core.ComparisonStage;
import pdfcompare.core.Difference;
import pdfcompare.core.Severity;
import pdfcompare.core.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compares the skeleton of two documents: page count, page geometry, and what each page
 * contains (fonts, images, annotations). Says nothing about what the text actually is.
 */
public final class StructureStage implements ComparisonStage {

    /** Page boxes may differ by this many points before it counts as a size change. */
    private static final float SIZE_TOLERANCE = 0.5f;

    @Override
    public Stage stage() {
        return Stage.STRUCTURE;
    }

    @Override
    public List<Difference> compare(PDDocument expected, PDDocument actual) throws IOException {
        List<Difference> out = new ArrayList<>();
        int pagesA = expected.getNumberOfPages();
        int pagesB = actual.getNumberOfPages();
        if (pagesA != pagesB) {
            out.add(diff(Severity.ERROR, null, "pages", String.valueOf(pagesA), String.valueOf(pagesB)));
        }

        for (int i = 0; i < Math.min(pagesA, pagesB); i++) {
            comparePage(i + 1, expected.getPage(i), actual.getPage(i), out);
        }
        return out;
    }

    private void comparePage(int n, PDPage a, PDPage b, List<Difference> out) throws IOException {
        String prefix = "page[" + n + "].";

        PDRectangle boxA = a.getMediaBox();
        PDRectangle boxB = b.getMediaBox();
        if (differs(boxA.getWidth(), boxB.getWidth()) || differs(boxA.getHeight(), boxB.getHeight())) {
            out.add(diff(Severity.ERROR, n, prefix + "size", size(boxA), size(boxB)));
        }

        if (a.getRotation() != b.getRotation()) {
            out.add(diff(Severity.ERROR, n, prefix + "rotation", String.valueOf(a.getRotation()), String.valueOf(b.getRotation())));
        }

        Set<String> fontsA = fontNames(a);
        Set<String> fontsB = fontNames(b);
        if (!fontsA.equals(fontsB)) {
            out.add(diff(Severity.WARNING, n, prefix + "fonts", String.join(", ", fontsA), String.join(", ", fontsB)));
        }

        int imgA = countImages(a);
        int imgB = countImages(b);
        if (imgA != imgB) {
            out.add(diff(Severity.ERROR, n, prefix + "images", String.valueOf(imgA), String.valueOf(imgB)));
        }

        int annA = a.getAnnotations().size();
        int annB = b.getAnnotations().size();
        if (annA != annB) {
            out.add(diff(Severity.WARNING, n, prefix + "annotations", String.valueOf(annA), String.valueOf(annB)));
        }
    }

    private static Difference diff(Severity severity, Integer page, String path, String expected, String actual) {
        return new Difference(Stage.STRUCTURE, severity, page, path, expected, actual);
    }

    private static boolean differs(float x, float y) {
        return Math.abs(x - y) > SIZE_TOLERANCE;
    }

    private static String size(PDRectangle r) {
        return String.format(Locale.ROOT, "%.1fx%.1f", r.getWidth(), r.getHeight());
    }

    /** Base font names with any subset prefix ("ABCDEF+") removed, so re-subsetting is not a difference. */
    private static Set<String> fontNames(PDPage page) throws IOException {
        Set<String> names = new TreeSet<>();
        PDResources res = page.getResources();
        if (res == null) {
            return names;
        }
        for (COSName key : res.getFontNames()) {
            PDFont font = res.getFont(key);
            if (font != null) {
                names.add(stripSubsetPrefix(font.getName()));
            }
        }
        return names;
    }

    private static String stripSubsetPrefix(String name) {
        if (name != null && name.length() > 7 && name.charAt(6) == '+' && name.substring(0, 6).chars().allMatch(Character::isUpperCase)) {
            return name.substring(7);
        }
        return name;
    }

    private static int countImages(PDPage page) {
        PDResources res = page.getResources();
        if (res == null) {
            return 0;
        }
        int count = 0;
        for (COSName cosName : res.getXObjectNames()) {
            if (res.isImageXObject(cosName)) {
                count++;
            }
        }
        return count;
    }
}
