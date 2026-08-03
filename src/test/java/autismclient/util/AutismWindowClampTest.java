package autismclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
    void anOversizedWindowShrinksToFit() {
        AutismWindowLayout clamped = AutismWindow.clampToScreenSize(layout(0, 0, 9999, 9999), 100, 60, 800, 600);
        assertEquals(800 - 8, clamped.width);
        assertEquals(600 - 8, clamped.height);
        assertEquals(4, clamped.x);
        assertEquals(4, clamped.y);
    }
}
