package autismclient.util;

import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.direct.DirectLayout;
import autismclient.gui.vanillaui.components.CompactListRenderer;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactOverlayControls;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.ScrollState;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.Tooltip;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.modules.AutismModule;
import autismclient.util.AutismSharedState.QueuedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

public class AutismQueueEditorOverlay extends AutismOverlayBase {
    private static final Minecraft MC = Minecraft.getInstance();

    private int panelX = 100;
    private int panelY = 100;
    private int PANEL_WIDTH = 248;
    private int PANEL_HEIGHT = 176;

    private boolean isDragging = false;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;

    private int scrollOffset = 0;
    private boolean collapsed = false;
    private boolean visible = false;
    private boolean configurationOnly = false;
    private int selectedPacketId = -1;
    private boolean keepSelectedPacketVisible = false;
    private long lastObservedQueueChangeMs = 0L;
    private String lastObservedQueueSignature = "";
    private boolean pendingAutoReorder = false;
    private boolean scrollbarDragging = false;
    private int scrollbarGrabOffset = 0;

    private final Font textRenderer;
    private final CompactTheme theme = new CompactTheme();
    private final ScrollState listScrollState = new ScrollState();
    private List<QueuedPacket> cachedDisplayQueue = new ArrayList<>();
    private final List<ClickRegion> toolbarRegions = new ArrayList<>();
    private final List<RowRegion> rowRegions = new ArrayList<>();

    private String hoveredTooltip = null;
    private int tooltipX = 0;
    private int tooltipY = 0;

    public AutismQueueEditorOverlay(Font textRenderer) {
        this.textRenderer = textRenderer;
        this.PANEL_WIDTH = defaultPanelWidth();
        this.PANEL_HEIGHT = defaultPanelHeight();
    }

    private static class ClickRegion {
        final int x;
        final int y;
        final int width;
        final int height;
        final Runnable action;

        private ClickRegion(int x, int y, int width, int height, Runnable action) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.action = action;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private static class RowRegion {
        final QueuedPacket packet;
        final int x;
        final int y;
        final int width;
        final int height;
        final int delayX;
        final int delayWidth;
        final int removeX;
        final int removeWidth;
        final int modeX;
        final int modeWidth;

        private RowRegion(QueuedPacket packet, int x, int y, int width, int height,
                          int delayX, int delayWidth, int removeX, int removeWidth,
                          int modeX, int modeWidth) {
            this.packet = packet;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.delayX = delayX;
            this.delayWidth = delayWidth;
            this.removeX = removeX;
            this.removeWidth = removeWidth;
            this.modeX = modeX;
            this.modeWidth = modeWidth;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        private boolean overDelay(double mouseX, double mouseY) {
            return contains(mouseX, mouseY) && mouseX >= delayX && mouseX < delayX + delayWidth;
        }

        private boolean overRemove(double mouseX, double mouseY) {
            return contains(mouseX, mouseY) && mouseX >= removeX && mouseX < removeX + removeWidth;
        }

        private boolean overMode(double mouseX, double mouseY) {
            return contains(mouseX, mouseY) && mouseX >= modeX && mouseX < modeX + modeWidth;
        }
    }

    @Override
    public int getMinWidth() {
        return defaultPanelWidth();
    }

    @Override
    public int getMinHeight() {
        return minimumPanelHeight();
    }

    @Override
    public AutismWindowLayout getBounds() {
        return new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, visible, collapsed);
    }

    @Override
    public void setBounds(AutismWindowLayout bounds) {
        if (bounds == null) return;
        AutismWindowLayout clamped = clampToScreen(this, bounds);
        panelX = clamped.x;
        panelY = clamped.y;
        PANEL_WIDTH = clamped.width;
        PANEL_HEIGHT = clamped.height;
        visible = clamped.visible;
        collapsed = clamped.collapsed;
    }

    public boolean isCaptureMode() {
        return AutismSharedState.get().isCaptureMode();
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            scrollOffset = 0;
            listScrollState.jumpTo(0, 0);
            AutismOverlayManager.get().bringToFront(this);
        }
        saveState();
    }

    public void toggle() {
        setVisible(!visible);
    }

    public void setConfigurationOnly(boolean configurationOnly) {
        this.configurationOnly = configurationOnly;
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
        if (collapsed) clearHiddenInteractionState();
        saveLayout();
    }

