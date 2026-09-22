package pdfcompare.stages;

import java.util.ArrayList;
import java.util.List;

/**
 * Classic LCS-backed edit-script diff over two word lists. Package-private: an implementation
 * detail of {@link TextStage}.
 *
 * <p>Builds the full dynamic-programming table, so it is O(n*m) time and space; callers should
 * bound the input sizes before calling {@link #diff}.
 */
final class WordDiff {

    enum OpType { REPLACE, DELETE, INSERT }

    /**
     * @param expectedIndex index into the expected list, meaningful for REPLACE and DELETE
     * @param actualIndex   index into the actual list, meaningful for REPLACE and INSERT
     * @param anchor        expected-side position to report in a difference's path: the word
     *                      itself for REPLACE/DELETE, or the insertion point for INSERT
     */
    record Op(OpType type, int expectedIndex, int actualIndex, int anchor) {}

    private WordDiff() {
    }

    static List<Op> diff(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a.get(i).equals(b.get(j))
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        // Backtrace forward into a raw sequence of equal/delete/insert steps.
        List<int[]> raw = new ArrayList<>(); // {kind, i, j}; kind: 0=equal, 1=delete, 2=insert
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                raw.add(new int[] {0, i, j});
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                raw.add(new int[] {1, i, j});
                i++;
            } else {
                raw.add(new int[] {2, i, j});
                j++;
            }
        }
        while (i < n) {
            raw.add(new int[] {1, i, j});
            i++;
        }
        while (j < m) {
            raw.add(new int[] {2, i, j});
            j++;
        }

        // Group consecutive non-equal steps into hunks and pair up deletes with inserts inside
        // each one, so a changed word reports as one REPLACE instead of a DELETE plus an INSERT.
        List<Op> ops = new ArrayList<>();
        List<int[]> deletes = new ArrayList<>();
        List<int[]> inserts = new ArrayList<>();
        for (int[] step : raw) {
            if (step[0] == 0) {
                flushHunk(deletes, inserts, ops);
                continue;
            }
            (step[0] == 1 ? deletes : inserts).add(step);
        }
        flushHunk(deletes, inserts, ops);
        return ops;
    }

    private static void flushHunk(List<int[]> deletes, List<int[]> inserts, List<Op> ops) {
        int paired = Math.min(deletes.size(), inserts.size());
        for (int k = 0; k < paired; k++) {
            int ei = deletes.get(k)[1];
            int ai = inserts.get(k)[2];
            ops.add(new Op(OpType.REPLACE, ei, ai, ei));
        }
        for (int k = paired; k < deletes.size(); k++) {
            int ei = deletes.get(k)[1];
            ops.add(new Op(OpType.DELETE, ei, -1, ei));
        }
        for (int k = paired; k < inserts.size(); k++) {
            int[] step = inserts.get(k);
            ops.add(new Op(OpType.INSERT, -1, step[2], step[1]));
        }
        deletes.clear();
        inserts.clear();
    }
}
