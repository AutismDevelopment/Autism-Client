package autismclient.util.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CaptureListSelectorTest {
    private static final List<String> NAMES = List.of(".BedrockKid", "Alex", "Steve", ".bedrockPro");

    @Test
    void prefixAndContainsFiltersIgnoreCase() {
        assertEquals(List.of(".BedrockKid", ".bedrockPro"),
            CaptureListSelector.filter(NAMES, CaptureListSelector.Filter.PREFIX, ".BEDROCK"));
        assertEquals(List.of(".BedrockKid", ".bedrockPro"),
            CaptureListSelector.filter(NAMES, CaptureListSelector.Filter.CONTAINS, "rock"));
        assertEquals(NAMES, CaptureListSelector.filter(NAMES, CaptureListSelector.Filter.PREFIX, ""));
        assertEquals(NAMES, CaptureListSelector.filter(NAMES, CaptureListSelector.Filter.NONE, "x"));
    }

    @Test
    void regexFilterMatchesAndBadRegexFiltersEverything() {
        assertEquals(List.of("Alex"),
            CaptureListSelector.filter(NAMES, CaptureListSelector.Filter.REGEX, "^A.*x$"));
        assertEquals(List.of(),
            CaptureListSelector.filter(NAMES, CaptureListSelector.Filter.REGEX, "["));
        assertFalse(CaptureListSelector.filterError(CaptureListSelector.Filter.REGEX, "[").isBlank());
        assertTrue(CaptureListSelector.filterError(CaptureListSelector.Filter.REGEX, "^A").isBlank());
        assertTrue(CaptureListSelector.filterError(CaptureListSelector.Filter.PREFIX, "[").isBlank());
    }

    @Test
    void nullStatePeeksFirstEntryWithoutMemory() {
        var pick = CaptureListSelector.pick(NAMES, CaptureListSelector.Selection.RANDOM, null).orElseThrow();
        assertEquals(NAMES.get(0), pick.value());
        assertEquals(0, pick.index());
        assertTrue(CaptureListSelector.pick(List.of(),
            CaptureListSelector.Selection.RANDOM, new CaptureListSelector.State()).isEmpty());
    }

    @Test
    void sequentialAdvancesAndWraps() {
        var state = new CaptureListSelector.State();
        List<String> pool = List.of("a", "b", "c");
        assertEquals("a", CaptureListSelector.pick(pool, CaptureListSelector.Selection.SEQUENTIAL, state).orElseThrow().value());
        assertEquals("b", CaptureListSelector.pick(pool, CaptureListSelector.Selection.SEQUENTIAL, state).orElseThrow().value());
        assertEquals("c", CaptureListSelector.pick(pool, CaptureListSelector.Selection.SEQUENTIAL, state).orElseThrow().value());
        assertEquals("a", CaptureListSelector.pick(pool, CaptureListSelector.Selection.SEQUENTIAL, state).orElseThrow().value());
    }

    @Test
    void sequentialCursorSurvivesAShrinkingPool() {
        var state = new CaptureListSelector.State();
        List<String> big = List.of("a", "b", "c", "d", "e");
        for (int i = 0; i < 4; i++) CaptureListSelector.pick(big, CaptureListSelector.Selection.SEQUENTIAL, state);

        assertEquals("a", CaptureListSelector.pick(List.of("a", "b"),
            CaptureListSelector.Selection.SEQUENTIAL, state).orElseThrow().value());
    }

    @Test
    void randomNoRepeatUsesEveryValueOncePerCycle() {
        var state = new CaptureListSelector.State();
        List<String> pool = List.of("a", "b", "c");
        Set<String> firstCycle = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            firstCycle.add(CaptureListSelector.pick(pool,
                CaptureListSelector.Selection.RANDOM_NO_REPEAT, state).orElseThrow().value());
        }
        assertEquals(Set.of("a", "b", "c"), firstCycle);

        assertTrue(pool.contains(CaptureListSelector.pick(pool,
            CaptureListSelector.Selection.RANDOM_NO_REPEAT, state).orElseThrow().value()));
    }

    @Test
    void randomStaysInsideThePool() {
        var state = new CaptureListSelector.State();
        for (int i = 0; i < 50; i++) {
            assertTrue(NAMES.contains(CaptureListSelector.pick(NAMES,
                CaptureListSelector.Selection.RANDOM, state).orElseThrow().value()));
        }
    }

    @Test
    void suffixAndNotContainsFilters() {
        assertEquals(List.of(".BedrockKid"),
            CaptureListSelector.filter(NAMES, CaptureListSelector.Filter.SUFFIX, "KID"));
        assertEquals(List.of("Alex", "Steve"),
            CaptureListSelector.filter(NAMES, CaptureListSelector.Filter.NOT_CONTAINS, "bedrock"));
    }

    @Test
    void excludeDropsMatchingEntries() {
        assertEquals(List.of("Alex", "Steve"),
            CaptureListSelector.exclude(NAMES, "BEDROCK"));
        assertEquals(List.of(".BedrockKid", ".bedrockPro"),
            CaptureListSelector.exclude(NAMES, "alex , steve"));
        assertEquals(NAMES, CaptureListSelector.exclude(NAMES, ""));
        assertEquals(NAMES, CaptureListSelector.exclude(NAMES, " , ,"));
    }

    @Test
    void deterministicPicksResolveWithAndWithoutState() {
        List<String> pool = List.of("a", "b", "c");
        assertEquals("a", CaptureListSelector.pick(pool, CaptureListSelector.Selection.FIRST, 1, null).orElseThrow().value());
        assertEquals("c", CaptureListSelector.pick(pool, CaptureListSelector.Selection.LAST, 1, null).orElseThrow().value());
        assertEquals("b", CaptureListSelector.pick(pool, CaptureListSelector.Selection.POSITION, 2, null).orElseThrow().value());
        var state = new CaptureListSelector.State();
        assertEquals("b", CaptureListSelector.pick(pool, CaptureListSelector.Selection.POSITION, 2, state).orElseThrow().value());

        assertEquals("a", CaptureListSelector.pick(pool, CaptureListSelector.Selection.POSITION, 0, state).orElseThrow().value());
        assertEquals("c", CaptureListSelector.pick(pool, CaptureListSelector.Selection.POSITION, 99, state).orElseThrow().value());
    }

    @Test
    void stripMatchRemovesOnlyPrefixOrSuffix() {
        assertEquals("BedrockKid", CaptureListSelector.stripMatch(".BedrockKid", CaptureListSelector.Filter.PREFIX, "."));
        assertEquals("BedrockKid", CaptureListSelector.stripMatch("BedrockKidVIP", CaptureListSelector.Filter.SUFFIX, "vip"));
        assertEquals(".BedrockKid", CaptureListSelector.stripMatch(".BedrockKid", CaptureListSelector.Filter.CONTAINS, "."));
        assertEquals(".BedrockKid", CaptureListSelector.stripMatch(".BedrockKid", CaptureListSelector.Filter.REGEX, "\\."));
        assertEquals("Steve", CaptureListSelector.stripMatch("Steve", CaptureListSelector.Filter.PREFIX, "."));
    }
}
