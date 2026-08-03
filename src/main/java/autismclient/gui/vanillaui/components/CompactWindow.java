package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

public final class CompactWindow {
    private CompactWindow() {
    }

    public static UiBounds clamp(UiBounds wanted, int screenWidth, int screenHeight, int minWidth, int minHeight) {
        int width = Math.max(Math.min(wanted.width(), Math.max(1, screenWidth - 8)), Math.min(minWidth, Math.max(1, screenWidth - 8)));
        int height = Math.max(Math.min(wanted.height(), Math.max(1, screenHeight - 8)), Math.min(minHeight, Math.max(1, screenHeight - 8)));
        return UiBounds.of(wanted.x(), wanted.y(), width, height).clampInside(screenWidth - 4, screenHeight - 4);
    }

    public static void renderFrame(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean close, boolean headerHovered) {
        renderFrame(context, bounds, title, collapsed, close, headerHovered, true);
    }

    public static void renderFrame(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean close, boolean headerHovered, boolean active) {
        renderFrame(context, bounds, title, collapsed, true, close, headerHovered, active);
    }

    public static void renderFrame(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean collapse, boolean close, boolean headerHovered, boolean active) {
        renderFrame(context, bounds, title, collapsed, collapse, close, headerHovered, active, 4, 4);
    }

    public static void renderFrame(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean collapse, boolean close, boolean headerHovered, boolean active,
                                   int titleLeftInset, int titleRightInset) {
        renderFrame(context, bounds, title, collapsed, collapse, close, headerHovered, active, titleLeftInset, titleRightInset,
            context.theme().spacing().topBarHeight);
    }

    public static void renderFrame(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean collapse, boolean close, boolean headerHovered, boolean active,
                                   int titleLeftInset, int titleRightInset, int headerHeight) {
        var colors = context.theme().colors();

        UiRenderer.frame(context.graphics(), bounds, colors.window, colors.borderSoft);
        OverlayTopBar.render(context, bounds, headerHeight, title, collapsed, collapse, close,
            headerHovered, active, titleLeftInset, titleRightInset);
    }
}
