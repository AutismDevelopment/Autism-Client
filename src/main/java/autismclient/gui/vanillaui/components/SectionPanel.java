package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

public final class SectionPanel {
    private SectionPanel() {
    }

    public static void render(UiContext context, UiBounds bounds, String title) {
        var colors = context.theme().colors();
        int headerHeight = Math.min(context.theme().spacing().topBarHeight, Math.max(1, bounds.height() - 2));
        renderBody(context, bounds, colors.row);
        UiBounds header = UiBounds.of(bounds.x() + 1, bounds.y() + 1, Math.max(0, bounds.width() - 2), Math.max(0, headerHeight - 1));
        UiRenderer.rect(context.graphics(), header, colors.header);
        UiRenderer.horizontalEdge(context.graphics(), bounds.x() + 1, bounds.y() + headerHeight, Math.max(0, bounds.width() - 2), colors.borderSoft);
        context.text().drawFitted(context.graphics(), title == null ? "" : title, bounds.x() + 6,
            context.text().centeredY(header),
            Math.max(1, bounds.width() - 12), colors.text);
    }

    public static void renderBody(UiContext context, UiBounds bounds) {
        renderBody(context, bounds, context.theme().colors().row);
    }

    public static void renderBody(UiContext context, UiBounds bounds, int fill) {
        UiRenderer.frame(context.graphics(), bounds, fill, context.theme().colors().borderSoft);
    }
}
