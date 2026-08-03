package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.direct.DirectUiColumn;
import autismclient.gui.vanillaui.direct.DirectUiContainer;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;

public class DirectPanel extends DirectUiContainer {
    private final DirectUiColumn content = new DirectUiColumn();
    private boolean active = true;
    private DirectUiInsets padding = DirectUiInsets.all(6);
    private boolean drawBorder = true;
    private boolean drawFill = true;

    public DirectPanel() {
        add(content);
    }

    public DirectUiColumn content() {
        return content;
    }

    public DirectPanel setActive(boolean active) {
        this.active = active;
        return this;
    }

    public DirectPanel setPadding(DirectUiInsets padding) {
        DirectUiInsets next = padding == null ? DirectUiInsets.NONE : padding;
        if (!this.padding.equals(next)) {
            this.padding = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectPanel setDrawBorder(boolean drawBorder) {
        this.drawBorder = drawBorder;
        return this;
    }

    public DirectPanel setDrawFill(boolean drawFill) {
        this.drawFill = drawFill;
        return this;
    }

    @Override
    public float preferredWidth(DirectRenderContext context) {
        DirectUiInsets scaledPadding = context.theme().scale(padding);
        return scaledPadding.horizontal() + content.preferredWidth(context);
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        DirectUiInsets scaledPadding = context.theme().scale(padding);
        return scaledPadding.vertical() + content.preferredHeight(context, Math.max(0.0f, availableWidth - scaledPadding.horizontal()));
    }

    @Override
    protected void layoutChildren(DirectRenderContext context) {
        DirectUiInsets scaledPadding = context.theme().scale(padding);
        content.setBounds(
            x + scaledPadding.left(),
            y + scaledPadding.top(),
            Math.max(0.0f, width - scaledPadding.horizontal()),
            Math.max(0.0f, height - scaledPadding.vertical())
        );
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;
        DirectRenderContext drawContext = context.withAlpha(active ? 1.0f : 0.56f);
        int drawX = Math.round(x);
        int drawY = Math.round(y);
        int drawW = Math.round(width);
        int drawH = Math.round(height);
        int fill = active ? drawContext.theme().windowFill() : drawContext.theme().windowFillInactive();
        int border = active ? drawContext.theme().borderColor() : drawContext.theme().borderSoft();

        UiBounds bounds = UiBounds.of(drawX, drawY, drawW, drawH);
        if (drawFill && drawBorder) UiRenderer.frame(drawContext.drawContext(), bounds, drawContext.applyAlpha(fill), drawContext.applyAlpha(border));
        else if (drawFill) UiRenderer.rect(drawContext.drawContext(), bounds, drawContext.applyAlpha(fill));
        else if (drawBorder) UiRenderer.outline(drawContext.drawContext(), bounds, drawContext.applyAlpha(border));

        super.render(drawContext);
    }
}
