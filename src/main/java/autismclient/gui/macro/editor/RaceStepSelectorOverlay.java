package autismclient.gui.macro.editor;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.components.OverlayTopBar;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.gui.vanillaui.direct.DirectUiLabel;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.direct.DirectScrollViewport;
import autismclient.gui.vanillaui.direct.DirectSurface;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTextInput;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import autismclient.gui.vanillaui.components.SearchableSelector;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectViewport;
import autismclient.gui.vanillaui.direct.DirectViewportSlot;
import autismclient.gui.vanillaui.direct.DirectWindow;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import autismclient.util.AutismColors;
import autismclient.util.AutismOverlayBase;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismWindowLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

public final class RaceStepSelectorOverlay extends AutismOverlayBase {
    public record Option(String category, String id, String label, String description) {}

    private static final Minecraft MC = Minecraft.getInstance();
    private static final int MIN_PANEL_WIDTH = 260;
    private static final int ROW_HEIGHT = 18;
    private static final int MAX_VISIBLE_ROWS = 12;
    private static final int PAD = 6;
    private static final int HEADER_CONTROL = 12;
    private static final int HEADER_ARROW_WIDTH = 10;
    private static final int HEADER_ARROW_GAP = 3;
    private static final int SCROLLBAR_WIDTH = 6;

    private final Font textRenderer;
    private final CompactTheme theme = new CompactTheme();
    private final DirectWindow windowNode = new DirectWindow("Race Selector");
    private final DirectSurface surface = new DirectSurface(theme, windowNode);
    private final CompactTextInput searchField = new CompactTextInput();
    private final DirectUiLabel summaryLabel = new DirectUiLabel("", UiTone.MUTED).setTrimToBounds(true);
    private final DirectViewportSlot listSlot = new DirectViewportSlot();

    private DirectScrollViewport listViewport;
    private final SearchableSelector<Option> selector = new SearchableSelector<>(
        option -> option.id() + '\n' + option.label() + '\n' + option.category() + '\n' + option.description());
    private List<Option> allOptions = List.of();
    private List<Option> filteredOptions = List.of();
    private Consumer<Option> onSelect;
    private boolean visible;
    private boolean collapsed;
    private boolean dragging;
    private boolean dragMoved;
    private float dragOffsetX;
    private float dragOffsetY;
    private int panelX;
    private int panelY;
    private int panelWidth = MIN_PANEL_WIDTH;
    private int panelHeight;

    public RaceStepSelectorOverlay(Font textRenderer) {
        this.textRenderer = textRenderer;
        windowNode.setCenterTitle(false);
        windowNode.setTitleTone(UiTone.LABEL);
        windowNode.setHeaderControls(true, true);
        windowNode.setTitleAreaInsets(PAD + 2, PAD + HEADER_CONTROL + HEADER_ARROW_WIDTH + HEADER_ARROW_GAP + 10);
        windowNode.content().setGap(4).setPadding(DirectUiInsets.all(PAD));
        searchField
                .setPlaceholder("Search...")
                .setFieldHeight(16)
                .setGrowX(true)
                .setOnChange(this::updateFilter);
        rebuildUi();
    }

    public void open(String title, List<Option> options, Consumer<Option> onSelect) {
        this.visible = true;
        this.collapsed = false;
        this.dragging = false;
        this.dragMoved = false;
        this.onSelect = onSelect;
        this.allOptions = options == null ? List.of() : List.copyOf(options);
        selector.setItems(this.allOptions);
        selector.setQuery("");
        this.filteredOptions = selector.items();
        windowNode.setTitle(title == null || title.isBlank() ? "Race Selector" : title);
        searchField.setText("");
        searchField.setFocused(true);
        rebuildUi();

        DirectRenderContext metrics = surface.measurementContext();
        if (metrics != null) panelHeight = Math.round(windowNode.preferredHeight(metrics, panelWidth));
        DirectViewport viewport = surface.viewport();
        panelX = Math.max(8, Math.round((viewport.uiWidth() - panelWidth) / 2.0f));
        panelY = Math.max(8, Math.round((viewport.uiHeight() - panelHeight) / 2.0f));
        AutismWindowLayout clamped = clampToViewport(new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        windowNode.syncShowBody(true);
        AutismOverlayManager.get().bringToFrontParent(this);
    }

    public void close() {
        visible = false;
        collapsed = false;
        dragging = false;
        dragMoved = false;
        onSelect = null;
        allOptions = List.of();
        filteredOptions = List.of();
        selector.setItems(List.of());
        surface.clearFocusedTextInputs();
        windowNode.syncShowBody(true);
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean hasTextFieldFocused() {
        return visible && searchField.isFocused();
    }

    private void updateFilter(String query) {
        selector.setItems(allOptions);
        selector.setQuery(query);
        filteredOptions = selector.items();
        if (listViewport != null) listViewport.jumpTo(0);
        rebuildUi();
    }

    private void rebuildUi() {
        windowNode.content().clearChildren();
        windowNode.content().add(searchField);
        listSlot.setPreferredHeight(computeViewportHeight(filteredOptions.size()));
        windowNode.content().add(listSlot);
        summaryLabel.setText(filteredOptions.size() + " choices");
        windowNode.content().add(summaryLabel);
    }

    private int computeViewportHeight(int count) {
        int rows = Math.max(5, Math.min(MAX_VISIBLE_ROWS, Math.max(1, count)));
        return rows * ROW_HEIGHT + 2;
    }

    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible || MC == null || MC.font == null) return;
        context.nextStratum();

        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);
        DirectRenderContext metrics = new DirectRenderContext(context, MC.font, viewport, theme, uiMouseX, uiMouseY, delta);
        windowNode.setShowBody(!collapsed);
        windowNode.setActive(true);
        windowNode.setHeaderHovered(isOverHeader(uiMouseX, uiMouseY));
        panelHeight = Math.max(theme.headerHeight(), Math.round(windowNode.preferredHeight(metrics, panelWidth)));
        AutismWindowLayout clamped = clampToViewport(new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        windowNode.setBounds(panelX, panelY, panelWidth, panelHeight);

        surface.render(context, mouseX, mouseY, delta);
        context.nextStratum();
        if (!collapsed) {
            renderList(context, uiMouseX, uiMouseY);
        }
    }

