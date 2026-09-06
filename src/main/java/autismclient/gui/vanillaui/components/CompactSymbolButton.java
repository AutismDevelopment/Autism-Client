package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CompactSymbolButton {
    public static final String CLOSE = "X";
    public static final String MOVE_UP = "^";
    public static final String MOVE_DOWN = "v";
    public static final String EXPAND = "+";
    public static final String COLLAPSE = "-";

    private CompactSymbolButton() {
    }

    public static void render(GuiGraphicsExtractor graphics, Font font, UiBounds bounds, String symbol,
                              boolean hovered, boolean active, boolean danger) {
        if (graphics == null || font == null || bounds == null || bounds.width() <= 0 || bounds.height() <= 0) return;
        var context = UiContexts.overlay(graphics, font, -10000, -10000);
        var colors = context.theme().colors();
        int fill = danger ? autismclient.util.AutismTheme.recolor(0xCC351317, autismclient.util.AutismTheme.Channel.DANGER) : 0xBB161A21;
        UiRenderer.frame(graphics, bounds, fill, danger ? colors.bad : colors.borderSoft);
        float hoverT = autismclient.gui.vanillaui.HoverFades.get(autismclient.gui.vanillaui.HoverFades.key(bounds), hovered && active);
        if (hoverT > 0.001f) UiRenderer.rect(graphics, bounds.inset(1), ((int) (0x20 * hoverT)) << 24 | 0xFFFFFF);
        if (!active) UiRenderer.rect(graphics, bounds.inset(1), 0x66000000);
        int color = active ? colors.text : colors.disabled;

        if (CLOSE.equals(symbol)) {
            UiRenderer.cross(graphics, bounds.inset(3), color);
            return;
        }
        if (MOVE_UP.equals(symbol) || MOVE_DOWN.equals(symbol)) {

            UiBounds glyph = centeredSquare(bounds, 3);
            if (MOVE_UP.equals(symbol)) UiRenderer.chevronUp(graphics, glyph, color);
            else UiRenderer.chevron(graphics, glyph, true, color);
            return;
        }
        if (EXPAND.equals(symbol)) {

            UiBounds glyph = centeredSquare(bounds, 3);
            autismclient.util.AutismUiIcons.blit(graphics, autismclient.util.AutismUiIcons.PLUS,
                glyph.x(), glyph.y(), glyph.width(), glyph.height(), color);
            return;
        }
        String glyph = symbol == null ? "" : symbol;
        context.text().drawCentered(graphics, glyph, bounds, color);
    }

    private static UiBounds centeredSquare(UiBounds bounds, int inset) {
        int gs = Math.max(4, Math.min(bounds.width(), bounds.height()) - inset * 2);
        int box = Math.min(bounds.width(), bounds.height());
        if (((box - gs) & 1) != 0) gs--;
        return UiBounds.of(bounds.x() + (bounds.width() - gs) / 2,
            bounds.y() + (bounds.height() - gs) / 2, Math.max(1, gs), Math.max(1, gs));
    }

    public static void renderIcon(GuiGraphicsExtractor graphics, Font font, UiBounds bounds, net.minecraft.resources.Identifier icon,
                                  boolean hovered, boolean active, boolean danger) {
        if (graphics == null || font == null || bounds == null || bounds.width() <= 0 || bounds.height() <= 0) return;
        var context = UiContexts.overlay(graphics, font, -10000, -10000);
        var colors = context.theme().colors();

        int fill = danger ? 0xD0521A1E : 0xBB161A21;
        int borderColor = danger ? 0xFFE24A4A : colors.borderSoft;
        UiRenderer.frame(graphics, bounds, fill, borderColor);
        float hoverT = autismclient.gui.vanillaui.HoverFades.get(autismclient.gui.vanillaui.HoverFades.key(bounds), hovered && active);
        if (hoverT > 0.001f) UiRenderer.rect(graphics, bounds.inset(1), ((int) (0x20 * hoverT)) << 24 | 0xFFFFFF);
        if (!active) UiRenderer.rect(graphics, bounds.inset(1), 0x66000000);

        int color = active ? 0xFFFFFFFF : 0x80FFFFFF;
        int size = Math.max(4, Math.min(bounds.width(), bounds.height()) - 5);

        int box = Math.min(bounds.width(), bounds.height());
        if (((box - size) & 1) != 0) size--;
        autismclient.util.AutismUiIcons.blit(graphics, icon,
            bounds.x() + (bounds.width() - size) / 2, bounds.y() + (bounds.height() - size) / 2, size, color);
    }

    public static void renderGlyph(GuiGraphicsExtractor graphics, Font font, UiBounds bounds, String symbol,
                                   int color, float alpha) {
        if (graphics == null || font == null || bounds == null || alpha <= 0.001f) return;
        int resolved = UiRenderer.applyAlpha(color, alpha);
        String glyph = symbol == null ? "" : symbol;
        UiContexts.overlay(graphics, font, -10000, -10000).text().drawCentered(graphics, glyph, bounds, resolved);
    }

    public static Font minecraftFont() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.font;
    }
}
