package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.direct.DirectUiNode;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.UiContexts;

public final class DirectMetricLabel extends DirectUiNode {
    private String key;
    private String value;
    private int keyColor = 0xFFB79E9E;
    private int valueColor = 0xFFF3ECE7;

    public DirectMetricLabel(String key, String value) {
        this.key = key == null ? "" : key;
        this.value = value == null ? "" : value;
        this.height = 11;
    }

    public DirectMetricLabel setValue(String value) {
        String next = value == null ? "" : value;
        if (!this.value.equals(next)) {
            this.value = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectMetricLabel setKeyColor(int keyColor) {
        this.keyColor = keyColor;
        return this;
    }

    public DirectMetricLabel setValueColor(int valueColor) {
        this.valueColor = valueColor;
        return this;
    }

    @Override
    public DirectMetricLabel setGrowX(boolean growX) {
        super.setGrowX(growX);
        return this;
    }

    @Override
    public float preferredWidth(DirectRenderContext context) {
        return UiContexts.textRenderer(context.textRenderer()).width(key + value);
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        return 11.0f;
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;
        var text = UiContexts.textRenderer(context.textRenderer());
        int drawX = Math.round(x);
        int drawY = Math.round(y) + Math.max(1, (Math.round(height) - 9) / 2);
        String safeKey = key == null ? "" : key;
        String safeValue = value == null ? "" : value;
        text.draw(context.drawContext(), safeKey, drawX, drawY, context.applyAlpha(keyColor));
        int keyWidth = text.width(safeKey);
        text.drawFitted(context.drawContext(), safeValue, drawX + keyWidth, drawY,
            Math.max(1, Math.round(width) - keyWidth), context.applyAlpha(valueColor));
    }
}
