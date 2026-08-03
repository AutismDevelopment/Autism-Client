package autismclient.util;

import autismclient.gui.multi.MultiMenuInput;
import autismclient.gui.multi.MultiMenuRenderer;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.UiScissorStack;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactWindow;
import autismclient.util.multi.MultiClientCommands;
import autismclient.util.multi.MultiManager;
import autismclient.util.multi.MultiSession;
import autismclient.util.multi.MultiSharedGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AutismMultiGuiOverlay extends AutismOverlayBase {
    private static final int CELL = 18;
    private static final int PAD = 7;
    private static final int TITLE_H = 13;
    private static final int INFO_H = 12;
    private static final int TOOLBAR_H = 17;
    private static final int GRID_BOTTOM_MARGIN = 3;
    private static final int PANEL_BG = 0xFFC6C6C6;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_HOVER = 0x80FFFFFF;
    private static final int TEXT = 0xFFF2F2F2;
    private static final int BORDER = 0xFF463A40;
    private static final int SUCCESS = 0xFF35D873;
    private static final int DANGER = 0xFFFF5B5B;

    private final Minecraft mc = Minecraft.getInstance();
    private final Font font;
    private final List<ActionHit> actions = new ArrayList<>();
    private final List<SlotHit> slotHits = new ArrayList<>();
    private final List<MultiMenuRenderer.MenuHit> widgetHits = new ArrayList<>();
    private final MultiMenuInput menuInput = new MultiMenuInput();
    private String accountId = "";
    private String accountName = "";

    private final boolean shared;
    private String sharedKey = "";
    private List<MultiSharedGui.Group> sharedGroups = List.of();
    private MultiSession.MenuView view;
    private String viewFor = "";
    private long viewRevision = Long.MIN_VALUE;
    private int contentWidth = CELL;
    private int contentHeight = CELL;
    private int scrollY;
    private int gridX;
    private int gridY;
    private int gridWidth;
    private int gridHeight;
    private boolean dragging;
    private boolean scrollbarDragging;
    private int scrollbarGrab;
    private double dragOffsetX;
    private double dragOffsetY;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private int hoveredHandler = -1;
    private int hoveredHotbar = -1;
    private int hoveredX;
    private int hoveredY;
    private int[] closeSilentToolbarRect;

    public AutismMultiGuiOverlay(Font font, String accountId) {
        this(font, accountId, false);
    }

    public static AutismMultiGuiOverlay shared(Font font) {
        return new AutismMultiGuiOverlay(font, "@shared", true);
    }

    private AutismMultiGuiOverlay(Font font, String accountId, boolean shared) {
        super("autism-multi-gui-" + (shared ? "shared" : accountId == null ? "" : accountId), 230, shared ? 258 : 238);
        this.font = font;
        this.shared = shared;
        this.accountId = shared || accountId == null ? "" : accountId;

        panelX = 548;
        panelY = 30 + (shared ? 0 : Math.floorMod(this.accountId.hashCode(), 8) * 14);
    }

    public boolean open() {
        if (shared) {
            if (!MultiManager.get().isActive()) {
                AutismNotifications.show("No active Multi batch", DANGER);
                return false;
            }
            sharedGroups = MultiSharedGui.groups();
            adoptSharedGroup();
        } else {
            MultiSession.Snapshot snapshot = findSnapshot(accountId);
            if (snapshot == null) {
                AutismNotifications.show("Multi session is unavailable", DANGER);
                return false;
            }
            accountName = snapshot.accountName();
        }
        viewRevision = Long.MIN_VALUE;
        scrollY = 0;
        refreshView();
        sizeForCurrentView();
        setCollapsed(false);
        setVisible(true);
        return true;
    }

    public boolean isOpenFor(String targetAccountId) {

        return visible && !shared && targetAccountId != null && targetAccountId.equals(accountId);
    }

    void onServerMenuInvalidated(boolean closeHostedViewer) {
        view = null;
        viewFor = "";
        viewRevision = Long.MIN_VALUE;
        slotHits.clear();
        hoveredStack = ItemStack.EMPTY;
        hoveredHandler = -1;
        scrollY = 0;
        if (closeHostedViewer) setVisible(false);
    }

    private MultiSharedGui.Group currentSharedGroup() {
        return MultiSharedGui.pick(sharedGroups, sharedKey);
    }

    private List<String> sharedIds() {
        MultiSharedGui.Group group = currentSharedGroup();
        return group == null ? List.of() : group.accountIds();
    }

    private void adoptSharedGroup() {
        MultiSharedGui.Group group = currentSharedGroup();
        accountId = group == null ? "" : group.representativeId();
        accountName = group == null ? "Shared GUI"
            : "Shared GUI - " + group.size() + (group.size() == 1 ? " bot" : " bots");
    }

    private void cycleSharedGroup() {
        if (sharedGroups.size() < 2) return;
        MultiSharedGui.Group current = currentSharedGroup();
        int index = 0;
        for (int i = 0; i < sharedGroups.size(); i++) {
            if (current != null && sharedGroups.get(i).key().equals(current.key())) {
                index = i;
                break;
            }
        }
        sharedKey = sharedGroups.get((index + 1) % sharedGroups.size()).key();
        scrollY = 0;
    }

    @Override
    public void setVisible(boolean value) {
        if (!value) {
            dragging = false;
            scrollbarDragging = false;
            hoveredStack = ItemStack.EMPTY;
            hoveredHandler = -1;
        }
        super.setVisible(value);
    }

    @Override public int getMinWidth() { return 128; }
    @Override public int getMinHeight() { return 92; }
    @Override public OverlayScope getDefaultOverlayScope() { return OverlayScope.BACKGROUND_STATUS; }

    @Override public boolean persistsAcrossScreenClose() { return true; }
    @Override public boolean wantsKeyboardCapture() { return visible && !collapsed; }

    @Override
    public void restoreLayout() {
        AutismWindowLayout layout = AutismSharedState.get().getWindowLayout(getOverlayId());
        if (layout != null) {
            setBounds(new AutismWindowLayout(layout.x, layout.y, layout.width, layout.height, visible, collapsed));
        }
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        MultiManager manager = MultiManager.getIfInitialized();
        if (manager == null || !manager.isActive() || (!shared && findSnapshot(accountId) == null)) {
            setVisible(false);
            return;
        }

        if (mc == null || mc.gui.screen() == null) return;
        if (shared) {
            sharedGroups = MultiSharedGui.groups();
            adoptSharedGroup();
        } else {
            MultiSession.Snapshot snapshot = findSnapshot(accountId);
            if (snapshot == null) {
                setVisible(false);
                return;
            }
            accountName = snapshot.accountName();
        }
        refreshView();
        AutismWindowLayout bounds = clampToScreen(this);
        panelX = bounds.x;
        panelY = bounds.y;
        panelWidth = bounds.width;
        panelHeight = bounds.height;
        renderGuiWindowFrame(graphics, mouseX, mouseY);
        if (collapsed) return;

        actions.clear();
        slotHits.clear();
        widgetHits.clear();
        int hoverForInfo = hoveredHandler;
        hoveredStack = ItemStack.EMPTY;
        hoveredHandler = -1;
        hoveredHotbar = -1;
        boolean clipped = beginWindowBodyClip(graphics, getBounds(), false);
        int innerX = panelX + PAD;
        int innerWidth = Math.max(1, panelWidth - PAD * 2);
        int titleY = panelY + HEADER_HEIGHT + 3;

        Component title = view == null || view.title() == null
            ? Component.literal(shared ? "Waiting for a bot GUI..." : "GUI") : view.title();
        UiScissorStack.global().push(graphics, UiBounds.of(innerX, titleY, innerWidth, TITLE_H));
        graphics.text(font, title.getVisualOrderText(), innerX, titleY, text(), false);
        UiScissorStack.global().pop(graphics);

        int toolbarY = titleY + TITLE_H;

        if (view != null) {
            UiScissorStack.global().push(graphics, UiBounds.of(innerX, toolbarY, innerWidth, 10));
            int keyColor = AutismTheme.recolor(0xFFB79E9E, AutismTheme.Channel.TEXT);
            int mx2 = innerX;
            mx2 = drawMetricInline(graphics, mx2, toolbarY, "Rev: ", Integer.toString(view.stateId()),
                keyColor, AutismTheme.recolor(0xFFFF4A4A, AutismTheme.Channel.ACCENT));
            mx2 = drawMetricInline(graphics, mx2 + 8, toolbarY, "SyncID: ", Integer.toString(view.syncId()),
                keyColor, AutismTheme.recolor(0xFFF3ECE7, AutismTheme.Channel.TEXT));

            int visibleSlot = hoverForInfo >= 0
                ? MultiManager.get().visibleSlotForHandler(accountId, hoverForInfo) : -1;
            drawMetricInline(graphics, mx2 + 8, toolbarY, "Slot: ",
                visibleSlot >= 0 ? Integer.toString(visibleSlot) : "--", keyColor,
                AutismTheme.recolor(0xFF8FD7FF, AutismTheme.Channel.ACCENT));
            UiScissorStack.global().pop(graphics);
            toolbarY += INFO_H;
        }
        if (shared) {

            MultiSharedGui.Group group = currentSharedGroup();
            String pickerLabel = group == null ? "No GUIs open yet"
                : "GUI: " + group.label() + (sharedGroups.size() > 1 ? "  >" : "");
            action(graphics, innerX, toolbarY, innerWidth, pickerLabel,
                sharedGroups.size() > 1 ? success() : border(), mouseX, mouseY, this::cycleSharedGroup);
            toolbarY += TOOLBAR_H + 3;
        }
        int gap = 3;
        int firstWidth = Math.max(1, (innerWidth - gap) / 2);
        closeSilentToolbarRect = new int[]{innerX, toolbarY, firstWidth, TOOLBAR_H};
        action(graphics, innerX, toolbarY, firstWidth, "Close W/O Pkt", border(), mouseX, mouseY,
            () -> toolbarClose(true));
        action(graphics, innerX + firstWidth + gap, toolbarY, innerWidth - firstWidth - gap,
            "Close", border(), mouseX, mouseY,
            () -> toolbarClose(false));

        int areaTop = toolbarY + TOOLBAR_H + 6;
        int availableHeight = Math.max(CELL,
            panelY + panelHeight - PAD - GRID_BOTTOM_MARGIN - areaTop);

        boolean scroll = contentHeight > availableHeight;
        int scrollbarSpace = scroll ? 6 : 0;
        gridWidth = Math.max(1, Math.min(contentWidth, innerWidth - scrollbarSpace));
        gridHeight = Math.min(contentHeight, availableHeight);
        gridX = innerX + Math.max(0, (innerWidth - gridWidth - scrollbarSpace) / 2);
        gridY = areaTop;
        scrollY = clamp(scrollY, 0, maxScroll());
        renderGrid(graphics, mouseX, mouseY);
        CompactScrollbar.Metrics scrollbar = scrollbarMetrics();
        CompactScrollbar.draw(graphics, scrollbar, scrollbar.contains(mouseX, mouseY), scrollbarDragging);

        endWindowBodyClip(graphics, clipped);

        ItemStack carried = view == null ? ItemStack.EMPTY : view.carried();
        if (carried != null && !carried.isEmpty()) {
            graphics.nextStratum();
            graphics.item(carried, mouseX - 8, mouseY - 8);
            graphics.itemDecorations(font, carried, mouseX - 8, mouseY - 8);
        } else if (!hoveredStack.isEmpty()) {
            renderItemTooltip(graphics, hoveredStack, hoveredX, hoveredY, hoveredHotbar >= 0);
        } else if (autismclient.util.AutismConfig.getGlobal().multiShowTooltips
                && closeSilentToolbarRect != null && autismclient.gui.multi.MultiTooltip.hovered(
                closeSilentToolbarRect[0], closeSilentToolbarRect[1], closeSilentToolbarRect[2],
                closeSilentToolbarRect[3], mouseX, mouseY)) {
            autismclient.gui.multi.MultiTooltip.render(graphics, font,
                "Hide GUI locally, keep it open", mouseX, mouseY);
        }
    }

    private void renderGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiBounds area = UiBounds.of(gridX - 3, gridY - 3, gridWidth + 6, gridHeight + 6);
        UiRenderer.rect(graphics, area, PANEL_BG);
        int selectedHandler = MultiManager.get().selectedHotbarHandler(accountId);
        UiScissorStack.global().push(graphics, UiBounds.of(gridX, gridY, gridWidth, gridHeight));
        if (view != null) {
            for (MultiSession.ViewSlot slot : view.slots()) {
                int x = gridX + slot.x();
                int y = gridY + slot.y() - scrollY;
                if (x + CELL <= gridX || x >= gridX + gridWidth || y + CELL <= gridY || y >= gridY + gridHeight) continue;
                drawSlotCell(graphics, x, y);
                if (selectedHandler >= 0 && slot.handler() == selectedHandler) {

                    UiRenderer.frame(graphics, UiBounds.of(x, y, CELL, CELL), 0x1AFF4040, 0xB0FF4040);
                }
                ItemStack stack = slot.item();
                if (stack != null && !stack.isEmpty()) {
                    try {
                        graphics.item(stack, x + 1, y + 1);
                        graphics.itemDecorations(font, stack, x + 1, y + 1);
                    } catch (Throwable ignored) {

                    }
                }
                slotHits.add(new SlotHit(x, y, slot.handler(), stack == null ? ItemStack.EMPTY : stack));
                if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
                    && mouseX >= gridX && mouseX < gridX + gridWidth
                    && mouseY >= gridY && mouseY < gridY + gridHeight) {
                    UiRenderer.rect(graphics, UiBounds.of(x + 1, y + 1, 16, 16), SLOT_HOVER);
                    hoveredStack = stack == null ? ItemStack.EMPTY : stack;
                    hoveredHandler = slot.handler();
                    hoveredHotbar = MultiManager.get().hotbarIndexForHandler(accountId, slot.handler());
                    hoveredX = mouseX;
                    hoveredY = mouseY;
                }
            }

            menuInput.sync(viewTypeId());
            MultiMenuRenderer.render(graphics, font, view, gridX, gridY, scrollY, mouseX, mouseY, widgetHits, menuInput);
        }
        UiScissorStack.global().pop(graphics);
    }

    private void renderGuiWindowFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean active = dragging || AutismOverlayManager.get().isFocusedOverlay(this)
            || AutismOverlayManager.get().isTopOverlay(this);
        CompactWindow.renderFrame(
            UiContexts.overlay(graphics, font, mouseX, mouseY),
            UiBounds.of(panelX, panelY, panelWidth, collapsed ? HEADER_HEIGHT : panelHeight),
            shared ? accountName : "GUI - " + accountName,
            collapsed,
            false,
            true,
            mouseY >= panelY && mouseY < panelY + HEADER_HEIGHT,
            active,
            4,
            4,
            HEADER_HEIGHT
        );
    }

    @Override
    protected boolean isOverCollapseButton(double mouseX, double mouseY, AutismWindowLayout bounds) {
        return false;
    }

    private void drawSlotCell(GuiGraphicsExtractor graphics, int x, int y) {
        UiRenderer.rect(graphics, UiBounds.of(x, y, CELL, CELL), SLOT_FILL);
        UiRenderer.rect(graphics, UiBounds.of(x, y, CELL, 1), SLOT_SHADOW);
        UiRenderer.rect(graphics, UiBounds.of(x, y, 1, CELL), SLOT_SHADOW);
        UiRenderer.rect(graphics, UiBounds.of(x, y + CELL - 1, CELL, 1), SLOT_LIGHT);
        UiRenderer.rect(graphics, UiBounds.of(x + CELL - 1, y, 1, CELL), SLOT_LIGHT);
    }

    private void action(GuiGraphicsExtractor graphics, int x, int y, int width, String label, int outline,
                        int mouseX, int mouseY, Runnable callback) {
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TOOLBAR_H;
        Button.render(UiContexts.overlay(graphics, font, mouseX, mouseY),
            UiBounds.of(x, y, width, TOOLBAR_H), label, Button.Tone.NORMAL, hover, false);
        actions.add(new ActionHit(x, y, width, TOOLBAR_H, callback));
    }

    private int lastSizedContentW = -1;
    private int lastSizedContentH = -1;
    private int lastSizedScreenH = -1;

    private void refreshView() {
        long revision = MultiManager.get().menuRevision(accountId);
        if (revision == viewRevision && view != null && accountId.equals(viewFor)) {

            if (autismclient.util.AutismUiScale.getVirtualScreenHeight() != lastSizedScreenH) sizeForCurrentView();
            return;
        }
        viewRevision = revision;
        viewFor = accountId;
        view = MultiManager.get().menuView(accountId);

        int[] cs = MultiMenuRenderer.contentSize(view);
        contentWidth = Math.max(CELL, cs[0]);
        contentHeight = Math.max(CELL, cs[1]);
        menuInput.sync(viewTypeId());

        if (contentWidth != lastSizedContentW || contentHeight != lastSizedContentH
            || autismclient.util.AutismUiScale.getVirtualScreenHeight() != lastSizedScreenH) sizeForCurrentView();
        scrollY = clamp(scrollY, 0, maxScroll());
    }

    private int chromeHeight() {
        int info = view != null ? INFO_H : 0;
        return HEADER_HEIGHT + 3 + TITLE_H + info + (shared ? TOOLBAR_H + 3 : 0) + TOOLBAR_H + 6 + GRID_BOTTOM_MARGIN + PAD;
    }

    private void sizeForCurrentView() {
        lastSizedContentW = contentWidth;
        lastSizedContentH = contentHeight;
        lastSizedScreenH = autismclient.util.AutismUiScale.getVirtualScreenHeight();
        int screenH = Math.max(120, lastSizedScreenH);
        int maxGridH = Math.max(CELL, screenH - chromeHeight() - 20);
        int gridH = Math.min(contentHeight, maxGridH);
        boolean needsScroll = contentHeight > gridH;
        int wantedWidth = Math.max(getMinWidth(), contentWidth + PAD * 2 + (needsScroll ? 6 : 0));
        int wantedHeight = Math.max(getMinHeight(), chromeHeight() + gridH);
        setBounds(new AutismWindowLayout(panelX, panelY, wantedWidth, wantedHeight, true, false));
    }

    private MultiSession.Snapshot findSnapshot(String id) {
        if (id == null || id.isBlank()) return null;
        for (MultiSession.Snapshot snapshot : MultiManager.get().snapshots()) {
            if (id.equals(snapshot.accountId())) return snapshot;
        }
        return null;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - Math.max(CELL, gridHeight));
    }

    private CompactScrollbar.Metrics scrollbarMetrics() {
        return CompactScrollbar.compute(contentHeight, Math.max(CELL, gridHeight),
            gridX + gridWidth + 2, gridY, 3, Math.max(CELL, gridHeight), scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        AutismWindowLayout bounds = getBounds();
        if (isOverCloseButton(mouseX, mouseY, bounds)) {
            setVisible(false);
            return true;
        }
        if (button == 0 && scrollbarMetrics().overThumb(mouseX, mouseY)) {
            scrollbarDragging = true;
            scrollbarGrab = (int) Math.round(mouseY) - scrollbarMetrics().thumbY();
            return true;
        }
        if (button == 0 && isOverDragBar(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - panelX;
            dragOffsetY = mouseY - panelY;
            return true;
        }
        if (collapsed) return false;
        if (button == 0) {
            for (ActionHit action : actions) {
                if (action.hit(mouseX, mouseY)) {
                    action.callback().run();
                    return true;
                }
            }
        }
        boolean insideGrid = mouseX >= gridX && mouseX < gridX + gridWidth
            && mouseY >= gridY && mouseY < gridY + gridHeight;
        if (insideGrid && button == 0) {
            for (MultiMenuRenderer.MenuHit hit : widgetHits) {
                if (mouseX < hit.x() || mouseX >= hit.x() + hit.w() || mouseY < hit.y() || mouseY >= hit.y() + hit.h()) continue;
                dispatchWidget(hit.action());
                return true;
            }
        }
        if (insideGrid) {
            for (SlotHit slot : slotHits) {
                if (!slot.hit(mouseX, mouseY)) continue;
                if (button == 1 && ctrlDown() && shiftDown()) {
                    if (!slot.stack().isEmpty()) openNbt(slot.stack(), (int) mouseX, (int) mouseY);
                    return true;
                }
                if (slot.handler() < 0) return true;
                if (view == null || !view.interactive()) {
                    AutismNotifications.show(view != null && view.synchronizationBlocked()
                        ? "Inventory is waiting for a server update" : "Inventory is synchronizing", danger());
                    return true;
                }
                MultiClientCommands.ClickSpec spec = MultiClientCommands.fromMouse(button, shiftDown(), ctrlDown());
                if (shared) handleFanout(MultiManager.get().clickBotSlots(sharedIds(), slot.handler(), spec));
                else handleClickResult(MultiManager.get().clickBotSlot(accountId, slot.handler(), spec));
                return true;
            }
        }
        if (insideGrid) return true;
        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        if (dragging) {
            dragging = false;
            saveLayout();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollbarDragging) {
            scrollY = CompactScrollbar.scrollFromThumb(scrollbarMetrics(), mouseY, scrollbarGrab);
            return true;
        }
        if (dragging) {
            AutismWindowLayout next = clampToScreen(this, new AutismWindowLayout(
                (int) Math.round(mouseX - dragOffsetX), (int) Math.round(mouseY - dragOffsetY),
                panelWidth, panelHeight, visible, collapsed));
            panelX = next.x;
            panelY = next.y;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible || collapsed || !isMouseOver(mouseX, mouseY)) return false;
        scrollY = clamp(scrollY - (int) Math.signum(amount) * CELL, 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (menuInput.rename.focused() && menuInput.rename.keyPressed(keyCode)) {
            sendRename();
            return true;
        }

        if (AutismOverlayManager.get().isAnyTextFieldFocused()) return false;

        if (!collapsed && keyCode == GLFW.GLFW_KEY_Q && hoveredHandler >= 0
            && view != null && view.interactive()) {
            boolean wholeStack = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || ctrlDown();
            MultiClientCommands.ClickSpec spec = MultiClientCommands.dropSpec(wholeStack);
            if (shared) handleFanout(MultiManager.get().clickBotSlots(sharedIds(), hoveredHandler, spec));
            else handleClickResult(MultiManager.get().clickBotSlot(accountId, hoveredHandler, spec));
            return true;
        }

        if (!collapsed && hoveredHotbar >= 0 && view != null && view.interactive()
            && (keyCode == GLFW.GLFW_KEY_U || keyCode == GLFW.GLFW_KEY_I)) {
            boolean use = keyCode == GLFW.GLFW_KEY_I;
            if (shared) {
                handleFanout(use ? MultiManager.get().useBotHotbars(sharedIds(), hoveredHotbar)
                    : MultiManager.get().selectBotHotbars(sharedIds(), hoveredHotbar));
            } else {
                handleClickResult(use
                    ? MultiManager.get().useBotHotbar(accountId, hoveredHotbar)
                    : MultiManager.get().selectBotHotbar(accountId, hoveredHotbar));
            }
            return true;
        }
        return false;
    }

    @Override public boolean charTyped(char chr, int modifiers) {
        if (menuInput.rename.focused() && menuInput.rename.charTyped(chr)) {
            sendRename();
            return true;
        }
        return false;
    }

    private void sendRename() {
        if (shared) MultiManager.get().renameBotItems(sharedIds(), menuInput.rename.text(), viewTypeId());
        else MultiManager.get().renameBotItem(accountId, menuInput.rename.text());
    }

    private String viewTypeId() {
        return view != null && view.extras() != null ? view.extras().typeId() : "";
    }

    private void dispatchWidget(MultiMenuRenderer.MenuAction action) {
        if (action instanceof MultiMenuRenderer.RenameFocusAct) {
            menuInput.rename.focus();
            menuInput.rename.set("");
            return;
        }
        if (action instanceof MultiMenuRenderer.BeaconPick p) {
            if (p.secondary()) menuInput.beaconSecondary = p.effectId();
            else menuInput.beaconPrimary = p.effectId();
            return;
        }
        if (view == null || !view.interactive()) {
            AutismNotifications.show("Inventory is synchronizing", danger());
            return;
        }
        MultiManager mgr = MultiManager.get();
        String type = viewTypeId();
        if (action instanceof MultiMenuRenderer.ButtonAct b) {
            if (shared) handleFanout(mgr.buttonClickBots(sharedIds(), b.id(), type));
            else handleClickResult(mgr.buttonClickBot(accountId, b.id()));
        } else if (action instanceof MultiMenuRenderer.TradeAct t) {
            if (shared) handleFanout(mgr.selectTradeBots(sharedIds(), t.index(), type));
            else handleClickResult(mgr.selectTradeBot(accountId, t.index()));
        } else if (action instanceof MultiMenuRenderer.BeaconAct be) {
            if (shared) handleFanout(mgr.setBeaconBots(sharedIds(), be.primary(), be.secondary(), type));
            else handleClickResult(mgr.setBeaconBot(accountId, be.primary(), be.secondary()));
        } else if (action instanceof MultiMenuRenderer.RecipeStep rs) {
            menuInput.recipeIndex = Math.max(0, menuInput.recipeIndex + rs.delta());
            if (shared) handleFanout(mgr.buttonClickBots(sharedIds(), menuInput.recipeIndex, type));
            else handleClickResult(mgr.buttonClickBot(accountId, menuInput.recipeIndex));
        }
    }

    private void handleActionResult(MultiManager.BroadcastResult result) {
        if (result == null || result.failed() > 0) {
            AutismNotifications.show("GUI action failed", danger());
        } else if (result.sent() == 0 && result.skipped() > 0) {
            AutismNotifications.show("GUI action skipped", border());
        }
    }

    private void handleClickResult(String result) {
        if ("Sent".equals(result)) return;
        String message = result == null || result.isBlank() ? "GUI action failed" : MultiManager.singleLine(result, 80);
        AutismNotifications.show(message, danger());
    }

    private void handleFanout(MultiManager.BroadcastResult result) {
        if (result == null) return;
        int missed = result.failed() + result.skipped();
        if (result.sent() > 0 && missed == 0) return;
        AutismNotifications.show(result.sent() == 0 ? "No bot took the click"
            : "Sent " + result.sent() + ", missed " + missed, result.sent() == 0 ? danger() : border());
    }

    private void toolbarClose(boolean silent) {
        Set<String> scope = shared ? new java.util.LinkedHashSet<>(sharedIds()) : Set.of(accountId);
        if (shared && scope.isEmpty()) return;
        handleActionResult(silent ? MultiManager.get().closeSilentOnScope(scope)
            : MultiManager.get().closeOnScope(scope));
    }

    private void openNbt(ItemStack stack, int mouseX, int mouseY) {
        AutismItemNbtInspectOverlay overlay = AutismItemNbtInspectOverlay.getSharedOverlay(font);
        if (overlay == null) return;
        overlay.open(stack, mouseX + 8, mouseY);
        AutismOverlayManager.get().register(overlay, OverlayScope.BACKGROUND_STATUS);
        AutismOverlayManager.get().bringToFront(overlay);
    }

    private void renderItemTooltip(GuiGraphicsExtractor graphics, ItemStack stack, int mouseX, int mouseY, boolean hotbar) {
        try {
            List<Component> base = Screen.getTooltipFromItem(mc, stack);
            if (base == null || base.isEmpty()) return;
            List<Component> lines = base;
            if (hotbar) {

                lines = new ArrayList<>(base);
                lines.add(Component.literal("[U] switch to slot"));
                lines.add(Component.literal("[I] switch + use item"));
            }
            int width = 0;
            for (Component line : lines) width = Math.max(width, font.width(line));
            int height = lines.size() == 1 ? 8 : lines.size() * 10 - 2;
            int screenWidth = AutismUiScale.getVirtualScreenWidth();
            int screenHeight = AutismUiScale.getVirtualScreenHeight();
            int x = mouseX + 12;
            int y = mouseY - 12;
            if (x + width + 4 > screenWidth) x = Math.max(4, mouseX - width - 16);
            if (y + height + 4 > screenHeight) y = screenHeight - height - 4;
            if (y < 4) y = 4;
            graphics.nextStratum();
            UiRenderer.rect(graphics, UiBounds.of(x - 3, y - 3, width + 6, height + 6), 0xF0100010);
            UiRenderer.frame(graphics, UiBounds.of(x - 3, y - 3, width + 6, height + 6), 0, 0x505000A0);
            int lineY = y;
            for (Component line : lines) {
                graphics.text(font, line.getVisualOrderText(), x, lineY, 0xFFFFFFFF, true);
                lineY += 10;
            }
        } catch (Throwable ignored) {

        }
    }

    private String trim(String value, int width) {
        String safe = value == null ? "" : value;
        if (font.width(safe) <= width) return safe;
        return font.plainSubstrByWidth(safe, Math.max(1, width - font.width("..."))) + "...";
    }

    private boolean ctrlDown() {
        if (mc == null || mc.getWindow() == null) return false;
        long window = mc.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private boolean shiftDown() {
        if (mc == null || mc.getWindow() == null) return false;
        long window = mc.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private int text() { return AutismTheme.recolor(TEXT, AutismTheme.Channel.TEXT); }
    private int border() { return AutismTheme.recolor(BORDER, AutismTheme.Channel.OUTLINE); }

    private int drawMetricInline(GuiGraphicsExtractor graphics, int x, int y, String key, String value,
                                 int keyColor, int valueColor) {
        graphics.text(font, Component.literal(key).getVisualOrderText(), x, y, keyColor, false);
        int vx = x + font.width(key);
        graphics.text(font, Component.literal(value).getVisualOrderText(), vx, y, valueColor, false);
        return vx + font.width(value);
    }
    private int success() { return AutismTheme.recolor(SUCCESS, AutismTheme.Channel.SUCCESS); }
    private int danger() { return AutismTheme.recolor(DANGER, AutismTheme.Channel.DANGER); }
    private static int tint(int color, int alpha) { return (alpha << 24) | (color & 0x00FFFFFF); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private record ActionHit(int x, int y, int width, int height, Runnable callback) {
        boolean hit(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record SlotHit(int x, int y, int handler, ItemStack stack) {
        boolean hit(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
        }
    }
}
