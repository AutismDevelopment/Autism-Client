package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.direct.DirectUiContainer;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.gui.vanillaui.direct.DirectUiNode;
import autismclient.gui.vanillaui.direct.DirectRenderContext;

public class DirectRow extends DirectUiContainer {
    private DirectUiInsets padding = DirectUiInsets.NONE;
    private int gap = 0;

    public DirectRow setPadding(DirectUiInsets padding) {
        DirectUiInsets next = padding == null ? DirectUiInsets.NONE : padding;
        if (!this.padding.equals(next)) {
            this.padding = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectRow setGap(int gap) {
        int next = Math.max(0, gap);
        if (this.gap != next) {
            this.gap = next;
            markLayoutDirty();
        }
        return this;
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        DirectUiInsets scaledPadding = context.theme().scale(padding);
        float tallest = 0;
        float innerWidth = Math.max(0, availableWidth - scaledPadding.horizontal());
        for (DirectUiNode child : children) {
            if (child == null || !child.isVisible()) continue;
            tallest = Math.max(tallest, child.preferredHeight(context, innerWidth));
        }
        return scaledPadding.top() + tallest + scaledPadding.bottom();
    }

    @Override
    protected void layoutChildren(DirectRenderContext context) {
        DirectUiInsets scaledPadding = context.theme().scale(padding);
        int scaledGap = context.theme().scale(gap);
        float innerX = x + scaledPadding.left();
        float innerY = y + scaledPadding.top();
        float innerWidth = Math.max(0, width - scaledPadding.horizontal());
        float innerHeight = Math.max(0, height - scaledPadding.vertical());

        float fixedWidth = 0;
        int growCount = 0;
        int visibleCount = 0;
        for (DirectUiNode child : children) {
            if (child == null || !child.isVisible()) continue;
            visibleCount++;
            if (child.growX()) growCount++;
            else fixedWidth += child.preferredWidth(context);
        }

        float totalGap = Math.max(0, visibleCount - 1) * scaledGap;
        float freeWidth = Math.max(0, innerWidth - fixedWidth - totalGap);
        float growWidth = growCount > 0 ? freeWidth / growCount : 0;

        float cursorX = innerX;
        for (DirectUiNode child : children) {
            if (child == null || !child.isVisible()) continue;
            float childWidth = child.growX() ? growWidth : child.preferredWidth(context);
            float childHeight = Math.max(innerHeight, child.preferredHeight(context, childWidth));
            child.setBounds(cursorX, innerY, childWidth, childHeight);
            cursorX += childWidth + scaledGap;
        }
    }
}
