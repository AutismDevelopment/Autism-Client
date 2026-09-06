package autismclient.util.multi;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiMacroDelayTest {
    @Test
    void valuesStayInRangeAndOnAStep() {
        assertEquals(0, MultiMacroDelay.clamp(-4_000));
        assertEquals(MultiMacroDelay.MAX_MS, MultiMacroDelay.clamp(999_999));
        assertEquals(700, MultiMacroDelay.clamp(740));
        assertEquals(800, MultiMacroDelay.clamp(760));
        assertEquals(5_000, MultiMacroDelay.clamp(5_000));
    }

    @Test
    void everyTenthOfASecondIsAStop() {
        for (int ms = MultiMacroDelay.MIN_MS; ms <= MultiMacroDelay.MAX_MS; ms += 100) {
            assertEquals(ms, MultiMacroDelay.clamp(ms));
        }
    }

    @Test
    void theWheelWalksOneStopAtATimeAndPinsAtTheEnds() {
        assertEquals(100, MultiMacroDelay.nudge(0, 1));
        assertEquals(500, MultiMacroDelay.nudge(400, 1));
        assertEquals(300, MultiMacroDelay.nudge(400, -1));
        assertEquals(0, MultiMacroDelay.nudge(0, -1));
        assertEquals(MultiMacroDelay.MAX_MS, MultiMacroDelay.nudge(MultiMacroDelay.MAX_MS, 1));
        assertEquals(400, MultiMacroDelay.nudge(400, 0));
    }

    @Test
    void bothEndsOfTheTrackAreReachable() {
        assertEquals(0, MultiMacroDelay.fromMouse(10, 10, 200));
        assertEquals(MultiMacroDelay.MAX_MS, MultiMacroDelay.fromMouse(210, 10, 200));
        assertEquals(5_000, MultiMacroDelay.fromMouse(110, 10, 200));

        assertEquals(0, MultiMacroDelay.fromMouse(-500, 10, 200));
        assertEquals(MultiMacroDelay.MAX_MS, MultiMacroDelay.fromMouse(5_000, 10, 200));
    }

    @Test
    void aTrackWiderThanItsStopCountDragsOntoEveryStop() {
        int trackW = 240;
        Set<Integer> reached = new HashSet<>();
        for (int px = 0; px <= trackW; px++) {
            int ms = MultiMacroDelay.fromMouse(10 + px, 10, trackW);
            assertEquals(0, ms % MultiMacroDelay.STEP_MS);
            reached.add(ms);
        }
        assertEquals(MultiMacroDelay.MAX_MS / MultiMacroDelay.STEP_MS + 1, reached.size());
    }

    @Test
    void valueTextReadsAsATime() {
        assertEquals("Off", MultiMacroDelay.valueText(0));
        assertEquals("0.1s", MultiMacroDelay.valueText(100));
        assertEquals("0.4s", MultiMacroDelay.valueText(400));
        assertEquals("0.5s", MultiMacroDelay.valueText(500));
        assertEquals("1.3s", MultiMacroDelay.valueText(1_300));
        assertEquals("9.9s", MultiMacroDelay.valueText(9_900));
        assertEquals("5s", MultiMacroDelay.valueText(5_000));
        assertEquals("10s", MultiMacroDelay.valueText(10_000));
    }

    @Test
    void theBoxOnlyHoldsWhatTheSliderCanRepresent() {
        assertTrue(MultiMacroDelay.typable(""));
        assertTrue(MultiMacroDelay.typable("0"));
        assertTrue(MultiMacroDelay.typable("0."));
        assertTrue(MultiMacroDelay.typable("0.5"));
        assertTrue(MultiMacroDelay.typable("10"));
        assertTrue(MultiMacroDelay.typable("10.0"));
        assertFalse(MultiMacroDelay.typable("0.55"));
        assertFalse(MultiMacroDelay.typable("100"));
        assertFalse(MultiMacroDelay.typable("-1"));
        assertFalse(MultiMacroDelay.typable("1e9"));
        assertFalse(MultiMacroDelay.typable("NaN"));
        assertFalse(MultiMacroDelay.typable("Infinity"));
        assertFalse(MultiMacroDelay.typable("1 "));
        assertFalse(MultiMacroDelay.typable(null));
    }

    @Test
    void theBoxRoundTripsEveryStop() {
        for (int ms = MultiMacroDelay.MIN_MS; ms <= MultiMacroDelay.MAX_MS; ms += 100) {
            String text = MultiMacroDelay.editText(ms);
            assertTrue(text.length() <= MultiMacroDelay.TYPED_MAX_LENGTH, text);
            assertTrue(MultiMacroDelay.typable(text), text);
            assertEquals(ms, MultiMacroDelay.fromTyped(text, -1));
        }
        assertEquals("0", MultiMacroDelay.editText(0));
        assertEquals("2.5", MultiMacroDelay.editText(2_500));
        assertEquals("10", MultiMacroDelay.editText(10_000));
    }

    @Test
    void typedTextCannotOverflowOrEscapeTheRange() {
        assertEquals(2_500, MultiMacroDelay.fromTyped("2.5", 0));
        assertEquals(MultiMacroDelay.MAX_MS, MultiMacroDelay.fromTyped("99", 0));
        assertEquals(MultiMacroDelay.MAX_MS, MultiMacroDelay.fromTyped("99999999999999", 0));
        assertEquals(MultiMacroDelay.MAX_MS, MultiMacroDelay.fromTyped("1e30", 0));
        assertEquals(MultiMacroDelay.MAX_MS, MultiMacroDelay.fromTyped("Infinity", 0));
        assertEquals(0, MultiMacroDelay.fromTyped("-5", 3_000));
        assertEquals(0, MultiMacroDelay.fromTyped("-1e30", 3_000));
        assertEquals(700, MultiMacroDelay.fromTyped("0.66", 0));

        assertEquals(3_000, MultiMacroDelay.fromTyped("", 3_000));
        assertEquals(3_000, MultiMacroDelay.fromTyped(".", 3_000));
        assertEquals(3_000, MultiMacroDelay.fromTyped("abc", 3_000));
        assertEquals(3_000, MultiMacroDelay.fromTyped("NaN", 3_000));
        assertEquals(3_000, MultiMacroDelay.fromTyped(null, 3_000));
        assertEquals(0, MultiMacroDelay.fromTyped("0.", 3_000));
        assertEquals(MultiMacroDelay.MAX_MS, MultiMacroDelay.fromTyped("", 999_999));
    }

    @Test
    void countdownKeepsOneDecimalAndNeverReadsZeroWhileWaiting() {
        assertEquals("4.5s", MultiMacroDelay.countdownText(4_500));
        assertEquals("0.1s", MultiMacroDelay.countdownText(1));
        assertEquals("0.0s", MultiMacroDelay.countdownText(0));
    }

    @Test
    void theLadderStaggersOnlyTheBotsThatStart() {
        long launch = 1_000L;
        assertEquals(1_000L, MultiMacroDelay.startAt(launch, 0, 5_000));
        assertEquals(6_000L, MultiMacroDelay.startAt(launch, 1, 5_000));
        assertEquals(11_000L, MultiMacroDelay.startAt(launch, 2, 5_000));

        assertEquals(launch, MultiMacroDelay.startAt(launch, 7, 0));
    }
}
