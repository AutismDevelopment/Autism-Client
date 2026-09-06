package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.direct.DirectUiContainer;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.gui.vanillaui.direct.DirectUiNode;
import autismclient.gui.vanillaui.direct.DirectRenderContext;

public class DirectFormRow extends DirectUiContainer {
    private final DirectUiNode labelNode;
    private final DirectUiNode controlNode;
    private DirectUiInsets padding = DirectUiInsets.NONE;
    private int gap = 0;
    private float labelWidth = -1.0f;
    private boolean alignControlEnd = false;

    public DirectFormRow(DirectUiNode labelNode, DirectUiNode controlNode) {
        this.labelNode = add(labelNode);
        this.controlNode = add(controlNode);
    }

    public DirectFormRow setPadding(DirectUiInsets padding) {
        DirectUiInsets next = padding == null ? DirectUiInsets.NONE : padding;
        if (!this.padding.equals(next)) {
            this.padding = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectFormRow setGap(int gap) {
        int next = Math.max(0, gap);
        if (this.gap != next) {
            this.gap = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectFormRow setLabelWidth(float labelWidth) {
        float next = Math.max(0.0f, labelWidth);
        if (Float.compare(this.labelWidth, next) != 0) {
            this.labelWidth = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectFormRow setAlignControlEnd(boolean alignControlEnd) {
        if (this.alignControlEnd != alignControlEnd) {
            this.alignControlEnd = alignControlEnd;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiNode labelNode() {
        return labelNode;
    }

    public DirectUiNode controlNode() {
        return controlNode;
    }

    private float resolvedLabelWidth(DirectRenderContext context) {
        if (labelWidth > 0.0f) return labelWidth;
        return labelNode == null ? 0.0f : labelNode.preferredWidth(context);
    }

    @Override
    public float preferredWidth(DirectRenderContext context) {
        DirectUiInsets scaledPadding = context.theme().scale(padding);
        int scaledGap = context.theme().scale(gap);
        float total = scaledPadding.horizontal();
        total += resolvedLabelWidth(context);
        if (labelNode != null && labelNode.isVisible() && controlNode != null && controlNode.isVisible()) total += scaledGap;
        if (controlNode != null && controlNode.isVisible()) total += controlNode.preferredWidth(context);
        return total;
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        DirectUiInsets scaledPadding = context.theme().scale(padding);
        float labelHeight = labelNode != null && labelNode.isVisible() ? labelNode.preferredHeight(context, availableWidth) : 0.0f;
        float controlHeight = controlNode != null && controlNode.isVisible() ? controlNode.preferredHeight(context, availableWidth) : 0.0f;
        return scaledPadding.top() + Math.max(labelHeight, controlHeight) + scaledPadding.bottom();
    }

    @Override
    protected void layoutChildren(DirectRenderContext context) {
        DirectUiInsets scaledPadding = context.theme().scale(padding);
        int scaledGap = context.theme().scale(gap);
        float innerX = x + scaledPadding.left();
        float innerY = y + scaledPadding.top();
        float innerWidth = Math.max(0.0f, width - scaledPadding.horizontal());
        float innerHeight = Math.max(0.0f, height - scaledPadding.vertical());

        float resolvedLabelWidth = Math.min(resolvedLabelWidth(context), innerWidth);
        float controlX = innerX;

        if (labelNode != null && labelNode.isVisible()) {
            float labelHeight = Math.min(innerHeight, labelNode.preferredHeight(context, resolvedLabelWidth));
            float labelY = innerY + Math.max(0.0f, (innerHeight - labelHeight) / 2.0f);
            labelNode.setBounds(innerX, labelY, resolvedLabelWidth, labelHeight);
            controlX = innerX + resolvedLabelWidth + (controlNode != null && controlNode.isVisible() ? scaledGap : 0);
        }

        if (controlNode != null && controlNode.isVisible()) {
            float controlWidth = Math.max(0.0f, innerX + innerWidth - controlX);
            if (!controlNode.growX()) {
                controlWidth = Math.min(controlWidth, controlNode.preferredWidth(context));
            }
            if (alignControlEnd && !controlNode.growX()) {
                controlX = Math.max(controlX, innerX + innerWidth - controlWidth);
            }
            float controlHeight = Math.min(innerHeight, controlNode.preferredHeight(context, controlWidth));
            float controlY = innerY + Math.max(0.0f, (innerHeight - controlHeight) / 2.0f);
            controlNode.setBounds(controlX, controlY, controlWidth, controlHeight);
        }
    }
}
