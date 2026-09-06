package autismclient.util;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismTheme.Channel;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class AutismColors {
    private static final CompactTheme PACK_UI_THEME = new CompactTheme();
    private static final int ACCENT = 0xFFB24848;
    private static final int ACCENT_SOFT = 0x66B24848;
    private static final int SURFACE = PACK_UI_THEME.windowFill();
    private static final int SURFACE_ALT = PACK_UI_THEME.listFill();
    private static final int SURFACE_HOVER = PACK_UI_THEME.overlaySurface(0x00201A1D);
    private static final int HEADER = PACK_UI_THEME.headerFill();
    private static final int HEADER_ACTIVE = PACK_UI_THEME.headerFill();
    private static final int DIVIDER = 0x663B2427;
    private static final int OUTER = PACK_UI_THEME.windowFillInactive();
    private static final int BORDER = PACK_UI_THEME.borderSoft();
    private static final int TEXT_PRIMARY = 0xFFF3EDEE;
    private static final int TEXT_SECONDARY = 0xFFCCB6B8;
    private static final int TEXT_MUTED = 0xFF9E8588;
    private static final int DANGER = 0xFFE26A6A;
    private static final int DANGER_BG = PACK_UI_THEME.dangerFill();
    private static final int SUCCESS = 0xFF57DD88;
    private static final int SUCCESS_BG = PACK_UI_THEME.overlaySurface(0x0020342A);
    private static final int TOOLTIP = PACK_UI_THEME.overlaySurface(0x0008090C);
    private static final int PACKET_GREEN = 0xFF7CE39D;
    private static final int PACKET_PINK = 0xFFFF92D4;
    private static final int PACKET_ORANGE = 0xFFFFB56D;
    private static final int PACKET_BLUE = 0xFF80B9FF;
    private static final int LOADING_BG = 0xFF000000;
    private static final int PACKET_YELLOW = 0xFFFFD86A;
    private static final int PACKET_CYAN = 0xFF74E8FF;
    private static final int PACKET_WHITE = 0xFFF8F4EE;
    private static final int PACKET_GRAY = 0xFFB0B8C2;
    private static final int PACKET_LIGHT_YELLOW = 0xFFFFF0AF;

    public static int lessTransparent30(int color) { return boostAlpha(color, 11, 10); }
    public static int lessTransparent60(int color) { return boostAlpha(color, 13, 10); }

    private static int boostAlpha(int color, int numerator, int denominator) {
        int alpha = (color >>> 24) & 0xFF;
        int boosted = Math.min(255, (alpha * numerator) / denominator);
        return (boosted << 24) | (color & 0x00FFFFFF);
    }

    public static int accent() { return AutismTheme.recolor(ACCENT, Channel.ACCENT); }
    public static int accentSoft() { return AutismTheme.recolor(ACCENT_SOFT, Channel.ACCENT); }
    public static int primary() { return AutismTheme.recolor(ACCENT, Channel.ACCENT); }
    public static int primaryDark() { return AutismTheme.recolor(0xFF8D3838, Channel.ACCENT); }
    public static int secondary() { return SURFACE_ALT; }
    public static int background() { return SURFACE; }
    public static int border() { return PACK_UI_THEME.borderSoft(); }
    public static int windowOuter() { return OUTER; }
    public static int windowGlass() { return SURFACE; }
    public static int header() { return HEADER; }
    public static int headerActive() { return HEADER_ACTIVE; }
    public static int divider() { return AutismTheme.recolor(DIVIDER, Channel.OUTLINE); }
    public static int buttonBg() { return SURFACE_ALT; }
    public static int buttonHoverBg() { return SURFACE_HOVER; }
    public static int buttonBorder() { return PACK_UI_THEME.borderSoft(); }
    public static int buttonHover() { return buttonHoverBg(); }
    public static int closeHover() { return AutismTheme.recolor(ACCENT, Channel.ACCENT); }
    public static int closeNormal() { return AutismTheme.recolor(TEXT_MUTED, Channel.TEXT); }
    public static int sectionHeaderBg() { return lessTransparent30(0x331F252A); }
    public static int sectionHeaderText() { return AutismTheme.recolor(TEXT_SECONDARY, Channel.TEXT); }
    public static int actionBg() { return SURFACE_ALT; }
    public static int actionBorder() { return PACK_UI_THEME.borderSoft(); }
    public static int dangerBg() { return PACK_UI_THEME.dangerFill(); }
    public static int dangerBorder() { return AutismTheme.recolor(DANGER, Channel.DANGER); }
    public static int dangerText() { return AutismTheme.recolor(DANGER, Channel.DANGER); }
    public static int successBg() { return SUCCESS_BG; }
    public static int successBorder() { return AutismTheme.recolor(SUCCESS, Channel.SUCCESS); }
    public static int successText() { return AutismTheme.recolor(SUCCESS, Channel.SUCCESS); }
    public static int packetGreen() { return PACKET_GREEN; }
    public static int packetPink() { return PACKET_PINK; }
    public static int packetOrange() { return PACKET_ORANGE; }
    public static int packetBlue() { return PACKET_BLUE; }
    public static int packetYellow() { return PACKET_YELLOW; }
    public static int packetCyan() { return PACKET_CYAN; }
    public static int packetWhite() { return PACKET_WHITE; }
    public static int packetGray() { return PACKET_GRAY; }
    public static int packetLightYellow() { return PACKET_LIGHT_YELLOW; }
    public static int listBg() { return SURFACE_ALT; }
    public static int rowNormal() { return PACK_UI_THEME.rowFillNormal(); }
    public static int rowHover() { return PACK_UI_THEME.rowFillHovered(); }
    public static int rowSelected() { return PACK_UI_THEME.rowFillSelected(); }
    public static int rowSelectedBorder() { return AutismTheme.recolor(0xFF66E08A, Channel.TOGGLE); }
    public static int rowSelectedAccent() { return AutismTheme.recolor(0xFF40C96D, Channel.TOGGLE); }
    public static int rowSelectedText() { return AutismTheme.recolor(0xFFFFF4F4, Channel.TEXT); }
    public static int packetRowBg(boolean c2s, int rowIndex, boolean hovered) {
        if (hovered) return c2s ? lessTransparent30(0x44405A78) : lessTransparent30(0x44513A25);
        if (c2s) return (rowIndex & 1) == 0 ? lessTransparent30(0x20243443) : lessTransparent30(0x18293D52);
        return (rowIndex & 1) == 0 ? lessTransparent30(0x202A231B) : lessTransparent30(0x1832261D);
    }
    public static int packetRowText(boolean c2s, int rowIndex) {
        if (c2s) return (rowIndex & 1) == 0 ? 0xFF9CDEFF : 0xFF78C8F8;
        return (rowIndex & 1) == 0 ? 0xFFFFC88F : 0xFFFFAE67;
    }
    public static int packetRowSelectedBg(boolean hovered) {
        return hovered ? PACK_UI_THEME.overlaySurface(0x00529B63) : PACK_UI_THEME.overlaySurfaceSoft(0x00407850);
    }
    public static int packetRowSelectedText() { return 0xFFB6F3C5; }
    public static int packetRowSelectedAccent() { return 0xFF73D98E; }

    public static int packetRowBlockedBg(boolean hovered) {
        return hovered ? lessTransparent30(0x88C0303C) : lessTransparent30(0x66981F2A);
    }
    public static int packetRowBlockedAccent() { return 0xFFE85A6E; }
    public static int packetRowDivider() { return lessTransparent30(0x30FFFFFF); }
    public static int textPrimary() { return AutismTheme.recolor(TEXT_PRIMARY, Channel.TEXT); }
    public static int textSecondary() { return AutismTheme.recolor(TEXT_SECONDARY, Channel.TEXT); }
    public static int textDim() { return AutismTheme.recolor(TEXT_MUTED, Channel.TEXT); }
    public static int textMuted() { return AutismTheme.recolor(TEXT_MUTED, Channel.TEXT); }
    public static int textLight() { return AutismTheme.recolor(TEXT_PRIMARY, Channel.TEXT); }
    public static int subPanelBorder() { return PACK_UI_THEME.borderSoft(); }
    public static int tooltipBg() { return TOOLTIP; }
    public static int loadingBg() { return LOADING_BG; }
    public static int popupBg() { return PACK_UI_THEME.overlaySurface(0x000A0B0E); }
    public static int popupHover() { return lessTransparent60(0x30FFFFFF); }
    public static int slotNormal() { return 0xFFB8C1C7; }
    public static int slotHover() { return 0xFFE8EEF2; }
    public static int slotSelectedA() { return AutismTheme.recolor(ACCENT, Channel.ACCENT); }
    public static int slotSelectedB() { return AutismTheme.recolor(0xFF8D3838, Channel.ACCENT); }
    public static int slotBorder() { return 0xFF67737D; }

    public static void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        UiRenderer.outline(ctx, UiBounds.of(x, y, w, h), c);
    }

    public static void drawDivider(GuiGraphicsExtractor ctx, int x, int y, int width, int color) {
        UiRenderer.rect(ctx, UiBounds.of(x, y, width, 1), color);
    }

    public static void drawResizeHandle(GuiGraphicsExtractor ctx, int x, int y, int size, int color) {
        for (int i = 0; i < 3; i++) {
            int start = x + size - 3 - (i * 4);
            UiRenderer.rect(ctx, UiBounds.of(start, y + size - 2, 2, 2), color);
            UiRenderer.rect(ctx, UiBounds.of(x + size - 2, y + size - 3 - (i * 4), 2, 2), color);
        }
    }

    public static void drawInsetPanel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, boolean focused) {
        UiRenderer.frame(ctx, UiBounds.of(x, y, w, h), listBg(), focused ? accent() : subPanelBorder());
    }
}
