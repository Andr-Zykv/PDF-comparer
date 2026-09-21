package pdfcompare.stages;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import pdfcompare.core.ComparisonStage;
import pdfcompare.core.Difference;
import pdfcompare.core.Severity;
import pdfcompare.core.Stage;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Compares document-level metadata: the header version, the Info dictionary, the trailer ID,
 * XMP properties and the outline (bookmarks). Volatile fields such as producer and dates are
 * emitted as WARNING here; {@link pdfcompare.core.Policy#defaults()} downgrades them to INFO.
 */
public final class MetadataStage implements ComparisonStage {

    @Override
    public Stage stage() {
        return Stage.METADATA;
    }

    @Override
    public List<Difference> compare(PDDocument expected, PDDocument actual) throws IOException {
        List<Difference> out = new ArrayList<>();

        String verA = String.valueOf(expected.getVersion());
        String verB = String.valueOf(actual.getVersion());
        if (!verA.equals(verB)) {
            out.add(diff(Severity.WARNING, "header.version", verA, verB));
        }

        diffMaps("info.", infoOf(expected), infoOf(actual), out);
        diffMaps("trailer.", idOf(expected), idOf(actual), out);
        diffMaps("xmp.", xmpOf(expected), xmpOf(actual), out);
        compareOutline(outlineOf(expected), outlineOf(actual), out);
        return out;
    }

    private static Difference diff(Severity severity, String path, String expected, String actual) {
        return new Difference(Stage.METADATA, severity, null, path, expected, actual);
    }

    /** Emits one difference per key whose value differs or that exists on only one side. */
    private static void diffMaps(String prefix, Map<String, String> a, Map<String, String> b, List<Difference> out) {
        TreeSet<String> keys = new TreeSet<>(a.keySet());
        keys.addAll(b.keySet());
        for (String key : keys) {
            String va = a.get(key);
            String vb = b.get(key);
            if (!Objects.equals(va, vb)) {
                out.add(diff(Severity.WARNING, prefix + key, va, vb));
            }
        }
    }

    private static Map<String, String> infoOf(PDDocument doc) {
        Map<String, String> map = new TreeMap<>();
        if (doc.getDocumentInformation() == null) {
            return map;
        }
        var dict = doc.getDocumentInformation().getCOSObject();
        for (COSName key : dict.keySet()) {
            COSBase value = dict.getDictionaryObject(key);
            if (value instanceof COSString s) {
                map.put(key.getName(), s.getString());
            } else if (value != null) {
                map.put(key.getName(), value.toString());
            }
        }
        return map;
    }

    /** The trailer /ID pair, as hex strings, under keys {@code ID} (one entry, both halves joined). */
    private static Map<String, String> idOf(PDDocument doc) {
        Map<String, String> map = new TreeMap<>();
        COSArray id = doc.getDocument().getTrailer().getCOSArray(COSName.ID);
        if (id != null) {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < id.size(); i++) {
                if (id.getObject(i) instanceof COSString s) {
                    parts.add(s.toHexString());
                }
            }
            map.put("ID", String.join("/", parts));
        }
        return map;
    }

    /**
     * Flattens XMP to {@code localName -> value}. Repeated names (list items) are joined with
     * "; " in document order. Returns an empty map when there is no XMP or it cannot be parsed.
     */
    private static Map<String, String> xmpOf(PDDocument doc) throws IOException {
        Map<String, String> map = new TreeMap<>();
        PDMetadata metadata = doc.getDocumentCatalog().getMetadata();
        if (metadata == null) {
            return map;
        }
        Document xml;
        try (InputStream in = metadata.exportXMPMetadata()) {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            xml = f.newDocumentBuilder().parse(in);
        } catch (Exception e) {
            map.put("unparseable", e.getClass().getSimpleName());
            return map;
        }
        collectLeaves(xml.getDocumentElement(), map);
        return map;
    }

    private static void collectLeaves(Element el, Map<String, String> map) {
        boolean hasElementChild = false;
        for (Node n = el.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element child) {
                hasElementChild = true;
                collectLeaves(child, map);
            }
        }
        if (!hasElementChild) {
            String text = el.getTextContent().trim();
            if (!text.isEmpty()) {
                map.merge(el.getLocalName(), text, (x, y) -> x + "; " + y);
            }
        }
    }

    private static List<String> outlineOf(PDDocument doc) {
        List<String> titles = new ArrayList<>();
        PDDocumentOutline outline = doc.getDocumentCatalog().getDocumentOutline();
        if (outline != null) {
            walk(outline, 0, titles);
        }
        return titles;
    }

    /** Depth-first; nesting is encoded as leading "> " so a re-parented item is a difference. */
    private static void walk(PDOutlineNode node, int depth, List<String> titles) {
        for (PDOutlineItem item : node.children()) {
            titles.add(">".repeat(depth) + (depth > 0 ? " " : "") + item.getTitle());
            walk(item, depth + 1, titles);
        }
    }

    private static void compareOutline(List<String> a, List<String> b, List<Difference> out) {
        if (a.size() != b.size()) {
            out.add(diff(Severity.ERROR, "outline.count", String.valueOf(a.size()), String.valueOf(b.size())));
        }
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            if (!Objects.equals(a.get(i), b.get(i))) {
                out.add(diff(Severity.ERROR, "outline.item[" + i + "]", a.get(i), b.get(i)));
            }
        }
    }
}
