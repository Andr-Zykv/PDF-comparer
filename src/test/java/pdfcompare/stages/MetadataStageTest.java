package pdfcompare.stages;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;

import pdfcompare.core.Difference;
import pdfcompare.core.Severity;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataStageTest {

    private final MetadataStage stage = new MetadataStage();

    private static PDDocument doc(String title) {
        PDDocument d = new PDDocument();
        d.addPage(new PDPage());
        PDDocumentInformation info = new PDDocumentInformation();
        if (title != null) {
            info.setTitle(title);
        }
        d.setDocumentInformation(info);
        return d;
    }

    private static void addBookmark(PDDocument d, String title) {
        PDDocumentOutline outline = new PDDocumentOutline();
        d.getDocumentCatalog().setDocumentOutline(outline);
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        outline.addLast(item);
    }

    @Test
    void identicalMetadataHasNoDifferences() throws IOException {
        try (PDDocument a = doc("T"); PDDocument b = doc("T")) {
            assertTrue(stage.compare(a, b).isEmpty());
        }
    }

    @Test
    void changedAndMissingInfoFieldsAreReported() throws IOException {
        try (PDDocument a = doc("Old"); PDDocument b = doc("New")) {
            a.getDocumentInformation().setAuthor("Me");
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(2, diffs.size());
            assertEquals("info.Author", diffs.get(0).path());
            assertEquals("Me", diffs.get(0).expected());
            assertEquals(null, diffs.get(0).actual());
            assertEquals("info.Title", diffs.get(1).path());
        }
    }

    @Test
    void outlineChangesAreErrors() throws IOException {
        try (PDDocument a = doc("T"); PDDocument b = doc("T")) {
            addBookmark(a, "Chapter 1");
            addBookmark(b, "Chapter One");
            List<Difference> diffs = stage.compare(a, b);
            assertEquals(1, diffs.size());
            assertEquals("outline.item[0]", diffs.get(0).path());
            assertEquals(Severity.ERROR, diffs.get(0).severity());
        }
    }

    @Test
    void missingOutlineIsReportedAsCountChange() throws IOException {
        try (PDDocument a = doc("T"); PDDocument b = doc("T")) {
            addBookmark(a, "Chapter 1");
            List<Difference> diffs = stage.compare(a, b);
            assertEquals("outline.count", diffs.get(0).path());
        }
    }
}
