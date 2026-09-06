package autismclient.util.oresim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NearestPositionSelectorTest {

    @Test
    void globallyClosestEntriesWinRegardlessOfInsertionOrderOrStateFamily() {
        Candidate[] candidates = {
            new Candidate(80.0, 800L, 1),
            new Candidate(3.0, 300L, 91),
            new Candidate(90.0, 900L, 2),
            new Candidate(1.0, 100L, 73),
            new Candidate(2.0, 200L, 4)
        };

        assertSelection(candidates, false, 3,
            new long[]{100L, 200L, 300L}, new int[]{73, 4, 91});
        assertSelection(candidates, true, 3,
            new long[]{100L, 200L, 300L}, new int[]{73, 4, 91});
    }

    @Test
    void writesRetainedEntriesNearestFirst() {
        Candidate[] candidates = {
            new Candidate(9.0, 90L, 9),
            new Candidate(1.0, 10L, 1),
            new Candidate(4.0, 40L, 4)
        };

        assertSelection(candidates, false, candidates.length,
            new long[]{10L, 40L, 90L}, new int[]{1, 4, 9});
    }

    @Test
    void equalDistancesHaveDeterministicTieOrderingAndRetention() {
        Candidate[] candidates = {
            new Candidate(4.0, 50L, 5),
            new Candidate(4.0, 10L, 9),
            new Candidate(4.0, 40L, 4),
            new Candidate(4.0, 20L, 8),
            new Candidate(4.0, 30L, 3)
        };

        assertSelection(candidates, false, 3,
            new long[]{10L, 20L, 30L}, new int[]{9, 8, 3});
        assertSelection(candidates, true, 3,
            new long[]{10L, 20L, 30L}, new int[]{9, 8, 3});
    }

    @Test
    void reportsTruncationOnlyAfterMoreCandidatesThanTheCap() {
        NearestPositionSelector selector = new NearestPositionSelector(2);
        selector.offer(1.0, 10L, 1);
        selector.offer(2.0, 20L, 2);
        assertFalse(selector.truncated());

        selector.offer(3.0, 30L, 3);
        assertTrue(selector.truncated());
    }

    private static void assertSelection(Candidate[] candidates, boolean reverse, int cap,
                                        long[] expectedPositions, int[] expectedStates) {
        NearestPositionSelector selector = new NearestPositionSelector(cap);
        for (int i = 0; i < candidates.length; i++) {
            Candidate candidate = candidates[reverse ? candidates.length - 1 - i : i];
            selector.offer(candidate.distanceSquared, candidate.position, candidate.state);
        }

        long[] positions = new long[cap];
        int[] states = new int[cap];
        selector.writeNearestFirst(positions, states);
        assertArrayEquals(expectedPositions, positions);
        assertArrayEquals(expectedStates, states);
    }

    private record Candidate(double distanceSquared, long position, int state) {
    }
}
