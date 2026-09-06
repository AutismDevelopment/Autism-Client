package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

import java.util.Locale;

public final class CollapsibleSection {
    private CollapsibleSection() {
    }

    public static void renderHeader(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean hovered) {
        var colors = context.theme().colors();

        UiRenderer.frame(context.graphics(), bounds, hovered ? colors.rowHover : colors.row, colors.borderSoft);
        UiRenderer.rect(context.graphics(), UiBounds.of(bounds.x(), bounds.y(), 2, bounds.height()), colors.accent);
        UiRenderer.chevron(context.graphics(),
            UiBounds.of(bounds.x() + 5, bounds.y() + Math.max(0, (bounds.height() - 8) / 2), 8, 8), !collapsed, colors.text);
        context.text().drawFitted(
            context.graphics(),
            safe(title).toUpperCase(Locale.ROOT),
            bounds.x() + 17,
            context.text().centeredY(bounds),
            Math.max(1, bounds.width() - 22),
            colors.muted
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
