package autismclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanoramaRecolorSmoothnessTest {
    private static final float TARGET_HUE = 0.0f;
    private static final float TARGET_SAT = 0.77f;

    private static int pixel(float hDeg, float s, float v) {
        return java.awt.Color.HSBtoRGB(hDeg / 360.0f, s, v);
    }

    private static float[] hsb(int argb) {
        return java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
    }

    private static double hueDistanceDeg(float a, float b) {
        double d = Math.abs(a - b) * 360.0;
        return Math.min(d, 360.0 - d);
    }

    @Test
    void hueSweepAcrossTheBandEdgesIsContinuous() {
        int prev = AutismTheme.recolorImagePixelTo(pixel(160.0f, 0.5f, 0.8f), TARGET_HUE, TARGET_SAT);
        for (int k = 321; k <= 680; k++) {
            float deg = k * 0.5f;
            int cur = AutismTheme.recolorImagePixelTo(pixel(deg, 0.5f, 0.8f), TARGET_HUE, TARGET_SAT);
            double jump = hueDistanceDeg(hsb(prev)[0], hsb(cur)[0]);
            assertTrue(jump <= 16.0,
                "hue jump of " + jump + " deg between source hues " + (deg - 0.5f) + " and " + deg);
            prev = cur;
        }
    }

    @Test
    void saturationSweepAcrossTheNeutralEdgeIsContinuous() {
        int prev = AutismTheme.recolorImagePixelTo(pixel(250.0f, 0.05f, 0.8f), TARGET_HUE, TARGET_SAT);
        for (int k = 6; k <= 35; k++) {
            float s = k / 100.0f;
            int cur = AutismTheme.recolorImagePixelTo(pixel(250.0f, s, 0.8f), TARGET_HUE, TARGET_SAT);
            double delta = Math.abs(hsb(cur)[1] - hsb(prev)[1]);
            assertTrue(delta <= 0.06,
                "saturation jump of " + delta + " between source saturations " + (s - 0.01f) + " and " + s);
            prev = cur;
        }
    }

    @Test
    void preservedExtremesAreBitIdentical() {
        for (int deg = 0; deg < 360; deg += 7) {
            for (float s : new float[] {0.0f, 0.05f, 0.10f}) {
                int src = pixel(deg, s, 0.8f);
                assertEquals(src, AutismTheme.recolorImagePixelTo(src, TARGET_HUE, TARGET_SAT),
                    "low-saturation pixel changed at hue " + deg + " sat " + s);
            }
        }
        for (float deg = 0.0f; deg <= 180.0f; deg += 5.0f) {
            int src = pixel(deg, 0.5f, 0.8f);
            assertEquals(src, AutismTheme.recolorImagePixelTo(src, TARGET_HUE, TARGET_SAT),
                "out-of-band pixel changed at hue " + deg);
        }
        for (float deg = 330.0f; deg < 360.0f; deg += 5.0f) {
            int src = pixel(deg, 0.5f, 0.8f);
            assertEquals(src, AutismTheme.recolorImagePixelTo(src, TARGET_HUE, TARGET_SAT),
                "out-of-band pixel changed at hue " + deg);
        }
    }
}
