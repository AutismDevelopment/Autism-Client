package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.direct.DirectUiButton;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
public final class CompactTheme {

    private static int rc(int color, Channel channel) {
        return AutismTheme.recolor(color, channel);
    }

    public static final float DEFAULT_DENSITY = 2.0f;
    private static final int BODY_FONT_HEIGHT = 11;
    private static final int LABEL_FONT_HEIGHT = 12;
    private static final int TITLE_FONT_HEIGHT = 13;
    private static final int HEADER_HEIGHT = 16;
    private static final int CONTENT_PADDING = 4;
    private static final int ROW_GAP = 2;
    private static final int BUTTON_HEIGHT = 14;
    private static final int LABEL_GAP = 1;

    private final int windowRadius = 0;

    public static int lessTransparent30(int color) {
        return boostAlpha(color, 13, 10);
    }

    private static int boostAlpha(int color, int numerator, int denominator) {
        int alpha = (color >>> 24) & 0xFF;
        int boosted = Math.min(255, (alpha * numerator) / denominator);
        return (boosted << 24) | (color & 0x00FFFFFF);
    }

    public net.minecraft.resources.Identifier fontFor(UiTone tone) {
        return switch (tone) {
            case TITLE -> UiAssets.FONT_TITLE;
            case LABEL -> UiAssets.FONT_LABEL;
            case BODY, MUTED, ACCENT -> UiAssets.FONT_BODY;
        };
    }

    public int windowRadius() {
        return windowRadius;
    }

    public int headerHeight() {
        return Math.max(HEADER_HEIGHT, lineHeight(UiTone.LABEL, 3));
    }

    public int contentPadding() {
        return CONTENT_PADDING;
    }

    public int rowGap() {
        return ROW_GAP;
    }

    public int buttonHeight() {
        return Math.max(BUTTON_HEIGHT, lineHeight(UiTone.BODY, 3));
    }

    public int labelGap() {
        return LABEL_GAP;
    }

    public float scaleFactor() {
        return 1.0f;
    }

    public int scale(int value) {
        return value;
    }

    public float scale(float value) {
        return value;
    }

    public DirectUiInsets scale(DirectUiInsets insets) {
        if (insets == null) return DirectUiInsets.NONE;
        return new DirectUiInsets(
            scale(insets.left()),
            scale(insets.top()),
            scale(insets.right()),
            scale(insets.bottom())
        );
    }

    public int fontHeight(UiTone tone) {
        int vanillaHeight = UiText.fontHeight(fontFor(tone));
        if (vanillaHeight > 0) return vanillaHeight;
        return switch (tone) {
            case TITLE -> TITLE_FONT_HEIGHT;
            case LABEL -> LABEL_FONT_HEIGHT;
            case BODY, MUTED, ACCENT -> BODY_FONT_HEIGHT;
        };
    }

    public int lineHeight(UiTone tone, int extraSpacing) {
        int minimumSpacing = switch (tone) {
            case TITLE -> 3;
            case LABEL -> 2;
            case BODY, MUTED, ACCENT -> 2;
        };
        return fontHeight(tone) + Math.max(scale(extraSpacing), minimumSpacing);
    }

    public int bodyTextNudge() {
        return 1;
    }

    public int buttonTextNudge() {
        return 1;
    }

    public int fieldTextNudge() {
        return 1;
    }

    public int color(UiTone tone) {
        return switch (tone) {
            case TITLE, LABEL, BODY -> rc(0xFFF3ECE7, Channel.TEXT);
            case MUTED -> rc(0xFFB79E9E, Channel.TEXT);
            case ACCENT -> rc(0xFFFF4D4D, Channel.ACCENT);
        };
    }

    public int overlaySurface(int rgb) {
        return 0xA8000000 | (rgb & 0x00FFFFFF);
    }

    public int overlaySurfaceSoft(int rgb) {
        return 0xA0000000 | (rgb & 0x00FFFFFF);
    }

