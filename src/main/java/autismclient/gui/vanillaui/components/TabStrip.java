package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;

public final class TabStrip {
    private TabStrip() {
    }

    public static UiBounds tabBounds(UiBounds strip, int count, int index, int gap) {
        int safeCount = Math.max(1, count);
        int safeGap = Math.max(0, gap);
        int width = Math.max(1, (strip.width() - safeGap * (safeCount - 1)) / safeCount);
        int x = strip.x() + index * (width + safeGap);
        int resolvedWidth = index == safeCount - 1 ? Math.max(1, strip.right() - x) : width;
        return UiBounds.of(x, strip.y(), resolvedWidth, strip.height());
    }

    public static void renderTab(UiContext context, UiBounds bounds, String label, boolean active) {
        Button.render(context, bounds, label, active ? Button.Tone.PRIMARY : Button.Tone.NORMAL,
            bounds.contains(context.mouseX(), context.mouseY()), active);
    }
}
