package autismclient.util;

import autismclient.gui.mm.MatchmakingPanel;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class AutismMatchmakingOverlay extends AutismOverlayBase implements MatchmakingPanel.Host {
    private static final String OVERLAY_ID = "autism-matchmaking";

    private final MatchmakingPanel panel;
    private final Font font;
    private boolean isDragging;
    private double dragOffsetX, dragOffsetY;

    private boolean mainMenuMode;
    private int hudX, hudY, hudW, hudH;

    private static final int MAX_H = 360;

    public AutismMatchmakingOverlay(Font font) {
        super(OVERLAY_ID, 400, 230);
        this.font = font;
        this.panel = new MatchmakingPanel(this, font);
        this.panelX = 80;
        this.panelY = 36;
    }

    public void toggle() {
        setMainMenuMode(false);
        setVisible(!visible);
        if (visible) AutismOverlayManager.get().bringToFront(this);
    }

    public void setMainMenuMode(boolean v) {
        if (v == mainMenuMode) return;
        if (v) {
            hudX = panelX; hudY = panelY; hudW = panelWidth; hudH = panelHeight;
            collapsed = false;
        } else {
            panelX = hudX; panelY = hudY; panelWidth = hudW; panelHeight = hudH;
        }
        mainMenuMode = v;
    }

    @Override
    public void setVisible(boolean v) {
        if (visible == v) {
            if (v) panel.openDirectory();
            return;
        }
        if (!v) panel.closeDirectory();

        if (!v && mainMenuMode) {
            panelX = hudX; panelY = hudY; panelWidth = hudW; panelHeight = hudH;
            mainMenuMode = false;
        }
        super.setVisible(v);
        if (v) panel.openDirectory();
    }

    public void openInGameInteractive() {
        setMainMenuMode(false);
        setVisible(true);
    }

    @Override public int getMinWidth() { return 360; }
    @Override public int getMinHeight() { return 150; }
    @Override public OverlayScope getDefaultOverlayScope() { return OverlayScope.BACKGROUND_STATUS; }

    @Override public boolean usesSharedHeaderClickCollapse() { return !mainMenuMode; }
    @Override public boolean hasTextFieldFocused() { return panel.hasFocusedTextInput(); }
    @Override public void clearTextFieldFocus() { panel.clearFocus(); }

    @Override public Screen returnScreen() { return null; }

    @Override
    public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        if (!visible) return;
        if (mainMenuMode) { renderMainMenu(ctx, mx, my, delta); return; }
        AutismWindowLayout bounds = clampToScreen(this);
        panelX = bounds.x; panelY = bounds.y; panelWidth = bounds.width; panelHeight = bounds.height;
        renderWindowFrame(ctx, mx, my, getBounds(), "Matchmaking", collapsed, isDragging);
        if (collapsed) return;
        boolean clipped = beginWindowBodyClip(ctx, getBounds(), collapsed);
        panel.render(ctx, panelX + 3, panelY + HEADER_HEIGHT + 1, panelWidth - 6, panelHeight - HEADER_HEIGHT - 4, mx, my, delta);
        endWindowBodyClip(ctx, clipped);

        if (!collapsed) {
            int want = panel.desiredHeight() + HEADER_HEIGHT + 5;
            int maxH = Math.min(AutismUiScale.getVirtualScreenHeight() - 16, MAX_H);
            panelHeight = Math.max(getMinHeight(), Math.min(want, maxH));
        }
    }

    private void renderMainMenu(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        int sw = AutismUiScale.getVirtualScreenWidth();
        int sh = AutismUiScale.getVirtualScreenHeight();
        UiRenderer.rect(ctx, UiBounds.of(0, 0, sw, sh), 0xC0000000);
        int pw = Math.min(sw - 40, 520);
        int px = (sw - pw) / 2;
        int py = 26;
        int ph = Math.max(getMinHeight(), sh - py - 44);
        panelX = px; panelY = py; panelWidth = pw; panelHeight = ph;
        String title = "Matchmaking";
        ctx.text(font, title, (sw - font.width(title)) / 2, 9, 0xFFFFFFFF, true);

        autismclient.gui.vanillaui.components.Card.render(
            autismclient.gui.vanillaui.UiContexts.overlay(ctx, font, mx, my), UiBounds.of(px - 2, py - 2, pw + 4, ph + 4));
        UiScissorStack.global().push(ctx, UiBounds.of(px, py, pw, ph));
        panel.render(ctx, px, py, pw, ph, mx, my, delta);
        UiScissorStack.global().pop(ctx);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible) return false;
        AutismWindowLayout bounds = getBounds();
        if (!mainMenuMode && isOverCloseButton(mx, my, bounds)) { setVisible(false); isDragging = false; return true; }
        if (!mainMenuMode && isOverCollapseButton(mx, my, bounds)) { setCollapsed(!collapsed); return true; }
        if (!mainMenuMode && button == 0 && isOverDragBar(mx, my)) {
            isDragging = true;
            dragOffsetX = mx - panelX;
            dragOffsetY = my - panelY;
            return true;
        }
        if (collapsed) return false;
        if (panel.mouseClicked((int) Math.round(mx), (int) Math.round(my), button)) return true;
        return isMouseOver(mx, my);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (isDragging) { isDragging = false; saveLayout(); return true; }
        return panel.mouseReleased((int) Math.round(mx), (int) Math.round(my), button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isDragging) {
            AutismWindowLayout c = clampToScreen(this, new AutismWindowLayout(
                (int) Math.round(mx - dragOffsetX), (int) Math.round(my - dragOffsetY),
                panelWidth, panelHeight, visible, collapsed));
            panelX = c.x;
            panelY = c.y;
            return true;
        }
        if (collapsed) return false;
        return panel.mouseDragged((int) Math.round(mx), (int) Math.round(my), button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || collapsed || !isMouseOver(mx, my)) return false;
        return panel.mouseScrolled((int) Math.round(mx), (int) Math.round(my), amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return visible && !collapsed && panel.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return visible && !collapsed && panel.charTyped(chr, modifiers);
    }
}
