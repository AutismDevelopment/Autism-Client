package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactScreenPanel;
import autismclient.gui.vanillaui.direct.DirectLayout;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AutismForceOpPreviewScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int BG = 0xAA000000;
    private static final int PANEL = THEME.windowFill();
    private static final int BORDER = THEME.borderSoft();
    private static final int RED = THEME.headerAccent();
    private static final int TEXT = THEME.color(UiTone.BODY);
    private static final int MUTED = THEME.color(UiTone.MUTED);

    private static final int ROW_H = 18;
    private static final int HEADER_H = 26;
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 260;

    private final Screen parent;
    private final String[] passwords;
    private final List<String> visiblePasswords;

    private int scroll = 0;
    private boolean draggingScroll = false;
    private double scrollGrabOffset = 0;

    private final List<Hit> hits = new ArrayList<>();

    private enum HitType {
        CLOSE, SCROLLBAR
    }

    private record Hit(HitType type, int x, int y, int w, int h) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public AutismForceOpPreviewScreen(Screen parent, String[] passwords) {
        super(Component.literal("ForceOp Preview"));
        this.parent = parent;
        this.passwords = passwords;
        this.visiblePasswords = new ArrayList<>();
        int limit = Math.min(passwords.length, 500);
        for (int i = 0; i < limit; i++) {
            this.visiblePasswords.add(passwords[i]);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int panelW() {
        return DirectLayout.fitPanelDimension(screenWidth(), 4, PANEL_W);
    }

    private int panelH() {
        return DirectLayout.fitPanelDimension(screenHeight(), 4, visiblePasswords.size() * ROW_H + HEADER_H + 40);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int mx = AutismUiScale.toVirtualInt(mouseX);
        int my = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            hits.clear();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), BG);
            int x = panelX();
            int y = panelY();
            int w = panelW();
            int h = panelH();
            drawTopBar(graphics, x, y, w, h, "Password Preview (First " + visiblePasswords.size() + ")", mx, my);
            if (w < 100 || h < HEADER_H + 28) return;

            int listX = x + 10;
            int listY = y + HEADER_H + 10;
            int listW = w - 20;
            int listH = h - HEADER_H - 20;

            frame(graphics, listX, listY, listW, listH, 0xAA09090B, BORDER);

            int innerX = listX + 2;
            int innerY = listY + 2;
            int innerW = listW - 4;
            int innerH = listH - 4;

            int maxScroll = Math.max(0, visiblePasswords.size() * ROW_H - innerH);
            scroll = Math.max(0, Math.min(maxScroll, scroll));

            autismclient.gui.vanillaui.UiScissorStack.global().push(graphics,
                autismclient.gui.vanillaui.UiBounds.of(innerX, innerY, Math.max(0, innerW), Math.max(0, innerH)));
            try {
                int contentY = innerY - scroll;
                for (int i = 0; i < visiblePasswords.size(); i++) {
                    int rowY = contentY + i * ROW_H;
                    if (rowY + ROW_H <= innerY || rowY >= innerY + innerH) continue;

                    int color = (i % 2 == 0) ? 0xFF66B2FF : 0xFFFFA066;
                    CompactSurfaces.row(graphics, innerX, rowY, innerW - 8, ROW_H, false, false);

                    String text = UiText.trimToWidth(font, visiblePasswords.get(i), innerW - 14, THEME.fontFor(UiTone.BODY), color);
                    drawText(graphics, text, innerX + 4,
                        UiSizing.alignTextY(rowY, ROW_H, THEME.fontHeight(UiTone.BODY), THEME.bodyTextNudge()),
                        color, innerW - 14);
                }
            } finally {
                autismclient.gui.vanillaui.UiScissorStack.global().pop(graphics);
            }

            if (maxScroll > 0) {
                CompactScrollbar.Metrics metrics = CompactScrollbar.compute(visiblePasswords.size() * ROW_H, innerH, innerX + innerW - 6, innerY, 6, innerH, scroll);
                boolean overBar = metrics.contains(mx, my);
                CompactScrollbar.draw(graphics, metrics, overBar, draggingScroll);
                hits.add(new Hit(HitType.SCROLLBAR, innerX + innerW - 6, innerY, 6, innerH));
            }
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void drawTopBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String title, int mx, int my) {
        UiBounds bounds = UiBounds.of(x, y, width, height);
        CompactScreenPanel.render(UiContexts.overlay(graphics, font, mx, my), bounds, HEADER_H, title,
            mx >= x && mx < x + width && my >= y && my < y + HEADER_H);
        UiBounds close = CompactScreenPanel.closeButton(bounds, HEADER_H);
        hits.add(new Hit(HitType.CLOSE, close.x(), close.y(), close.width(), close.height()));
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int x, int y, int w, int h, int color) {
        String display = UiText.trimToWidth(font, text, Math.max(0, w - 8), THEME.fontFor(UiTone.LABEL), color);
        int tw = UiText.width(font, display, THEME.fontFor(UiTone.LABEL), color);
        int th = THEME.fontHeight(UiTone.LABEL);
        UiText.draw(graphics, font, display, THEME.fontFor(UiTone.LABEL), color, x + Math.max(2, (w - tw) / 2), y + Math.max(1, (h - th + 1) / 2 + 1), false);
    }

    private void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill, int border) {
        UiRenderer.frame(graphics, UiBounds.of(x, y, w, h), fill, border);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxWidth) {
        UiText.draw(graphics, font, text, THEME.fontFor(UiTone.BODY), color, x, y, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean allowBypass) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());

        for (Hit hit : hits) {
            if (hit.contains(mx, my)) {
                if (hit.type == HitType.CLOSE && event.button() == 0) {
                    onClose();
                    return true;
                } else if (hit.type == HitType.SCROLLBAR && event.button() == 0) {
                    int innerH = panelH() - HEADER_H - 24;
                    CompactScrollbar.Metrics metrics = CompactScrollbar.compute(visiblePasswords.size() * ROW_H, innerH, hit.x, hit.y, hit.w, hit.h, scroll);
                    if (metrics.overThumb(mx, my)) {
                        draggingScroll = true;
                        scrollGrabOffset = my - metrics.thumbY();
                    } else {
                        scroll = CompactScrollbar.scrollFromThumb(metrics, my, metrics.thumbHeight() / 2);
                    }
                    return true;
                }
            }
        }

        if (mx < panelX() || mx > panelX() + panelW() || my < panelY() || my > panelY() + panelH()) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, allowBypass);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScroll = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        if (draggingScroll) {
            int innerH = panelH() - HEADER_H - 24;
            int x = panelX() + 10;
            int y = panelY() + HEADER_H + 10;
            CompactScrollbar.Metrics metrics = CompactScrollbar.compute(visiblePasswords.size() * ROW_H, innerH, x + panelW() - 26, y + 2, 6, innerH, scroll);
            scroll = CompactScrollbar.scrollFromThumb(metrics, my, (int) scrollGrabOffset);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = AutismUiScale.toVirtualInt(mouseX);
        int my = AutismUiScale.toVirtualInt(mouseY);
        int listX = panelX() + 10;
        int listY = panelY() + HEADER_H + 10;
        int listW = panelW() - 20;
        int listH = panelH() - HEADER_H - 20;

        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            scroll -= scrollY * ROW_H * 2;
            int maxScroll = Math.max(0, visiblePasswords.size() * ROW_H - (listH - 4));
            scroll = Math.max(0, Math.min(maxScroll, scroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    private int panelX() {
        return DirectLayout.centerPanel(screenWidth(), panelW(), 4);
    }

    private int panelY() {
        return DirectLayout.centerPanel(screenHeight(), panelH(), 4);
    }
}
