package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismLinks;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public class AutismWelcomeScreen extends Screen {
    private static final Identifier FONT_TITLE = UiAssets.FONT_TITLE;
    private static final Identifier FONT_LABEL = UiAssets.FONT_LABEL;
    private static final Identifier FONT_BODY = UiAssets.FONT_BODY;
    private static final long UNLOCK_DELAY_MS = 7000L;

    private static final int TITLE_COLOR = 0xFFFFF4F4;
    private static final int HEADER_COLOR = 0xFFFF4D4D;
    private static final int CARD_FILL = 0xF20A0A0C;
    private static final int CARD_BORDER = 0xFFFF4A4A;
    private static final int DIVIDER_COLOR = 0x66FF4A4A;
    private static final Identifier HUD_LOGO = Identifier.fromNamespaceAndPath("autismclient", "textures/gui/hud/autismclient_welcome.png");
    private static final int HUD_LOGO_TEXTURE_WIDTH = 878;
    private static final int HUD_LOGO_TEXTURE_HEIGHT = 83;
    private static final int HUD_LOGO_DISPLAY_WIDTH = 878;
    private static final int HUD_LOGO_DISPLAY_HEIGHT = 83;
    private static final Identifier DONATE_SUPPORT_ICON = Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/icons/donate.png");
    private static final int SUPPORT_ICON_WIDTH = 32;
    private static final int SUPPORT_ICON_HEIGHT = 32;
    private static final int SUPPORT_ICON_DRAW_SIZE = 16;

    private static final String TITLE_TEXT = "Thanks for using";
    private static final String[][] SECTIONS = {
        {"Donate",
            "If AUTISM Client has helped you or made Minecraft more fun, a donation is a kind way to support the time behind it."},
        {"Choose Method",
            "Use Card or PayPal for the normal route, or Crypto if you want the direct one."},
    };

    private static final int PAD = 12;
    private static final int CARD_MAX_WIDTH = 340;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;

    private final CompactTheme theme = new CompactTheme();
    private final long createdAtMs = System.currentTimeMillis();
    private final Btn crypto = new Btn("crypto", "Crypto", DONATE_SUPPORT_ICON, SUPPORT_ICON_WIDTH, SUPPORT_ICON_HEIGHT, () -> AutismLinks.open(AutismLinks.CRYPTO_DONATE));
    private final Btn donate = new Btn("donate", "Card/PayPal", DONATE_SUPPORT_ICON, SUPPORT_ICON_WIDTH, SUPPORT_ICON_HEIGHT, () -> AutismLinks.open(AutismLinks.KOFI));
    private final Btn continueBtn = new Btn("continue", "Continue", () -> this.minecraft.gui.setScreen(new TitleScreen()));
    private int layoutScreenWidth = -1;
    private int layoutScreenHeight = -1;
    private int wrappedWidth = -1;
    private List<List<String>> wrappedSections = List.of();

    public AutismWelcomeScreen() {
        super(Component.literal(TITLE_TEXT));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    private boolean continueUnlocked() {
        return System.currentTimeMillis() - createdAtMs >= UNLOCK_DELAY_MS;
    }

    private int continueSecondsLeft() {
        long remaining = UNLOCK_DELAY_MS - (System.currentTimeMillis() - createdAtMs);
        return (int) Math.max(1, Math.ceil(remaining / 1000.0));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.minecraft.gameRenderer.panorama().extractRenderState(graphics, this.width, this.height);

        float uiMouseX = (float) AutismUiScale.toVirtual(mouseX);
        float uiMouseY = (float) AutismUiScale.toVirtual(mouseY);
        layout();

        AutismUiScale.pushOverlayScale(graphics);
        try {
            int screenW = AutismUiScale.getVirtualScreenWidth();
            int screenH = AutismUiScale.getVirtualScreenHeight();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenW, screenH), 0xC8000000);

            int cardW = cardWidth();
            int cardX = (screenW - cardW) / 2;
            int cardH = cardHeight();
            int cardY = Math.max(8, (screenH - cardH) / 2);
            int innerW = cardW - PAD * 2;
            int x = cardX + PAD;

            UiRenderer.rect(graphics, UiBounds.of(cardX, cardY, cardW, cardH), CARD_FILL);
            drawThickBorder(graphics, cardX, cardY, cardW, cardH, autismclient.util.AutismTheme.recolor(CARD_BORDER, autismclient.util.AutismTheme.Channel.OUTLINE), 2);

            int y = cardY + PAD + 2;
            drawCentered(graphics, TITLE_TEXT, FONT_TITLE, autismclient.util.AutismTheme.recolor(TITLE_COLOR, autismclient.util.AutismTheme.Channel.TEXT), x, innerW, y);
            y += UiText.fontHeight(FONT_TITLE) + 7;
            int logoW = welcomeLogoWidth(innerW);
            int logoH = welcomeLogoHeight(logoW);
            int logoX = x + (innerW - logoW) / 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, autismclient.util.AutismThemeTextures.recolored(HUD_LOGO, autismclient.util.AutismTheme.Channel.ACCENT), logoX, y, 0.0F, 0.0F, logoW, logoH,
                HUD_LOGO_TEXTURE_WIDTH, HUD_LOGO_TEXTURE_HEIGHT, HUD_LOGO_TEXTURE_WIDTH, HUD_LOGO_TEXTURE_HEIGHT);
            y += logoH + 9;
            UiRenderer.rect(graphics, UiBounds.of(x, y, innerW, 1), autismclient.util.AutismTheme.recolor(DIVIDER_COLOR, autismclient.util.AutismTheme.Channel.OUTLINE));
            y += 8;

            int headerH = UiText.fontHeight(FONT_LABEL);
            int lineH = UiText.fontHeight(FONT_LABEL) + 3;
            int bodyColor = theme.color(UiTone.MUTED);
            List<List<String>> linesBySection = wrappedSections(innerW);
            for (int i = 0; i < SECTIONS.length; i++) {
                String[] section = SECTIONS[i];
                UiText.draw(graphics, this.font, section[0], FONT_LABEL, autismclient.util.AutismTheme.recolor(HEADER_COLOR, autismclient.util.AutismTheme.Channel.ACCENT), x, y, false);
                y += headerH + 3;
                for (String line : linesBySection.get(i)) {
                    UiText.draw(graphics, this.font, line, FONT_LABEL, bodyColor, x, y, false);
                    y += lineH;
                }
                y += 8;
            }

            renderButton(graphics, donate, uiMouseX, uiMouseY, true);
            renderButton(graphics, crypto, uiMouseX, uiMouseY, true);
            boolean unlocked = continueUnlocked();
            continueBtn.label = unlocked ? "Continue" : "Continue (" + continueSecondsLeft() + ")";
            renderButton(graphics, continueBtn, uiMouseX, uiMouseY, unlocked);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        float mx = (float) AutismUiScale.toVirtual(event.x());
        float my = (float) AutismUiScale.toVirtual(event.y());
        layout();
        if (donate.contains(mx, my)) { press(donate, mx, my); return true; }
        if (crypto.contains(mx, my)) { press(crypto, mx, my); return true; }
        if (continueUnlocked() && continueBtn.contains(mx, my)) { press(continueBtn, mx, my); return true; }
        return false;
    }

    private void press(Btn btn, float mx, float my) {
        btn.action.run();
    }

    @Override
    public void removed() {
    }

    private int cardWidth() {
        int screenW = AutismUiScale.getVirtualScreenWidth();
        return Math.max(1, Math.min(Math.max(1, screenW - 12), CARD_MAX_WIDTH));
    }

    private int cardHeight() {
        int innerW = Math.max(1, cardWidth() - PAD * 2);
        int headerH = UiText.fontHeight(FONT_LABEL);
        int lineH = UiText.fontHeight(FONT_LABEL) + 3;

        int logoW = welcomeLogoWidth(innerW);
        int h = PAD + UiText.fontHeight(FONT_TITLE) + 7 + welcomeLogoHeight(logoW) + 9 + 1 + 8;
        List<List<String>> linesBySection = wrappedSections(innerW);
        for (int i = 0; i < SECTIONS.length; i++) {
            h += headerH + 3 + linesBySection.get(i).size() * lineH + 8;
        }
        h += BUTTON_HEIGHT + PAD;
        return h;
    }

    private void layout() {
        int screenW = AutismUiScale.getVirtualScreenWidth();
        int screenH = AutismUiScale.getVirtualScreenHeight();
        if (layoutScreenWidth == screenW && layoutScreenHeight == screenH) return;
        layoutScreenWidth = screenW;
        layoutScreenHeight = screenH;
        int cardW = cardWidth();
        int cardX = (screenW - cardW) / 2;
        int cardH = cardHeight();
        int cardY = Math.max(8, (screenH - cardH) / 2);

        int innerW = Math.max(1, cardW - PAD * 2);
        int btnW = Math.max(1, (innerW - BUTTON_GAP * 2) / 3);
        int btnY = cardY + cardH - PAD - BUTTON_HEIGHT;
        int bx = cardX + PAD;
        donate.set(bx, btnY, btnW, BUTTON_HEIGHT);
        crypto.set(bx + btnW + BUTTON_GAP, btnY, btnW, BUTTON_HEIGHT);
        int lastX = bx + (btnW + BUTTON_GAP) * 2;
        continueBtn.set(lastX, btnY, cardX + cardW - PAD - lastX, BUTTON_HEIGHT);
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (current.length() == 0 || UiText.width(this.font, candidate, FONT_LABEL, 0xFFFFFFFF) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private List<List<String>> wrappedSections(int innerWidth) {
        if (wrappedWidth == innerWidth && wrappedSections.size() == SECTIONS.length) return wrappedSections;
        List<List<String>> next = new ArrayList<>(SECTIONS.length);
        for (String[] section : SECTIONS) {
            next.add(List.copyOf(wrap(section[1], innerWidth)));
        }
        wrappedWidth = innerWidth;
        wrappedSections = List.copyOf(next);
        return wrappedSections;
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, Identifier fontId, int color, int x, int width, int y) {
        int textW = UiText.width(this.font, text, fontId, color);
        UiText.draw(graphics, this.font, text, fontId, color, x + (width - textW) / 2, y, false);
    }

    private static int welcomeLogoWidth(int innerW) {
        return Math.max(170, Math.min(innerW - 18, 286));
    }

    private static int welcomeLogoHeight(int width) {
        return Math.max(1, Math.round(width * (HUD_LOGO_DISPLAY_HEIGHT / (float) HUD_LOGO_DISPLAY_WIDTH)));
    }

    private void renderButton(GuiGraphicsExtractor graphics, Btn btn, float mouseX, float mouseY, boolean enabled) {
        boolean hovered = enabled && btn.contains(mouseX, mouseY);
        UiContext ctx = UiContexts.overlay(graphics, this.font, (int) mouseX, (int) mouseY);
        UiBounds bounds = UiBounds.of(btn.x, btn.y, btn.w, btn.h);
        if (btn.icon != null) {
            Button.renderIcon(ctx, bounds, btn.label, btn.icon, Button.Tone.SECONDARY, hovered, false, 0.0f);
        } else {
            Button.render(ctx, bounds, btn.label, Button.Tone.SECONDARY, hovered, false);
        }
        if (!enabled) {
            UiRenderer.rect(graphics, bounds, 0x66000000);
        }
    }

    private void drawCenteredButtonLabel(GuiGraphicsExtractor graphics, Btn btn, int textColor) {
        int textAreaX = btn.x + 3;
        int textAreaW = Math.max(1, btn.w - 6);
        if (btn.icon != null) {
            int iconSize = Math.min(SUPPORT_ICON_DRAW_SIZE, Math.max(1, Math.min(btn.w - 4, btn.h - 4)));
            textAreaX = btn.x + 4 + iconSize + 7;
            textAreaW = Math.max(1, btn.x + btn.w - 5 - textAreaX);
        }
        String label = UiText.trimToWidth(this.font, btn.label, textAreaW, FONT_LABEL, textColor);
        int textW = UiText.width(this.font, label, FONT_LABEL, textColor);
        int textX = btn.icon != null ? textAreaX : textAreaX + (textAreaW - textW + 1) / 2;
        if ("crypto".equals(btn.id)) {
            int centeredX = btn.x + (btn.w - textW + 1) / 2;
            textX = Math.max(textAreaX, centeredX);
        }
        int textY = UiSizing.alignTextY(btn.y, btn.h, UiText.fontHeight(FONT_LABEL), theme.buttonTextNudge());
        UiText.draw(graphics, this.font, label, FONT_LABEL, textColor, textX, textY, false);
    }

    private static void drawThickBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color, int t) {
        if (width <= 0 || height <= 0) return;
        UiRenderer.rect(graphics, UiBounds.of(x, y, width, t), color);
        UiRenderer.rect(graphics, UiBounds.of(x, y + height - t, width, t), color);
        UiRenderer.rect(graphics, UiBounds.of(x, y, t, height), color);
        UiRenderer.rect(graphics, UiBounds.of(x + width - t, y, t, height), color);
    }

    private static final class Btn {
        private final String id;
        private String label;
        private final Identifier icon;
        private final int iconTextureWidth;
        private final int iconTextureHeight;
        private final Runnable action;
        private int x;
        private int y;
        private int w;
        private int h;

        private Btn(String id, String label, Runnable action) {
            this(id, label, null, 1, 1, action);
        }

        private Btn(String id, String label, Identifier icon, int iconTextureWidth, int iconTextureHeight, Runnable action) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.iconTextureWidth = Math.max(1, iconTextureWidth);
            this.iconTextureHeight = Math.max(1, iconTextureHeight);
            this.action = action;
        }

        private void set(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        private boolean contains(float mx, float my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }
}
