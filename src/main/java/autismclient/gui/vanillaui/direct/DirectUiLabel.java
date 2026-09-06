package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

public class DirectUiLabel extends DirectUiNode {
    private String text;
    private UiTone tone;
    private boolean shadow;
    private boolean trimToBounds;

    public DirectUiLabel(String text, UiTone tone) {
        this.text = text == null ? "" : text;
        this.tone = tone == null ? UiTone.BODY : tone;
        this.height = 10;
    }

    public DirectUiLabel setText(String text) {
        String next = text == null ? "" : text;
        if (!this.text.equals(next)) {
            this.text = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiLabel setTone(UiTone tone) {
        UiTone next = tone == null ? UiTone.BODY : tone;
        if (this.tone != next) {
            this.tone = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiLabel setShadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public DirectUiLabel setTrimToBounds(boolean trimToBounds) {
        this.trimToBounds = trimToBounds;
        return this;
    }

    @Override
    public DirectUiLabel setGrowX(boolean growX) {
        super.setGrowX(growX);
        return this;
    }

    @Override
    public float preferredWidth(DirectRenderContext context) {
        return UiText.width(context.textRenderer(), text, context.theme().fontFor(tone), context.theme().color(tone));
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        return context.theme().lineHeight(tone, 3);
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;
        String displayComponent = text;
        if (trimToBounds && width > 0) {
            displayComponent = UiText.trimToWidth(
                context.textRenderer(),
                text,
                Math.round(width),
                context.theme().fontFor(tone),
                context.theme().color(tone)
            );
        }
        UiText.draw(
            context.drawContext(),
            context.textRenderer(),
            displayComponent,
            context.theme().fontFor(tone),
            context.applyAlpha(context.theme().color(tone)),
            Math.round(x),
            UiSizing.alignTextY(Math.round(y), Math.round(height), context.theme().fontHeight(tone), context.theme().bodyTextNudge()),
            shadow
        );
    }
}
