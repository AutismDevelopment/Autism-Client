package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

public final class Slider {
    private Slider() {
    }

    public static void render(UiContext context, UiBounds bounds, double ratio, boolean hovered) {
        var colors = context.theme().colors();
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        UiRenderer.frame(context.graphics(), bounds, colors.field, hovered ? colors.border : colors.borderSoft);
        UiBounds track = bounds.inset(3, Math.max(4, bounds.height() / 2 - 1), 3, Math.max(4, bounds.height() / 2 - 1));
        if (track.height() <= 0) track = UiBounds.of(bounds.x() + 3, bounds.y() + bounds.height() / 2, Math.max(1, bounds.width() - 6), 1);
        UiRenderer.rect(context.graphics(), track, 0xCC30333C);
        UiRenderer.rect(context.graphics(), UiBounds.of(track.x(), track.y(), (int) Math.round(track.width() * ratio), track.height()), colors.accent);
        int knobX = track.x() + (int) Math.round(track.width() * ratio) - 2;
        knobX = Math.max(bounds.x() + 2, Math.min(bounds.right() - 5, knobX));
        UiRenderer.rect(context.graphics(), UiBounds.of(knobX, bounds.y() + 2, 5, Math.max(1, bounds.height() - 4)), hovered ? colors.text : colors.muted);
    }

    public static double ratio(double value, double min, double max) {
        return max <= min ? 0.0 : Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }

    public static double valueFromMouse(double mouseX, int x, int width, double min, double max, double step) {
        double ratio = width <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (mouseX - x) / width));
        double value = min + ratio * (max - min);
        double safeStep = Math.max(0.0001, step);
        return min + Math.round((value - min) / safeStep) * safeStep;
    }
}
