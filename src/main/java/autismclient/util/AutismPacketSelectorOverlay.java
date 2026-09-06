package autismclient.util;

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
import autismclient.gui.vanillaui.components.SearchableSelector;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectViewport;
import autismclient.gui.vanillaui.direct.DirectViewportSlot;
import autismclient.gui.vanillaui.direct.DirectWindow;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.Packet;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AutismPacketSelectorOverlay extends AutismOverlayBase {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final int MIN_PANEL_WIDTH = 230;
    private static final int ROW_HEIGHT = 16;
    private static final int MAX_VISIBLE_ROWS = 12;
    private static final int PAD = 6;
    private static final int VIEWPORT_BORDER = 1;
    private static final int HEADER_CONTROL = 12;
    private static final int HEADER_ARROW_WIDTH = 10;
    private static final int HEADER_ARROW_GAP = 3;
    private static final float HEADER_CLICK_DRAG_THRESHOLD = 3.0f;
    private static final int PACKET_LIST_SCROLLBAR_WIDTH = 6;

    private final Font textRenderer;
    private final CompactTheme theme = new CompactTheme();
    private final DirectWindow windowNode = new DirectWindow("Select Packet");
    private final DirectSurface surface = new DirectSurface(theme, windowNode);
    private final CompactTextInput searchField = new CompactTextInput();
    private final DirectUiLabel summaryLabel = new DirectUiLabel("", UiTone.MUTED).setTrimToBounds(true);
    private final DirectViewportSlot listSlot = new DirectViewportSlot();
    private DirectScrollViewport listViewport = null;

    private final List<Class<? extends Packet<?>>> allPackets = new ArrayList<>();
    private final List<Class<? extends Packet<?>>> sortedC2sPackets = new ArrayList<>();
    private final List<Class<? extends Packet<?>>> sortedS2cPackets = new ArrayList<>();
    private final List<Class<? extends Packet<?>>> sortedAnyPackets = new ArrayList<>();
    private final Set<Class<? extends Packet<?>>> c2sPacketSet = new LinkedHashSet<>();
    private final Map<Class<? extends Packet<?>>, String> packetSearchKeys = new HashMap<>();
    private final SearchableSelector<Class<? extends Packet<?>>> selector =
        new SearchableSelector<>(packetClass -> packetSearchKeys.computeIfAbsent(packetClass, AutismPacketSelectorOverlay::searchKey));
    private List<Class<? extends Packet<?>>> activePool = List.of();
    private List<Class<? extends Packet<?>>> filteredPackets = List.of();
    private Set<Class<? extends Packet<?>>> excludedPackets = Set.of();
    private Set<Class<? extends Packet<?>>> selectedPackets = Set.of();

    private boolean visible = false;
    private boolean collapsed = false;
    private boolean dragging = false;
    private boolean dragMoved = false;
    private float dragOffsetX;
    private float dragOffsetY;
    private float pressStartUiX;
    private float pressStartUiY;
    private int pressStartPanelX;
    private int pressStartPanelY;
    private int panelX;
    private int panelY;
    private int panelWidth = MIN_PANEL_WIDTH;
    private int panelHeight = 0;
    private int contentHeight = 0;
    private boolean scrollbarDragging = false;
    private int scrollbarGrabOffset = 0;

    private Consumer<Class<? extends Packet<?>>> onSelect;
    private BiConsumer<Class<? extends Packet<?>>, Boolean> onToggleSelect;
    private boolean closeOnSelect = true;
    private boolean toggleMode = false;
    private boolean uiDirty = true;

    public AutismPacketSelectorOverlay(Font textRenderer) {
        this.textRenderer = textRenderer;
        sortedC2sPackets.addAll(AutismPacketRegistry.getC2SPackets());
        sortedC2sPackets.sort(Comparator.comparing(AutismPacketNamer::getFriendlyName, String.CASE_INSENSITIVE_ORDER));
        sortedS2cPackets.addAll(AutismPacketRegistry.getS2CPackets());
        sortedS2cPackets.sort(Comparator.comparing(AutismPacketNamer::getFriendlyName, String.CASE_INSENSITIVE_ORDER));
        c2sPacketSet.addAll(sortedC2sPackets);
        sortedAnyPackets.addAll(sortedC2sPackets);
        sortedAnyPackets.addAll(sortedS2cPackets);
        this.allPackets.addAll(sortedAnyPackets);
        this.allPackets.sort(Comparator.comparing(AutismPacketNamer::getFriendlyName, String.CASE_INSENSITIVE_ORDER));
        for (Class<? extends Packet<?>> packetClass : allPackets) {
            packetSearchKeys.put(packetClass, searchKey(packetClass));
        }
        this.activePool = List.copyOf(allPackets);
        this.filteredPackets = List.copyOf(allPackets);
        buildUi();
    }

    private void buildUi() {
        panelWidth = panelMinimumWidth();
        windowNode.setCenterTitle(false);
        windowNode.setTitleTone(UiTone.LABEL);
        windowNode.setHeaderControls(true, true);
        windowNode.setTitleAreaInsets(panelPadding() + 2, panelPadding() + headerControlSize() + headerArrowWidth() + headerArrowGap() + 12);
        windowNode.content().setGap(windowContentGap()).setPadding(DirectUiInsets.all(panelPadding()));

        searchField
            .setPlaceholder("Search packets...")
            .setFieldHeight(searchFieldHeight())
            .setGrowX(true)
            .setOnChange(text -> {
                updateFilter(text);
            });

        rebuildUi();
    }

    private void rebuildUi() {
        windowNode.content().clearChildren();
        windowNode.setTitle(toggleMode ? "Packet Selector [Toggle]" : "Packet Selector");
        windowNode.content().add(searchField);
        listSlot.setPreferredHeight(computeViewportHeight(filteredPackets.size()));
        windowNode.content().add(listSlot);
        summaryLabel.setText(toggleMode
            ? filteredPackets.size() + " packets | Selected " + selectedPackets.size()
            : filteredPackets.size() + " packets");
        windowNode.content().add(summaryLabel);
        contentHeight = filteredPackets.size() * rowHeight();
        uiDirty = false;
    }

    private int computeViewportHeight(int itemCount) {
        int visibleRows = Math.max(5, Math.min(maxVisibleRows(), Math.max(1, itemCount)));
        return visibleRows * rowHeight() + (VIEWPORT_BORDER * 2);
    }

    public void open(Consumer<Class<? extends Packet<?>>> onSelect) {
        openWith(onSelect, allPackets, Set.of(), true);
    }

    public void openC2S(Consumer<Class<? extends Packet<?>>> onSelect) {
        openC2S(onSelect, Set.of(), true);
    }

    public void openC2S(Consumer<Class<? extends Packet<?>>> onSelect,
                        Collection<Class<? extends Packet<?>>> excludedPackets,
                        boolean closeOnSelect) {
        openWith(onSelect, sortedC2sPackets, excludedPackets, closeOnSelect);
    }

    public void openS2C(Consumer<Class<? extends Packet<?>>> onSelect) {
        openS2C(onSelect, Set.of(), true);
    }

    public void openS2C(Consumer<Class<? extends Packet<?>>> onSelect,
                        Collection<Class<? extends Packet<?>>> excludedPackets,
                        boolean closeOnSelect) {
        openWith(onSelect, sortedS2cPackets, excludedPackets, closeOnSelect);
    }

    public void openAny(Consumer<Class<? extends Packet<?>>> onSelect,
                        Collection<Class<? extends Packet<?>>> excludedPackets,
                        boolean closeOnSelect) {
        openWith(onSelect, sortedAnyPackets, excludedPackets, closeOnSelect);
    }

    public void openToggleC2S(BiConsumer<Class<? extends Packet<?>>, Boolean> onToggleSelect,
                              Collection<Class<? extends Packet<?>>> selectedPackets) {
        openToggleWith(onToggleSelect, sortedC2sPackets, selectedPackets, Set.of());
    }

    public void openToggleC2S(BiConsumer<Class<? extends Packet<?>>, Boolean> onToggleSelect,
                              Collection<Class<? extends Packet<?>>> selectedPackets,
                              Collection<Class<? extends Packet<?>>> excludedPackets) {
        openToggleWith(onToggleSelect, sortedC2sPackets, selectedPackets, excludedPackets);
    }

    public void openToggleS2C(BiConsumer<Class<? extends Packet<?>>, Boolean> onToggleSelect,
                              Collection<Class<? extends Packet<?>>> selectedPackets) {
        openToggleWith(onToggleSelect, sortedS2cPackets, selectedPackets, Set.of());
    }

    public void openToggleAny(BiConsumer<Class<? extends Packet<?>>, Boolean> onToggleSelect,
                              Collection<Class<? extends Packet<?>>> selectedPackets) {
        openToggleWith(onToggleSelect, sortedAnyPackets, selectedPackets, Set.of());
    }

    private void openWith(Consumer<Class<? extends Packet<?>>> onSelect,
                          List<Class<? extends Packet<?>>> pool,
                          Collection<Class<? extends Packet<?>>> excludedPackets,
                          boolean closeOnSelect) {
        this.visible = true;
        this.collapsed = false;
        this.onSelect = onSelect;
        this.onToggleSelect = null;
        this.closeOnSelect = closeOnSelect;
        this.toggleMode = false;
        this.excludedPackets = excludedPackets == null ? Set.of() : new LinkedHashSet<>(excludedPackets);
        this.selectedPackets = Set.of();
        this.activePool = filterExcluded(pool);
        updateFilter("");
        finishOpen();
    }

    private void openToggleWith(BiConsumer<Class<? extends Packet<?>>, Boolean> onToggleSelect,
                                List<Class<? extends Packet<?>>> pool,
                                Collection<Class<? extends Packet<?>>> selectedPackets,
                                Collection<Class<? extends Packet<?>>> excludedPackets) {
        this.visible = true;
        this.collapsed = false;
        this.onSelect = null;
        this.onToggleSelect = onToggleSelect;
        this.closeOnSelect = false;
        this.toggleMode = true;
        this.excludedPackets = excludedPackets == null ? Set.of() : new LinkedHashSet<>(excludedPackets);
        this.selectedPackets = selectedPackets == null ? new LinkedHashSet<>() : new LinkedHashSet<>(selectedPackets);
        this.activePool = filterExcluded(pool);
        updateFilter("");
        finishOpen();
    }

    private void finishOpen() {
        rebuildUi();
        DirectRenderContext metrics = surface.measurementContext();
        if (metrics != null) {
            panelHeight = Math.round(windowNode.preferredHeight(metrics, panelWidth));
        }
        DirectViewport viewport = surface.viewport();
        panelX = Math.max(8, Math.round((viewport.uiWidth() - panelWidth) / 2.0f));
        panelY = Math.max(8, Math.round((viewport.uiHeight() - panelHeight) / 2.0f));
        AutismWindowLayout clamped = clampToViewport(new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        searchField.setText("");
        searchField.setFocused(true);
        windowNode.syncShowBody(true);
        AutismOverlayManager.get().bringToFrontParent(this);
    }

    public void close() {
        visible = false;
        collapsed = false;
        dragging = false;
        dragMoved = false;
        onSelect = null;
        onToggleSelect = null;
        excludedPackets = Set.of();
        selectedPackets = Set.of();
        closeOnSelect = true;
        toggleMode = false;
        surface.clearFocusedTextInputs();
        windowNode.syncShowBody(true);
    }

    public boolean isVisible() {
        return visible;
    }

    private void updateFilter(String query) {
        List<Class<? extends Packet<?>>> pool = filterExcluded(activePool);
        selector.setItems(pool);
        selector.setQuery(query);
        filteredPackets = selector.items();
        contentHeight = filteredPackets.size() * rowHeight();
        rebuildUi();
    }

    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible || MC == null || MC.font == null) return;

        if (uiDirty) rebuildUi();

        context.nextStratum();

        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);
        boolean headerHovered = isOverHeaderUi(uiMouseX, uiMouseY);

        DirectRenderContext metrics = new DirectRenderContext(context, MC.font, viewport, theme, uiMouseX, uiMouseY, delta);
        windowNode.setShowBody(!collapsed);
        windowNode.setActive(true);
        windowNode.setHeaderHovered(headerHovered);

        panelHeight = Math.round(windowNode.preferredHeight(metrics, panelWidth));
        panelHeight = Math.max(theme.headerHeight(), panelHeight);
        AutismWindowLayout clamped = clampToViewport(new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        windowNode.setBounds(panelX, panelY, panelWidth, panelHeight);

        surface.render(context, mouseX, mouseY, delta);
        context.nextStratum();
        if (!collapsed) {
            renderlistViewport(context, viewport, uiMouseX, uiMouseY, delta);
        }

    }

    private void renderlistViewport(GuiGraphicsExtractor context, DirectViewport viewport, float uiMouseX, float uiMouseY, float delta) {
        int viewX = Math.round(listSlot.x());
        int viewY = Math.round(listSlot.y());
        int viewW = Math.round(listSlot.width());
        int viewH = Math.round(listSlot.height());
        if (viewW <= 2 || viewH <= 2) return;

        int contentHeight = filteredPackets.size() * rowHeight();

        if (listViewport == null || listViewport.getX() != viewX || listViewport.getY() != viewY
            || listViewport.getWidth() != viewW || listViewport.getHeight() != viewH) {
            int preservedScroll = listViewport == null ? 0 : listViewport.getScrollOffset();
            listViewport = new DirectScrollViewport(viewX, viewY, viewW, viewH, rowHeight(), PACKET_LIST_SCROLLBAR_WIDTH);
            listViewport.setContentHeight(contentHeight);
            listViewport.jumpTo(preservedScroll);
        }
        listViewport.setContentHeight(contentHeight);

        listViewport.beginRender(context, theme.borderSoft(), theme.listFill());
        try {
            listViewport.renderSimple(context, filteredPackets.size(), (idx, bnd) -> {
                Class<? extends Packet<?>> packetClass = filteredPackets.get(idx);
                renderPacketRowSimple(context, packetClass, bnd.x, bnd.y, bnd.width, idx);
            });
        } finally {
            listViewport.endRender(context);
        }

        renderScrollbar(context, viewX, viewY, viewW, viewH, uiMouseX, uiMouseY);
    }

    private void renderPacketRowSimple(GuiGraphicsExtractor context, Class<? extends Packet<?>> packetClass, int x, int y, int width, int index) {
        boolean c2s = c2sPacketSet.contains(packetClass);
        boolean selected = toggleMode && selectedPackets.contains(packetClass);
        int rowColor = selected ? AutismColors.packetRowSelectedBg(false) : AutismColors.packetRowBg(c2s, index, false);
        int color = selected ? AutismColors.packetRowSelectedText() : AutismColors.packetRowText(c2s, index);
        CompactSurfaces.tintedRow(context, x, y, width, rowHeight(), rowColor);

        String name = AutismPacketNamer.getFriendlyName(packetClass);
        int textWidth = width - 10;
        String trimmed = UiText.trimToWidth(textRenderer, name, textWidth, theme.fontFor(UiTone.BODY), color);
        int textY = UiSizing.alignTextY(y, rowHeight(), theme.fontHeight(UiTone.BODY), theme.bodyTextNudge());
        UiText.draw(context, textRenderer, trimmed, theme.fontFor(UiTone.BODY), color, x + 6, textY, false);
        CompactSurfaces.divider(context, x + 4, y + rowHeight() - 1, width - 8, AutismColors.packetRowDivider());
    }

    private void renderScrollbar(GuiGraphicsExtractor context, int viewX, int viewY, int viewW, int viewH, float uiMouseX, float uiMouseY) {
        if (listViewport != null) {
            listViewport.renderScrollbar(context, uiMouseX, uiMouseY);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);

        if (button == 0 && isOverCloseButton(uiMouseX, uiMouseY)) {
            close();
            return true;
        }

        if (button == 0 && isOverHeaderUi(uiMouseX, uiMouseY)) {
            dragging = true;
            dragMoved = false;
            dragOffsetX = uiMouseX - panelX;
            dragOffsetY = uiMouseY - panelY;
            pressStartUiX = uiMouseX;
            pressStartUiY = uiMouseY;
            pressStartPanelX = panelX;
            pressStartPanelY = panelY;
            return true;
        }

        if (!collapsed && surface.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (!collapsed && button == 0 && uiContains(listSlot.x(), listSlot.y(), listSlot.width(), listSlot.height(), uiMouseX, uiMouseY)) {
            if (listViewport != null && listViewport.mouseClicked(uiMouseX, uiMouseY, button)) {
                return true;
            }
        }

        if (!collapsed && button == 0 && uiContains(listSlot.x(), listSlot.y(), listSlot.width(), listSlot.height(), uiMouseX, uiMouseY)) {
            int index = (int) ((uiMouseY - listSlot.y() + listViewport.getScrollOffset()) / rowHeight());
            if (index >= 0 && index < filteredPackets.size()) {
                Class<? extends Packet<?>> selectedPacket = filteredPackets.get(index);
                if (toggleMode) {
                    selectedPackets = new LinkedHashSet<>(selectedPackets);
                    boolean nowSelected;
                    if (selectedPackets.contains(selectedPacket)) {
                        selectedPackets.remove(selectedPacket);
                        nowSelected = false;
                    } else {
                        selectedPackets.add(selectedPacket);
                        nowSelected = true;
                    }
                    if (onToggleSelect != null) onToggleSelect.accept(selectedPacket, nowSelected);
                    rebuildUi();
                    searchField.setFocused(true);
                } else {
                    if (onSelect != null) onSelect.accept(selectedPacket);
                    if (closeOnSelect) {
                        close();
                    } else {
                        excludedPackets = new LinkedHashSet<>(excludedPackets);
                        excludedPackets.add(selectedPacket);
                        activePool = filterExcluded(activePool);
                        updateFilter(searchField.text());
                        searchField.setFocused(true);
                    }
                }
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
            boolean moved = dragMoved
                || Math.abs(uiMouseX - pressStartUiX) >= HEADER_CLICK_DRAG_THRESHOLD
                || Math.abs(uiMouseY - pressStartUiY) >= HEADER_CLICK_DRAG_THRESHOLD
                || panelX != pressStartPanelX
                || panelY != pressStartPanelY;
            dragging = false;
            dragMoved = false;
            if (!moved && isOverHeaderUi(uiMouseX, uiMouseY) && !isOverCloseButton(uiMouseX, uiMouseY)) {
                setCollapsed(!collapsed);
            }
            return true;
        }

        if (button == 0 && listViewport != null) {
            listViewport.mouseReleased();
        }

        if (!collapsed && surface.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return visible && uiContains(panelX, panelY, panelWidth, panelHeight, uiMouseX, uiMouseY);
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

        if (!collapsed && uiContains(listSlot.x(), listSlot.y(), listSlot.width(), listSlot.height(), uiMouseX, uiMouseY)) {
            if (listViewport != null && listViewport.isScrollbarDragging()) {
                listViewport.mouseDragged(uiMouseX, uiMouseY);
                return true;
            }
        }

        if (!collapsed && surface.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return visible && uiContains(panelX, panelY, panelWidth, panelHeight, uiMouseX, uiMouseY);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (searchField.isFocused()) {
                searchField.setFocused(false);
                return true;
            }
            close();
            return true;
        }

        if (collapsed) return false;
        surface.keyPressed(keyCode, scanCode, modifiers);
        if (searchField.isFocused()) return true;
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        return visible && !collapsed && surface.charTyped(chr, modifiers);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible) return false;

        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);

        if (!collapsed && uiContains(listSlot.x(), listSlot.y(), listSlot.width(), listSlot.height(), uiMouseX, uiMouseY)) {
            if (listViewport != null) {
                listViewport.mouseScrolled(uiMouseX, uiMouseY, amount);
                return true;
            }
        }

        if (searchField.isFocused()) return false;

        return uiContains(panelX, panelY, panelWidth, panelHeight, uiMouseX, uiMouseY);
    }

    private List<Class<? extends Packet<?>>> filterExcluded(List<Class<? extends Packet<?>>> packets) {
        if (packets == null || packets.isEmpty()) return List.of();
        if (excludedPackets.isEmpty()) return new ArrayList<>(packets);
        List<Class<? extends Packet<?>>> filtered = new ArrayList<>(packets.size());
        for (Class<? extends Packet<?>> packetClass : packets) {
            if (!excludedPackets.contains(packetClass)) filtered.add(packetClass);
        }
        return filtered;
    }

    private static String searchKey(Class<? extends Packet<?>> packetClass) {
        if (packetClass == null) return "";
        return (packetClass.getSimpleName() + '\n' + AutismPacketNamer.getFriendlyName(packetClass))
            .toLowerCase(Locale.ROOT);
    }

    public boolean hasTextFieldFocused() {
        return visible && surface.hasFocusedTextInput();
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
    public void clearTextFieldFocus() {
        surface.clearFocusedTextInputs();
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
        return isOverHeaderUi(uiMouseX, uiMouseY) && !isOverCloseButton(uiMouseX, uiMouseY);
    }

    @Override
    public int getMinWidth() {
        return panelMinimumWidth();
    }

    @Override
    public int getMinHeight() {
        return theme.headerHeight() + 20;
    }

    private boolean isOverHeaderUi(float uiMouseX, float uiMouseY) {
        return uiMouseX >= panelX && uiMouseX < panelX + panelWidth
            && uiMouseY >= panelY && uiMouseY < panelY + theme.headerHeight();
    }

    private boolean isOverCloseButton(float uiMouseX, float uiMouseY) {
        return OverlayTopBar.isOverClose(UiBounds.of(panelX, panelY, panelWidth, Math.max(theme.headerHeight(), panelHeight)),
            theme.headerHeight(), uiMouseX, uiMouseY);
    }

    private AutismWindowLayout clampToViewport(AutismWindowLayout bounds) {
        DirectViewport viewport = surface.viewport();
        int margin = 4;
        int viewportW = Math.round(viewport.uiWidth());
        int viewportH = Math.round(viewport.uiHeight());
        int availableW = Math.max(1, viewportW - margin * 2);
        int availableH = Math.max(theme.headerHeight(), viewportH - margin * 2);
        int width = Math.max(Math.min(panelMinimumWidth(), availableW), Math.min(bounds.width, availableW));
        int minHeight = bounds.collapsed ? theme.headerHeight() : theme.headerHeight() + 20;
        int height = Math.max(Math.min(minHeight, availableH), Math.min(bounds.height, availableH));
        int renderedHeight = bounds.collapsed ? theme.headerHeight() : height;
        int x = Math.max(margin, Math.min(bounds.x, Math.max(margin, viewportW - margin - width)));
        int y = Math.max(margin, Math.min(bounds.y, Math.max(margin, viewportH - margin - renderedHeight)));
        return new AutismWindowLayout(x, y, width, height, bounds.visible, bounds.collapsed);
    }

    private boolean uiContains(float x, float y, float width, float height, float mouseX, float mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

        private int panelMinimumWidth() {
        return 230;
    }

    private int rowHeight() {
        return 16;
    }

    private int maxVisibleRows() {
        return 12;
    }

    private int panelPadding() {
        return 6;
    }

    private int windowContentGap() {
        return 4;
    }

    private int searchFieldHeight() {
        return 16;
    }

    private int headerControlSize() {
        return 12;
    }

    private int headerArrowWidth() {
        return 10;
    }

    private int headerArrowGap() {
        return 3;
    }
}
