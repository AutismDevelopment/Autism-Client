package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

public final class Chip {
    private Chip() {
    }

    public static void render(UiContext context, UiBounds bounds, String label, boolean selected, boolean hovered) {
        render(context, bounds, label, context.theme().colors().accent, selected, hovered);
    }

    public static void render(UiContext context, UiBounds bounds, String label, int accentColor, boolean selected, boolean hovered) {
        var colors = context.theme().colors();
        float hoverT = autismclient.gui.vanillaui.HoverFades.get(autismclient.gui.vanillaui.HoverFades.key(bounds), hovered);
        int border = selected ? accentColor : colors.buttonBorder;
        int fill = selected ? tint(accentColor, 0.12f) : lerpRow(colors.row, colors.rowHover, hoverT);
        UiRenderer.frame(context.graphics(), bounds, fill, border);
        int text = selected || hovered ? colors.text : colors.muted;
        context.text().drawCentered(context.graphics(), label == null ? "" : label, bounds, text);
    }

    public static void renderDisabled(UiContext context, UiBounds bounds, String label) {
        render(context, bounds, label, false, false);
        UiRenderer.rect(context.graphics(), bounds, 0x66000000);
    }

    private static int tint(int color, float alpha) {
        int a = Math.round(255 * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int lerpRow(int from, int to, float t) {
        int ar = (from >> 16) & 0xFF, ag = (from >> 8) & 0xFF, ab = from & 0xFF;
        int br = (to >> 16) & 0xFF, bg = (to >> 8) & 0xFF, bb = to & 0xFF;
        return 0xFF000000
            | (Math.round(ar + (br - ar) * t) << 16)
            | (Math.round(ag + (bg - ag) * t) << 8)
            | Math.round(ab + (bb - ab) * t);
    }
}
