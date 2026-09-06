package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

public final class TopBar {
    private TopBar() {
    }

    public static UiBounds collapseButton(UiBounds bounds) {
        int size = Math.min(12, Math.max(8, bounds.height() - 3));
        return UiBounds.of(bounds.x() + 2, bounds.y() + Math.max(1, (bounds.height() - size) / 2), size, size);
    }

    public static UiBounds closeButton(UiBounds bounds) {
        int size = Math.min(14, Math.max(10, bounds.height() - 1));
        return UiBounds.of(bounds.right() - size - 2, bounds.y() + Math.max(1, (bounds.height() - size) / 2), size, size);
    }

    public static void render(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean close, boolean hovered) {
        render(context, bounds, title, collapsed, true, close, hovered);
    }

    public static void render(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean collapse, boolean close, boolean hovered) {
        render(context, bounds, title, collapsed, collapse, close, hovered, 4, 4);
    }

    public static void render(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean collapse, boolean close, boolean hovered,
                              int titleLeftInset, int titleRightInset) {
        var graphics = context.graphics();
        var colors = context.theme().colors();
        UiRenderer.rect(graphics, bounds, hovered ? colors.headerHover : colors.header);
        if (!collapsed) UiRenderer.horizontalEdge(graphics, bounds.x(), bounds.bottom() - 1, bounds.width(), colors.accent);
        UiBounds collapseBounds = collapseButton(bounds);
        if (collapse) UiRenderer.chevron(graphics, collapseBounds.inset(1), !collapsed, colors.text);
        UiBounds closeBounds = close ? closeButton(bounds) : null;
        int titleLeft = collapse ? collapseBounds.right() + 3 : bounds.x() + Math.max(0, titleLeftInset);
        int titleRight = close ? closeBounds.x() - 3 : bounds.right() - Math.max(0, titleRightInset);
        context.text().drawEllipsized(graphics, title, titleLeft, context.text().centeredY(bounds), Math.max(1, titleRight - titleLeft), colors.text);
        if (close) {

            boolean closeHovered = closeBounds.contains(context.mouseX(), context.mouseY());
            int icon = 8;
            int ix = closeBounds.x() + (closeBounds.width() - icon) / 2;
            int iy = closeBounds.y() + (closeBounds.height() - icon) / 2;
            if (closeHovered) {
                UiRenderer.disc(graphics, closeBounds.x() + closeBounds.width() / 2.0F,
                    closeBounds.y() + closeBounds.height() / 2.0F, 6.5F, colors.accentSoft);
            }
            autismclient.util.AutismUiIcons.blit(graphics, autismclient.util.AutismUiIcons.X,
                ix, iy, icon, icon, closeHovered ? colors.text : colors.muted);
        }
    }
}
