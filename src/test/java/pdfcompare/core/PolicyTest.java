package pdfcompare.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolicyTest {

    private static Difference diff(String path, Severity severity) {
        return new Difference(Stage.METADATA, severity, null, path, "a", "b");
    }

    @Test
    void unmatchedDifferenceKeepsItsSeverity() {
        Policy policy = new Policy().rule("info.Producer", Severity.INFO);
        assertEquals(Severity.WARNING, policy.apply(diff("info.Title", Severity.WARNING)).severity());
    }

    @Test
    void firstMatchingRuleWins() {
        Policy policy = new Policy()
                .rule("outline.*", Severity.ERROR)
                .rule("*", Severity.INFO);
        assertEquals(Severity.ERROR, policy.classify(diff("outline.item[2]", Severity.WARNING)));
        assertEquals(Severity.INFO, policy.classify(diff("info.Title", Severity.WARNING)));
    }

    @Test
    void wildcardsAndBracketsAreHandledLiterally() {
        Policy policy = new Policy().rule("page[*].fonts", Severity.ERROR);
        assertEquals(Severity.ERROR, policy.classify(diff("page[3].fonts", Severity.INFO)));
        assertEquals(Severity.INFO, policy.classify(diff("page3.fonts", Severity.INFO)));
    }

    @Test
    void defaultsDowngradeReSaveNoise() {
        Policy policy = Policy.defaults();
        assertEquals(Severity.INFO, policy.classify(diff("info.ModDate", Severity.WARNING)));
        assertEquals(Severity.INFO, policy.classify(diff("xmp.xmp:ModifyDate", Severity.WARNING)));
        assertEquals(Severity.WARNING, policy.classify(diff("info.Title", Severity.WARNING)));
    }
}
