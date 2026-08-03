package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CompactOverlayWindow {
    private static final int SAFE_MARGIN = 4;

    private CompactOverlayWindow() {
    }

    public static UiBounds clamp(UiBounds wanted, int minWidth, int minHeight) {
        int screenWidth = Math.max(1, AutismUiScale.getVirtualScreenWidth());
        int screenHeight = Math.max(1, AutismUiScale.getVirtualScreenHeight());
        int availableWidth = Math.max(1, screenWidth - SAFE_MARGIN * 2);
        int availableHeight = Math.max(1, screenHeight - SAFE_MARGIN * 2);
        int width = Math.max(Math.min(minWidth, availableWidth), Math.min(wanted.width(), availableWidth));
        int height = Math.max(Math.min(minHeight, availableHeight), Math.min(wanted.height(), availableHeight));
        int x = Math.max(SAFE_MARGIN, Math.min(wanted.x(), Math.max(SAFE_MARGIN, screenWidth - SAFE_MARGIN - width)));
        int y = Math.max(SAFE_MARGIN, Math.min(wanted.y(), Math.max(SAFE_MARGIN, screenHeight - SAFE_MARGIN - height)));
        return UiBounds.of(x, y, width, height);
    }

    public static void render(UiContext context, UiBounds bounds, int headerHeight, String title,
                              boolean collapsed, boolean active, boolean headerHovered) {
        CompactWindow.renderFrame(context, bounds, title, collapsed, true, true, headerHovered, active, 4, 4, headerHeight);
    }

    public static boolean beginBodyClip(GuiGraphicsExtractor graphics, UiBounds bounds, int headerHeight, boolean collapsed) {
        if (collapsed || bounds.height() <= headerHeight + 1) return false;
        UiScissorStack.global().push(graphics, UiBounds.of(
            bounds.x() + 1,
            bounds.y() + headerHeight,
            Math.max(0, bounds.width() - 2),
            Math.max(0, bounds.height() - headerHeight - 1)
        ));
        return true;
    }

    public static void endBodyClip(GuiGraphicsExtractor graphics, boolean clipped) {
        if (clipped) UiScissorStack.global().pop(graphics);
    }
}
