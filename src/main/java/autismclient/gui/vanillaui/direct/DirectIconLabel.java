package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.direct.DirectUiNode;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.components.ConnectedButton;
import net.minecraft.resources.Identifier;

public final class DirectIconLabel extends DirectUiNode {
    private String text;
    private Identifier icon;
    private int iconSize = 12;
    private int iconGap = 5;
    private boolean connectedStyle;

    public DirectIconLabel(String text, Identifier icon) {
        this.text = text == null ? "" : text;
        this.icon = icon;
        this.height = 14;
    }

    public DirectIconLabel setIconSize(int iconSize) {
        this.iconSize = Math.max(1, iconSize);
        markLayoutDirty();
        return this;
    }

    public DirectIconLabel setConnectedStyle(boolean connectedStyle) {
        this.connectedStyle = connectedStyle;
        return this;
    }

    @Override
    public float preferredWidth(DirectRenderContext context) {
        int textWidth = UiContexts.textRenderer(context.textRenderer()).width(text);
        return textWidth + (icon != null ? iconSize + iconGap : 0);
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        if (connectedStyle) return 15;
        return Math.max(11, iconSize + 1);
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;
        int drawX = Math.round(x);
        int drawY = Math.round(y);
        int drawH = Math.round(height);
        if (connectedStyle) {
            ConnectedButton.renderCategory(
                UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY())),
                UiBounds.of(drawX, drawY, Math.round(width), drawH),
                text,
                icon
            );
            return;
        }
        int contentX = drawX;
        if (icon != null) {
            int iconY = drawY + Math.max(1, (drawH - iconSize) / 2);
            context.drawTexturedQuad(autismclient.util.AutismThemeTextures.recolored(icon, autismclient.util.AutismTheme.Channel.ACCENT), drawX, iconY, drawX + iconSize, iconY + iconSize);
            contentX += iconSize + iconGap;
        }
        UiContexts.textRenderer(context.textRenderer()).draw(context.drawContext(), text, contentX,
            drawY + Math.max(1, (drawH - 9) / 2), context.applyAlpha(autismclient.util.AutismTheme.recolor(0xFFF3ECE7, autismclient.util.AutismTheme.Channel.TEXT)));
    }

}
