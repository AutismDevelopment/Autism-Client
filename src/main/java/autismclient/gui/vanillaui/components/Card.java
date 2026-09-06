package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

public final class Card {
    private Card() {
    }

    public static void render(UiContext context, UiBounds bounds) {
        render(context, bounds, null);
    }

    public static void render(UiContext context, UiBounds bounds, String title) {
        var colors = context.theme().colors();
        UiRenderer.frame(context.graphics(), bounds, colors.row, colors.borderSoft);
        if (title == null || title.isBlank()) return;
        int titleY = bounds.y() + 4;
        context.text().drawFitted(context.graphics(), title, bounds.x() + 5, titleY,
            Math.max(1, bounds.width() - 10), colors.muted);
        int lineY = titleY + 9 + 3;
        UiRenderer.horizontalEdge(context.graphics(), bounds.x() + 4, lineY, Math.max(1, bounds.width() - 8), colors.borderSoft);
    }

    public static int contentY(UiContext context, UiBounds bounds, String title) {
        if (title == null || title.isBlank()) return bounds.y() + 4;
        return bounds.y() + 4 + 9 + 4;
    }
}
