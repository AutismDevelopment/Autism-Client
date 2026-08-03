package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CompactSurfaces {
    private static final CompactTheme THEME = new CompactTheme();

    private CompactSurfaces() {
    }

    public static void row(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                           boolean hovered, boolean selected) {
        int fill = selected ? THEME.rowFillSelected() : hovered ? THEME.rowFillHovered() : THEME.rowFillNormal();
        tintedRow(graphics, x, y, width, height, fill);
    }

    public static void tintedRow(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill) {
        UiRenderer.rect(graphics, UiBounds.of(x, y, width, height), fill);
    }

    public static void divider(GuiGraphicsExtractor graphics, int x, int y, int width) {
        divider(graphics, x, y, width, THEME.borderSoft());
    }

    public static void divider(GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
        UiRenderer.horizontalEdge(graphics, x, y, width, color);
    }

    public static void header(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        UiRenderer.rect(graphics, UiBounds.of(x, y, width, height), THEME.headerFillInactive());
    }

    public static void insetPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int accent) {
        UiBounds bounds = UiBounds.of(x, y, width, height);
        UiRenderer.frame(graphics, bounds, THEME.windowFillInactive(), THEME.borderSoft());
        if (accent != 0 && height > 2) {
            UiRenderer.verticalEdge(graphics, x, y + 1, height - 2, accent);
        }
    }

    public static void valueField(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        UiRenderer.frame(graphics, UiBounds.of(x, y, width, height), THEME.listFill(), THEME.borderSoft());
    }

    public static void indicator(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        UiRenderer.rect(graphics, UiBounds.of(x, y, width, height), color);
    }
}
