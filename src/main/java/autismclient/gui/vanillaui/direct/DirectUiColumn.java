package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

public class DirectUiColumn extends DirectUiContainer {
    private DirectUiInsets padding = DirectUiInsets.NONE;
    private int gap = 0;

    public DirectUiColumn setPadding(DirectUiInsets padding) {
        DirectUiInsets next = padding == null ? DirectUiInsets.NONE : padding;
        if (!this.padding.equals(next)) {
            this.padding = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiColumn setGap(int gap) {
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
        int scaledGap = context.theme().scale(gap);
        float innerWidth = Math.max(0, availableWidth - scaledPadding.horizontal());
        float total = scaledPadding.top() + scaledPadding.bottom();
        boolean first = true;
        for (DirectUiNode child : children) {
            if (child == null || !child.isVisible()) continue;
            if (!first) total += scaledGap;
            total += child.preferredHeight(context, innerWidth);
            first = false;
        }
        return total;
    }

    @Override
    protected void layoutChildren(DirectRenderContext context) {
        DirectUiInsets scaledPadding = context.theme().scale(padding);
        int scaledGap = context.theme().scale(gap);
        float innerX = x + scaledPadding.left();
        float cursorY = y + scaledPadding.top();
        float innerWidth = Math.max(0, width - scaledPadding.horizontal());
        for (DirectUiNode child : children) {
            if (child == null || !child.isVisible()) continue;
            float childHeight = child.preferredHeight(context, innerWidth);
            child.setBounds(innerX, cursorY, innerWidth, childHeight);
            cursorY += childHeight + scaledGap;
        }
    }
}
