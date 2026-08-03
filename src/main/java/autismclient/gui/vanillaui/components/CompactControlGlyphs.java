package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CompactControlGlyphs {
    public enum ChevronDirection {
        RIGHT,
        DOWN,
        UP
    }

    private CompactControlGlyphs() {
    }

    public static void drawClose(GuiGraphicsExtractor context, int x, int y, int size, int color, int shadowColor, float alpha) {
        if (alpha <= 0.001f) return;
        UiRenderer.cross(context, UiBounds.of(x, y, size, size), UiRenderer.applyAlpha(color, alpha));
    }

    public static void drawChevron(GuiGraphicsExtractor context, int x, int y, int size, ChevronDirection direction, int color, int shadowColor, float alpha) {
        if (alpha <= 0.001f) return;
        int resolved = UiRenderer.applyAlpha(color, alpha);
        UiBounds bounds = UiBounds.of(x, y, size, size);
        switch (direction) {
            case UP -> UiRenderer.chevronUp(context, bounds, resolved);
            case DOWN -> UiRenderer.chevron(context, bounds, true, resolved);
            case RIGHT -> UiRenderer.chevron(context, bounds, false, resolved);
        }
    }

    public static void drawChevronProgress(GuiGraphicsExtractor context, int x, int y, int size, float progress, int color, int shadowColor, float alpha) {
        if (alpha <= 0.001f) return;
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        int resolved = UiRenderer.applyAlpha(color, alpha);
        UiBounds bounds = UiBounds.of(x, y, size, size);
        if (clamped >= 0.5f) UiRenderer.chevron(context, bounds, true, resolved);
        else UiRenderer.chevron(context, bounds, false, resolved);
    }
}
