package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

public final class RangeSlider {

    private static final int KNOB_HALF = 2;

    private RangeSlider() {
    }

    public static void render(UiContext context, UiBounds bounds,
                              double minRatio, double maxRatio, boolean hovered, int activeThumb) {
        var colors = context.theme().colors();
        minRatio = clamp01(minRatio);
        maxRatio = clamp01(maxRatio);
        if (maxRatio < minRatio) {
            double swap = minRatio;
            minRatio = maxRatio;
            maxRatio = swap;
        }
        UiRenderer.frame(context.graphics(), bounds, colors.field, hovered ? colors.border : colors.borderSoft);

        UiBounds track = trackOf(bounds);
        UiRenderer.rect(context.graphics(), track, 0xCC30333C);

        int lowX = trackX(track, minRatio);
        int highX = trackX(track, maxRatio);
        if (highX > lowX) {
            UiRenderer.rect(context.graphics(),
                UiBounds.of(lowX, track.y(), highX - lowX, track.height()), colors.accent);
        }

        drawKnob(context, bounds, lowX, hovered || activeThumb == THUMB_MIN);
        drawKnob(context, bounds, highX, hovered || activeThumb == THUMB_MAX);
    }

    private static void drawKnob(UiContext context, UiBounds bounds, int centerX, boolean lit) {
        var colors = context.theme().colors();
        int x = Math.max(bounds.x() + 2, Math.min(bounds.right() - 5, centerX - KNOB_HALF));
        UiRenderer.rect(context.graphics(),
            UiBounds.of(x, bounds.y() + 2, 5, Math.max(1, bounds.height() - 4)),
            lit ? colors.text : colors.muted);
    }

    public static UiBounds trackOf(UiBounds bounds) {
        int inset = Math.max(4, bounds.height() / 2 - 1);
        UiBounds track = bounds.inset(3, inset, 3, inset);
        if (track.height() <= 0) {
            return UiBounds.of(bounds.x() + 3, bounds.y() + bounds.height() / 2,
                Math.max(1, bounds.width() - 6), 1);
        }
        return track;
    }

    private static int trackX(UiBounds track, double ratio) {
        return track.x() + (int) Math.round(track.width() * clamp01(ratio));
    }

    public static final int THUMB_NONE = -1;
    public static final int THUMB_MIN = 0;
    public static final int THUMB_MAX = 1;

    public static int nearestThumb(double mouseX, UiBounds bounds, double minRatio, double maxRatio) {
        UiBounds track = trackOf(bounds);
        int lowX = trackX(track, minRatio);
        int highX = trackX(track, maxRatio);
        double toLow = Math.abs(mouseX - lowX);
        double toHigh = Math.abs(mouseX - highX);
        if (Math.abs(toLow - toHigh) < 0.5D) return mouseX < lowX ? THUMB_MIN : THUMB_MAX;
        return toLow <= toHigh ? THUMB_MIN : THUMB_MAX;
    }

    public static double ratio(double value, double min, double max) {
        return max <= min ? 0.0 : clamp01((value - min) / (max - min));
    }

    public static double valueFromMouse(double mouseX, UiBounds bounds, double min, double max, double step) {
        UiBounds track = trackOf(bounds);
        double ratio = track.width() <= 0 ? 0.0 : clamp01((mouseX - track.x()) / track.width());
        double value = min + ratio * (max - min);
        double safeStep = Math.max(0.0001, step);
        return min + Math.round((value - min) / safeStep) * safeStep;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
