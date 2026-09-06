package autismclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutismWindowClampTest {
    private static AutismWindowLayout layout(int x, int y, int w, int h) {
        return new AutismWindowLayout(x, y, w, h, true, false);
    }

    @Test
    void aMinimizedWindowLeavesBoundsUntouched() {

        AutismWindowLayout bounds = layout(300, 200, 420, 260);
        assertSame(bounds, AutismWindow.clampToScreenSize(bounds, 100, 60, 0, 0));
        assertSame(bounds, AutismWindow.clampToScreenSize(bounds, 100, 60, 0, 1080));
        assertSame(bounds, AutismWindow.clampToScreenSize(bounds, 100, 60, 1920, 0));
    }

    @Test
    void anOnScreenWindowIsLeftWhereItIs() {
        AutismWindowLayout clamped = AutismWindow.clampToScreenSize(layout(300, 200, 420, 260), 100, 60, 1920, 1080);
        assertEquals(300, clamped.x);
        assertEquals(200, clamped.y);
        assertEquals(420, clamped.width);
        assertEquals(260, clamped.height);
    }

    @Test
    void anOffScreenWindowIsPulledBackIntoView() {
        AutismWindowLayout clamped = AutismWindow.clampToScreenSize(layout(5000, 4000, 420, 260), 100, 60, 800, 600);
        assertEquals(800 - 4 - clamped.width, clamped.x);
        assertEquals(600 - 4 - clamped.height, clamped.y);
    }

    @Test
    void shrinkingThenGrowingRestoresTheUsersLayout() {

        AutismWindowLayout user = layout(1400, 700, 420, 260);
        AutismWindowLayout small = AutismWindow.clampToScreenSize(user, 100, 60, 800, 600);
        assertEquals(800 - 4 - small.width, small.x);

        AutismWindowLayout restored = AutismWindow.clampToScreenSize(user, 100, 60, 1920, 1080);
        assertEquals(user.x, restored.x);
        assertEquals(user.y, restored.y);
        assertEquals(user.width, restored.width);
        assertEquals(user.height, restored.height);
    }

    @Test
    void reclampingAnAlreadyClampedLayoutLosesThePosition() {

        AutismWindowLayout user = layout(1400, 700, 420, 260);
        AutismWindowLayout small = AutismWindow.clampToScreenSize(user, 100, 60, 800, 600);
        AutismWindowLayout regrown = AutismWindow.clampToScreenSize(small, 100, 60, 1920, 1080);
        assertNotEquals(user.x, regrown.x);
        assertNotEquals(user.y, regrown.y);
    }

    @Test
    void aMinimizedFrameNeverDisturbsAShrunkLayout() {

        AutismWindowLayout user = layout(1400, 700, 420, 260);
        AutismWindowLayout small = AutismWindow.clampToScreenSize(user, 100, 60, 800, 600);
        assertSame(small, AutismWindow.clampToScreenSize(small, 100, 60, 0, 0));
    }

    @Test
    void fitsOnScreenAgreesWithTheClamp() {

        assertTrue(AutismOverlayManager.fitsOnScreen(layout(300, 200, 420, 260), 1920, 1080));
        assertFalse(AutismOverlayManager.fitsOnScreen(layout(1400, 700, 420, 260), 800, 600));
        assertFalse(AutismOverlayManager.fitsOnScreen(layout(0, 0, 9999, 9999), 800, 600));
    }

    @Test
    void nothingFitsWhileTheWindowIsMinimized() {

        assertFalse(AutismOverlayManager.fitsOnScreen(layout(300, 200, 420, 260), 0, 0));
        assertFalse(AutismOverlayManager.fitsOnScreen(layout(300, 200, 420, 260), 1920, 0));
    }

    @Test
    void aWindowFlushAgainstTheSafeMarginStillFits() {

        AutismWindowLayout pulledIn = AutismWindow.clampToScreenSize(layout(5000, 4000, 420, 260), 100, 60, 800, 600);
        assertTrue(AutismOverlayManager.fitsOnScreen(pulledIn, 800, 600));
    }

    @Test
    void aClosedWindowStaysClosedWhenTheStashIsReplayed() {

        AutismWindowLayout stashedWhileOpen = new AutismWindowLayout(1400, 700, 420, 260, true, false);
        AutismWindowLayout liveAfterClosing = new AutismWindowLayout(376, 336, 420, 260, false, false);

        AutismWindowLayout applied = AutismOverlayManager.withLiveState(stashedWhileOpen, liveAfterClosing);
        assertFalse(applied.visible, "a closed window must not be reopened by a resize");
        assertEquals(1400, applied.x, "the user's real position still comes back");
        assertEquals(700, applied.y);
        assertEquals(420, applied.width);
        assertEquals(260, applied.height);
    }

    @Test
    void collapseStateAlsoComesFromTheLiveWindow() {
        AutismWindowLayout stashedExpanded = new AutismWindowLayout(1400, 700, 420, 260, true, false);
        AutismWindowLayout liveCollapsed = new AutismWindowLayout(376, 336, 420, 260, true, true);
        assertTrue(AutismOverlayManager.withLiveState(stashedExpanded, liveCollapsed).collapsed);
    }

    @Test
    void anOversizedWindowShrinksToFit() {
        AutismWindowLayout clamped = AutismWindow.clampToScreenSize(layout(0, 0, 9999, 9999), 100, 60, 800, 600);
        assertEquals(800 - 8, clamped.width);
        assertEquals(600 - 8, clamped.height);
        assertEquals(4, clamped.x);
        assertEquals(4, clamped.y);
    }
}
