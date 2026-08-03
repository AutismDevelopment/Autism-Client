package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;

import java.util.List;
import java.util.function.BiConsumer;

public final class DirectScrollViewport {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int rowHeight;
    private final int scrollbarWidth;

    private int contentHeight = 0;
    private int scrollOffset = 0;
    private boolean scrollbarDragging = false;
    private int scrollbarGrabOffset = 0;
    private int activeBorderColor = 0;

    private final int clipLeft;
    private final int clipTop;
    private final int clipRight;
    private final int clipBottom;

    public DirectScrollViewport(int x, int y, int width, int height, int rowHeight, int scrollbarWidth) {
        this.x = x;
        this.y = y;

        this.width = Math.max(4, width);
        this.height = Math.max(4, height);
        this.rowHeight = Math.max(1, rowHeight);
        this.scrollbarWidth = Math.max(0, scrollbarWidth);

        this.clipLeft = this.x;
        this.clipTop = this.y;
        this.clipRight = this.x + this.width;
        this.clipBottom = this.y + this.height;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getContentWidth() { return width - scrollbarWidth - 3; }
    public int getInnerWidth() { return width - 2; }

    public int getRowHeight() { return rowHeight; }
    public int getContentHeight() { return contentHeight; }
    public int getScrollOffset() { return scrollOffset; }

    private int getViewHeight() {
        return Math.max(0, clipBottom - clipTop);
    }

    private int getEffectiveViewHeight() {
        int viewHeight = getViewHeight();
        if (viewHeight <= 0 || rowHeight <= 0) return 0;

        int fullRows = viewHeight / rowHeight;
        if (fullRows > 0) {
            return fullRows * rowHeight;
        }

        return viewHeight;
    }

    private int getEffectiveClipBottom() {
        return clipTop + getEffectiveViewHeight();
    }

    public void setContentHeight(int height) {
        this.contentHeight = Math.max(0, height);
        clampScroll();
    }

    public int getMaxScroll() {
        int viewHeight = getEffectiveViewHeight();

        return Math.max(0, contentHeight - viewHeight);
    }

    public void jumpTo(int offset) {
        this.scrollOffset = quantizeToSteps(Math.max(0, Math.min(offset, getMaxScroll())));
    }

    public void scrollBy(int deltaRows) {
        int deltaPixels = deltaRows * rowHeight;
        jumpTo(scrollOffset + deltaPixels);
    }

    public void scrollToBottom() {
        jumpTo(getMaxScroll());
    }

    public void scrollToTop() {
        jumpTo(0);
    }

    private void clampScroll() {
        int max = getMaxScroll();
        if (scrollOffset > max) {
            scrollOffset = quantizeToSteps(max);
        }
    }

    private int quantizeToSteps(int offset) {

        if (rowHeight <= 0) return 0;
        return (offset / rowHeight) * rowHeight;
    }

    public int getVisibleRows() {
        int viewHeight = getEffectiveViewHeight();
        if (viewHeight <= 0 || rowHeight <= 0) return 0;
        return (viewHeight + rowHeight - 1) / rowHeight;
    }

    public int getFirstVisibleRow() {
        if (rowHeight <= 0) return 0;
        return scrollOffset / rowHeight;
    }

    public int getLastVisibleRow() {
        int viewHeight = getEffectiveViewHeight();
        if (viewHeight <= 0 || rowHeight <= 0) return -1;
        int lastVisiblePixel = viewHeight - 1;

        if (scrollOffset < 0 || scrollOffset > Integer.MAX_VALUE - lastVisiblePixel) {
            return Integer.MAX_VALUE / rowHeight;
        }

        return (scrollOffset + lastVisiblePixel) / rowHeight;
    }

    public int getRowScreenY(int rowIndex) {
        return y + (rowIndex * rowHeight) - scrollOffset;
    }

    public boolean isRowVisible(int rowY) {
        int effectiveClipBottom = getEffectiveClipBottom();
        return rowY + rowHeight > clipTop && rowY < effectiveClipBottom;
    }

    public int clampY(int y) {
        return Math.max(clipTop, Math.min(y, getEffectiveClipBottom()));
    }

    public int clampHeight(int y, int height) {
        int maxBottom = Math.min(y + height, getEffectiveClipBottom());
        return Math.max(0, maxBottom - y);
    }

    public DrawBounds getRowDrawBounds(int rowY) {
        if (!isRowVisible(rowY)) {
            return null;
        }
        int drawY = Math.max(rowY, clipTop);
        int drawBottom = Math.min(rowY + rowHeight, getEffectiveClipBottom());
        int drawHeight = drawBottom - drawY;
        if (drawHeight <= 0) return null;
        return new DrawBounds(clipLeft, drawY, clipRight - clipLeft, drawHeight);
    }

    public void beginRender(GuiGraphicsExtractor ctx, int borderColor, int fillColor) {
        this.activeBorderColor = borderColor;
        UiRenderer.rect(ctx, UiBounds.of(x, y, width, height), fillColor);
        UiScissorStack.global().push(ctx, UiBounds.of(clipLeft, clipTop, clipRight - clipLeft, clipBottom - clipTop));
    }

    public void renderContent(GuiGraphicsExtractor ctx, Font textRenderer,
                              List<? extends ScrollRow> rows,
                              RowRenderer renderer) {
        int startRow = Math.max(0, getFirstVisibleRow() - 1);
        int endRow = Math.min(rows.size(), startRow + getVisibleRows() + 2);

        for (int i = startRow; i < endRow; i++) {
            int rowY = getRowScreenY(i);
            DrawBounds bounds = getRowDrawBounds(rowY);
            if (bounds != null) {
                ScrollRow row = rows.get(i);
                renderer.render(ctx, textRenderer, row, i, bounds.x, bounds.y, bounds.width, bounds.height, rowY);
            }
        }
    }

    public void renderSimple(GuiGraphicsExtractor ctx, int totalRows, BiConsumer<Integer, DrawBounds> rowRenderer) {
        int startRow = Math.max(0, getFirstVisibleRow() - 1);
        int endRow = Math.min(totalRows, startRow + getVisibleRows() + 2);

        for (int i = startRow; i < endRow; i++) {
            int rowY = getRowScreenY(i);
            DrawBounds bounds = getRowDrawBounds(rowY);
            if (bounds != null) {
                rowRenderer.accept(i, bounds);
            }
        }
    }

    public void endRender(GuiGraphicsExtractor ctx) {
        UiScissorStack.global().pop(ctx);
        UiRenderer.outline(ctx, UiBounds.of(x, y, width, height), activeBorderColor);
    }

    public void renderScrollbar(GuiGraphicsExtractor ctx, double mouseX, double mouseY) {
        if (getMaxScroll() <= 0) return;

        int trackX = x + width - scrollbarWidth - 2;
        int trackY = y + 2;
        int trackWidth = scrollbarWidth;
        int trackHeight = height - 4;

        CompactScrollbar.Metrics metrics = CompactScrollbar.compute(
            contentHeight, getEffectiveViewHeight(),
            trackX, trackY, trackWidth, trackHeight,
            scrollOffset
        );

        boolean hovered = metrics.contains(mouseX, mouseY);
        CompactScrollbar.draw(ctx, metrics, hovered, scrollbarDragging);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!contains(mouseX, mouseY)) return false;
        if (getMaxScroll() <= 0) return false;

        int deltaRows = amount > 0 ? -1 : 1;
        scrollBy(deltaRows);
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (getMaxScroll() <= 0) return false;

        CompactScrollbar.Metrics metrics = getScrollbarMetrics();
        if (metrics == null) return false;

        if (metrics.overThumb(mouseX, mouseY)) {
            scrollbarDragging = true;
            scrollbarGrabOffset = (int) Math.round(mouseY) - metrics.thumbY();
            return true;
        }
        if (metrics.contains(mouseX, mouseY)) {

            int newScroll = CompactScrollbar.scrollFromThumb(metrics, mouseY, scrollbarGrabOffset);
            jumpTo(quantizeToSteps(newScroll));
            scrollbarDragging = true;
            scrollbarGrabOffset = metrics.thumbHeight() / 2;
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        scrollbarDragging = false;
        scrollbarGrabOffset = 0;
    }

    public void mouseDragged(double mouseX, double mouseY) {
        if (!scrollbarDragging) return;

        CompactScrollbar.Metrics metrics = getScrollbarMetrics();
        if (metrics == null) return;

        int newScroll = CompactScrollbar.scrollFromThumb(metrics, mouseY, scrollbarGrabOffset);
        jumpTo(quantizeToSteps(newScroll));
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public CompactScrollbar.Metrics getScrollbarMetrics() {
        if (getMaxScroll() <= 0) return null;

        int trackX = x + width - scrollbarWidth - 2;
        int trackY = y + 2;
        int trackWidth = scrollbarWidth;
        int trackHeight = height - 4;

        return CompactScrollbar.compute(
            contentHeight, getEffectiveViewHeight(),
            trackX, trackY, trackWidth, trackHeight,
            scrollOffset
        );
    }

    public boolean isScrollbarDragging() {
        return scrollbarDragging;
    }

    public boolean isScrollbarHovered(double mouseX, double mouseY) {
        CompactScrollbar.Metrics metrics = getScrollbarMetrics();
        return metrics != null && metrics.contains(mouseX, mouseY);
    }

    public static class DrawBounds {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        public DrawBounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public interface ScrollRow {
        String getLabel();
        boolean isSelected();
        boolean isEnabled();
    }

    @FunctionalInterface
    public interface RowRenderer {
        void render(GuiGraphicsExtractor ctx, Font textRenderer, ScrollRow row,
                   int index, int x, int y, int width, int height, int originalY);
    }
}
