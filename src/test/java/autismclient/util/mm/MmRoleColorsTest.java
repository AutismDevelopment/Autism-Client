package autismclient.util.mm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MmRoleColorsTest {

    @Test
    void solidTierColours() {
        assertEquals(0xFF3498DB, MmRoleColors.roleColor("user"));
        assertEquals(0xFF3498DB, MmRoleColors.roleColor(null));
        assertEquals(0xFF3498DB, MmRoleColors.roleColor("garbage"));
        assertEquals(0xFF3498DB, MmRoleColors.roleColor("blue"));

        assertEquals(0xFFFF5C5C, MmRoleColors.roleColor("god"));
        assertEquals(0xFF4CADD0, MmRoleColors.roleColor("admin"));
        assertEquals(0xFFD4843D, MmRoleColors.roleColor("gold"));
        assertEquals(0xFF00E5FF, MmRoleColors.roleColor("aqua"));
        assertEquals(0xFFC91AEC, MmRoleColors.roleColor("notbroke"));
    }

    @Test
    void gradientDetection() {
        assertTrue(MmRoleColors.isGradient("god"));
        assertTrue(MmRoleColors.isGradient("admin"));
        assertTrue(MmRoleColors.isGradient("gold"));
        assertTrue(MmRoleColors.isGradient("aqua"));
        assertTrue(MmRoleColors.isGradient("notbroke"));
        assertFalse(MmRoleColors.isGradient("blue"));
        assertFalse(MmRoleColors.isGradient("user"));
        assertFalse(MmRoleColors.isGradient(null));
    }

    @Test
    void gradientEndpointsMatchSpec() {
        int len = 8;
        assertGradient("notbroke", 0xFFC91AEC, 0xFF1540BE, len);
        assertGradient("god", 0xFFFF5C5C, 0xFFFF0000, len);
        assertGradient("admin", 0xFF4CADD0, 0xFFB2F9FF, len);
        assertGradient("gold", 0xFFD4843D, 0xFFFFDE90, len);
        assertGradient("aqua", 0xFF00E5FF, 0xFF3AFF00, len);
        assertEquals(0xFFC91AEC, MmRoleColors.gradientNameColor("notbroke", 0, 1));
    }

    private static void assertGradient(String role, int start, int end, int len) {
        assertEquals(start, MmRoleColors.gradientNameColor(role, 0, len), role + " start");
        assertEquals(end, MmRoleColors.gradientNameColor(role, len - 1, len), role + " end");
    }

    @Test
    void solidTierIsConstantAcrossCharacters() {
        for (int i = 0; i < 6; i++) {
            assertEquals(0xFF3498DB, MmRoleColors.gradientNameColor("blue", i, 6));
            assertEquals(0xFF3498DB, MmRoleColors.gradientNameColor("user", i, 6));
            assertEquals(0xFF3498DB, MmRoleColors.gradientNameColor(null, i, 6));
        }
    }

    @Test
    void gradientIsMonotonicAcrossCharacters() {

        int len = 12;
        int prevR = 256, prevG = -1, prevB = 256;
        for (int i = 0; i < len; i++) {
            int color = MmRoleColors.gradientNameColor("notbroke", i, len);
            int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
            assertTrue(r <= prevR, "red channel must not increase at index " + i);
            assertTrue(g >= prevG, "green channel must not decrease at index " + i);
            assertTrue(b <= prevB, "blue channel must not increase at index " + i);
            prevR = r;
            prevG = g;
            prevB = b;
        }
    }

    @Test
    void lerpRgbHitsBothEnds() {
        assertEquals(0xFFC91AEC, MmRoleColors.lerpRgb(0xFFC91AEC, 0xFF1540BE, 0.0F));
        assertEquals(0xFF1540BE, MmRoleColors.lerpRgb(0xFFC91AEC, 0xFF1540BE, 1.0F));
    }
}
