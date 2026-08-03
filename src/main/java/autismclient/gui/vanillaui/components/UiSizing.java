package autismclient.gui.vanillaui.components;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;

import java.util.Collection;

public final class UiSizing {
    private UiSizing() {
    }

    public static float clamp(float value, float min, float max) {
        float safeMax = Math.max(min, max);
        return Math.max(min, Math.min(value, safeMax));
    }

    public static float fitTextWidth(Font renderer, String text, Identifier fontId, int color, float horizontalPadding, float minWidth, float maxWidth) {
        float textWidth = UiText.width(renderer, text == null ? "" : text, fontId, color);
        return clamp(textWidth + (horizontalPadding * 2.0f), minWidth, maxWidth);
    }

    public static int fitTextWidthInt(Font renderer, String text, Identifier fontId, int color, int horizontalPadding, int minWidth, int maxWidth) {
        return Math.round(fitTextWidth(renderer, text, fontId, color, horizontalPadding, minWidth, maxWidth));
    }

    public static int measureWidestText(Font renderer, Identifier fontId, int color, Collection<String> values) {
        if (renderer == null || values == null || values.isEmpty()) return 0;
        int widest = 0;
        for (String value : values) {
            widest = Math.max(widest, UiText.width(renderer, value == null ? "" : value, fontId, color));
        }
        return widest;
    }

    public static int centerInside(int outerStart, int outerSize, int innerSize) {
        return outerStart + Math.max(0, (outerSize - innerSize) / 2);
    }

    public static int alignMiddle(int outerStart, int outerSize, int innerSize) {
        return centerInside(outerStart, outerSize, innerSize);
    }

    public static int alignTextY(int boxY, int boxHeight, int fontHeight, int offset) {
        int spareHeight = Math.max(0, boxHeight - fontHeight);
        return boxY + ((spareHeight + 1) / 2) + offset;
    }

    public static int lerpColor(int from, int to, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        int a1 = (from >>> 24) & 0xFF;
        int r1 = (from >>> 16) & 0xFF;
        int g1 = (from >>> 8) & 0xFF;
        int b1 = from & 0xFF;
        int a2 = (to >>> 24) & 0xFF;
        int r2 = (to >>> 16) & 0xFF;
        int g2 = (to >>> 8) & 0xFF;
        int b2 = to & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * clamped);
        int r = Math.round(r1 + (r2 - r1) * clamped);
        int g = Math.round(g1 + (g2 - g1) * clamped);
        int b = Math.round(b1 + (b2 - b1) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
