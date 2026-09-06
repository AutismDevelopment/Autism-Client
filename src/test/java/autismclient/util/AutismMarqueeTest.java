package autismclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutismMarqueeTest {
    private static final int TEXT_WIDTH = 300;
    private static final int CLIP_WIDTH = 148;
    private static final int HOLD_UNTIL = 2_200;

    private static final int SPEED = 40;

    @Test
    void cycleLengthIsTextPlusGap() {
        assertEquals(TEXT_WIDTH + AutismMarquee.GAP, AutismMarquee.cycleLength(TEXT_WIDTH));
        assertEquals(AutismMarquee.GAP, AutismMarquee.cycleLength(0));
    }

    @Test
    void noScrollWhenTextFitsClip() {

        assertEquals(0.0, AutismMarquee.offset(CLIP_WIDTH, CLIP_WIDTH, 99_000L, 0L, SPEED), 1e-9);
        assertEquals(0.0, AutismMarquee.offset(CLIP_WIDTH - 1, CLIP_WIDTH, 99_000L, 0L, SPEED), 1e-9);
    }

    @Test
    void noScrollDuringHold() {

        assertEquals(0.0, AutismMarquee.offset(TEXT_WIDTH, CLIP_WIDTH, HOLD_UNTIL - 1, HOLD_UNTIL, SPEED), 1e-9);
        assertEquals(0.0, AutismMarquee.offset(TEXT_WIDTH, CLIP_WIDTH, 0L, HOLD_UNTIL, SPEED), 1e-9);
    }

    @Test
    void scrollAdvancesAtConfiguredSpeedFromHoldEnd() {

        assertEquals(0.0, AutismMarquee.offset(TEXT_WIDTH, CLIP_WIDTH, HOLD_UNTIL, HOLD_UNTIL, SPEED), 1e-9);
        assertEquals(40.0, AutismMarquee.offset(TEXT_WIDTH, CLIP_WIDTH, HOLD_UNTIL + 1_000L, HOLD_UNTIL, SPEED), 1e-9);
        assertEquals(4.0, AutismMarquee.offset(TEXT_WIDTH, CLIP_WIDTH, HOLD_UNTIL + 100L, HOLD_UNTIL, SPEED), 1e-9);

        assertEquals(1.0, AutismMarquee.offset(TEXT_WIDTH, CLIP_WIDTH, HOLD_UNTIL + 25L, HOLD_UNTIL, SPEED), 1e-9);
    }

    @Test
    void offsetAlwaysStaysInsideCycle() {
        int cycle = AutismMarquee.cycleLength(TEXT_WIDTH);

        for (int step = 0; step < 5_000; step++) {
            long now = HOLD_UNTIL + step * 977L;
            float offset = AutismMarquee.offset(TEXT_WIDTH, CLIP_WIDTH, now, HOLD_UNTIL, SPEED);
            assertTrue(offset >= 0.0f && offset < cycle, "offset escaped the cycle: " + offset);
            double expected = ((now - HOLD_UNTIL) * SPEED / 1000.0) % cycle;
            assertEquals(expected, offset, 1e-4);
        }
    }

    @Test
    void seamKeepsConstantSpacingAndNeverBlanksClip() {

        int cycle = AutismMarquee.cycleLength(TEXT_WIDTH);
        assertTrue(CLIP_WIDTH > AutismMarquee.GAP, "test premise: clip wider than the gap");
        for (int offset = 0; offset < cycle; offset++) {
            int aLeft = -offset;
            int aRight = aLeft + TEXT_WIDTH;
            int bLeft = aLeft + cycle;
            int bRight = bLeft + TEXT_WIDTH;
            assertEquals(AutismMarquee.GAP, bLeft - aRight, "copy spacing drifted at offset " + offset);

            int uncovered = 0;
            for (int p = 0; p < CLIP_WIDTH; p++) {
                boolean covered = (p >= aLeft && p < aRight) || (p >= bLeft && p < bRight);
                if (!covered) uncovered++;
            }
            assertTrue(uncovered <= AutismMarquee.GAP,
                "blank band wider than the designed gap at offset " + offset + ": " + uncovered);
            assertTrue(uncovered < CLIP_WIDTH, "clip fully blank at offset " + offset);
        }
    }

    @Test
    void clipFullyFilledAtRest() {

        for (int p = 0; p < CLIP_WIDTH; p++) {
            assertTrue(p < TEXT_WIDTH, "clip pixel " + p + " not covered by the resting text");
        }
    }

    @Test
    void singlePixelOverflowIsEnoughToScroll() {

        int textWidth = CLIP_WIDTH + 1;
        assertEquals(0.0, AutismMarquee.offset(textWidth, CLIP_WIDTH, HOLD_UNTIL - 1, HOLD_UNTIL, SPEED), 1e-9);
        assertEquals(40.0, AutismMarquee.offset(textWidth, CLIP_WIDTH, HOLD_UNTIL + 1_000L, HOLD_UNTIL, SPEED), 1e-9);
        int cycle = AutismMarquee.cycleLength(textWidth);
        for (int step = 0; step < 1_000; step++) {
            float offset = AutismMarquee.offset(textWidth, CLIP_WIDTH, HOLD_UNTIL + step * 613L, HOLD_UNTIL, SPEED);
            assertTrue(offset >= 0.0f && offset < cycle, "offset escaped the cycle: " + offset);
        }
    }

    private static AutismSpotify.Snapshot snap(AutismSpotify.Status status, String artist, String title) {
        return new AutismSpotify.Snapshot(status, artist, title, 7L, 0.0, 0.0, false, AutismSpotify.Repeat.OFF, -1, "");
    }

    @Test
    void trackTextJoinsArtistAndTitleWithEmDash() {
        assertEquals("Artist — Song", AutismMarquee.trackText(snap(AutismSpotify.Status.PLAYING, "Artist", "Song")));
    }

    @Test
    void trackTextFallsBackToTitleAloneWhenNoArtist() {

        assertEquals("Song", AutismMarquee.trackText(snap(AutismSpotify.Status.PLAYING, "", "Song")));
        assertEquals("Song", AutismMarquee.trackText(snap(AutismSpotify.Status.PLAYING, null, "Song")));
    }

    @Test
    void trackTextIsEmptyWithoutUsableTrack() {

        assertEquals("", AutismMarquee.trackText(null));
        assertEquals("", AutismMarquee.trackText(snap(AutismSpotify.Status.PAUSED, "Artist", "   ")));
        assertEquals("", AutismMarquee.trackText(snap(AutismSpotify.Status.STOPPED, null, null)));
    }

    @Test
    void compositionKeyInvalidatesOnExactlyTheCompositionInputs() {
        Object font = new Object();
        AutismMarquee.CompositionKey key = new AutismMarquee.CompositionKey(100L, "", font, 7);

        assertTrue(key.matches(100L, "", font, 7));

        assertFalse(key.matches(101L, "", font, 7));
        assertFalse(key.matches(100L, "Title Only", font, 7));
        assertFalse(key.matches(100L, "", new Object(), 7));
        assertFalse(key.matches(100L, "", font, 8));

        assertTrue(new AutismMarquee.CompositionKey(1L, null, font, 0).matches(1L, null, font, 0));

        assertFalse(new AutismMarquee.CompositionKey(1L, "M", null, 0).matches(1L, "M", font, 0));
    }

    @Test
    void interpolatePositionAdvancesOnlyWhilePlayingAndClamps() {

        assertEquals(13.5, AutismMarquee.interpolatePosition(10.0, 1_000L, true, 200.0, 4_500L), 1e-9);

        assertEquals(10.0, AutismMarquee.interpolatePosition(10.0, 1_000L, false, 200.0, 4_500L), 1e-9);

        assertEquals(200.0, AutismMarquee.interpolatePosition(10.0, 1_000L, true, 200.0, 900_000L), 1e-9);

        assertEquals(10.0, AutismMarquee.interpolatePosition(10.0, 1_000L, true, 0.0, 4_500L), 1e-9);
        assertEquals(0.0, AutismMarquee.interpolatePosition(-5.0, 1_000L, false, 0.0, 4_500L), 1e-9);
    }
}
