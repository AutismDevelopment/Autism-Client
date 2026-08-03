package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import net.minecraft.client.gui.Font;

public final class DirectLayout {
    private DirectLayout() {
    }

    public static int contentWidth(int panelWidth, int horizontalPadding) {
        return Math.max(0, panelWidth - (horizontalPadding * 2));
    }

    public static int reserveScrollbar(int width, boolean hasScroll, int gutter) {
        return Math.max(0, width - (hasScroll ? gutter : 0));
    }

    public static int fitOverlayButtonWidth(Font renderer, CompactTheme theme, UiTone tone, String label, int horizontalPadding, int minWidth, int maxWidth) {
        if (renderer == null || theme == null) return Math.max(minWidth, 0);
        return UiSizing.fitTextWidthInt(
            renderer,
            label == null ? "" : label,
            theme.fontFor(tone == null ? UiTone.BODY : tone),
            theme.color(tone == null ? UiTone.BODY : tone),
            horizontalPadding,
            minWidth,
            maxWidth
        );
    }

    public static int rightAlign(int leftX, int totalWidth, int childWidth, int rightInset) {
        return leftX + Math.max(0, totalWidth - childWidth - rightInset);
    }

    public static int rowY(int startY, int rowHeight, int gap, int index) {
        return startY + (Math.max(0, index) * (rowHeight + Math.max(0, gap)));
    }

    public static int fitPanelDimension(int screenSize, int margin, int preferredSize) {
        int available = Math.max(1, screenSize - Math.max(0, margin) * 2);
        return Math.max(1, Math.min(preferredSize, available));
    }

    public static int centerPanel(int screenSize, int panelSize, int margin) {
        int safeMargin = Math.max(0, margin);
        return Math.max(safeMargin, Math.min((screenSize - panelSize) / 2, Math.max(safeMargin, screenSize - safeMargin - panelSize)));
    }
}