    public int overlaySurfaceStrong(int rgb) {
        return 0xAE000000 | (rgb & 0x00FFFFFF);
    }

    public int windowFill() {
        return overlaySurface(0x000A0A0C);
    }

    public int windowFillInactive() {
        return overlaySurfaceSoft(0x00070709);
    }

    public int headerFill() {
        return overlaySurface(0x00111114);
    }

    public int headerFillInactive() {
        return overlaySurfaceSoft(0x000A0A0C);
    }

    public int headerAccent() {
        return rc(0xFFFF3B3B, Channel.HEADER);
    }

    public int borderColor() {
        return rc(0xFFD24848, Channel.OUTLINE);
    }

    public int borderSoft() {
        return rc(lessTransparent30(0xC0993A3A), Channel.OUTLINE);
    }

    public int hoverFill() {
        return rc(0x661E0E10, Channel.HOVER);
    }

    public int dangerFill() {
        return rc(overlaySurface(0x002A0E12), Channel.DANGER);
    }

    public int dangerBorder() {
        return rc(0xFFFF7D7D, Channel.DANGER);
    }

    public int buttonFill(DirectUiButton.Variant variant) {
        return switch (variant) {
            case PRIMARY   -> rc(overlaySurface(0x008F1F24), Channel.BUTTON);
            case SECONDARY -> rc(overlaySurface(0x00120E11), Channel.BUTTON);
            case GHOST     -> rc(overlaySurfaceSoft(0x00120D0F), Channel.BUTTON);
            case DANGER    -> rc(overlaySurface(0x00230D11), Channel.DANGER);
            case SUCCESS   -> rc(overlaySurface(0x0010261A), Channel.SUCCESS);
        };
    }

    public int buttonFill(DirectUiButton.Variant variant, boolean active) {
        if (!active) return buttonFillInactive(variant);
        return buttonFill(variant);
    }

    private int buttonFillInactive(DirectUiButton.Variant variant) {
        return switch (variant) {
            case PRIMARY   -> rc(overlaySurfaceSoft(0x008F1F24), Channel.BUTTON);
            case SECONDARY -> overlaySurfaceSoft(0x00121316);
            case GHOST     -> 0x70121316;
            case DANGER    -> overlaySurfaceSoft(0x00121316);
            case SUCCESS   -> overlaySurfaceSoft(0x00121316);
        };
    }

    public int buttonBorder(DirectUiButton.Variant variant) {
        return switch (variant) {
            case PRIMARY        -> rc(0xFFFF8787, Channel.OUTLINE);
            case SECONDARY, GHOST -> borderSoft();
            case DANGER        -> rc(0xFFB54848, Channel.DANGER);
            case SUCCESS       -> rc(0xFF4FD986, Channel.SUCCESS);
        };
    }

    public int buttonBorderInactive(DirectUiButton.Variant variant) {
        return switch (variant) {
            case PRIMARY        -> rc(0xFFFF8787, Channel.OUTLINE);
            case SECONDARY, GHOST -> rc(0xFF6B5050, Channel.OUTLINE);
            case DANGER        -> rc(0xFFB54848, Channel.DANGER);
            case SUCCESS       -> rc(0xFF4FD986, Channel.SUCCESS);
        };
    }

    public int buttonBorderGlow(DirectUiButton.Variant variant) {
        return switch (variant) {
            case PRIMARY        -> rc(0xFFFFA4A4, Channel.HOVER);
            case SECONDARY, GHOST -> rc(0xFFFF6464, Channel.HOVER);
            case DANGER        -> rc(0xFFFF9090, Channel.DANGER);
            case SUCCESS       -> rc(0xFFAAEBC0, Channel.SUCCESS);
        };
    }

    public int buttonTextColor(DirectUiButton.Variant variant) {
        return switch (variant) {
            case PRIMARY        -> rc(0xFFFFF4F4, Channel.TEXT);
            case SECONDARY, GHOST -> color(UiTone.BODY);
            case DANGER        -> rc(0xFFFF5A5A, Channel.DANGER);
            case SUCCESS       -> rc(0xFFB8F3CB, Channel.SUCCESS);
        };
    }