    private void renderList(GuiGraphicsExtractor context, float uiMouseX, float uiMouseY) {
        int x = Math.round(listSlot.x());
        int y = Math.round(listSlot.y());
        int w = Math.round(listSlot.width());
        int h = Math.round(listSlot.height());
        if (w <= 2 || h <= 2) return;
        if (filteredOptions.isEmpty()) {
            CompactListRenderer.drawEmptyState(context, textRenderer, "Nothing to pick.", x, y, w - SCROLLBAR_WIDTH - 1);
            return;
        }
        if (listViewport == null || listViewport.getX() != x || listViewport.getY() != y
                || listViewport.getWidth() != w || listViewport.getHeight() != h) {
            int oldScroll = listViewport == null ? 0 : listViewport.getScrollOffset();
            listViewport = new DirectScrollViewport(x, y, w, h, ROW_HEIGHT, SCROLLBAR_WIDTH);
            listViewport.jumpTo(oldScroll);
        }
        listViewport.setContentHeight(filteredOptions.size() * ROW_HEIGHT);
        listViewport.beginRender(context, theme.borderSoft(), theme.listFill());
        try {
            listViewport.renderSimple(context, filteredOptions.size(), (idx, bnd) -> renderRow(context, filteredOptions.get(idx), bnd.x, bnd.y, bnd.width, idx, uiMouseX, uiMouseY));
        } finally {
            listViewport.endRender(context);
        }
        listViewport.renderScrollbar(context, uiMouseX, uiMouseY);
    }

