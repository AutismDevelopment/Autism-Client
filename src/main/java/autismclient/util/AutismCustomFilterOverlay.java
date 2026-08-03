package autismclient.util;

import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import autismclient.gui.vanillaui.components.CompactListViewport;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactOverlayControls;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.ScrollState;
import autismclient.gui.vanillaui.components.CompactTextInput;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.direct.DirectViewport;
import autismclient.modules.AutismModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AutismCustomFilterOverlay extends AutismOverlayBase {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final int DEFAULT_PANEL_WIDTH = 220;
    private static final int MIN_PANEL_WIDTH = 220;
    private static final int ACTION_HEIGHT = 13;
    private static final int ACTION_GAP = 2;
    private static final int SECTION_GAP = 6;
    private static final int SEARCH_HEIGHT = 16;
    private static final int FILTER_ROW_HEIGHT = 14;
    private static final int FILTER_FOOTER_HEIGHT = 12;
    private static final int FILTER_FOOTER_GAP = 4;
    private static final int FILTER_ROW_STEP = FILTER_ROW_HEIGHT + 1;
    private static final int SCROLLBAR_GUTTER = 8;
    private static final int SCROLLBAR_TRACK_WIDTH = 3;
    private static final int SCROLLBAR_NONE = 0;
    private static final int SCROLLBAR_C2S = 1;
    private static final int SCROLLBAR_S2C = 2;

    private final Font textRenderer;
    private final CompactTheme theme = new CompactTheme();
    private final CompactTextInput c2sSearchField;
    private final CompactTextInput s2cSearchField;
    private final AutismPacketSelectorOverlay packetSelectorOverlay;
    private final AutismCustomFilterPresetOverlay presetManagerOverlay;
    private final List<ActionButton> buttons = new ArrayList<>();
    private final PacketListState c2sListState = new PacketListState();
    private final PacketListState s2cListState = new PacketListState();

    private int panelX = 260;
    private int panelY = 36;
    private int panelWidth = DEFAULT_PANEL_WIDTH;
    private int panelHeight = 326;
    private boolean visible = false;
    private boolean collapsed = false;

    private boolean dragging = false;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    private int activeScrollbarDrag = SCROLLBAR_NONE;
    private int scrollbarGrabOffset = 0;

    public AutismCustomFilterOverlay(Font textRenderer) {
        this.textRenderer = textRenderer;
        this.panelWidth = defaultPanelWidth();
        this.panelHeight = defaultPanelHeight();
        this.c2sSearchField = createField("Search C2S packets...");
        this.s2cSearchField = createField("Search S2C packets...");
        this.packetSelectorOverlay = new AutismPacketSelectorOverlay(textRenderer);
        this.presetManagerOverlay = new AutismCustomFilterPresetOverlay(textRenderer);

        this.c2sSearchField.setOnChange(text -> c2sListState.scroll.jumpTo(0, 0));
        this.s2cSearchField.setOnChange(text -> s2cListState.scroll.jumpTo(0, 0));
    }

    private CompactTextInput createField(String placeholder) {
        CompactTextInput field = new CompactTextInput()
            .setPlaceholder(placeholder)
            .setFieldHeight(searchHeight())
            .setMinWidth(120)
            .setPreferredWidth(120)
            .setTextTone(UiTone.BODY)
            .setPlaceholderTone(UiTone.MUTED);
        return field;
    }

    public AutismCustomFilterPresetOverlay getPresetManagerOverlay() {
        return presetManagerOverlay;
    }

    public AutismPacketSelectorOverlay getPacketSelectorOverlay() {
        return packetSelectorOverlay;
    }

    @Override
    public String getOverlayId() {
        return "autism-custom-filter";
    }

    @Override
    public int getMinWidth() {
        return defaultPanelWidth();
    }

    @Override
    public int getMinHeight() {
        return defaultPanelHeight();
    }

    @Override
    public AutismWindowLayout getBounds() {
        return new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed);
    }

    @Override
    public void setBounds(AutismWindowLayout bounds) {
        if (bounds == null) return;
        AutismWindowLayout clamped = clampToScreen(this, bounds);
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        visible = clamped.visible;
        collapsed = clamped.collapsed;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            AutismOverlayManager.get().bringToFront(this);
        } else {
            clearFocus();
            packetSelectorOverlay.close();
        }
        saveLayout();
    }

    public void toggle() {
        setVisible(!visible);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public boolean isCollapsed() {
        return collapsed;
    }

    @Override
    public void setCollapsed(boolean collapsed) {
        if (this.collapsed == collapsed) return;
        this.collapsed = collapsed;
        if (collapsed) {
            clearHiddenInteractionState();
            packetSelectorOverlay.close();
        }
        saveLayout();
    }

    @Override
    public boolean usesSharedHeaderClickCollapse() {
        return true;
    }

    @Override
    public boolean isOverDragBar(double mouseX, double mouseY) {
        if (!visible) return false;
        AutismWindowLayout bounds = getBounds();
        return mouseX >= panelX && mouseX <= panelX + panelWidth
            && mouseY >= panelY && mouseY <= panelY + HEADER_HEIGHT
            && !isOverWindowControl(mouseX, mouseY, bounds);
    }

    @Override
    public boolean hasTextFieldFocused() {
        return packetSelectorOverlay.isVisible()
            || c2sSearchField.isFocused()
            || s2cSearchField.isFocused();
    }

    @Override
    public void clearTextFieldFocus() {
        clearFocus();
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        buttons.clear();
        AutismWindowLayout clamped = clampToScreen(this, new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        AutismWindowLayout bounds = new AutismWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed);
        renderWindowFrame(context, mouseX, mouseY, bounds, "Packet Delay", collapsed, dragging);
        boolean clipBody = beginWindowBodyClip(context, bounds, collapsed);
        if (!clipBody) {
            renderWindowInactiveOverlay(context, bounds, collapsed, dragging);
            return;
        }

        try {
            int x = panelX + panelInset();
            int y = panelY + HEADER_HEIGHT + topInset();
            int width = panelWidth - (panelInset() * 2);
            AutismModule module = AutismModule.get();

            y = drawToggleAction(context, mouseX, mouseY, x, y, width,
                "Custom Packets " + (module.shouldUseCustomPackets() ? "On" : "Off"),
                () -> module.setUseCustomPackets(!module.shouldUseCustomPackets()),
                module.shouldUseCustomPackets(),
                "custom-filter:enabled");

            AutismText.draw(context, textRenderer,
                "C2S " + module.getC2SPackets().size() + " | S2C " + module.getS2CPackets().size(),
                AutismText.Tone.MUTED, x, y, false);
            y += infoLineHeight() + actionGap();

            y = drawAction(context, mouseX, mouseY, x, y, width, "Presets", () -> {
                presetManagerOverlay.toggle();
                AutismOverlayManager.get().bringToFront(presetManagerOverlay);
            }, CompactOverlayButton.Variant.SECONDARY, true);
            y += sectionGap() - actionGap();

            int availableHeight = Math.max(sectionMinimumHeight() * 2 + sectionGap(), panelHeight - (y - panelY) - panelInset());
            int sectionHeight = Math.max(sectionMinimumHeight(), (availableHeight - sectionGap()) / 2);

            y = renderPacketSection(context, mouseX, mouseY, delta, x, y, width, sectionHeight, true, module, c2sSearchField, c2sListState);
            renderPacketSection(context, mouseX, mouseY, delta, x, y, width, sectionHeight, false, module, s2cSearchField, s2cListState);
        } finally {
            endWindowBodyClip(context, clipBody);
            renderWindowInactiveOverlay(context, bounds, collapsed, dragging);
        }

        if (packetSelectorOverlay.isVisible()) {
            packetSelectorOverlay.render(context, mouseX, mouseY, delta);
        }
    }

    private int renderPacketSection(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta,
                                    int x, int y, int width, int sectionHeight, boolean c2s,
                                    AutismModule module, CompactTextInput searchField, PacketListState listState) {
        int sectionStartY = y;
        String label = c2s ? "C2S Packets" : "S2C Packets";
        Set<Class<? extends Packet<?>>> selectedPackets = c2s ? module.getC2SPackets() : module.getS2CPackets();
        List<Class<? extends Packet<?>>> filteredPackets = getCachedPackets(listState, selectedPackets, searchField.text());

        CompactListRenderer.drawHeader(context, textRenderer, label + " (" + selectedPackets.size() + ")", x, y);
        y += headerLabelHeight() + 2;

        searchField.setBounds(x, y, width, searchHeight());
        searchField.render(renderContext(context, mouseX, mouseY, delta));
        y += searchHeight() + actionGap();

        y = drawAction(context, mouseX, mouseY, x, y, width, c2s ? "Add C2S" : "Add S2C", () -> {
            if (c2s) {
                packetSelectorOverlay.openToggleC2S((packetClass, selected) -> setPacketSelected(true, packetClass, selected), module.getC2SPackets());
            } else {
                packetSelectorOverlay.openToggleS2C((packetClass, selected) -> setPacketSelected(false, packetClass, selected), module.getS2CPackets());
            }
        }, CompactOverlayButton.Variant.SECONDARY, true);

        int listHeight = Math.max(listMinimumHeight(), sectionHeight - (y - sectionStartY) - footerHeight() - footerGap());
        CompactListViewport.Layout baseLayout = packetListLayout(x, y, width, listHeight, filteredPackets.size(), 0);
        int visualScroll = listState.scroll.tick(delta, baseLayout.maxScroll());
        CompactListViewport.Layout listLayout = packetListLayout(x, y, width, listHeight, filteredPackets.size(), visualScroll);
        boolean focused = AutismOverlayManager.get().isFocusedOverlay(this) || AutismOverlayManager.get().isTopOverlay(this);
        listLayout.drawFrame(context, focused);
        int contentWidth = Math.max(24, listLayout.contentWidth());
        listState.setBounds(x, y, width, listHeight, listLayout.scrollbar());

        if (filteredPackets.isEmpty()) {
            CompactListRenderer.drawEmptyState(context, textRenderer, "No packets", x, y, contentWidth);
        } else {
            listLayout.beginRows(context);
            try {
                listLayout.forEachVisibleRow(filteredPackets.size(), (index, rowY) -> {
                    Class<? extends Packet<?>> packetClass = filteredPackets.get(index);
                    boolean hovered = mouseX >= x + 2 && mouseX < x + 2 + contentWidth && mouseY >= rowY && mouseY < rowY + filterRowHeight();
                    String packetName = AutismPacketNamer.getFriendlyName(packetClass);
                    CompactListRenderer.drawRow(
                        context,
                        textRenderer,
                        packetName,
                        x + 2,
                        rowY,
                        contentWidth,
                        filterRowHeight(),
                        hovered,
                        false,
                        c2s ? CompactListRenderer.RowTone.WARNING : CompactListRenderer.RowTone.NORMAL
                    );
                    int iconSize = filterRowHeight() - 2;
                    int iconX = x + 2 + contentWidth - iconSize - 1;
                    int iconY = rowY + 1;
                    CompactListRenderer.drawDeleteButton(context, iconX, iconY, iconSize, hovered);
                    CompactListRenderer.drawDivider(context, x + 2, rowY + filterRowHeight(), contentWidth);
                    buttons.add(new ActionButton(x + 2, rowY, contentWidth, filterRowHeight(), () -> removePacket(c2s, packetClass), true));
                });
            } finally {
                listLayout.endRows(context);
            }
        }

        listLayout.drawScrollbar(context, mouseX, mouseY, activeScrollbarDrag == (c2s ? SCROLLBAR_C2S : SCROLLBAR_S2C));

        String footer = filteredPackets.isEmpty()
            ? ""
            : (listLayout.maxScroll() > 0
                ? "Scroll: " + (Math.min(filteredPackets.size(), Math.round(listState.scroll.targetOffset() / (float) filterRowStep()) + 1))
                    + "/" + (Math.max(1, Math.round(listLayout.maxScroll() / (float) filterRowStep()) + 1))
                : "Click a row to remove");
        if (!footer.isEmpty()) {
            AutismText.draw(context, textRenderer, footer, AutismText.Tone.MUTED, x + 4, y + listHeight + footerGap(), false);
        }

        return sectionStartY + sectionHeight + sectionGap();
    }

    private List<Class<? extends Packet<?>>> filterPackets(List<Class<? extends Packet<?>>> packets, String query) {
        if (query == null || query.isBlank()) return packets;

        String lowered = query.toLowerCase(Locale.ROOT);
        List<Class<? extends Packet<?>>> filtered = new ArrayList<>();
        for (Class<? extends Packet<?>> packetClass : packets) {
            String name = AutismPacketNamer.getFriendlyName(packetClass).toLowerCase(Locale.ROOT);
            String fullName = packetClass.getName().toLowerCase(Locale.ROOT);
            if (name.contains(lowered) || fullName.contains(lowered)) filtered.add(packetClass);
        }
        return filtered;
    }

    private List<Class<? extends Packet<?>>> getCachedPackets(PacketListState state,
                                                               Set<Class<? extends Packet<?>>> packets,
                                                               String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int sourceHash = packets == null ? 0 : packets.hashCode();
        int sourceSize = packets == null ? 0 : packets.size();
        if (state.sourceHash == sourceHash && state.sourceSize == sourceSize && state.query.equals(normalizedQuery)) {
            return state.filteredPackets;
        }

        List<Class<? extends Packet<?>>> sorted = packets == null ? new ArrayList<>() : new ArrayList<>(packets);
        sorted.sort(Comparator.comparing(AutismPacketNamer::getFriendlyName, String.CASE_INSENSITIVE_ORDER));
        state.filteredPackets = List.copyOf(filterPackets(sorted, normalizedQuery));
        state.sourceHash = sourceHash;
        state.sourceSize = sourceSize;
        state.query = normalizedQuery;
        return state.filteredPackets;
    }

    private void addPacket(boolean c2s, Class<? extends Packet<?>> packetClass) {
        AutismModule module = AutismModule.get();
        Set<Class<? extends Packet<?>>> packets = new LinkedHashSet<>(c2s ? module.getC2SPackets() : module.getS2CPackets());
        if (packets.add(packetClass)) {
            if (c2s) module.setC2SPackets(packets);
            else module.setS2CPackets(packets);
        }
    }

    private void removePacket(boolean c2s, Class<? extends Packet<?>> packetClass) {
        AutismModule module = AutismModule.get();
        Set<Class<? extends Packet<?>>> packets = new LinkedHashSet<>(c2s ? module.getC2SPackets() : module.getS2CPackets());
        if (packets.remove(packetClass)) {
            if (c2s) module.setC2SPackets(packets);
            else module.setS2CPackets(packets);
        }
    }

    private void setPacketSelected(boolean c2s, Class<? extends Packet<?>> packetClass, boolean selected) {
        if (selected) {
            addPacket(c2s, packetClass);
        } else {
            removePacket(c2s, packetClass);
        }
    }

    private int drawAction(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int width, String label, Runnable action,
                           CompactOverlayButton.Variant variant, boolean enabled) {
        CompactOverlayControls.action(context, textRenderer, x, y, width, actionHeight(), label,
            enabled ? variant : CompactOverlayButton.Variant.GHOST, enabled, mouseX, mouseY);
        buttons.add(new ActionButton(x, y, width, actionHeight(), action, enabled));
        return y + actionHeight() + actionGap();
    }

    private int drawToggleAction(GuiGraphicsExtractor context, int mouseX, int mouseY, int x, int y, int width, String label, Runnable action,
                                 boolean enabled, String animationKey) {
        CompactOverlayControls.toggle(context, textRenderer, x, y, width, actionHeight(), label, enabled, animationKey, mouseX, mouseY);
        buttons.add(new ActionButton(x, y, width, actionHeight(), action, true));
        return y + actionHeight() + actionGap();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (packetSelectorOverlay.isVisible()) return packetSelectorOverlay.mouseClicked(mouseX, mouseY, button);

        if (button == 0 && mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= panelY && mouseY < panelY + HEADER_HEIGHT) {
            AutismWindowLayout bounds = getBounds();
            if (isOverCloseButton(mouseX, mouseY, bounds)) {
                setVisible(false);
                return true;
            }
            if (isOverCollapseButton(mouseX, mouseY, bounds)) {
                toggleCollapsed();
                return true;
            }
            dragging = true;
            dragOffsetX = mouseX - panelX;
            dragOffsetY = mouseY - panelY;
            clearFocus();
            return true;
        }

        if (collapsed) return false;

        if (button == 0) {
            if (tryStartScrollbarDrag(c2sListState, SCROLLBAR_C2S, mouseX, mouseY)) return true;
            if (tryStartScrollbarDrag(s2cListState, SCROLLBAR_S2C, mouseX, mouseY)) return true;
        }

        if (handleTextFieldClick(c2sSearchField, mouseX, mouseY, button)) {
            c2sListState.scroll.jumpTo(0, 0);
            return true;
        }
        if (handleTextFieldClick(s2cSearchField, mouseX, mouseY, button)) {
            s2cListState.scroll.jumpTo(0, 0);
            return true;
        }

        clearFocus();
        if (button == 0) {
            for (ActionButton actionButton : buttons) {
                if (actionButton.contains(mouseX, mouseY)) {
                    if (!actionButton.enabled()) return true;
                    actionButton.action.run();
                    return true;
                }
            }
        }

        return isMouseOver(mouseX, mouseY);
    }

    private boolean handleTextFieldClick(CompactTextInput field, double mouseX, double mouseY, int button) {
        boolean clicked = field.mouseClicked(inputContext(mouseX, mouseY), (float) mouseX, (float) mouseY, button);
        if (clicked) {
            clearFocus();
            field.setFocused(true);
        }
        return clicked;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (packetSelectorOverlay.isVisible() && packetSelectorOverlay.mouseReleased(mouseX, mouseY, button)) return true;
        if (c2sSearchField.mouseReleased(inputContext(mouseX, mouseY), (float) mouseX, (float) mouseY, button)) return true;
        if (s2cSearchField.mouseReleased(inputContext(mouseX, mouseY), (float) mouseX, (float) mouseY, button)) return true;
        if (button == 0 && activeScrollbarDrag != SCROLLBAR_NONE) {
            activeScrollbarDrag = SCROLLBAR_NONE;
            return true;
        }
        if (button == 0) {
            if (dragging) saveLayout();
            dragging = false;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (packetSelectorOverlay.isVisible() && packetSelectorOverlay.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        if (c2sSearchField.mouseDragged(inputContext(mouseX, mouseY), (float) mouseX, (float) mouseY, button, (float) deltaX, (float) deltaY)) return true;
        if (s2cSearchField.mouseDragged(inputContext(mouseX, mouseY), (float) mouseX, (float) mouseY, button, (float) deltaX, (float) deltaY)) return true;
        if (activeScrollbarDrag == SCROLLBAR_C2S) {
            c2sListState.scroll.setFromThumbStepped(c2sListState.scrollbarMetrics, mouseY, scrollbarGrabOffset, filterRowStep());
            return true;
        }
        if (activeScrollbarDrag == SCROLLBAR_S2C) {
            s2cListState.scroll.setFromThumbStepped(s2cListState.scrollbarMetrics, mouseY, scrollbarGrabOffset, filterRowStep());
            return true;
        }
        if (dragging && button == 0) {
            AutismWindowLayout nextBounds = clampToScreen(this,
                new AutismWindowLayout((int) (mouseX - dragOffsetX), (int) (mouseY - dragOffsetY),
                    panelWidth, panelHeight, visible, collapsed));
            panelX = nextBounds.x;
            panelY = nextBounds.y;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (packetSelectorOverlay.isVisible()) return packetSelectorOverlay.mouseScrolled(mouseX, mouseY, amount);
        if (c2sListState.contains(mouseX, mouseY)) {
            int maxScroll = packetListLayout(c2sListState.x, c2sListState.y, c2sListState.width, c2sListState.height, c2sListState.filteredPackets.size(), 0).maxScroll();
            c2sListState.scroll.nudge(amount, filterRowStep(), maxScroll);
            return true;
        }
        if (s2cListState.contains(mouseX, mouseY)) {
            int maxScroll = packetListLayout(s2cListState.x, s2cListState.y, s2cListState.width, s2cListState.height, s2cListState.filteredPackets.size(), 0).maxScroll();
            s2cListState.scroll.nudge(amount, filterRowStep(), maxScroll);
            return true;
        }
        return false;
    }

    private CompactListViewport.Layout packetListLayout(int x, int y, int width, int listHeight, int rowCount, int scrollOffset) {
        return CompactListViewport.layout(
            x, y, width, listHeight, rowCount, filterRowHeight(), filterRowStep(),
            scrollOffset, scrollbarTrackWidth(), scrollbarGutter()
        );
    }

    private boolean tryStartScrollbarDrag(PacketListState listState, int dragId, double mouseX, double mouseY) {
        CompactScrollbar.Metrics metrics = listState.scrollbarMetrics;
        if (metrics == null || !metrics.hasScroll()) return false;
        if (!metrics.contains(mouseX, mouseY)) {
            return false;
        }

        activeScrollbarDrag = dragId;
        scrollbarGrabOffset = Math.max(0, (int) mouseY - metrics.thumbY());
        listState.scroll.setFromThumbStepped(metrics, mouseY, scrollbarGrabOffset, filterRowStep());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (packetSelectorOverlay.isVisible()) return packetSelectorOverlay.keyPressed(keyCode, scanCode, modifiers);
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            clearFocus();
            return false;
        }

        CompactTextInput focusedField = getFocusedField();
        if (focusedField == null) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) return true;

        focusedField.keyPressed(inputContext(0, 0), keyCode, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (packetSelectorOverlay.isVisible()) return packetSelectorOverlay.charTyped(chr, modifiers);
        if (!visible) return false;

        CompactTextInput focusedField = getFocusedField();
        if (focusedField == null) return false;
        return focusedField.charTyped(inputContext(0, 0), chr, modifiers);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;
        if (packetSelectorOverlay.isVisible()) return true;

        AutismWindowLayout bounds = getBounds();
        int frameHeight = collapsed ? HEADER_HEIGHT : panelHeight;
        if (mouseX >= bounds.x && mouseX <= bounds.x + bounds.width && mouseY >= bounds.y && mouseY <= bounds.y + frameHeight) return true;
        return isMouseOverField(c2sSearchField, mouseX, mouseY)
            || isMouseOverField(s2cSearchField, mouseX, mouseY);
    }

    private CompactTextInput getFocusedField() {
        if (c2sSearchField.isFocused()) return c2sSearchField;
        if (s2cSearchField.isFocused()) return s2cSearchField;
        return null;
    }

        private int defaultPanelWidth() {
        return 220;
    }

    private int defaultPanelHeight() {
        return 308;
    }

    private int panelInset() {
        return 10;
    }

    private int topInset() {
        return 6;
    }

    private int actionHeight() {
        return 13;
    }

    private int actionGap() {
        return 2;
    }

    private int sectionGap() {
        return 6;
    }

    private int searchHeight() {
        return 16;
    }

    private int filterRowHeight() {
        return 14;
    }

    private int filterRowStep() {
        return filterRowHeight() + 1;
    }

    private int footerHeight() {
        return 12;
    }

    private int footerGap() {
        return 4;
    }

    private int scrollbarGutter() {
        return 8;
    }

    private int scrollbarTrackWidth() {
        return 3;
    }

    private int infoLineHeight() {
        return 10;
    }

    private int headerLabelHeight() {
        return 10;
    }

    private int sectionMinimumHeight() {
        return 86;
    }

    private int listMinimumHeight() {
        return 40;
    }

    private void clearFocus() {
        c2sSearchField.setFocused(false);
        s2cSearchField.setFocused(false);
    }

    private DirectRenderContext renderContext(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        DirectViewport viewport = DirectViewport.current(1.0f);
        return new DirectRenderContext(context, textRenderer, viewport, theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), delta);
    }

    private DirectRenderContext inputContext(double mouseX, double mouseY) {
        DirectViewport viewport = DirectViewport.current(1.0f);
        return new DirectRenderContext(null, textRenderer, viewport, theme, viewport.toUiX(mouseX), viewport.toUiY(mouseY), 0.0f);
    }

    private boolean isMouseOverField(CompactTextInput field, double mouseX, double mouseY) {
        return mouseX >= field.x() && mouseX <= field.x() + field.width()
            && mouseY >= field.y() && mouseY <= field.y() + field.height();
    }

    private record ActionButton(int x, int y, int width, int height, Runnable action, boolean enabled) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private static final class PacketListState {
        private int x;
        private int y;
        private int width;
        private int height;
        private final ScrollState scroll = new ScrollState();
        private CompactScrollbar.Metrics scrollbarMetrics = null;
        private List<Class<? extends Packet<?>>> filteredPackets = List.of();
        private int sourceHash;
        private int sourceSize = -1;
        private String query = "";

        private void setBounds(int x, int y, int width, int height, CompactScrollbar.Metrics scrollbarMetrics) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.scrollbarMetrics = scrollbarMetrics;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
}
