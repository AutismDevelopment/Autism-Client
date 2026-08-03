package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.direct.DirectUiNode;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;

public class DirectInfoRow extends DirectUiNode {
    private String label;
    private String value;
    private float labelWidth = 76.0f;
    private UiTone labelTone = UiTone.MUTED;
    private UiTone valueTone = UiTone.BODY;
    private Integer valueColorOverride;
    private int textYOffset = 0;
    private Runnable onPress;

    public DirectInfoRow(String label, String value) {
        this.label = label == null ? "" : label;
        this.value = value == null ? "--" : value;
        this.height = 12;
    }

    public DirectInfoRow setLabel(String label) {
        this.label = label == null ? "" : label;
        return this;
    }

    public DirectInfoRow setValue(String value) {
        this.value = value == null ? "--" : value;
        return this;
    }

    public DirectInfoRow setLabelWidth(float labelWidth) {
        this.labelWidth = Math.max(24.0f, labelWidth);
        return this;
    }

    public DirectInfoRow setLabelTone(UiTone labelTone) {
        this.labelTone = labelTone == null ? UiTone.MUTED : labelTone;
        return this;
    }

    public DirectInfoRow setValueTone(UiTone valueTone) {
        this.valueTone = valueTone == null ? UiTone.BODY : valueTone;
        return this;
    }

    public DirectInfoRow setValueColorOverride(Integer valueColorOverride) {
        this.valueColorOverride = valueColorOverride;
        return this;
    }

    public DirectInfoRow setTextYOffset(int textYOffset) {
        this.textYOffset = textYOffset;
        return this;
    }

    public DirectInfoRow setOnPress(Runnable onPress) {
        this.onPress = onPress;
        return this;
    }

    private boolean isClickable() {
        return onPress != null && enabled;
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        int baselineHeight = 12;
        int labelHeight = context.theme().fontHeight(labelTone) + context.theme().scale(3);
        int valueHeight = context.theme().fontHeight(valueTone) + context.theme().scale(3);
        return Math.max(baselineHeight, Math.max(labelHeight, valueHeight));
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;
        int drawX = Math.round(x);
        int drawY = Math.round(y);
        int drawW = Math.round(width);
        int drawH = Math.round(height);
        int minSegment = context.theme().scale(24);
        int labelW = Math.round(Math.min(labelWidth, Math.max(minSegment, drawW - minSegment)));
        int valueX = drawX + labelW;
        int valueW = Math.max(1, drawW - labelW);
        boolean clickable = isClickable();
        boolean hovered = clickable && contains(context.mouseX(), context.mouseY());
        if (hovered) {
            context.drawContext().fill(drawX, drawY, drawX + drawW, drawY + drawH, context.applyAlpha(autismclient.util.AutismTheme.recolor(0x14351B1F, autismclient.util.AutismTheme.Channel.ACCENT)));
        }
        int labelColor = clickable && hovered
            ? UiSizing.lerpColor(context.theme().color(labelTone), autismclient.util.AutismTheme.recolor(0xFFF3ECE7, autismclient.util.AutismTheme.Channel.TEXT), 0.35f)
            : context.theme().color(labelTone);
        int valueColor = valueColorOverride != null ? valueColorOverride : context.theme().color(valueTone);
        if (clickable && hovered) {
            valueColor = UiSizing.lerpColor(valueColor, autismclient.util.AutismTheme.recolor(0xFFFFF6F6, autismclient.util.AutismTheme.Channel.TEXT), 0.28f);
        }
        int labelTextY = UiSizing.alignTextY(drawY, drawH, context.theme().fontHeight(labelTone), context.theme().bodyTextNudge() + textYOffset);
        int valueTextY = UiSizing.alignTextY(drawY, drawH, context.theme().fontHeight(valueTone), context.theme().bodyTextNudge() + textYOffset);

        String displayLabel = UiText.trimToWidth(
            context.textRenderer(),
            label,
            Math.max(1, labelW - context.theme().scale(4)),
            context.theme().fontFor(labelTone),
            labelColor
        );
        String displayValue = UiText.trimToWidth(
            context.textRenderer(),
            value,
            Math.max(1, valueW - context.theme().scale(2)),
            context.theme().fontFor(valueTone),
            valueColor
        );

        UiText.draw(context.drawContext(), context.textRenderer(), displayLabel, context.theme().fontFor(labelTone), context.applyAlpha(labelColor), drawX, labelTextY, false);
        UiText.draw(context.drawContext(), context.textRenderer(), displayValue, context.theme().fontFor(valueTone), context.applyAlpha(valueColor), valueX, valueTextY, false);
    }

    @Override
    public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
        if (button != 0 || !isClickable() || !contains(mouseX, mouseY)) return false;
        onPress.run();
        return true;
    }
}
