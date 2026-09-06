package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class CompactOverlayButton {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int HORIZONTAL_PADDING = 4;

    public enum Variant {
        SECONDARY,
        DANGER,
        SUCCESS,
        PRIMARY,
        GHOST,
        FILTER_ON,
        FILTER_OFF
    }

    public boolean active = true;
    public boolean visible = true;

    private int x;
    private int y;
    private int width;
    private int height;
    private Component message;
    private Variant variant = Variant.SECONDARY;
    private UiTone tone = UiTone.BODY;
    private Boolean toggleState;
    private String animationKey;
    private Identifier icon;
    private Boolean selected;
    private final PressAction primaryAction;
    private final PressAction secondaryAction;

    private static Identifier resolvedFont(int height) {
        return THEME.fontFor(UiTone.BODY);
    }

    private static int fittedWidth(int requestedWidth, int height, Component message) {
        if (requestedWidth > 0) return requestedWidth;
        Minecraft client = Minecraft.getInstance();
        Font renderer = client == null ? null : client.font;
        if (renderer == null) return requestedWidth;

        String label = AutismText.sanitizeUiLabel(message == null ? "" : message.getString());
        Identifier font = resolvedFont(height);
        int measured = UiSizing.fitTextWidthInt(
            renderer,
            label,
            font,
            THEME.color(UiTone.BODY),
            HORIZONTAL_PADDING,
            requestedWidth,
            Integer.MAX_VALUE
        );
        return Math.max(requestedWidth, measured);
    }

    private CompactOverlayButton(int x, int y, int width, int height, Component message, PressAction primaryAction, PressAction secondaryAction) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.message = message == null ? Component.empty() : message;
        this.primaryAction = primaryAction;
        this.secondaryAction = secondaryAction;
    }

    public static CompactOverlayButton create(int x, int y, int width, int height, Component message, PressAction pressAction) {
        return new CompactOverlayButton(x, y, fittedWidth(width, height, message), height, message, pressAction, null);
    }

    public static CompactOverlayButton create(int x, int y, int width, int height, Component message, PressAction pressAction, PressAction secondaryPressAction) {
        return new CompactOverlayButton(x, y, fittedWidth(width, height, message), height, message, pressAction, secondaryPressAction);
    }

    public CompactOverlayButton setTone(UiTone tone) {
        this.tone = tone == null ? UiTone.BODY : tone;
        return this;
    }

    public CompactOverlayButton setToggleState(boolean toggleState) {
        this.toggleState = toggleState;
        return this;
    }

    public CompactOverlayButton setAnimationKey(String animationKey) {
        this.animationKey = animationKey;
        return this;
    }

    public CompactOverlayButton setIcon(Identifier icon) {
        this.icon = icon;
        return this;
    }

    public CompactOverlayButton setSelected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public static boolean fireIfHit(CompactOverlayButton button, double mouseX, double mouseY, int mouseButton) {
        if (button == null || !button.visible || !button.active) return false;
        if ((mouseButton != 0 && mouseButton != 1) || !button.contains(mouseX, mouseY)) return false;
        PressAction action = mouseButton == 1 ? button.secondaryAction : button.primaryAction;
        if (action == null) return false;
        action.onPress(button);
        return true;
    }

    public static void renderStyled(GuiGraphicsExtractor context, Font textRenderer, CompactOverlayButton button, int mouseX, int mouseY) {
        if (button == null || !button.visible) return;

        if (button.icon != null) {
            boolean iconHovered = button.contains(mouseX, mouseY);
            CompactSymbolButton.renderIcon(context, textRenderer,
                UiBounds.of(button.x, button.y, button.width, button.height),
                button.icon, iconHovered, button.active, button.variant == Variant.DANGER);
            return;
        }

        String label = AutismText.sanitizeUiLabel(button.message.getString());
        String upperLabel = label.toUpperCase();
        boolean inferredDanger = "x".equalsIgnoreCase(label) || label.toLowerCase().contains("delete") || label.toLowerCase().contains("clear");
        boolean toggleOn = label.startsWith("[x]") || label.startsWith("[X]") || label.startsWith("[\u2713]")
            || upperLabel.equals("ON") || upperLabel.endsWith(": ON")
            || upperLabel.equals("MODE: ENABLE") || upperLabel.equals("ENABLE")
            || upperLabel.equals("ENABLED") || upperLabel.endsWith(": ENABLED");
        boolean toggleOff = label.startsWith("[ ]")
            || upperLabel.equals("OFF") || upperLabel.endsWith(": OFF")
            || upperLabel.equals("MODE: DISABLE") || upperLabel.equals("DISABLE")
            || upperLabel.equals("DISABLED") || upperLabel.endsWith(": DISABLED");
        Variant variant = button.variant;
        if (variant == Variant.SECONDARY && inferredDanger) variant = Variant.DANGER;
        if (variant == Variant.SECONDARY && toggleOn) variant = Variant.SUCCESS;
        if (variant == Variant.SECONDARY && toggleOff) variant = Variant.DANGER;
        boolean hovered = button.contains(mouseX, mouseY);
        UiContext ui = UiContexts.overlay(context, textRenderer, mouseX, mouseY);
        UiBounds bounds = UiBounds.of(button.x, button.y, button.width, button.height);
        boolean allowInferredToggle = button.variant != Variant.FILTER_ON && button.variant != Variant.FILTER_OFF;
        Boolean resolvedToggleState = button.toggleState != null
            ? button.toggleState
            : allowInferredToggle ? toggleOn ? Boolean.TRUE : toggleOff ? Boolean.FALSE : null : null;
        if (resolvedToggleState != null) {
            AnimatedToggleButton.render(
                ui,
                bounds,
                label,
                resolvedToggleState,
                hovered && button.active,
                button.animationKey == null ? stableVisualKey(button) : button.animationKey
            );
        } else {

            boolean on = button.selected != null
                ? button.selected
                : switch (variant) {
                    case SUCCESS, DANGER, PRIMARY, FILTER_ON -> true;
                    default -> false;
                };
            Button.render(ui, bounds, label, buttonTone(variant), hovered && button.active, on);
        }

        if (!button.active && !Boolean.TRUE.equals(button.selected)) {
            UiRenderer.rect(context, bounds.inset(1), 0x66000000);
            UiRenderer.outline(context, bounds, autismclient.util.AutismTheme.recolor(0x88402A30, autismclient.util.AutismTheme.Channel.OUTLINE));
        }
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public Component getMessage() {
        return message;
    }

    public void setMessage(Component message) {
        this.message = message == null ? Component.empty() : message;
    }

    public Variant getVariant() {
        return variant;
    }

    public CompactOverlayButton setVariant(Variant variant) {
        this.variant = variant == null ? Variant.SECONDARY : variant;
        return this;
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static Button.Tone buttonTone(Variant variant) {
        return switch (variant) {
            case PRIMARY -> Button.Tone.PRIMARY;
            case SUCCESS, FILTER_ON -> Button.Tone.SUCCESS;
            case DANGER -> Button.Tone.DANGER;
            case SECONDARY, GHOST, FILTER_OFF -> Button.Tone.NORMAL;
        };
    }

    private static String stableVisualKey(CompactOverlayButton button) {
        return "overlay-toggle:"
            + button.x + ':'
            + button.y + ':'
            + button.width + ':'
            + button.height + ':'
            + button.tone;
    }

    @FunctionalInterface
    public interface PressAction {
        void onPress(CompactOverlayButton button);
    }
}
