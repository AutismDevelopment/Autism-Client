package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.ConnectedButton;

public class DirectCompactRow extends DirectUiContainer {
    private DirectUiInsets padding = DirectUiInsets.NONE;
    private int gap = 0;
    private boolean underlineOnHover = false;
    private int underlineColor = 0xFFFF3B3B;
    private float occupiedWidth = 0.0f;

    public DirectCompactRow setPadding(DirectUiInsets padding) {
        DirectUiInsets next = padding == null ? DirectUiInsets.NONE : padding;
        if (!this.padding.equals(next)) {
            this.padding = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectCompactRow setGap(int gap) {
        int next = Math.max(0, gap);
        if (this.gap != next) {
            this.gap = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectCompactRow setUnderlineOnHover(boolean underlineOnHover) {
        this.underlineOnHover = underlineOnHover;
        return this;
    }

    public DirectCompactRow setUnderlineColor(int underlineColor) {
        this.underlineColor = underlineColor;
        return this;
    }

    @Override
    public float preferredWidth(DirectRenderContext context) {
        DirectUiInsets scaledPadding = context.theme().scale(padding);
        int scaledGap = context.theme().scale(gap);
        float total = scaledPadding.horizontal();
        int visibleCount = 0;
        for (DirectUiNode child : children) {
            if (child == null || !child.isVisible()) continue;
            total += child.preferredWidth(context);
            visibleCount++;
        }
        if (visibleCount > 1) total += (visibleCount - 1) * scaledGap;
        return total;
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        DirectUiInsets scaledPadding = context.theme().scale(padding);
        float tallest = 0.0f;
        for (DirectUiNode child : children) {
            if (child == null || !child.isVisible()) continue;
            tallest = Math.max(tallest, child.preferredHeight(context, availableWidth));
        }
        return scaledPadding.top() + tallest + scaledPadding.bottom();
    }

    @Override
    protected void layoutChildren(DirectRenderContext context) {
        DirectUiInsets scaledPadding = context.theme().scale(padding);
        int scaledGap = context.theme().scale(gap);
        float innerX = x + scaledPadding.left();
        float innerY = y + scaledPadding.top();
        float innerWidth = Math.max(0.0f, width - scaledPadding.horizontal());
        float innerHeight = Math.max(0.0f, height - scaledPadding.vertical());

        float fixedWidth = 0.0f;
        int growCount = 0;
        int visibleCount = 0;
        for (DirectUiNode child : children) {
            if (child == null || !child.isVisible()) continue;
            visibleCount++;
            if (child.growX()) growCount++;
            else fixedWidth += child.preferredWidth(context);
        }

        float totalGap = Math.max(0, visibleCount - 1) * scaledGap;
        float freeWidth = Math.max(0.0f, innerWidth - fixedWidth - totalGap);
        float growWidth = growCount > 0 ? freeWidth / growCount : 0.0f;

        float cursorX = innerX;
        occupiedWidth = 0.0f;
        for (DirectUiNode child : children) {
            if (child == null || !child.isVisible()) continue;
            float childWidth = child.growX() ? growWidth : child.preferredWidth(context);
            float childHeight = Math.min(innerHeight, child.preferredHeight(context, childWidth));
            float childY = innerY + Math.max(0.0f, (innerHeight - childHeight) / 2.0f);
            child.setBounds(cursorX, childY, childWidth, childHeight);
            cursorX += childWidth + scaledGap;
        }
        occupiedWidth = Math.max(0.0f, cursorX - innerX - (visibleCount > 0 ? scaledGap : 0));
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;
        super.render(context);
        drawConnectedButtonSeams(context);
        if (underlineOnHover) {
            float hover = updateHover(contains(context.mouseX(), context.mouseY()), context.delta());
            if (hover <= 0.0f || context.drawContext() == null) return;
            DirectUiInsets scaledPadding = context.theme().scale(padding);
            int drawX = Math.round(x + scaledPadding.left());
            int drawY = Math.round(y + height - context.theme().scale(1));
            float underlineAreaWidth = Math.max(occupiedWidth, width - scaledPadding.horizontal());
            int underlineWidth = Math.max(1, Math.round(underlineAreaWidth * hover));
            int underlineX = drawX + Math.max(0, Math.round((underlineAreaWidth - underlineWidth) / 2.0f));
            context.drawContext().fill(underlineX, drawY, underlineX + underlineWidth, drawY + context.theme().scale(1), autismclient.util.AutismTheme.recolor(underlineColor, autismclient.util.AutismTheme.Channel.ACCENT));
        }
    }

    private void drawConnectedButtonSeams(DirectRenderContext context) {
        if (context.drawContext() == null) return;
        UiContext ui = null;
        DirectUiNode previous = null;
        for (DirectUiNode child : children) {
            if (child == null || !child.isVisible()) continue;
            if (previous instanceof DirectUiButton left
                && child instanceof DirectUiButton right
                && left.isConnectedCell()
                && right.isConnectedCell()
                && Math.abs((left.x() + left.width()) - right.x()) <= 0.5f) {
                if (ui == null) {
                    ui = UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY()));
                }
                int leftColor = left.connectedSeamColor(ui);
                int rightColor = right.connectedSeamColor(ui);
                float leftWeight = left.connectedSeamWeight();
                float rightWeight = right.connectedSeamWeight();
                int seamColor = ConnectedButton.chooseSeamColor(leftColor, leftWeight, rightColor, rightWeight);
                int seamX = Math.round(right.x());
                int seamY = Math.round(Math.max(left.y(), right.y()));
                int seamBottom = Math.round(Math.min(left.y() + left.height(), right.y() + right.height()));
                ConnectedButton.drawVerticalSeam(ui, seamX, seamY, Math.max(0, seamBottom - seamY), seamColor);
            }
            previous = child;
        }
    }

}
