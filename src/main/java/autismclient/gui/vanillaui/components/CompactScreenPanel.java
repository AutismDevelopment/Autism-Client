package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;

public final class CompactScreenPanel {
    private CompactScreenPanel() {
    }

    public static void render(UiContext context, UiBounds bounds, int headerHeight, String title, boolean headerHovered) {
        CompactWindow.renderFrame(context, bounds, title, false, false, true, headerHovered, true, 4, 4, headerHeight);
    }

    public static UiBounds closeButton(UiBounds bounds, int headerHeight) {
        return OverlayTopBar.closeButton(bounds, headerHeight);
    }

    public static boolean isOverClose(UiBounds bounds, int headerHeight, int mouseX, int mouseY) {
        return closeButton(bounds, headerHeight).contains(mouseX, mouseY);
    }
}
