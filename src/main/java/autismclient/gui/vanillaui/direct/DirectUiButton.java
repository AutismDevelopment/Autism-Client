package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.ConnectedButton;
import net.minecraft.resources.Identifier;

public class DirectUiButton extends DirectUiNode {

    public enum ContentAlignment {
        CENTER,
        START
    }

    public enum Variant {
        PRIMARY,
        SECONDARY,
        GHOST,
        DANGER,
        SUCCESS
    }

    private String text;
    private Variant variant;
    private Runnable onPress;
    private float minWidth = 26.0f;
    private float maxWidth = Float.MAX_VALUE;
    private float preferredWidth = -1.0f;
    private int horizontalPadding = 8;
    private int fixedHeight = -1;
    private int textYOffset = 0;
    private Identifier leadingIcon;
    private int iconSize = 10;
    private int iconGap = 4;
    private ContentAlignment contentAlignment = ContentAlignment.CENTER;
    private UiTone tone = UiTone.BODY;
    private boolean connectedStyle;
    private boolean connectedToggle;
    private ConnectedButton.Edges connectedEdges = ConnectedButton.FULL;
    private float connectedToggleProgress;
    private boolean connectedToggleProgressInitialized;
    private long lastConnectedAnimationNanos = System.nanoTime();

    private Identifier resolvedFont(CompactTheme theme, int height) {
        return theme.fontFor(tone);
    }

    public DirectUiButton(String text, Variant variant, Runnable onPress) {
        this.text = text == null ? "" : text;
        this.variant = variant == null ? Variant.SECONDARY : variant;
        this.onPress = onPress;
        this.height = 16;
    }