    public int buttonTextColorInactive(DirectUiButton.Variant variant) {
        return switch (variant) {
            case PRIMARY        -> rc(0xFFFFF4F4, Channel.TEXT);
            case SECONDARY, GHOST -> color(UiTone.MUTED);
            case DANGER        -> rc(0xFF9C7B7B, Channel.DANGER);
            case SUCCESS       -> rc(0xFF88A690, Channel.SUCCESS);
        };
    }

    public int overlayButtonFill(CompactOverlayButton.Variant variant, boolean active) {
        if (variant == CompactOverlayButton.Variant.FILTER_ON) return 0x553D6BFF;
        if (variant == CompactOverlayButton.Variant.FILTER_OFF) return 0x66272B31;
        return buttonFill(toButtonVariant(variant), active);
    }

    private int overlayButtonFillInactive(CompactOverlayButton.Variant variant) {
        return buttonFillInactive(toButtonVariant(variant));
    }

    public int overlayButtonBorder(CompactOverlayButton.Variant variant, boolean active) {
        if (variant == CompactOverlayButton.Variant.FILTER_ON) return 0xFF6F8DFF;
        if (variant == CompactOverlayButton.Variant.FILTER_OFF) return 0xFF565D66;
        return active ? buttonBorder(toButtonVariant(variant)) : buttonBorderInactive(toButtonVariant(variant));
    }

    private int overlayButtonBorderInactive(CompactOverlayButton.Variant variant) {
        return buttonBorderInactive(toButtonVariant(variant));
    }

    public int overlayButtonBorderGlow(CompactOverlayButton.Variant variant) {
        if (variant == CompactOverlayButton.Variant.FILTER_ON) return 0xFF91A7FF;
        if (variant == CompactOverlayButton.Variant.FILTER_OFF) return 0xFF747C87;
        return buttonBorderGlow(toButtonVariant(variant));
    }

    public int overlayButtonTextColor(CompactOverlayButton.Variant variant, boolean active) {
        if (variant == CompactOverlayButton.Variant.FILTER_ON) return 0xFFFFFFFF;
        if (variant == CompactOverlayButton.Variant.FILTER_OFF) return 0xFFB8BEC7;
        return active ? buttonTextColor(toButtonVariant(variant)) : buttonTextColorInactive(toButtonVariant(variant));
    }

    private int overlayButtonTextColorInactive(CompactOverlayButton.Variant variant) {
        return buttonTextColorInactive(toButtonVariant(variant));
    }

    private DirectUiButton.Variant toButtonVariant(CompactOverlayButton.Variant variant) {
        return switch (variant) {
            case PRIMARY -> DirectUiButton.Variant.PRIMARY;
            case SECONDARY -> DirectUiButton.Variant.SECONDARY;
            case GHOST -> DirectUiButton.Variant.GHOST;
            case DANGER -> DirectUiButton.Variant.DANGER;
            case SUCCESS -> DirectUiButton.Variant.SUCCESS;
            case FILTER_ON -> DirectUiButton.Variant.PRIMARY;
            case FILTER_OFF -> DirectUiButton.Variant.GHOST;
        };
    }

    public int listFill() {
        return overlaySurface(0x00101014);
    }

    public int listFillFocused() {
        return overlaySurfaceStrong(0x00131418);
    }

    public int rowFillNormal() {
        return 0x26181A1E;
    }

    public int rowFillHovered() {
        return rc(0x4F241A1D, Channel.HOVER);
    }

    public int rowFillSelected() {
        return rc(overlaySurface(0x0023382B), Channel.TOGGLE);
    }

    public int inlineBannerFill() {
        return rc(overlaySurface(0x00121012), Channel.BUTTON);
    }

    public int inactiveCoverFill() {
        return 0x36000000;
    }

    public int inactiveBodyFadeFill() {
        return overlaySurface(0x0009090B);
    }
}
