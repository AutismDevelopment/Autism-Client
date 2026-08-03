package autismclient.util.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class MacroStepPaletteTest {

    private static final int NOW = 0xFF6FD38B;

    @Test
    void firstPassMarksDoneCurrentAndPending() {

        assertEquals(MacroStepPalette.DONE, MacroStepPalette.colorFor(0, 2, 2, NOW));
        assertEquals(MacroStepPalette.DONE, MacroStepPalette.colorFor(1, 2, 2, NOW));
        assertEquals(NOW, MacroStepPalette.colorFor(2, 2, 2, NOW));
        assertEquals(MacroStepPalette.WAIT, MacroStepPalette.colorFor(3, 2, 2, NOW));
    }

    @Test
    void staleCompletedCountFromThePreviousPassCannotSwallowTheHighlight() {

        int total = 5;
        int current = 0;
        int staleCompleted = total;

        assertEquals(NOW, MacroStepPalette.colorFor(current, current, staleCompleted, NOW));
        for (int i = 0; i < total; i++) {
            if (i == current) continue;
            assertNotEquals(NOW, MacroStepPalette.colorFor(i, current, staleCompleted, NOW));
        }
    }

    @Test
    void exactlyOneRowIsHighlightedAtAnyPointInALoop() {
        int total = 8;
        for (int current = 0; current < total; current++) {
            for (int completed = 0; completed <= total; completed++) {
                int highlighted = 0;
                for (int i = 0; i < total; i++) {
                    if (MacroStepPalette.colorFor(i, current, completed, NOW) == NOW) highlighted++;
                }
                assertEquals(1, highlighted,
                    "current=" + current + " completed=" + completed + " highlighted " + highlighted + " rows");
            }
        }
    }

    @Test
    void aBackwardJumpStillHighlightsTheStepWeJumpedTo() {

        assertEquals(NOW, MacroStepPalette.colorFor(1, 1, 6, NOW));
        assertEquals(MacroStepPalette.DONE, MacroStepPalette.colorFor(2, 1, 6, NOW));
    }
}
