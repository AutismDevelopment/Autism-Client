package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;

public final class OverlayTopBar {
    private OverlayTopBar() {
    }

    public static UiBounds barBounds(UiBounds windowBounds, int headerHeight) {
        int height = Math.max(1, Math.min(Math.max(1, headerHeight) - 2, Math.max(1, windowBounds.height() - 2)));
        return UiBounds.of(windowBounds.x() + 1, windowBounds.y() + 1, Math.max(0, windowBounds.width() - 2), height);
    }

    public static UiBounds closeButton(UiBounds windowBounds, int headerHeight) {
        return TopBar.closeButton(barBounds(windowBounds, headerHeight));
    }

    public static UiBounds collapseButton(UiBounds windowBounds, int headerHeight) {
        return TopBar.collapseButton(barBounds(windowBounds, headerHeight));
    }

    public static boolean isOverClose(UiBounds windowBounds, int headerHeight, double mouseX, double mouseY) {
        return closeButton(windowBounds, headerHeight).contains((int) mouseX, (int) mouseY);
    }

    public static boolean isOverCollapse(UiBounds windowBounds, int headerHeight, double mouseX, double mouseY) {
        return collapseButton(windowBounds, headerHeight).contains((int) mouseX, (int) mouseY);
    }

    public static boolean isOverHeader(UiBounds windowBounds, int headerHeight, double mouseX, double mouseY) {
        UiBounds header = UiBounds.of(windowBounds.x(), windowBounds.y(), windowBounds.width(), Math.max(1, headerHeight));
        return header.contains((int) mouseX, (int) mouseY);
    }

    public static boolean isOverDragArea(UiBounds windowBounds, int headerHeight, boolean hasCollapse, boolean hasClose,
                                         double mouseX, double mouseY) {
        if (!isOverHeader(windowBounds, headerHeight, mouseX, mouseY)) return false;
        if (hasClose && isOverClose(windowBounds, headerHeight, mouseX, mouseY)) return false;
        if (hasCollapse && isOverCollapse(windowBounds, headerHeight, mouseX, mouseY)) return false;
        return true;
    }

    public static void render(UiContext context, UiBounds windowBounds, int headerHeight, String title,
                              boolean collapsed, boolean hasCollapse, boolean hasClose,
                              boolean headerHovered, boolean active, int titleLeftInset, int titleRightInset) {
        TopBar.render(context, barBounds(windowBounds, headerHeight), title, collapsed, hasCollapse, hasClose, active && headerHovered,
            titleLeftInset, titleRightInset);
    }
}
