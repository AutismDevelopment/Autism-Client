package autismclient.util;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactOverlayWindow;
import autismclient.gui.vanillaui.components.OverlayTopBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class AutismWindow {
    protected static final int HEADER_HEIGHT = 16;
    protected static final int RESIZE_HANDLE = 10;

    public static int sharedHeaderHeight() {
        return HEADER_HEIGHT;
    }

    protected AutismWindowLayout clampToScreen(IAutismOverlay overlay) {
        return clampToScreen(overlay, overlay.getBounds());
    }

    protected AutismWindowLayout clampToScreen(IAutismOverlay overlay, AutismWindowLayout bounds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || bounds == null) return bounds;

        return clampToScreenSize(bounds, overlay.getMinWidth(), overlay.getMinHeight(),
            AutismUiScale.getVirtualScreenWidth(), AutismUiScale.getVirtualScreenHeight());
    }

    static AutismWindowLayout clampToScreenSize(AutismWindowLayout bounds, int overlayMinWidth, int overlayMinHeight,
                                                int screenWidth, int screenHeight) {
        if (bounds == null) return null;

        if (screenWidth <= 0 || screenHeight <= 0) return bounds;

        int safeMargin = 4;
        int availableWidth = Math.max(1, screenWidth - safeMargin * 2);
        int availableHeight = Math.max(HEADER_HEIGHT, screenHeight - safeMargin * 2);
        int minWidth = Math.min(overlayMinWidth, availableWidth);
        int minHeight = Math.min(overlayMinHeight, availableHeight);

        int width = Math.max(minWidth, Math.min(bounds.width, availableWidth));
        int height = Math.max(minHeight, Math.min(bounds.height, availableHeight));
        int renderedHeight = bounds.collapsed ? HEADER_HEIGHT : height;
        int x = Math.max(safeMargin, Math.min(bounds.x, Math.max(safeMargin, screenWidth - safeMargin - width)));
        int y = Math.max(safeMargin, Math.min(bounds.y, Math.max(safeMargin, screenHeight - safeMargin - renderedHeight)));

        return new AutismWindowLayout(x, y, width, height, bounds.visible, bounds.collapsed);
    }

    protected void renderWindowFrame(GuiGraphicsExtractor context, int mouseX, int mouseY, AutismWindowLayout bounds, String title, boolean collapsed, boolean activeDrag) {
        boolean active = activeDrag || isWindowActive();
        int frameHeight = getRenderedFrameHeight(bounds, collapsed);
        UiContext ui = UiContexts.overlay(context, Minecraft.getInstance().font, mouseX, mouseY);
        CompactOverlayWindow.render(ui, UiBounds.of(bounds.x, bounds.y, bounds.width, frameHeight), HEADER_HEIGHT, title,
            collapsed, active, mouseY >= bounds.y && mouseY < bounds.y + HEADER_HEIGHT);
    }

    protected boolean beginWindowBodyClip(GuiGraphicsExtractor context, AutismWindowLayout bounds, boolean collapsed) {
        int frameHeight = getRenderedFrameHeight(bounds, collapsed);
        if (collapsed || frameHeight <= HEADER_HEIGHT + 1) return false;
        return CompactOverlayWindow.beginBodyClip(context, UiBounds.of(bounds.x, bounds.y, bounds.width, frameHeight), HEADER_HEIGHT, collapsed);
    }

    protected void endWindowBodyClip(GuiGraphicsExtractor context, boolean clipped) {
        CompactOverlayWindow.endBodyClip(context, clipped);
    }

    protected void renderWindowInactiveOverlay(GuiGraphicsExtractor context, AutismWindowLayout bounds, boolean collapsed, boolean activeDrag) {
        int frameHeight = getRenderedFrameHeight(bounds, collapsed);
        if (activeDrag || isWindowActive()) return;
        UiRenderer.rect(context, UiBounds.of(bounds.x + 1, bounds.y + 1, Math.max(0, bounds.width - 2), Math.max(0, frameHeight - 2)), 0x24000000);
    }

    protected int alignViewportHeight(int innerHeight, int rowStep) {
        int safeInnerHeight = Math.max(0, innerHeight);
        int safeRowStep = Math.max(1, rowStep);
        if (safeInnerHeight == 0 || safeRowStep <= 1) {
            return safeInnerHeight;
        }

        int aligned = (safeInnerHeight / safeRowStep) * safeRowStep;
        return aligned > 0 ? aligned : Math.min(safeInnerHeight, safeRowStep);
    }

    protected int quantizeScrollOffset(int offset, int stepSize, int maxScroll) {
        int clampedMax = Math.max(0, maxScroll);
        int clampedOffset = Math.max(0, Math.min(offset, clampedMax));
        int safeStepSize = Math.max(1, stepSize);
        if (safeStepSize <= 1) {
            return clampedOffset;
        }

        int quantized = (clampedOffset / safeStepSize) * safeStepSize;
        if (quantized > clampedMax) {
            quantized = (clampedMax / safeStepSize) * safeStepSize;
        }
        return Math.max(0, Math.min(quantized, clampedMax));
    }

    protected int getRenderedFrameHeight(AutismWindowLayout bounds, boolean collapsed) {
        if (bounds == null) return HEADER_HEIGHT;
        return collapsed ? HEADER_HEIGHT : Math.max(HEADER_HEIGHT, bounds.height);
    }

    protected boolean isOverCloseButton(double mouseX, double mouseY, AutismWindowLayout bounds) {
        return topBarCloseBounds(bounds).contains((int) mouseX, (int) mouseY);
    }

    protected boolean isOverCollapseButton(double mouseX, double mouseY, AutismWindowLayout bounds) {
        if (shouldUseSharedHeaderClickCollapse()) return false;
        return topBarCollapseBounds(bounds).contains((int) mouseX, (int) mouseY);
    }

    protected boolean isOverWindowControl(double mouseX, double mouseY, AutismWindowLayout bounds) {
        return isOverCloseButton(mouseX, mouseY, bounds) || isOverCollapseButton(mouseX, mouseY, bounds);
    }

    private boolean isWindowActive() {
        if (!(this instanceof IAutismOverlay overlay)) return true;
        return AutismOverlayManager.get().isFocusedOverlay(overlay) || AutismOverlayManager.get().isTopOverlay(overlay);
    }

    private UiBounds topBarBounds(AutismWindowLayout bounds) {
        return UiBounds.of(bounds.x, bounds.y, bounds.width, HEADER_HEIGHT);
    }

    private UiBounds topBarCloseBounds(AutismWindowLayout bounds) {
        return OverlayTopBar.closeButton(topBarBounds(bounds), HEADER_HEIGHT);
    }

    private UiBounds topBarCollapseBounds(AutismWindowLayout bounds) {
        return OverlayTopBar.collapseButton(topBarBounds(bounds), HEADER_HEIGHT);
    }

    private boolean shouldUseSharedHeaderClickCollapse() {
        return this instanceof IAutismOverlay overlay && overlay.usesSharedHeaderClickCollapse();
    }
}
