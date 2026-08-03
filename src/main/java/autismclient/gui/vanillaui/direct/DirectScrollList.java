package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.direct.DirectScrollViewport;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import autismclient.util.AutismText;
import autismclient.util.AutismColors;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class DirectScrollList<T> {

    private final DirectScrollViewport viewport;
    private final Function<T, String> labelExtractor;
    private final BiConsumer<T, Boolean> selectionHandler;
    private final List<T> items = new ArrayList<>();
    private int selectedIndex = -1;
    private boolean enabled = true;

    private int borderColor = AutismColors.secondary();
    private int backgroundColor = AutismColors.listBg();
    private int selectedTextColor = AutismColors.rowSelectedText();
    private int normalTextColor = 0xFFFFFFFF;
    private int disabledTextColor = AutismColors.textDim();

    private Function<T, String> badgeExtractor;
    private Function<T, Integer> badgeColorExtractor;

    private Function<T, String> secondaryTextExtractor;
    private int secondaryTextColor = AutismColors.textDim();

    public DirectScrollList(int x, int y, int width, int height, int rowHeight, int scrollbarWidth,
                                   Function<T, String> labelExtractor,
                                   BiConsumer<T, Boolean> selectionHandler) {
        this.viewport = new DirectScrollViewport(x, y, width, height, rowHeight, scrollbarWidth);
        this.labelExtractor = labelExtractor;
        this.selectionHandler = selectionHandler;
    }

    public void setItems(List<T> items) {
        this.items.clear();
        if (items != null) {
            this.items.addAll(items);
        }
        updateContentHeight();
    }

    public void setItems(T[] items) {
        this.items.clear();
        if (items != null) {
            for (T item : items) {
                this.items.add(item);
            }
        }
        updateContentHeight();
    }

    public void addItem(T item) {
        this.items.add(item);
        updateContentHeight();
    }

    public void clear() {
        this.items.clear();
        this.selectedIndex = -1;
        updateContentHeight();
    }

    private void updateContentHeight() {
        int contentHeight = items.size() * viewport.getRowHeight();
        viewport.setContentHeight(contentHeight);
    }

    public void setSelectedIndex(int index) {
        if (index >= -1 && index < items.size()) {
            this.selectedIndex = index;
            if (index >= 0 && selectionHandler != null) {
                selectionHandler.accept(items.get(index), true);
            }
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public T getSelectedItem() {
        return selectedIndex >= 0 && selectedIndex < items.size() ? items.get(selectedIndex) : null;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setBadgeExtractor(Function<T, String> extractor, Function<T, Integer> colorExtractor) {
        this.badgeExtractor = extractor;
        this.badgeColorExtractor = colorExtractor;
    }

    public void setSecondaryTextExtractor(Function<T, String> extractor) {
        this.secondaryTextExtractor = extractor;
    }

    public void setColors(int border, int background, int selectedText, int normalText, int disabledText) {
        this.borderColor = border;
        this.backgroundColor = background;
        this.selectedTextColor = selectedText;
        this.normalTextColor = normalText;
        this.disabledTextColor = disabledText;
    }

    public void render(GuiGraphicsExtractor ctx, Font textRenderer, double mouseX, double mouseY) {
        viewport.beginRender(ctx, borderColor, backgroundColor);
        try {
            renderContent(ctx, textRenderer, mouseX, mouseY);
        } finally {
            viewport.endRender(ctx);
        }
        viewport.renderScrollbar(ctx, mouseX, mouseY);
    }

    public void renderCustom(GuiGraphicsExtractor ctx, Font textRenderer, double mouseX, double mouseY,
                              CustomRowRenderer<T> customRenderer) {
        viewport.beginRender(ctx, borderColor, backgroundColor);
        try {
            viewport.renderSimple(ctx, items.size(), (index, bounds) -> {
                T item = items.get(index);
                boolean isSelected = index == selectedIndex;
                boolean isHovered = enabled && isHovered(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height);
                int originalY = viewport.getRowScreenY(index);

                customRenderer.render(ctx, textRenderer, item, index, isSelected, isHovered,
                    bounds.x, bounds.y, bounds.width, bounds.height, originalY);
            });
        } finally {
            viewport.endRender(ctx);
        }
        viewport.renderScrollbar(ctx, mouseX, mouseY);
    }

    private void renderContent(GuiGraphicsExtractor ctx, Font textRenderer, double mouseX, double mouseY) {
        viewport.renderSimple(ctx, items.size(), (index, bounds) -> {
            T item = items.get(index);
            boolean isSelected = index == selectedIndex;
            boolean isHovered = enabled && isHovered(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height);

            CompactSurfaces.row(ctx, bounds.x, bounds.y, bounds.width, bounds.height, isHovered, isSelected);

            int textColor;
            if (!enabled) {
                textColor = disabledTextColor;
            } else if (isSelected) {
                textColor = selectedTextColor;
            } else {
                textColor = normalTextColor;
            }

            int availableWidth = bounds.width - 12;
            int textX = bounds.x + 6;
            int textY = bounds.y + 3;

            String label = labelExtractor.apply(item);
            if (label != null && !label.isEmpty()) {

                int reservedWidth = 0;
                String badge = badgeExtractor != null ? badgeExtractor.apply(item) : null;
                String secondary = secondaryTextExtractor != null ? secondaryTextExtractor.apply(item) : null;

                if (badge != null && !badge.isEmpty()) {
                    reservedWidth += textRenderer.width(badge) + 8;
                }
                if (secondary != null && !secondary.isEmpty()) {
                    reservedWidth += textRenderer.width(secondary) + 8;
                }

                int labelMaxWidth = Math.max(20, availableWidth - reservedWidth);
                String trimmedLabel = AutismText.trimToWidth(textRenderer, label, labelMaxWidth, AutismText.Tone.BODY);
                AutismText.draw(ctx, textRenderer, trimmedLabel, textColor, textX, textY, false);

                if (badge != null && !badge.isEmpty()) {
                    int labelWidth = AutismText.width(textRenderer, trimmedLabel, AutismText.Tone.BODY);
                    int badgeX = textX + labelWidth + 4;
                    int badgeColor = badgeColorExtractor != null ? badgeColorExtractor.apply(item) : 0xFFFFAA00;
                    AutismText.draw(ctx, textRenderer, badge, badgeColor, badgeX, textY, false);
                }

                if (secondary != null && !secondary.isEmpty()) {
                    int secondaryWidth = textRenderer.width(secondary);
                    int secondaryX = bounds.x + bounds.width - secondaryWidth - 6;
                    AutismText.draw(ctx, textRenderer, secondary, secondaryTextColor, secondaryX, textY, false);
                }
            }
        });
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled) return false;

        if (viewport.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (viewport.contains(mouseX, mouseY)) {
            int relativeY = (int) Math.round(mouseY) - viewport.getY() - 1 + viewport.getScrollOffset();
            int rowIndex = relativeY / viewport.getRowHeight();

            if (rowIndex >= 0 && rowIndex < items.size()) {
                setSelectedIndex(rowIndex);
                return true;
            }
        }

        return false;
    }

    public void mouseReleased() {
        viewport.mouseReleased();
    }

    public void mouseDragged(double mouseX, double mouseY) {
        viewport.mouseDragged(mouseX, mouseY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return viewport.mouseScrolled(mouseX, mouseY, amount);
    }

    public void scrollToSelected() {
        if (selectedIndex < 0) return;

        int rowTop = selectedIndex * viewport.getRowHeight();
        int rowBottom = rowTop + viewport.getRowHeight();
        int viewTop = viewport.getScrollOffset();
        int viewHeight = viewport.getHeight() - 2;
        int viewBottom = viewTop + viewHeight;

        if (rowTop < viewTop) {
            viewport.jumpTo(rowTop);
        } else if (rowBottom > viewBottom) {
            viewport.jumpTo(rowBottom - viewHeight);
        }
    }

    public T getItem(int index) {
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    private boolean isHovered(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @FunctionalInterface
    public interface CustomRowRenderer<T> {
        void render(GuiGraphicsExtractor ctx, Font textRenderer, T item, int index,
                   boolean isSelected, boolean isHovered,
                   int x, int y, int width, int height, int originalY);
    }

    public int getX() { return viewport.getX(); }
    public int getY() { return viewport.getY(); }
    public int getWidth() { return viewport.getWidth(); }
    public int getHeight() { return viewport.getHeight(); }
    public int getContentWidth() { return viewport.getContentWidth(); }
    public int getRowHeight() { return viewport.getRowHeight(); }
    public int getScrollOffset() { return viewport.getScrollOffset(); }
    public void jumpTo(int offset) { viewport.jumpTo(offset); }
    public void scrollBy(int rows) { viewport.scrollBy(rows); }
    public boolean contains(double x, double y) { return viewport.contains(x, y); }
    public boolean isScrollbarDragging() { return viewport.isScrollbarDragging(); }
    public boolean isScrollbarHovered(double mouseX, double mouseY) { return viewport.isScrollbarHovered(mouseX, mouseY); }
}