    private void renderRow(GuiGraphicsExtractor context, Option option, int x, int y, int width, int index, float mouseX, float mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
        int bg = hovered ? theme.rowFillHovered() : theme.rowFillNormal();
        int accent = "Condition".equalsIgnoreCase(option.category()) ? AutismColors.packetCyan() : AutismColors.accent();
        CompactSurfaces.tintedRow(context, x, y, width, ROW_HEIGHT, bg);
        CompactSurfaces.indicator(context, x, y, 2, ROW_HEIGHT, accent);
        String label = option.category() + " / " + option.label();
        String trimmed = UiText.trimToWidth(textRenderer, label, Math.max(1, width - 10), theme.fontFor(UiTone.BODY), theme.color(UiTone.BODY));
        int textY = UiSizing.alignTextY(y, ROW_HEIGHT, theme.fontHeight(UiTone.BODY), theme.bodyTextNudge());
        UiText.draw(context, textRenderer, trimmed, theme.fontFor(UiTone.BODY), theme.color(UiTone.BODY), x + 6, textY, false);
        CompactSurfaces.divider(context, x + 4, y + ROW_HEIGHT - 1, width - 8, AutismColors.packetRowDivider());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);
        if (button == 0 && isOverClose(uiMouseX, uiMouseY)) {
            close();
            return true;
        }
        if (button == 0 && isOverHeader(uiMouseX, uiMouseY)) {
            dragging = true;
            dragMoved = false;
            dragOffsetX = uiMouseX - panelX;
            dragOffsetY = uiMouseY - panelY;
            return true;
        }
        if (!collapsed && surface.mouseClicked(mouseX, mouseY, button)) return true;
        if (!collapsed && button == 0 && uiContains(listSlot.x(), listSlot.y(), listSlot.width(), listSlot.height(), uiMouseX, uiMouseY)) {
            if (listViewport != null && listViewport.mouseClicked(uiMouseX, uiMouseY, button)) return true;
            int index = (int) ((uiMouseY - listSlot.y() + (listViewport == null ? 0 : listViewport.getScrollOffset())) / ROW_HEIGHT);
            if (index >= 0 && index < filteredOptions.size()) {
                Option selected = filteredOptions.get(index);
                if (onSelect != null) onSelect.accept(selected);
                close();
                return true;
            }
        }
        if (!uiContains(panelX, panelY, panelWidth, panelHeight, uiMouseX, uiMouseY)) {
            surface.clearFocusedTextInputs();
            return true;
        }
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);
        if (button == 0 && dragging) {
            dragging = false;
            if (!dragMoved && isOverHeader(uiMouseX, uiMouseY) && !isOverClose(uiMouseX, uiMouseY)) {
                setCollapsed(!collapsed);
            }
            dragMoved = false;
            return true;
        }
        if (button == 0 && listViewport != null) listViewport.mouseReleased();
        if (!collapsed && surface.mouseReleased(mouseX, mouseY, button)) return true;
        return visible;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);
        if (dragging && button == 0) {
            int nextX = Math.round(uiMouseX - dragOffsetX);
            int nextY = Math.round(uiMouseY - dragOffsetY);
            if (nextX != panelX || nextY != panelY) dragMoved = true;
            AutismWindowLayout clamped = clampToViewport(new AutismWindowLayout(nextX, nextY, panelWidth, panelHeight, visible, collapsed));
            panelX = clamped.x;
            panelY = clamped.y;
            return true;
        }
        if (!collapsed && listViewport != null && listViewport.isScrollbarDragging()) {
            listViewport.mouseDragged(uiMouseX, uiMouseY);
            return true;
        }
        return !collapsed && surface.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible || collapsed || listViewport == null) return visible;
        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);
        if (uiContains(listSlot.x(), listSlot.y(), listSlot.width(), listSlot.height(), uiMouseX, uiMouseY)) {
            listViewport.mouseScrolled(uiMouseX, uiMouseY, amount);
            return true;
        }
        return visible;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (collapsed) return false;
        return surface.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char chr, int modifiers) {
        return visible && !collapsed && surface.charTyped(chr, modifiers);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);
        int renderedHeight = collapsed ? theme.headerHeight() : panelHeight;
        return uiContains(panelX, panelY, panelWidth, renderedHeight, uiMouseX, uiMouseY);
    }

    @Override
    public boolean isOverDragBar(double mouseX, double mouseY) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);
        return isOverHeader(uiMouseX, uiMouseY) && !isOverClose(uiMouseX, uiMouseY);
    }

    @Override
    public boolean isCollapsed() {
        return collapsed;
    }

    @Override
    public void setCollapsed(boolean collapsed) {
        if (this.collapsed == collapsed) return;
        this.collapsed = collapsed;
        dragging = false;
        dragMoved = false;
        windowNode.syncShowBody(!collapsed);
        if (collapsed) clearHiddenInteractionState();
        saveLayout();
    }

    @Override
    public AutismWindowLayout getBounds() {
        return new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed);
    }

    @Override
    public void setBounds(AutismWindowLayout bounds) {
        if (bounds == null) return;
        AutismWindowLayout clamped = clampToViewport(bounds);
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        visible = clamped.visible;
        collapsed = clamped.collapsed;
        windowNode.syncShowBody(!collapsed);
    }

    @Override
    public void clearTextFieldFocus() {
        surface.clearFocusedTextInputs();
    }

    @Override
    public int getMinWidth() {
        return MIN_PANEL_WIDTH;
    }

    @Override
    public int getMinHeight() {
        return theme.headerHeight() + 20;
    }

    private boolean isOverHeader(float x, float y) {
        return x >= panelX && x < panelX + panelWidth && y >= panelY && y < panelY + theme.headerHeight();
    }

    private boolean isOverClose(float x, float y) {
        return OverlayTopBar.isOverClose(UiBounds.of(panelX, panelY, panelWidth, Math.max(theme.headerHeight(), panelHeight)),
            theme.headerHeight(), x, y);
    }

    private boolean uiContains(float x, float y, float w, float h, float mx, float my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private AutismWindowLayout clampToViewport(AutismWindowLayout layout) {
        DirectViewport viewport = surface.viewport();
        int margin = 4;
        int viewportW = Math.round(viewport.uiWidth());
        int viewportH = Math.round(viewport.uiHeight());
        int availableW = Math.max(1, viewportW - margin * 2);
        int availableH = Math.max(theme.headerHeight(), viewportH - margin * 2);
        int width = Math.max(Math.min(MIN_PANEL_WIDTH, availableW), Math.min(layout.width, availableW));
        int minHeight = layout.collapsed ? theme.headerHeight() : theme.headerHeight() + 20;
        int height = Math.max(Math.min(minHeight, availableH), Math.min(layout.height, availableH));
        int renderedHeight = layout.collapsed ? theme.headerHeight() : height;
        int x = Math.max(margin, Math.min(layout.x, Math.max(margin, viewportW - margin - width)));
        int y = Math.max(margin, Math.min(layout.y, Math.max(margin, viewportH - margin - renderedHeight)));
        return new AutismWindowLayout(x, y, width, height, layout.visible, layout.collapsed);
    }
}