    @Override
    public boolean usesSharedHeaderClickCollapse() {
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;
        List<QueuedPacket> displayQueue = cachedDisplayQueue.isEmpty() ? getCurrentQueue() : cachedDisplayQueue;
        int panelHeight = collapsed
            ? HEADER_HEIGHT
            : clampToScreen(this, new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, getPanelHeight(displayQueue), visible, collapsed)).height;
        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
            && mouseY >= panelY && mouseY <= panelY + panelHeight;
    }

    @Override
    public boolean isOverDragBar(double mouseX, double mouseY) {
        if (!visible) return false;
        AutismWindowLayout bounds = getBounds();
        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
            && mouseY >= panelY && mouseY <= panelY + HEADER_HEIGHT
            && !isOverWindowControl(mouseX, mouseY, bounds);
    }

    public void saveState() {
        AutismSharedState shared = AutismSharedState.get();
        shared.setQueueEditorOverlayVisible(visible);
        shared.setQueueEditorOverlayX(panelX);
        shared.setQueueEditorOverlayY(panelY);
        saveLayout();
    }

    public void restoreState() {
        AutismSharedState shared = AutismSharedState.get();
        restoreLayout();
        this.visible = shared.isQueueEditorOverlayVisible();
        this.panelX = shared.getQueueEditorOverlayX();
        this.panelY = shared.getQueueEditorOverlayY();
    }

    private int getContentStartY() {
        return panelY + getContentStartOffset();
    }

    private int getContentStartOffset() {
        int toolbarRows = AutismLANSync.getInstance().isInSession() ? 4 : 3;
        return toolbarTopOffset() + (toolbarRows * toolbarButtonHeight()) + ((toolbarRows - 1) * toolbarGap()) + contentTopGap();
    }

    private int getListX() {
        return panelX + panelInset();
    }

    private int getListWidth() {
        return DirectLayout.contentWidth(PANEL_WIDTH, panelInset());
    }

    private int getListContentWidth(boolean hasScrollbar) {
        return Math.max(40, DirectLayout.reserveScrollbar(getListWidth(), hasScrollbar, listScrollbarGutter()));
    }

    private int getToolbarRowWidth() {
        return getListWidth();
    }

    private int fitToolbarButtonWidth(String label, int minWidth, int maxWidth) {
        return DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, label, 5, minWidth, maxWidth);
    }

    private int getListHeight(int panelHeight) {
        return Math.max(rowHeight() + 4, panelHeight - getContentStartOffset() - panelInset());
    }

    private int getlistViewportHeight(int panelHeight) {
        return alignViewportHeight(Math.max(0, getListHeight(panelHeight) - 2), rowHeight());
    }

    private int getListClipTop() {
        return getContentStartY() - 1;
    }

    private int getListClipBottom(int panelHeight) {
        return getListClipTop() + getlistViewportHeight(panelHeight);
    }

    private CompactScrollbar.Metrics getScrollbarMetrics(int panelHeight) {
        int visibleHeight = getlistViewportHeight(panelHeight);
        int contentHeight = cachedDisplayQueue.size() * rowHeight();
        int maxScroll = Math.max(0, contentHeight - visibleHeight);
        return CompactScrollbar.compute(
            contentHeight,
            Math.max(1, visibleHeight),
            getListX() + getListWidth() - 5,
            getListClipTop(),
            4,
            Math.max(1, getListHeight(panelHeight) - 2),
            listScrollState.tick(0.0f, maxScroll)
        );
    }

    private int getRemoveButtonX(int rowX, int rowWidth) {
        return rowX + rowWidth - rowButtonSize() - panelInset();
    }

    private int getDelayButtonWidth() {
        return fixedDelayButtonWidth();
    }

    private int getDelayButtonX(int rowX, int rowWidth) {
        return getRemoveButtonX(rowX, rowWidth) - 4 - getDelayButtonWidth();
    }

    private int getRowLabelMaxWidth(int rowX, int rowWidth) {
        return Math.max(40, getDelayButtonX(rowX, rowWidth) - 14 - 2 - (rowX + 8) - 6);
    }

    private int getPanelHeight(List<QueuedPacket> displayQueue) {
        int contentHeight = Math.min(displayQueue.size(), maxVisibleRows()) * rowHeight();
        return Math.max(getMinHeight(), getContentStartOffset() + contentHeight + contentBottomGap());
    }

    private QueuedPacket getSelectedPacket(List<QueuedPacket> queue) {
        if (selectedPacketId == -1) return null;
        for (QueuedPacket packet : queue) {
            if (packet.getId() == selectedPacketId) return packet;
        }
        selectedPacketId = -1;
        return null;
    }

    private void clearSelection() {
        selectedPacketId = -1;
    }

    private List<QueuedPacket> getCurrentQueue() {
        AutismSharedState shared = AutismSharedState.get();
        List<QueuedPacket> queue = shared.getStaggeredQueue();
        if (queue.isEmpty()) queue = shared.getDelayedPackets();
        return queue;
    }

    private int drawAutoToolbarButton(GuiGraphicsExtractor context, int x, int y, int h, String label, boolean active, int mouseX, int mouseY, String tip, Runnable action) {
        int width = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, label, 5, 24, 96);
        CompactOverlayControls.toggle(context, textRenderer, x, y, width, h, label, active,
            "queue:auto:" + label + ':' + x + ':' + y, mouseX, mouseY);
        toolbarRegions.add(new ClickRegion(x, y, width, h, action));
        checkBtnHover(x, y, width, h, mouseX, mouseY, tip);
        return x + width;
    }

    private int drawAutoToolbarButton(GuiGraphicsExtractor context, int x, int y, int h, String label, int mouseX, int mouseY, String tip, Runnable action) {
        int width = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, label, 5, 24, 96);
        CompactOverlayControls.action(context, textRenderer, x, y, width, h, label, CompactOverlayButton.Variant.GHOST, true, mouseX, mouseY);
        toolbarRegions.add(new ClickRegion(x, y, width, h, action));
        checkBtnHover(x, y, width, h, mouseX, mouseY, tip);
        return x + width;
    }

    private int drawAutoToolbarStatusButton(GuiGraphicsExtractor context, int x, int y, int h, String label, boolean active, int mouseX, int mouseY, String tip, Runnable action) {
        int width = DirectLayout.fitOverlayButtonWidth(textRenderer, theme, UiTone.BODY, label, 5, 24, 112);
        CompactOverlayControls.toggle(context, textRenderer, x, y, width, h, label, active,
            "queue:status:" + label + ':' + x + ':' + y, mouseX, mouseY);
        toolbarRegions.add(new ClickRegion(x, y, width, h, action));
        checkBtnHover(x, y, width, h, mouseX, mouseY, tip);
        return x + width;
    }

    private void drawFixedToolbarButton(GuiGraphicsExtractor context, int x, int y, int w, int h, String label, boolean active, int mouseX, int mouseY, String tip, Runnable action) {
        drawFixedToolbarButton(context, x, y, w, h, label, CompactOverlayButton.Variant.SECONDARY, active, mouseX, mouseY, tip, action);
    }

    private void drawFixedToolbarButton(GuiGraphicsExtractor context, int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean active, int mouseX, int mouseY, String tip, Runnable action) {
        CompactOverlayControls.action(context, textRenderer, x, y, w, h, label, variant, active, mouseX, mouseY);
        if (active) toolbarRegions.add(new ClickRegion(x, y, w, h, action));
        checkBtnHover(x, y, w, h, mouseX, mouseY, tip);
    }

    private void drawFixedToolbarButton(GuiGraphicsExtractor context, int x, int y, int w, int h, String label, int mouseX, int mouseY, String tip, Runnable action) {
        drawFixedToolbarButton(context, x, y, w, h, label, false, mouseX, mouseY, tip, action);
    }

    private void drawFixedToolbarStateButton(GuiGraphicsExtractor context, int x, int y, int w, int h, String label, boolean enabled, int mouseX, int mouseY, String tip, Runnable action) {
        CompactOverlayControls.toggle(context, textRenderer, x, y, w, h, label, enabled,
            "queue:state:" + label + ':' + x + ':' + y, mouseX, mouseY);
        toolbarRegions.add(new ClickRegion(x, y, w, h, action));
        checkBtnHover(x, y, w, h, mouseX, mouseY, tip);
    }

    private boolean isQueueSortedByDelay(List<QueuedPacket> queue) {
        for (int i = 1; i < queue.size(); i++) {
            QueuedPacket previous = queue.get(i - 1);
            QueuedPacket current = queue.get(i);
            if (previous.getDelay() > current.getDelay()) return false;
            if (previous.getDelay() == current.getDelay() && previous.getId() > current.getId()) return false;
        }
        return true;
    }

    private String buildQueueSignature(List<QueuedPacket> queue) {
        if (queue.isEmpty()) return "";

        StringBuilder signature = new StringBuilder(queue.size() * 16);
        for (QueuedPacket packet : queue) {
            signature.append(packet.getId()).append(':').append(packet.getDelay()).append(';');
        }
        return signature.toString();
    }

    private void observeQueueChanges(List<QueuedPacket> queue) {
        String signature = buildQueueSignature(queue);
        if (!signature.equals(lastObservedQueueSignature)) {
            lastObservedQueueSignature = signature;
            lastObservedQueueChangeMs = System.currentTimeMillis();
            pendingAutoReorder = true;
        }
    }

    private void maybeAutoReorderDelayedQueue(AutismSharedState shared) {
        List<QueuedPacket> delayedQueue = shared.getDelayedPackets();
        observeQueueChanges(delayedQueue);
        if (delayedQueue.size() < 2) return;
        if (!pendingAutoReorder) return;
        if (System.currentTimeMillis() - lastObservedQueueChangeMs < 800L) return;
        if (isQueueSortedByDelay(delayedQueue)) {
            pendingAutoReorder = false;
            return;
        }

        shared.sortDelayedPacketsByDelayPreservingIds();
        lastObservedQueueSignature = buildQueueSignature(shared.getDelayedPackets());
        pendingAutoReorder = false;
        keepSelectedPacketVisible = true;
    }

    private void ensureSelectedPacketVisible(List<QueuedPacket> queue, QueuedPacket selectedPacket, int panelHeight) {
        if (selectedPacket == null || queue.isEmpty()) return;

        int selectedIndex = -1;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId() == selectedPacket.getId()) {
                selectedIndex = i;
                break;
            }
        }
        if (selectedIndex < 0) return;

        int visibleHeight = getlistViewportHeight(panelHeight);
        int maxScroll = Math.max(0, queue.size() * rowHeight() - visibleHeight);
        int rowTop = selectedIndex * rowHeight();
        int rowBottom = rowTop + rowHeight();

        if (rowTop < scrollOffset) {
            scrollOffset = rowTop;
        } else if (rowBottom > scrollOffset + visibleHeight) {
            scrollOffset = rowBottom - visibleHeight;
        }

        scrollOffset = quantizeScrollOffset(scrollOffset, rowHeight(), maxScroll);
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        AutismSharedState shared = AutismSharedState.get();
        List<QueuedPacket> staggeredQueue = shared.getStaggeredQueue();
        if (staggeredQueue.isEmpty()) {
            maybeAutoReorderDelayedQueue(shared);
            cachedDisplayQueue = shared.getDelayedPackets();
        } else {
            cachedDisplayQueue = new ArrayList<>(staggeredQueue);
        }
        QueuedPacket selectedPacket = getSelectedPacket(cachedDisplayQueue);
        AutismWindowLayout clamped = clampToScreen(
            this,
            new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, getPanelHeight(cachedDisplayQueue), visible, collapsed)
        );
        panelX = clamped.x;
        panelY = clamped.y;
        PANEL_WIDTH = clamped.width;
        int panelHeight = clamped.height;
        PANEL_HEIGHT = panelHeight;
        if (!collapsed && keepSelectedPacketVisible) {
            ensureSelectedPacketVisible(cachedDisplayQueue, selectedPacket, panelHeight);
            keepSelectedPacketVisible = false;
        }

        toolbarRegions.clear();
        rowRegions.clear();
        hoveredTooltip = null;

        String title = "Queue (" + cachedDisplayQueue.size() + ")";
        AutismWindowLayout bounds = new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, panelHeight, visible, collapsed);
        renderWindowFrame(context, mouseX, mouseY, bounds, title, collapsed, isDragging);
        boolean clipBody = beginWindowBodyClip(context, bounds, collapsed);
        if (!clipBody) {
            renderWindowInactiveOverlay(context, bounds, collapsed, isDragging);
            return;
        }

        try {
        int gap = toolbarGap();
        int row1Y = panelY + toolbarTopOffset();
        int row2Y = row1Y + toolbarButtonHeight() + gap;
        int row3Y = row2Y + toolbarButtonHeight() + gap;
        int row4Y = row3Y + toolbarButtonHeight() + gap;
        int leftX = panelX + panelInset();
        int rowWidth = getToolbarRowWidth();
        int rowRight = leftX + rowWidth;

        int bx = leftX;
        bx = drawAutoToolbarButton(context, bx, row1Y, toolbarButtonHeight(), "Copy", mouseX, mouseY,
            "Copy queue to clipboard (serialized)",
            () -> {
                AutismClipboardHelper.copyToClipboard(getCurrentQueue());
                AutismNotifications.copied("Queue copied.");
            }) + gap;
        bx = drawAutoToolbarButton(context, bx, row1Y, toolbarButtonHeight(), "Paste", mouseX, mouseY,
            "Replace queue from clipboard",
            () -> {
                if (AutismSharedState.get().getStaggeredQueue().isEmpty()) {
                    AutismSharedState.QueuedPacket.resetIdCounter();
                }
                List<QueuedPacket> newQueue = AutismClipboardHelper.pasteFromClipboard();
                if (newQueue != null) {
                    AutismSharedState.get().setDelayedPackets(newQueue);
                    clearSelection();
                    scrollOffset = 0;
                    AutismClientMessaging.sendPrefixed("§aQueue pasted!");
                }
            }) + gap;
        bx = drawAutoToolbarButton(context, bx, row1Y, toolbarButtonHeight(), "Dupe", mouseX, mouseY,
            "Duplicate all packets in the queue",
            () -> {
                List<QueuedPacket> currentQueue = AutismSharedState.get().getDelayedPackets();
                if (!currentQueue.isEmpty()) {
                    List<QueuedPacket> duplicatedQueue = new ArrayList<>(currentQueue);
                    for (QueuedPacket packet : currentQueue) duplicatedQueue.add(new QueuedPacket(packet.packet, packet.getDelay(), packet.getReplayMode()));
                    AutismSharedState.get().setDelayedPackets(duplicatedQueue);
                    clearSelection();
                    AutismClientMessaging.sendPrefixed("§aDuplicated " + currentQueue.size() + " packet(s).");
                }
            }) + gap;
        bx = drawAutoToolbarButton(context, bx, row1Y, toolbarButtonHeight(), "Ins", mouseX, mouseY,
            "Insert clipboard packets at the end of the queue",
            () -> {
                List<QueuedPacket> newPackets = AutismClipboardHelper.pasteFromClipboard();
                if (newPackets != null && !newPackets.isEmpty()) {
                    List<QueuedPacket> combinedQueue = new ArrayList<>(AutismSharedState.get().getDelayedPackets());
                    combinedQueue.addAll(newPackets);
                    AutismSharedState.get().setDelayedPackets(combinedQueue);
                    clearSelection();
                    AutismClientMessaging.sendPrefixed("§aInserted " + newPackets.size() + " packet(s).");
                }
            }) + gap;
        int replayWidth = Math.max(fitToolbarButtonWidth("Replay", 48, 84), rowRight - bx);
        drawFixedToolbarButton(context, bx, row1Y, replayWidth, toolbarButtonHeight(), "Replay", CompactOverlayButton.Variant.GHOST, true, mouseX, mouseY,
            "Restore the last flushed queue back into the editor",
            () -> {
                if (AutismSharedState.get().restoreLastFlushedQueue()) {
                    clearSelection();
                    scrollOffset = 0;
                    AutismClientMessaging.sendPrefixed("§aRestored - packets will regenerate on send");
                } else {
                    AutismClientMessaging.sendPrefixed("§cNo history");
                }
            });

        boolean flushOnDelay = AutismModule.get().shouldFlushQueueOnDelayDisable();
        boolean captureAsExact = AutismModule.get().isCaptureAsExact();
        int clearWidth = fitToolbarButtonWidth("Clear", 42, 58);
        int captureModeBtnWidth = toolbarButtonHeight();
        int flushWidth = Math.max(fitToolbarButtonWidth("FLUSH ON DELAY", 110, 150),
                rowWidth - clearWidth - captureModeBtnWidth - gap - gap);
        drawFixedToolbarStateButton(context, leftX, row2Y, flushWidth, toolbarButtonHeight(), "FLUSH ON DELAY", flushOnDelay, mouseX, mouseY,
            flushOnDelay
                ? "When delay turns off, queued packets auto-flush"
                : "When delay turns off, keep the queued packets until you flush or clear them",
            () -> {
                AutismModule module = AutismModule.get();
                boolean newValue = !module.shouldFlushQueueOnDelayDisable();
                module.setFlushQueueOnDelayDisable(newValue);
                AutismClientMessaging.sendPrefixed(newValue
                    ? "\u00a7aFlush on Delay ON"
                    : "\u00a7eFlush on Delay OFF");
            });
        drawFixedToolbarButton(context, leftX + flushWidth + gap, row2Y, captureModeBtnWidth, toolbarButtonHeight(),
            captureAsExact ? "E" : "R",
            captureAsExact ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.SUCCESS,
            true, mouseX, mouseY,
            captureAsExact
                ? "Capture as Exact: new packets stored verbatim"
                : "Capture as Regenerate: new packets rewritten on send",
            () -> {
                AutismModule module = AutismModule.get();
                boolean nowExact = !module.isCaptureAsExact();
                module.setCaptureAsExact(nowExact);
                AutismSharedState.ReplayMode bulkMode = nowExact
                    ? AutismSharedState.ReplayMode.EXACT
                    : AutismSharedState.ReplayMode.REGENERATE;
                int updated = 0;
                for (AutismSharedState.QueuedPacket qp : AutismSharedState.get().getDelayedPackets()) {
                    if (qp.getReplayMode() != bulkMode) {
                        qp.setReplayMode(bulkMode);
                        updated++;
                    }
                }
                AutismClientMessaging.sendPrefixed(nowExact
                    ? ("\u00a7cCapture mode: EXACT" + (updated > 0 ? " (" + updated + " queued updated)" : ""))
                    : ("\u00a7aCapture mode: REGENERATE" + (updated > 0 ? " (" + updated + " queued updated)" : "")));
            });
        drawFixedToolbarButton(context, leftX + flushWidth + gap + captureModeBtnWidth + gap, row2Y, clearWidth, toolbarButtonHeight(), "Clear", CompactOverlayButton.Variant.DANGER, true, mouseX, mouseY,
            "Remove all packets from the queue",
            () -> {
                int cleared = AutismSharedState.get().clearQueuedPackets();
                clearSelection();
                scrollOffset = 0;
                AutismModule.get().notifyClearQueuedPacketsUiResult(cleared);
            });

        boolean captureModeOn = AutismSharedState.get().isCaptureMode();
        String delayModeLabel = AutismSharedState.get().getDelayMode() == AutismSharedState.DelayMode.TICKS ? "Ticks" : "Ms";
        int modeWidth = fitToolbarButtonWidth(delayModeLabel, 34, 44);
        int captureWidth = fitToolbarButtonWidth("Capture", 56, 72);
        int plus1Width = fitToolbarButtonWidth("+1", 22, 28);
        int plus10Width = fitToolbarButtonWidth("+10", 26, 34);
        int plus20Width = fitToolbarButtonWidth("+20", 26, 34);
        int sendWidth = Math.max(fitToolbarButtonWidth("Send Q", 54, 82), rowWidth - modeWidth - captureWidth - plus1Width - plus10Width - plus20Width - (gap * 5));
        drawFixedToolbarButton(context, leftX, row3Y, modeWidth, toolbarButtonHeight(), delayModeLabel, CompactOverlayButton.Variant.GHOST, true, mouseX, mouseY,
            "Toggle delay unit: Ticks or milliseconds",
            () -> {
                AutismSharedState.get().toggleDelayMode();
                String newMode = AutismSharedState.get().getDelayMode() == AutismSharedState.DelayMode.TICKS ? "Ticks" : "Ms";
                AutismClientMessaging.sendPrefixed("\u00a7aDelay mode: " + newMode);
            });
        drawFixedToolbarStateButton(context, leftX + modeWidth + gap, row3Y, captureWidth, toolbarButtonHeight(), "Capture", captureModeOn, mouseX, mouseY,
            "Record real timing between captured packets",
            () -> {
                boolean newCapture = !AutismSharedState.get().isCaptureMode();
                AutismSharedState.get().setCaptureMode(newCapture);
                AutismClientMessaging.sendPrefixed(newCapture ? "\u00a7aCapture Real Delays ON" : "\u00a7eCapture Real Delays OFF");
            });
        int adjustX = leftX + modeWidth + gap + captureWidth + gap;
        drawFixedToolbarButton(context, adjustX, row3Y, plus1Width, toolbarButtonHeight(), "+1", CompactOverlayButton.Variant.GHOST, true, mouseX, mouseY,
            "Add +1 to the selected packet, or apply a +1 incremental pattern to the whole queue",
            () -> handleIncrementalDelay(1));
        drawFixedToolbarButton(context, adjustX + plus1Width + gap, row3Y, plus10Width, toolbarButtonHeight(), "+10", CompactOverlayButton.Variant.GHOST, true, mouseX, mouseY,
            "Add +10 to the selected packet, or apply a +10 incremental pattern to the whole queue",
            () -> handleIncrementalDelay(10));
        drawFixedToolbarButton(context, adjustX + plus1Width + gap + plus10Width + gap, row3Y, plus20Width, toolbarButtonHeight(), "+20", CompactOverlayButton.Variant.GHOST, true, mouseX, mouseY,
            "Add +20 to the selected packet, or apply a +20 incremental pattern to the whole queue",
            () -> handleIncrementalDelay(20));
        drawFixedToolbarButton(context, rowRight - sendWidth, row3Y, sendWidth, toolbarButtonHeight(), configurationOnly ? "In Game" : "Send Q",
            CompactOverlayButton.Variant.SUCCESS, !configurationOnly, mouseX, mouseY,
            configurationOnly ? "Join a world to send queued packets" : "Send all queued packets to the server",
            () -> {
                if (configurationOnly) return;
                if (MC.getConnection() != null) {
                    int count = AutismSharedState.get().flushDelayedPackets(MC.getConnection());
                    AutismModule.get().notifyFlushQueuedPacketsUiResult(count);
                }
            });

        boolean inLanSession = AutismLANSync.getInstance().isInSession();
        if (inLanSession) {
            int syncWidth = fitToolbarButtonWidth("Sync", 46, 58);
            int syncExecWidth = Math.max(fitToolbarButtonWidth("Sync Exec", 92, 118), rowWidth - syncWidth - gap);
            drawFixedToolbarButton(context, leftX, row4Y, syncWidth, toolbarButtonHeight(), "Sync", CompactOverlayButton.Variant.GHOST, !configurationOnly, mouseX, mouseY,
                configurationOnly ? "Join a world to share packet queues" : "Broadcast this queue to all LAN peers",
                this::handleSyncButton);
            drawFixedToolbarButton(context, leftX + syncWidth + gap, row4Y, syncExecWidth, toolbarButtonHeight(), "Sync Exec", CompactOverlayButton.Variant.SUCCESS, !configurationOnly, mouseX, mouseY,
                configurationOnly ? "Join a world to execute packet queues" : "Tell LAN peers to execute their queues together",
                () -> AutismLANSync.getInstance().sendQueuedPackets());
        }

        if (cachedDisplayQueue.isEmpty()) {
            AutismText.draw(context, textRenderer, "Queue empty", AutismText.Tone.MUTED, panelX + 10, getContentStartY(), false);
        } else {
            int visibleHeight = getlistViewportHeight(panelHeight);
            int totalContentHeight = cachedDisplayQueue.size() * rowHeight();
            int maxScroll = Math.max(0, totalContentHeight - visibleHeight);
            scrollOffset = quantizeScrollOffset(scrollOffset, rowHeight(), maxScroll);
            listScrollState.setTarget(scrollOffset, maxScroll);
            int drawScroll = listScrollState.tick(delta, maxScroll);

            int contentStartY = getContentStartY();
            int listX = getListX();
            int listY = contentStartY - 2;
            int listWidth = getListWidth();
            int listHeight = getListHeight(panelHeight);
            int clipTop = getListClipTop();
            int clipBottom = getListClipBottom(panelHeight);
            CompactListRenderer.drawFrame(context, listX, listY, listWidth, listHeight, selectedPacket != null);
            CompactScrollbar.Metrics scrollbarMetrics = getScrollbarMetrics(panelHeight);
            int listContentWidth = getListContentWidth(scrollbarMetrics.hasScroll());
            autismclient.gui.vanillaui.UiScissorStack.global().push(context,
                autismclient.gui.vanillaui.UiBounds.of(listX + 1, clipTop, Math.max(0, listWidth - 2), Math.max(0, clipBottom - clipTop)));
            int baseY = contentStartY - drawScroll;
            int groupColorIndex = 0;
            int lastDelay = Integer.MIN_VALUE;

            for (int i = 0; i < cachedDisplayQueue.size(); i++) {
                QueuedPacket packet = cachedDisplayQueue.get(i);
                int itemY = baseY + i * rowHeight();
                int rowY = itemY - 1;
                if (rowY + rowHeight() <= clipTop || rowY >= clipBottom) continue;

                boolean grouped = false;
                if (i > 0 && cachedDisplayQueue.get(i - 1).getDelay() == packet.getDelay()) grouped = true;
                if (i < cachedDisplayQueue.size() - 1 && cachedDisplayQueue.get(i + 1).getDelay() == packet.getDelay()) grouped = true;

                int textColor = AutismColors.textPrimary();
                if (grouped) {
                    if (packet.getDelay() != lastDelay) groupColorIndex++;
                    textColor = (groupColorIndex % 2 == 0) ? 0xFF44CCFF : 0xFFFFAA44;
                }
                lastDelay = packet.getDelay();

                int rowX = listX + 1;
                int rowW = Math.max(36, listContentWidth - 2);
                boolean selected = selectedPacket != null && selectedPacket.getId() == packet.getId();
                boolean hovered = mouseX >= rowX && mouseX < rowX + rowW && mouseY >= rowY && mouseY < rowY + rowHeight();
                CompactListRenderer.drawRow(
                    context,
                    textRenderer,
                    "",
                    rowX,
                    rowY,
                    rowW,
                    rowHeight(),
                    hovered,
                    selected,
                    grouped ? CompactListRenderer.RowTone.WARNING : CompactListRenderer.RowTone.NORMAL
                );

                String label = "#" + packet.getId() + " " + AutismPacketNamer.getFriendlyName(packet.packet);
                String delayText = String.valueOf(packet.getDelay());
                int labelMaxWidth = getRowLabelMaxWidth(rowX, rowW);
                String trimmed = UiText.width(textRenderer, label, theme.fontFor(UiTone.BODY), textColor) > labelMaxWidth
                    ? UiText.trimToWidth(textRenderer, label, Math.max(1, labelMaxWidth), theme.fontFor(UiTone.BODY), textColor)
                    : label;
                int rowTextY = UiSizing.alignTextY(rowY, rowHeight(), theme.fontHeight(UiTone.BODY), theme.bodyTextNudge());
                UiText.draw(context, textRenderer, trimmed, theme.fontFor(UiTone.BODY), selected ? AutismColors.rowSelectedText() : textColor, rowX + 7, rowTextY, false);

                int delayBtnWidth = getDelayButtonWidth();
                int delayBtnX = getDelayButtonX(rowX, rowW);
                int buttonY = rowY + Math.max(0, (rowHeight() - rowButtonSize()) / 2);

                int modeBtnW = 14;
                int modeBtnX = delayBtnX - modeBtnW - 2;
                boolean exact = packet.isExactReplay();
                CompactOverlayControls.action(context, textRenderer, modeBtnX, buttonY, modeBtnW, rowButtonSize(), exact ? "E" : "R",
                    exact ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.SUCCESS, true, mouseX, mouseY);
                CompactOverlayControls.action(context, textRenderer, delayBtnX, buttonY, delayBtnWidth, rowButtonSize(), delayText,
                    CompactOverlayButton.Variant.PRIMARY, true, mouseX, mouseY);

                int removeBtnX = getRemoveButtonX(rowX, rowW);
                boolean removeHovered = mouseX >= removeBtnX && mouseX < removeBtnX + rowButtonSize() && mouseY >= buttonY && mouseY < buttonY + rowButtonSize();
                CompactListRenderer.drawDeleteButton(context, removeBtnX, buttonY, rowButtonSize(), removeHovered);
                CompactListRenderer.drawDivider(context, rowX, rowY + rowHeight(), rowW);

                rowRegions.add(new RowRegion(packet, rowX, rowY, rowW, rowHeight(), delayBtnX, delayBtnWidth, removeBtnX, rowButtonSize(), modeBtnX, modeBtnW));
            }

            autismclient.gui.vanillaui.UiScissorStack.global().pop(context);
            CompactScrollbar.draw(context, scrollbarMetrics, scrollbarMetrics.contains(mouseX, mouseY), scrollbarDragging);
        }
        } finally {
            endWindowBodyClip(context, clipBody);
            renderWindowInactiveOverlay(context, bounds, collapsed, isDragging);
        }

        if (!collapsed && hoveredTooltip != null) {
            context.nextStratum();
            drawTooltip(context, hoveredTooltip, tooltipX, tooltipY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        AutismSharedState shared = AutismSharedState.get();
        List<QueuedPacket> displayQueue = cachedDisplayQueue.isEmpty() ? getCurrentQueue() : cachedDisplayQueue;
        int panelHeight = collapsed
            ? HEADER_HEIGHT
            : clampToScreen(this, new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, getPanelHeight(displayQueue), visible, collapsed)).height;

        if (button == 0 && mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= panelY && mouseY <= panelY + HEADER_HEIGHT) {
            AutismWindowLayout bounds = new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, panelHeight, visible, collapsed);
            if (isOverCollapseButton(mouseX, mouseY, bounds)) {
                toggleCollapsed();
                return true;
            }
            if (isOverCloseButton(mouseX, mouseY, bounds)) {
                setVisible(false);
                return true;
            }
            isDragging = true;
            dragOffsetX = mouseX - panelX;
            dragOffsetY = mouseY - panelY;
            return true;
        }

        if (collapsed) return false;

        if (button == 0) {
            for (ClickRegion region : toolbarRegions) {
                if (region.contains(mouseX, mouseY)) {
                    region.action.run();
                    return true;
                }
            }
        }

        CompactScrollbar.Metrics scrollbarMetrics = getScrollbarMetrics(panelHeight);
        if (button == 0 && scrollbarMetrics.hasScroll() && scrollbarMetrics.contains(mouseX, mouseY)) {
            scrollbarDragging = true;
            scrollbarGrabOffset = scrollbarMetrics.overThumb(mouseX, mouseY)
                ? (int) Math.round(mouseY) - scrollbarMetrics.thumbY()
                : scrollbarMetrics.thumbHeight() / 2;
            scrollOffset = quantizeScrollOffset(CompactScrollbar.scrollFromThumb(scrollbarMetrics, mouseY, scrollbarGrabOffset), rowHeight(), scrollbarMetrics.maxScroll());
            listScrollState.jumpTo(scrollOffset, scrollbarMetrics.maxScroll());
            return true;
        }

        for (RowRegion region : rowRegions) {
            if (!region.contains(mouseX, mouseY)) continue;

            if (region.overRemove(mouseX, mouseY) && button == 0) {
                shared.removeQueuedPacket(region.packet);
                if (selectedPacketId == region.packet.getId()) clearSelection();
                return true;
            }

            if (region.overMode(mouseX, mouseY) && button == 0) {
                AutismSharedState.ReplayMode next = region.packet.isExactReplay()
                    ? AutismSharedState.ReplayMode.REGENERATE
                    : AutismSharedState.ReplayMode.EXACT;
                region.packet.setReplayMode(next);
                return true;
            }

            if (region.overDelay(mouseX, mouseY)) {
                if (button == 0) {
                    shared.updatePacketDelay(region.packet, region.packet.getDelay() + 1);
                    return true;
                }
                if (button == 1) {
                    shared.updatePacketDelay(region.packet, Math.max(0, region.packet.getDelay() - 1));
                    return true;
                }
            }

            if (button == 0) {
                selectedPacketId = selectedPacketId == region.packet.getId() ? -1 : region.packet.getId();
                return true;
            }
        }

        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDragging && button == 0) {
            AutismWindowLayout nextBounds = clampToScreen(this,
                new AutismWindowLayout((int) (mouseX - dragOffsetX), (int) (mouseY - dragOffsetY),
                    PANEL_WIDTH, PANEL_HEIGHT, visible, collapsed));
            panelX = nextBounds.x;
            panelY = nextBounds.y;
            return true;
        }
        if (scrollbarDragging && button == 0) {
            List<QueuedPacket> displayQueue = cachedDisplayQueue.isEmpty() ? getCurrentQueue() : cachedDisplayQueue;
            int panelHeight = collapsed
                ? HEADER_HEIGHT
                : clampToScreen(this, new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, getPanelHeight(displayQueue), visible, collapsed)).height;
            CompactScrollbar.Metrics scrollbarMetrics = getScrollbarMetrics(panelHeight);
            scrollOffset = quantizeScrollOffset(CompactScrollbar.scrollFromThumb(scrollbarMetrics, mouseY, scrollbarGrabOffset), rowHeight(), scrollbarMetrics.maxScroll());
            listScrollState.jumpTo(scrollOffset, scrollbarMetrics.maxScroll());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDragging) {
            isDragging = false;
            saveState();
            return true;
        }
        if (button == 0 && scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible || collapsed || !isMouseOver(mouseX, mouseY)) return false;

        List<QueuedPacket> displayQueue = cachedDisplayQueue.isEmpty() ? getCurrentQueue() : cachedDisplayQueue;
        int panelHeight = clampToScreen(this, new AutismWindowLayout(panelX, panelY, PANEL_WIDTH, getPanelHeight(displayQueue), visible, collapsed)).height;
        int visibleHeight = getlistViewportHeight(panelHeight);
        int maxScroll = Math.max(0, displayQueue.size() * rowHeight() - visibleHeight);
        if (maxScroll <= 0) return false;

        scrollOffset = quantizeScrollOffset(scrollOffset - (int) (Math.signum(amount) * rowHeight()), rowHeight(), maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    private void handleSyncButton() {
        if (configurationOnly) return;
        AutismLANSync sync = AutismLANSync.getInstance();
        if (!sync.isInSession()) {
            AutismClientMessaging.sendPrefixed("\u00a7cNot in LAN session");
            return;
        }

        List<QueuedPacket> queue = getCurrentQueue();
        if (queue.isEmpty()) {
            AutismClientMessaging.sendPrefixed("\u00a7cQueue is empty");
            return;
        }

        String queueData = AutismClipboardHelper.serializeQueueToBase64(queue);
        if (queueData == null) {
            AutismClientMessaging.sendPrefixed("\u00a7cFailed to serialize queue");
            return;
        }

        sync.broadcastQueueSync(queueData);
        AutismClientMessaging.sendPrefixed("\u00a7aQueue sent to " + sync.getConnectedClientCount() + " clients");
    }

    private void checkBtnHover(int x, int y, int w, int h, int mx, int my, String tip) {
        if (mx >= x && mx < x + w && my >= y && my < y + h) {
            hoveredTooltip = tip;
            tooltipX = mx + 8;
            tooltipY = my + 12;
        }
    }

    private void drawTooltip(GuiGraphicsExtractor ctx, String text, int x, int y) {
        Tooltip.render(UiContexts.overlay(ctx, textRenderer, x, y), text, x, y, 200);
    }

    private void handleIncrementalDelay(int increment) {
        List<QueuedPacket> queue = getCurrentQueue();
        if (queue.isEmpty()) {
            AutismClientMessaging.sendPrefixed("\u00a7cQueue is empty");
            return;
        }

        if (selectedPacketId != -1) {
            for (QueuedPacket packet : queue) {
                if (packet.getId() == selectedPacketId) {
                    packet.setDelay(packet.getDelay() + increment);
                    String modeStr = AutismSharedState.get().getQueueDisplayDelayMode() == AutismSharedState.DelayMode.TICKS ? "ticks" : "ms";
                    AutismClientMessaging.sendPrefixed("\u00a7aPacket #" + packet.getId() + " delay: " + packet.getDelay() + " " + modeStr);
                    return;
                }
            }
            clearSelection();
            AutismClientMessaging.sendPrefixed("\u00a7cSelected packet not found");
            return;
        }

        for (int i = 0; i < queue.size(); i++) {
            queue.get(i).setDelay(i * increment);
        }
        AutismClientMessaging.sendPrefixed("\u00a7aApplied +" + increment + " incremental delays");
    }

        private int defaultPanelWidth() {
        return 248;
    }

    private int defaultPanelHeight() {
        return 176;
    }

    private int minimumPanelHeight() {
        return 168;
    }

    private int rowHeight() {
        return 14;
    }

    private int rowButtonSize() {
        return 12;
    }

    private int toolbarButtonHeight() {
        return 13;
    }

    private int maxVisibleRows() {
        return 6;
    }

    private int fixedDelayButtonWidth() {
        return 40;
    }

    private int listScrollbarGutter() {
        return 12;
    }

    private int panelInset() {
        return 4;
    }

    private int toolbarGap() {
        return 2;
    }

    private int toolbarTopOffset() {
        return HEADER_HEIGHT + 4;
    }

    private int contentTopGap() {
        return 6;
    }

    private int contentBottomGap() {
        return 6;
    }
}