    public DirectUiButton setText(String text) {
        String next = text == null ? "" : text;
        if (!this.text.equals(next)) {
            this.text = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiButton setVariant(Variant variant) {
        this.variant = variant == null ? Variant.SECONDARY : variant;
        return this;
    }

    public DirectUiButton setOnPress(Runnable onPress) {
        this.onPress = onPress;
        return this;
    }

    public DirectUiButton setMinWidth(float minWidth) {
        float next = Math.max(0.0f, minWidth);
        if (Float.compare(this.minWidth, next) != 0) {
            this.minWidth = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiButton setMaxWidth(float maxWidth) {
        float next = Math.max(this.minWidth, maxWidth);
        if (Float.compare(this.maxWidth, next) != 0) {
            this.maxWidth = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiButton setPreferredWidth(float preferredWidth) {
        if (Float.compare(this.preferredWidth, preferredWidth) != 0) {
            this.preferredWidth = preferredWidth;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiButton setHorizontalPadding(int horizontalPadding) {
        int next = Math.max(0, horizontalPadding);
        if (this.horizontalPadding != next) {
            this.horizontalPadding = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiButton setButtonHeight(int fixedHeight) {
        int next = Math.max(1, fixedHeight);
        if (this.fixedHeight != next) {
            this.fixedHeight = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiButton setTextYOffset(int textYOffset) {
        this.textYOffset = textYOffset;
        return this;
    }

    public DirectUiButton setLeadingIcon(Identifier leadingIcon) {
        if (!java.util.Objects.equals(this.leadingIcon, leadingIcon)) {
            this.leadingIcon = leadingIcon;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiButton setIconSize(int iconSize) {
        int next = Math.max(1, iconSize);
        if (this.iconSize != next) {
            this.iconSize = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiButton setIconGap(int iconGap) {
        int next = Math.max(0, iconGap);
        if (this.iconGap != next) {
            this.iconGap = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiButton setContentAlignment(ContentAlignment contentAlignment) {
        this.contentAlignment = contentAlignment == null ? ContentAlignment.CENTER : contentAlignment;
        return this;
    }

    public DirectUiButton setTone(UiTone tone) {
        UiTone next = tone == null ? UiTone.BODY : tone;
        if (this.tone != next) {
            this.tone = next;
            markLayoutDirty();
        }
        return this;
    }

    public DirectUiButton setConnectedStyle(boolean connectedStyle) {
        this.connectedStyle = connectedStyle;
        return this;
    }

    public DirectUiButton setConnectedToggle(boolean connectedToggle) {
        this.connectedToggle = connectedToggle;
        if (connectedToggle) {
            this.connectedStyle = true;
        }
        return this;
    }

    public DirectUiButton setConnectedEdges(ConnectedButton.Edges connectedEdges) {
        this.connectedEdges = connectedEdges == null ? ConnectedButton.LEFT_CELL : connectedEdges;
        return this;
    }

    @Override
    public DirectUiButton setGrowX(boolean growX) {
        super.setGrowX(growX);
        return this;
    }

    @Override
    public float preferredWidth(DirectRenderContext context) {
        int measureHeight = fixedHeight > 0 ? fixedHeight : context.theme().buttonHeight();
        Identifier font = resolvedFont(context.theme(), measureHeight);
        int scaledHorizontalPadding = context.theme().scale(horizontalPadding);
        int scaledIconSize = context.theme().scale(iconSize);
        int scaledIconGap = context.theme().scale(iconGap);
        float fittedWidth = UiSizing.fitTextWidth(
            context.textRenderer(),
            text,
            font,
            context.theme().color(UiTone.BODY),
            scaledHorizontalPadding,
            minWidth,
            maxWidth
        );
        if (leadingIcon != null) {
            fittedWidth += scaledIconSize + scaledIconGap;
        }
        if (preferredWidth > 0.0f) {
            return UiSizing.clamp(Math.max(preferredWidth, fittedWidth), minWidth, maxWidth);
        }
        return UiSizing.clamp(fittedWidth, minWidth, maxWidth);
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        int resolvedFixedHeight = fixedHeight > 0 ? fixedHeight : context.theme().buttonHeight();
        int contentHeight = context.theme().fontHeight(tone) + context.theme().scale(4);
        return Math.max(12, Math.max(resolvedFixedHeight, contentHeight));
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;
        int drawX = Math.round(x);
        int drawY = Math.round(y);
        int drawW = Math.round(width);
        int drawH = Math.round(height);
        boolean hovered = enabled && contains(context.mouseX(), context.mouseY());
        if (connectedStyle) {
            renderConnected(context, drawX, drawY, drawW, drawH, hovered);
            return;
        }
        renderRetained(context, drawX, drawY, drawW, drawH, hovered);
    }

    private void renderRetained(DirectRenderContext context, int drawX, int drawY, int drawW, int drawH, boolean hovered) {
        UiContext ui = UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY()));
        UiBounds bounds = UiBounds.of(drawX, drawY, drawW, drawH);
        Button.Tone buttonTone = buttonTone();
        if (leadingIcon == null) {
            Button.render(ui, bounds, text, buttonTone, hovered, false);
        } else {
            Button.renderIcon(ui, bounds, text, leadingIcon, buttonTone, hovered, false, 0.0F);
        }
        if (!enabled) {
            UiRenderer.rect(context.drawContext(), bounds.inset(1), 0x66000000);
        }
    }

    private void renderConnected(DirectRenderContext context, int drawX, int drawY, int drawW, int drawH, boolean hovered) {
        UiContext ui = UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY()));
        UiBounds bounds = UiBounds.of(drawX, drawY, drawW, drawH);
        if (connectedToggle) {
            float target = variant == Variant.SUCCESS ? 1.0f : 0.0f;
            if (!connectedToggleProgressInitialized) {
                connectedToggleProgress = target;
                connectedToggleProgressInitialized = true;
            } else {
                connectedToggleProgress = animateConnectedProgress(connectedToggleProgress, target);
            }
            ConnectedButton.renderToggle(ui, bounds, text, leadingIcon, hovered, connectedToggleProgress, connectedEdges);
            return;
        }
        ConnectedButton.renderAction(ui, bounds, text, leadingIcon, buttonTone(), hovered, connectedEdges);
    }

    boolean isConnectedCell() {
        return connectedStyle;
    }

    int connectedSeamColor(UiContext ui) {
        if (connectedToggle) return ConnectedButton.toggleBorderColor(ui, connectedToggleProgress);
        return ConnectedButton.toneBorderColor(ui, buttonTone());
    }

    float connectedSeamWeight() {
        return ConnectedButton.seamWeight(buttonTone(), connectedToggle, connectedToggle ? connectedToggleProgress : 0.0f);
    }

    private Button.Tone buttonTone() {
        return toneFor(variant);
    }

    public static Button.Tone toneFor(Variant variant) {
        return switch (variant) {
            case PRIMARY -> Button.Tone.PRIMARY;
            case SUCCESS -> Button.Tone.SUCCESS;
            case DANGER -> Button.Tone.DANGER;
            case SECONDARY, GHOST -> Button.Tone.NORMAL;
        };
    }

    private float animateConnectedProgress(float current, float target) {
        long now = System.nanoTime();
        float delta = Math.max(0.0f, Math.min(0.05f, (now - lastConnectedAnimationNanos) / 1_000_000_000.0f));
        lastConnectedAnimationNanos = now;
        float step = delta / 0.16f;
        if (current < target) return Math.min(target, current + step);
        if (current > target) return Math.max(target, current - step);
        return current;
    }

    @Override
    public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
        if (!enabled || !contains(mouseX, mouseY) || button != 0) return false;
        if (onPress != null) onPress.run();
        return true;
    }
}
