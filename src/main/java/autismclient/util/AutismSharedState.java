package autismclient.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import autismclient.AutismClientAddon;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.util.macro.CraftAction;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.core.BlockPos;

public final class AutismSharedState {
    private static final AutismSharedState INSTANCE = new AutismSharedState();

    private volatile boolean sendGuiPackets = true;
    private volatile boolean delayGuiPackets;
    private volatile boolean allowSignEditing = true;
    private volatile boolean allowBookUpdate = true;
    private volatile boolean bypassResourcePack;
    private volatile boolean resourcePackForceDeny;
    private volatile boolean xCarryActive;
    private volatile boolean xCarryForced;
    private volatile boolean xCarryArmorBypass;
    private volatile Set<Integer> xCarryForcedSlotMask = Collections.emptySet();
    private volatile boolean xCarryForcedCarryCursor;
    private volatile BlockPos lastInteractedBlockPos = null;

    private volatile String lastSentMessage = "";
    private volatile AutismContainerTarget lastContainerTarget = null;
    private volatile boolean useCustomPackets = false;

    private final Set<Class<? extends Packet<?>>> c2sPackets = ConcurrentHashMap.newKeySet();
    private final Set<Class<? extends Packet<?>>> s2cPackets = ConcurrentHashMap.newKeySet();

    private final List<QueuedPacket> delayedPackets = new CopyOnWriteArrayList<>();

    private volatile boolean staggeredPacketSend = false;
    private volatile int staggeredSendDelay = 1;
    private final List<QueuedPacket> staggeredQueue = new CopyOnWriteArrayList<>();
    private final List<QueuedPacket> staggeredDisplayQueue = new CopyOnWriteArrayList<>();
    private final List<QueuedPacket> lastFlushedQueue = new CopyOnWriteArrayList<>();
    private final AtomicInteger queueRenderRevision = new AtomicInteger();
    private int staggeredTickCounter = 0;
    private volatile ClientPacketListener staggeredNetworkHandler = null;
    private int staggeredTotal = 0;
    private boolean activeSendUsesExplicitDelays = false;

    public enum DelayMode { TICKS, MS }
    public enum ReplayMode { REGENERATE, EXACT }

    public static final class ReportSample {
        public final String label;
        public final long deltaMs;
        public final String startEvent;
        public final String endEvent;
        public final long capturedAtMs;
        public ReportSample(String label, long deltaMs, String startEvent, String endEvent, long capturedAtMs) {
            this.label = label == null ? "" : label;
            this.deltaMs = deltaMs;
            this.startEvent = startEvent == null ? "" : startEvent;
            this.endEvent = endEvent == null ? "" : endEvent;
            this.capturedAtMs = capturedAtMs;
        }
    }
    private static final int REPORT_RING_SIZE = 32;
    private final java.util.Deque<ReportSample> reportRing = new java.util.ArrayDeque<>(REPORT_RING_SIZE);

    public synchronized void pushReport(ReportSample sample) {
        if (sample == null) return;
        reportRing.addFirst(sample);
        while (reportRing.size() > REPORT_RING_SIZE) reportRing.removeLast();
    }

    public synchronized ReportSample getLatestReport(String label) {
        if (label == null) return reportRing.isEmpty() ? null : reportRing.peekFirst();
        for (ReportSample s : reportRing) {
            if (label.equals(s.label)) return s;
        }
        return null;
    }

    public synchronized java.util.List<ReportSample> getRecentReports(int n) {
        java.util.List<ReportSample> out = new java.util.ArrayList<>(Math.min(n, reportRing.size()));
        int i = 0;
        for (ReportSample s : reportRing) {
            if (i++ >= n) break;
            out.add(s);
        }
        return out;
    }
    private volatile DelayMode delayMode = DelayMode.TICKS;
    private DelayMode staggeredDelayMode = DelayMode.TICKS;
    private long flushStartNanos = 0;
    private int flushStartClientTick = 0;
    private long activeCaptureLastNanos = -1;
    private int activeCaptureLastClientTick = -1;
    private int activeCaptureTailDelay = 0;
    private String pendingQueueCompletionMessage = null;

    private Screen storedScreen;
    private AbstractContainerMenu storedAbstractContainerMenu;
    private boolean isFlushing = false;

    private boolean fabricatorOverlayVisible = false;
    private int fabricatorOverlayX = 500;
    private int fabricatorOverlayY = 5;

    private String fabricatorSlotValue = "0";
    private String fabricatorItemNameValue = "";
    private String fabricatorTimesValue = "1";
    private int fabricatorActionIndex = 0;
    private int fabricatorButtonIndex = 0;
    private boolean fabricatorDropWholeStack = false;
    private boolean fabricatorCraftUseMaxAmount = false;
    private String fabricatorCraftSearchValue = "";
    private int fabricatorCraftSelectedRecipeId = -1;
    private String fabricatorCraftSelectedRecipeKey = "";
    private int fabricatorCraftScrollOffset = 0;
    private final List<CraftAction.CraftEntry> fabricatorCraftPlanEntries = new ArrayList<>();
    private int fabricatorCraftPlanSelectedIndex = -1;
    private int fabricatorCraftPlanScrollOffset = 0;

    private boolean lanSyncOverlayVisible = false;
    private int lanSyncOverlayX = 500;
    private int lanSyncOverlayY = 5;
    private int lanSyncOverlayActiveTab = 0;
    private int lanSyncOverlayScrollOffset = 0;
    private boolean lanSyncOverlayPerUserMode = false;
    private String lanSyncOverlaySelectedMacroName = "";
    private String lanSyncOverlayExpandedExecutePeer = "";
    private String lanSyncOverlaySelectedPeer = "";
    private boolean macroListOverlayVisible = false;
    private int macroListOverlayX = 500;
    private int macroListOverlayY = 250;
    private int macroListOverlayScrollOffset = 0;
    private String macroListOverlaySearch = "";
    private boolean queueEditorOverlayVisible = false;
    private int queueEditorOverlayX = 100;
    private int queueEditorOverlayY = 100;
    private int serverInfoOverlayActiveTab = 0;
    private int serverInfoOverlayPluginScrollOffset = 0;
    private String serverInfoOverlaySelectedPlugin = "";
    private String serverInfoOverlayStateAddress = "";
    private volatile String realServerVersion = "";
    private volatile String realServerVersionAddress = "";
    private int serverInfoOverlayProbeDelayMs = 50;
    private int serverInfoOverlayInfoWidth = 252;
    private int serverInfoOverlayInfoHeight = 258;
    private int serverInfoOverlayPluginWidth = 280;
    private int serverInfoOverlayPluginHeight = 280;

    private boolean macroEditorVisible = false;
    private AutismMacro editingMacro = null;
    private int macroEditorPanelX = 400;
    private int macroEditorPanelY = 100;

    private boolean blockSelectorVisible = false;

    private volatile boolean gbreakCaptureMode = false;
    private volatile int gbreakPacketCount = 0;
    private Runnable gbreakCallback = null;
    private final List<QueuedPacket> gbreakCapturedPackets = new ArrayList<>();

    private volatile boolean captureMode = false;
    private volatile long lastCaptureNanos = -1;
    private volatile int lastCaptureClientTick = -1;
    private volatile int clientTickCounter = 0;

    private volatile double serverMsPerTick = 50.0;
    private volatile long lastTimeSyncMs = -1;
    private final Map<String, AutismWindowLayout> windowLayouts = new HashMap<>();
    private final Map<String, ServerPluginScan> serverPluginScans = new HashMap<>();
    private final List<String> overlayOrder = new ArrayList<>();
    private String focusedOverlayId = "";

    private AutismSharedState() {}

    public static AutismSharedState get() {
        return INSTANCE;
    }

    public boolean isMacroEditorVisible() { return macroEditorVisible; }
    public void setMacroEditorVisible(boolean visible) { this.macroEditorVisible = visible; }

    public AutismMacro getEditingMacro() { return editingMacro; }
    public void setEditingMacro(AutismMacro macro) { this.editingMacro = macro; }

    public int getMacroEditorPanelX() { return macroEditorPanelX; }
    public void setMacroEditorPanelX(int x) { this.macroEditorPanelX = x; }
    public int getMacroEditorPanelY() { return macroEditorPanelY; }
    public void setMacroEditorPanelY(int y) { this.macroEditorPanelY = y; }

    public synchronized AutismWindowLayout getWindowLayout(String id) {
        AutismWindowLayout layout = windowLayouts.get(id);
        if (layout == null) return null;
        return new AutismWindowLayout(layout.x, layout.y, layout.width, layout.height, layout.visible, layout.collapsed);
    }

    public synchronized void setWindowLayout(String id, AutismWindowLayout layout) {
        if (id == null || id.isEmpty() || layout == null) return;

        AutismWindowLayout trueGeometry = AutismOverlayManager.get().clampedAwayTrueGeometry(id);
        if (trueGeometry != null) {
            layout = new AutismWindowLayout(trueGeometry.x, trueGeometry.y, trueGeometry.width, trueGeometry.height,
                layout.visible, layout.collapsed);
        }
        windowLayouts.put(id, new AutismWindowLayout(layout.x, layout.y, layout.width, layout.height, layout.visible, layout.collapsed));
    }

    public synchronized List<String> getOverlayOrder() {
        return new ArrayList<>(overlayOrder);
    }

    public synchronized void setOverlayOrder(List<String> order) {
        overlayOrder.clear();
        if (order == null) return;
        for (String id : order) {
            if (id == null || id.isEmpty() || overlayOrder.contains(id)) continue;
            overlayOrder.add(id);
        }
    }

    public synchronized String getFocusedOverlayId() {
        return focusedOverlayId == null ? "" : focusedOverlayId;
    }

    public synchronized void setFocusedOverlayId(String overlayId) {
        focusedOverlayId = overlayId == null ? "" : overlayId.trim();
    }

    public synchronized ServerPluginScan getServerPluginScan(String address) {
        return getServerPluginScan(address, "");
    }

    public synchronized ServerPluginScan getServerPluginScan(String address, String contextSignature) {
        String key = buildServerPluginScanKey(address, contextSignature);
        if (key.isEmpty()) return null;
        ServerPluginScan scan = serverPluginScans.get(key);
        return scan == null ? null : scan.copy();
    }

    public synchronized void setServerPluginScan(String address, List<String> plugins, Map<String, List<String>> pluginCommands) {
        setServerPluginScan(address, "", plugins, pluginCommands, Map.of());
    }

    public synchronized void setServerPluginScan(String address, String contextSignature, List<String> plugins, Map<String, List<String>> pluginCommands) {
        setServerPluginScan(address, contextSignature, plugins, pluginCommands, Map.of());
    }

    public synchronized void setServerPluginScan(String address, String contextSignature, List<String> plugins, Map<String, List<String>> pluginCommands, Map<String, String> pluginEvidence) {
        String normalizedAddress = normalizeServerAddress(address);
        String normalizedContext = normalizeServerPluginContext(contextSignature);
        String key = buildServerPluginScanKey(normalizedAddress, normalizedContext);
        if (key.isEmpty()) return;
        serverPluginScans.put(key, new ServerPluginScan(normalizedAddress, normalizedContext, plugins, pluginCommands, pluginEvidence));
    }

    public synchronized void clearServerPluginScan(String address) {
        String normalized = normalizeServerAddress(address);
        if (normalized.isEmpty()) return;
        serverPluginScans.keySet().removeIf(key -> key.equals(normalized) || key.startsWith(normalized + "|"));
    }

    private static String normalizeServerAddress(String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeServerPluginContext(String contextSignature) {
        return contextSignature == null ? "" : contextSignature.trim().toLowerCase(Locale.ROOT);
    }

    private static String buildServerPluginScanKey(String address, String contextSignature) {
        String normalizedAddress = normalizeServerAddress(address);
        if (normalizedAddress.isEmpty()) return "";
        String normalizedContext = normalizeServerPluginContext(contextSignature);
        return normalizedContext.isEmpty() ? normalizedAddress + "|default" : normalizedAddress + "|" + normalizedContext;
    }

    public boolean isBlockSelectorVisible() { return blockSelectorVisible; }
    public void setBlockSelectorVisible(boolean visible) { this.blockSelectorVisible = visible; }

    public boolean shouldSendGuiPackets() {
        return sendGuiPackets;
    }

    public void setSendGuiPackets(boolean value) {
        this.sendGuiPackets = value;
    }

    public boolean shouldDelayGuiPackets() {
        return delayGuiPackets;
    }

    public void setDelayGuiPackets(boolean value) {
        if (value && PackHideState.isHardLocked()) return;
        this.delayGuiPackets = value;
    }

    public boolean isXCarryActive() {
        return xCarryActive;
    }

    public void setXCarryActive(boolean value) {
        this.xCarryActive = value;
    }

    public boolean isXCarryForced() {
        return xCarryForced;
    }

    public void setXCarryForced(boolean value) {
        if (value
                && (this.xCarryForcedSlotMask == null || this.xCarryForcedSlotMask.isEmpty())
                && !this.xCarryForcedCarryCursor) {

            return;
        }
        this.xCarryForced = value;
        if (!value) {
            this.xCarryForcedSlotMask = Collections.emptySet();
            this.xCarryForcedCarryCursor = false;
        }
    }

    public Set<Integer> getXCarryForcedSlotMask() {
        return xCarryForcedSlotMask;
    }

    public boolean isXCarryForcedCarryCursor() {
        return xCarryForcedCarryCursor;
    }

    public void setXCarryForcedTargets(Set<Integer> slotMask, boolean carryCursor) {
        LinkedHashSet<Integer> copy = new LinkedHashSet<>();
        if (slotMask != null) {
            for (Integer slot : slotMask) {
                if (slot != null && slot >= 0) copy.add(slot);
            }
        }
        this.xCarryForcedSlotMask = Collections.unmodifiableSet(copy);
        this.xCarryForcedCarryCursor = carryCursor;
    }

    public void mergeXCarryForcedTargets(Set<Integer> slotMask, boolean carryCursor) {
        LinkedHashSet<Integer> merged = new LinkedHashSet<>();
        if (xCarryForced && xCarryForcedSlotMask != null) {
            for (Integer slot : xCarryForcedSlotMask) {
                if (slot != null && slot >= 0) merged.add(slot);
            }
        }
        if (slotMask != null) {
            for (Integer slot : slotMask) {
                if (slot != null && slot >= 0) merged.add(slot);
            }
        }
        this.xCarryForcedSlotMask = Collections.unmodifiableSet(merged);
        this.xCarryForcedCarryCursor = (xCarryForced && xCarryForcedCarryCursor) || carryCursor;
    }

    public boolean isXCarryArmorBypass() { return xCarryArmorBypass; }
    public void setXCarryArmorBypass(boolean value) { this.xCarryArmorBypass = value; }

    public String getLastSentMessage() { return lastSentMessage == null ? "" : lastSentMessage; }
    public void setLastSentMessage(String message) { lastSentMessage = message == null ? "" : message; }

    public BlockPos getLastInteractedBlockPos() { return lastInteractedBlockPos; }
    public void setLastInteractedBlockPos(BlockPos pos) { lastInteractedBlockPos = pos; }
    public AutismContainerTarget getLastContainerTarget() { return lastContainerTarget; }
    public void setLastContainerTarget(AutismContainerTarget target) {
        lastContainerTarget = target;
        if (target != null && target.isBlock()) {
            lastInteractedBlockPos = target.blockPos();
        }
    }

    public synchronized List<QueuedPacket> getDelayedPackets() {
        return new ArrayList<>(delayedPackets);
    }

    public int getQueueRenderRevision() {
        return queueRenderRevision.get();
    }

    public synchronized QueueRenderSnapshot getQueueRenderSnapshot(boolean sending, int maxLines) {
        List<QueuedPacket> source = sending ? staggeredQueue : delayedPackets;
        int totalCount = source.size();
        if (totalCount == 0 || maxLines <= 0) {
            return new QueueRenderSnapshot(List.of(), totalCount);
        }

        int limit = Math.min(totalCount, maxLines);
        ArrayList<QueuedPacket> visiblePackets = new ArrayList<>(limit);
        for (QueuedPacket packet : source) {
            if (packet == null || packet.packet == null) continue;
            int insertAt = visiblePackets.size();
            for (int i = 0; i < visiblePackets.size(); i++) {
                QueuedPacket existing = visiblePackets.get(i);
                int byDelay = Integer.compare(existing.getDelay(), packet.getDelay());
                if (byDelay > 0 || (byDelay == 0 && existing.getId() > packet.getId())) {
                    insertAt = i;
                    break;
                }
            }
            if (insertAt < limit) {
                visiblePackets.add(insertAt, packet);
                if (visiblePackets.size() > limit) {
                    visiblePackets.remove(limit);
                }
            } else if (visiblePackets.size() < limit) {
                visiblePackets.add(packet);
            }
        }
        return new QueueRenderSnapshot(visiblePackets, totalCount);
    }

    public boolean hasDelayedPackets() {
        return !delayedPackets.isEmpty();
    }

    public synchronized void setDelayedPackets(List<QueuedPacket> packets) {
        delayedPackets.clear();
        if (packets != null && !packets.isEmpty()) {
            delayedPackets.addAll(rebuildQueueWithSequentialIds(packets));
        }
        checkAndResetIdCounter();
        markQueueRenderDirty();
    }

    public synchronized void clearDelayedPackets() {
        if (!delayedPackets.isEmpty()) {
            saveToHistory();
        }
        delayedPackets.clear();
        checkAndResetIdCounter();
        markQueueRenderDirty();
    }

    private void checkAndResetIdCounter() {
        if (delayedPackets.isEmpty() && staggeredQueue.isEmpty()) {
            resetCaptureTiming();
            QueuedPacket.resetIdCounter();
        }
    }

    private void resetCaptureTiming() {
        lastCaptureNanos = -1;
        lastCaptureClientTick = -1;
    }

    private void saveToHistory() {
        lastFlushedQueue.clear();
        if (!delayedPackets.isEmpty()) {
            lastFlushedQueue.addAll(rebuildQueueWithSequentialIds(delayedPackets));
        }
    }

    private void saveQueueToHistory(List<QueuedPacket> queue) {
        lastFlushedQueue.clear();
        if (queue == null || queue.isEmpty()) return;
        lastFlushedQueue.addAll(rebuildQueueWithSequentialIds(queue));
    }

    private void stopStaggeredSendLocked() {
        staggeredQueue.clear();
        staggeredDisplayQueue.clear();
        staggeredNetworkHandler = null;
        staggeredTickCounter = 0;
        flushStartNanos = 0;
        flushStartClientTick = 0;
        staggeredTotal = 0;
        activeSendUsesExplicitDelays = false;
        activeCaptureLastNanos = -1;
        activeCaptureLastClientTick = -1;
        activeCaptureTailDelay = 0;
        staggeredDelayMode = delayMode;
        markQueueRenderDirty();
    }

    private void markQueueRenderDirty() {
        queueRenderRevision.incrementAndGet();
    }

    private boolean isActiveStaggerSendLocked() {
        return staggeredNetworkHandler != null && !staggeredQueue.isEmpty();
    }

    private int getElapsedForDelayModeLocked(DelayMode mode) {
        if (mode == DelayMode.MS) {
            if (flushStartNanos <= 0L) return 0;
            return (int) Math.max(0L, (System.nanoTime() - flushStartNanos) / 1_000_000L);
        }
        return Math.max(0, clientTickCounter - flushStartClientTick);
    }

    private int getQueueTailDelayLocked(List<QueuedPacket> queue) {
        int maxDelay = 0;
        if (queue == null || queue.isEmpty()) return maxDelay;
        for (QueuedPacket qp : queue) {
            if (qp == null || qp.isSent()) continue;
            maxDelay = Math.max(maxDelay, qp.getDelay());
        }
        return maxDelay;
    }

    private void insertQueuedPacketSorted(List<QueuedPacket> queue, QueuedPacket packet) {
        if (queue == null || packet == null) return;
        int insertAt = queue.size();
        for (int i = 0; i < queue.size(); i++) {
            QueuedPacket existing = queue.get(i);
            if (existing == null) continue;
            int byDelay = Integer.compare(existing.getDelay(), packet.getDelay());
            if (byDelay > 0 || (byDelay == 0 && existing.getId() > packet.getId())) {
                insertAt = i;
                break;
            }
        }
        queue.add(insertAt, packet);
    }

    private int computeActiveQueueDelayLocked() {
        int elapsed = getElapsedForDelayModeLocked(staggeredDelayMode);
        if (activeSendUsesExplicitDelays || captureMode) {
            return elapsed;
        }

        if (staggeredPacketSend) {
            int tailDelay = getQueueTailDelayLocked(staggeredQueue);
            return Math.max(elapsed, tailDelay) + staggeredSendDelay;
        }

        return elapsed;
    }

    private int computeActiveCapturedQueueDelayLocked() {
        if (staggeredDelayMode == DelayMode.MS) {
            long now = System.nanoTime();
            long reference = activeCaptureLastNanos > 0L ? activeCaptureLastNanos : flushStartNanos;
            int delta = reference > 0L ? (int) Math.max(0L, (now - reference) / 1_000_000L) : 0;
            activeCaptureLastNanos = now;
            activeCaptureLastClientTick = clientTickCounter;
            activeCaptureTailDelay += delta;
            return activeCaptureTailDelay;
        }

        int tick = clientTickCounter;
        int reference = activeCaptureLastClientTick >= 0 ? activeCaptureLastClientTick : flushStartClientTick;
        int delta = Math.max(0, tick - reference);
        activeCaptureLastClientTick = tick;
        activeCaptureLastNanos = System.nanoTime();
        activeCaptureTailDelay += delta;
        return activeCaptureTailDelay;
    }

    public synchronized boolean restoreLastFlushedQueue() {
        if (lastFlushedQueue.isEmpty()) return false;

        delayedPackets.clear();
        resetCaptureTiming();

        if (!lastFlushedQueue.isEmpty()) {
            delayedPackets.addAll(rebuildQueueWithSequentialIds(lastFlushedQueue));
        }
        markQueueRenderDirty();
        return true;
    }

    public synchronized void enqueuePacket(Packet<?> packet) {
        enqueuePacket(packet, ReplayMode.REGENERATE);
    }

    public synchronized void enqueueExactPacket(Packet<?> packet) {
        enqueuePacket(packet, ReplayMode.EXACT);
    }

    public synchronized void enqueuePacket(Packet<?> packet, ReplayMode replayMode) {
        if (PackHideState.isHardLocked()) return;
        if (packet != null) {
            ReplayMode mode = normalizeReplayModeForEnqueue(packet, replayMode);
            if (isActiveStaggerSendLocked()) {
                int activeDelay = captureMode
                    ? computeActiveCapturedQueueDelayLocked()
                    : computeActiveQueueDelayLocked();
                QueuedPacket queuedPacket = new QueuedPacket(packet, activeDelay, mode);
                insertQueuedPacketSorted(staggeredQueue, queuedPacket);
                insertQueuedPacketSorted(staggeredDisplayQueue, queuedPacket);
                staggeredTotal++;
                markQueueRenderDirty();
                return;
            }

            int delay = 0;
            if (captureMode) {
                if (delayedPackets.isEmpty() && staggeredQueue.isEmpty()) {
                    resetCaptureTiming();
                }
                if (delayMode == DelayMode.TICKS) {

                    int tick = clientTickCounter;
                    if (lastCaptureClientTick < 0) {
                        lastCaptureClientTick = tick;
                        delay = 0;
                    } else {
                        delay = tick - lastCaptureClientTick;
                    }
                } else {

                    long now = System.nanoTime();
                    if (lastCaptureNanos < 0) {
                        lastCaptureNanos = now;
                        delay = 0;
                    } else {
                        delay = (int) ((now - lastCaptureNanos) / 1_000_000L);
                    }
                }
            } else if (staggeredPacketSend) {
                delay = delayedPackets.size() * staggeredSendDelay;
            }
            delayedPackets.add(new QueuedPacket(packet, delay, mode));
            markQueueRenderDirty();
        }
    }

    private ReplayMode normalizeReplayModeForEnqueue(Packet<?> packet, ReplayMode replayMode) {
        ReplayMode mode = replayMode == null ? ReplayMode.REGENERATE : replayMode;
        if (mode == ReplayMode.REGENERATE
            && packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClosePacket closePacket
            && hasExactContainerClickForContainer(readContainerCloseId(closePacket))) {
            return ReplayMode.EXACT;
        }
        return mode;
    }

    private int readContainerCloseId(net.minecraft.network.protocol.game.ServerboundContainerClosePacket packet) {
        if (packet == null) return -1;
        for (String methodName : new String[] {"containerId", "syncId", "getSyncId"}) {
            try {
                java.lang.reflect.Method method = packet.getClass().getDeclaredMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(packet);
                if (value instanceof Number number) return number.intValue();
            } catch (ReflectiveOperationException ignored) {  }
        }
        if (packet.getClass().isRecord()) {
            for (java.lang.reflect.RecordComponent component : packet.getClass().getRecordComponents()) {
                try {
                    Object value = component.getAccessor().invoke(packet);
                    if (value instanceof Number number) return number.intValue();
                } catch (ReflectiveOperationException ignored) {  }
            }
        }
        for (java.lang.reflect.Field field : packet.getClass().getDeclaredFields()) {
            if (field.getType() == int.class) {
                try {
                    field.setAccessible(true);
                    return field.getInt(packet);
                } catch (IllegalAccessException ignored) {  }
            }
        }
        return -1;
    }

    private boolean hasExactContainerClickForContainer(int containerId) {
        for (QueuedPacket queuedPacket : delayedPackets) {
            if (isExactClickForContainer(queuedPacket, containerId)) return true;
        }
        for (QueuedPacket queuedPacket : staggeredQueue) {
            if (isExactClickForContainer(queuedPacket, containerId)) return true;
        }
        return false;
    }

    private boolean isExactClickForContainer(QueuedPacket queuedPacket, int containerId) {
        return queuedPacket != null
            && queuedPacket.isExactReplay()
            && queuedPacket.packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket clickPacket
            && clickPacket.containerId() == containerId;
    }

    public void storeScreen(Screen screen, AbstractContainerMenu handler) {
        this.storedScreen = screen;
        this.storedAbstractContainerMenu = handler;
    }

    public Screen getStoredScreen() {
        return storedScreen;
    }

    public AbstractContainerMenu getStoredAbstractContainerMenu() {
        return storedAbstractContainerMenu;
    }

    public synchronized int flushDelayedPackets(ClientPacketListener networkHandler) {
        if (PackHideState.isHardLocked()) return 0;

        if (!staggeredQueue.isEmpty()) {
            AutismClientMessaging.sendPrefixed("\u00a7cAlready sending - wait for current queue to finish");
            return 0;
        }

        pendingQueueCompletionMessage = null;
        int count = delayedPackets.size();
        if (count == 0) return 0;

        resetCaptureTiming();

        AutismModule module = AutismModule.get();
        boolean hasDelays = delayedPackets.stream().anyMatch(qp -> qp.delay > 0);

        if (module.shouldUseDirectFlush() && !hasDelays) {
            return flushDelayedPacketsDirect(networkHandler);
        }

        saveToHistory();

        if (networkHandler != null) {
            boolean shouldStagger = staggeredPacketSend || hasDelays;

            if (shouldStagger) {

                staggeredDisplayQueue.clear();
                staggeredQueue.addAll(delayedPackets);
                staggeredDisplayQueue.addAll(delayedPackets);
                staggeredNetworkHandler = networkHandler;
                staggeredTickCounter = 0;
                staggeredTotal = count;
                activeSendUsesExplicitDelays = hasDelays;
                staggeredDelayMode = delayMode;
                flushStartNanos = System.nanoTime();
                flushStartClientTick = clientTickCounter;
                activeCaptureLastNanos = flushStartNanos;
                activeCaptureLastClientTick = flushStartClientTick;
                activeCaptureTailDelay = getQueueTailDelayLocked(staggeredQueue);
            } else {
                isFlushing = true;
                try {
                    for (QueuedPacket qp : delayedPackets) {
                        if (AutismPacketRegistry.getC2SPackets().contains(qp.packet.getClass())) {
                            Packet<?> packetToSend = packetForQueuedSend(qp);
                            if (packetToSend == null) {
                                continue;
                            }
                            autismclient.util.AutismPacketSender.send(packetToSend);
                        } else {
                            AutismClientAddon.LOG.warn("[Autism] Skipped sending invalid C2S packet during flush: {}", qp.packet.getClass().getName());
                        }
                    }
                } finally {
                    isFlushing = false;
                }

                QueuedPacket.resetIdCounter();
            }
        }

        delayedPackets.clear();
        markQueueRenderDirty();
        return count;
    }

    public void sendPacketBypassDelay(ClientPacketListener networkHandler, Packet<?> packet) {
        if (PackHideState.isHardLocked()) return;
        if (networkHandler == null || packet == null) return;
        isFlushing = true;
        try {
            networkHandler.getConnection().send(packet, null);
        } finally {
            isFlushing = false;
        }
    }

    private int flushDelayedPacketsDirect(ClientPacketListener networkHandler) {
        if (PackHideState.isHardLocked()) return 0;

        int count = delayedPackets.size();
        if (count == 0) return 0;

        saveToHistory();

        if (networkHandler != null) {
            isFlushing = true;
            try {
                for (QueuedPacket qp : delayedPackets) {
                    if (AutismPacketRegistry.getC2SPackets().contains(qp.packet.getClass())) {
                        Packet<?> packetToSend = packetForQueuedSend(qp);
                        if (packetToSend == null) {
                            continue;
                        }
                        networkHandler.getConnection().send(packetToSend, null);
                    } else {
                        AutismClientAddon.LOG.warn("[Autism] Skipped invalid C2S packet during bypass flush: {}",
                            qp.packet.getClass().getName());
                    }
                }
            } finally {
                isFlushing = false;
            }
        }

        delayedPackets.clear();
        checkAndResetIdCounter();
        markQueueRenderDirty();
        return count;
    }

    public synchronized void regenerateQueue() {
        if (delayedPackets.isEmpty()) return;

        List<QueuedPacket> newPackets = new ArrayList<>(delayedPackets.size());
        int skipped = 0;
        int deferredClickSlot = 0;

        for (QueuedPacket qp : delayedPackets) {

            if (qp.packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket) {

                newPackets.add(new QueuedPacket(qp.packet, qp.delay, qp.id, qp.replayMode));
                deferredClickSlot++;
                continue;
            }

            if (qp.isExactReplay()) {
                newPackets.add(new QueuedPacket(qp.packet, qp.delay, qp.id, qp.replayMode));
                continue;
            }

            Packet<?> regenerated = PacketRegenerator.regenerate(qp.packet);
            if (regenerated != null) {

                newPackets.add(new QueuedPacket(regenerated, qp.delay, qp.id, qp.replayMode));
            } else {

                skipped++;
            }
        }

        delayedPackets.clear();
        delayedPackets.addAll(rebuildQueueWithSequentialIds(newPackets));
        markQueueRenderDirty();

        if (skipped > 0 || deferredClickSlot > 0) {
            AutismClientAddon.LOG.warn("[Autism] Regenerated {} packets, skipped {}, deferred {} ClickSlot (IDs preserved).",
                newPackets.size() - deferredClickSlot, skipped, deferredClickSlot);
        } else {
            AutismClientAddon.LOG.info("[Autism] Regenerated {} queued packets (IDs preserved).", newPackets.size());
        }
    }

    public synchronized void tickStaggeredSend() {
        if (PackHideState.isHardLocked()) {
            staggeredQueue.clear();
            staggeredDisplayQueue.clear();
            staggeredNetworkHandler = null;
            markQueueRenderDirty();
            return;
        }
        if (staggeredQueue.isEmpty() || staggeredNetworkHandler == null) {
            return;
        }

        List<QueuedPacket> toRemove = new ArrayList<>();

        for (QueuedPacket qp : staggeredQueue) {

            boolean shouldSend;
            if (staggeredDelayMode == DelayMode.MS) {

                long elapsedMs = (System.nanoTime() - flushStartNanos) / 1_000_000L;
                shouldSend = elapsedMs >= qp.getDelay() && !qp.isSent();
            } else {

                int elapsedTicks = clientTickCounter - flushStartClientTick;
                shouldSend = elapsedTicks >= qp.getDelay() && !qp.isSent();
            }

            if (shouldSend) {
                isFlushing = true;
                try {

                if (AutismPacketRegistry.getC2SPackets().contains(qp.packet.getClass())) {

                    Packet<?> packetToSend = packetForQueuedSend(qp);
                    if (packetToSend == null) {
                        qp.markAsSent();
                        toRemove.add(qp);
                        continue;
                    }
                    staggeredNetworkHandler.getConnection().send(packetToSend, null);
                    qp.markAsSent();
                    toRemove.add(qp);
                } else {
                        AutismClientAddon.LOG.warn("[Autism] Skipped sending invalid C2S packet: {}", qp.packet.getClass().getName());

                        qp.markAsSent();
                        toRemove.add(qp);
                    }
                } catch (Exception e) {
                    qp.markAsSent();
                    toRemove.add(qp);
                    AutismClientAddon.LOG.error("[Autism] Failed to send queued packet: {}", qp.packet.getClass().getName(), e);
                } finally {
                    isFlushing = false;
                }
            }
        }

        staggeredQueue.removeAll(toRemove);
        if (!toRemove.isEmpty()) {
            markQueueRenderDirty();
        }
        staggeredTickCounter++;

        if (staggeredQueue.isEmpty()) {
            int total = staggeredTotal;
            String completionMessage = consumePendingQueueCompletionMessageLocked();
            stopStaggeredSendLocked();

            if (delayedPackets.isEmpty()) {
                resetCaptureTiming();
                QueuedPacket.resetIdCounter();
            }

            if (completionMessage == null || completionMessage.isBlank()) {
                completionMessage = "Finished sending " + total + " packets.";
            }
            AutismNotifications.show(completionMessage, 0xFF35D873);
        }
    }

    public boolean hasStaggeredSendWork() {
        return !staggeredQueue.isEmpty() && staggeredNetworkHandler != null;
    }

    public boolean hasPacketFlowWork() {
        return delayGuiPackets
            || !sendGuiPackets
            || useCustomPackets
            || isFlushing
            || suppressNextContainerClosePacket
            || suppressNextSignUpdatePacket
            || suppressNextBookEditPacket
            || forceNextSignUpdatePacket
            || forceNextBookEditPacket
            || !allowSignEditing
            || !allowBookUpdate
            || gbreakCaptureMode
            || captureMode
            || blockCaptureCallback != null
            || directionalBlockCaptureCallback != null
            || attackCaptureCallback != null
            || entityCaptureCallback != null
            || captureCancelCallback != null;
    }

    public boolean isStaggering() {
        return !staggeredQueue.isEmpty() && staggeredNetworkHandler != null;
    }

    public synchronized void setPendingQueueCompletionMessage(String message) {
        pendingQueueCompletionMessage = (message == null || message.isBlank()) ? null : message;
    }

    private String consumePendingQueueCompletionMessageLocked() {
        String message = pendingQueueCompletionMessage;
        pendingQueueCompletionMessage = null;
        return message;
    }

    public synchronized List<QueuedPacket> getStaggeredQueue() {
        return new ArrayList<>(staggeredQueue);
    }

    public boolean hasStaggeredPackets() {
        return !staggeredQueue.isEmpty();
    }

    public synchronized List<QueuedPacket> getStaggeredDisplayQueue() {
        return new ArrayList<>(staggeredDisplayQueue);
    }

    public synchronized int clearQueuedPackets() {
        if (!staggeredQueue.isEmpty()) {
            int count = staggeredQueue.size() + delayedPackets.size();
            List<QueuedPacket> combined = new ArrayList<>(staggeredQueue.size() + delayedPackets.size());
            combined.addAll(staggeredQueue);
            combined.addAll(delayedPackets);
            saveQueueToHistory(combined);
            pendingQueueCompletionMessage = null;
            stopStaggeredSendLocked();
            delayedPackets.clear();
            checkAndResetIdCounter();
            return count;
        }

        int count = delayedPackets.size();
        clearDelayedPackets();
        return count;
    }

    public synchronized void removeQueuedPacket(QueuedPacket packet) {
        if (packet == null) return;

        staggeredDisplayQueue.remove(packet);
        boolean removedFromStaggered = staggeredQueue.remove(packet);
        if (removedFromStaggered && staggeredQueue.isEmpty()) {
            stopStaggeredSendLocked();
        }

        if (!removedFromStaggered) {
            delayedPackets.remove(packet);
            if (!delayedPackets.isEmpty()) {
                List<QueuedPacket> rebuilt = rebuildQueueWithSequentialIds(delayedPackets);
                delayedPackets.clear();
                delayedPackets.addAll(rebuilt);
            }
        }

        checkAndResetIdCounter();
        markQueueRenderDirty();
    }

    public synchronized void updatePacketDelay(QueuedPacket packet, int newDelay) {
        if (packet != null) {
            packet.setDelay(newDelay);
            markQueueRenderDirty();
        }
    }

    public synchronized void sortDelayedPacketsByDelayPreservingIds() {
        if (delayedPackets.size() < 2) return;

        List<QueuedPacket> ordered = new ArrayList<>(delayedPackets);
        ordered.sort((a, b) -> {
            int byDelay = Integer.compare(a.getDelay(), b.getDelay());
            if (byDelay != 0) return byDelay;
            return Integer.compare(a.getId(), b.getId());
        });

        delayedPackets.clear();
        delayedPackets.addAll(ordered);
        markQueueRenderDirty();
    }

    private List<QueuedPacket> rebuildQueueWithSequentialIds(List<QueuedPacket> source) {
        List<QueuedPacket> rebuilt = new ArrayList<>();
        if (source == null || source.isEmpty()) return rebuilt;

        List<QueuedPacket> ordered = new ArrayList<>();
        for (QueuedPacket qp : source) {
            if (qp != null && qp.packet != null) {
                ordered.add(qp);
            }
        }
        ordered.sort((a, b) -> {
            int byDelay = Integer.compare(a.getDelay(), b.getDelay());
            if (byDelay != 0) return byDelay;
            return Integer.compare(a.getId(), b.getId());
        });

        QueuedPacket.resetIdCounter();
        for (QueuedPacket qp : ordered) {
            rebuilt.add(new QueuedPacket(qp.packet, qp.getDelay(), qp.getReplayMode()));
        }
        return rebuilt;
    }

    private Packet<?> packetForQueuedSend(QueuedPacket queuedPacket) {
        if (queuedPacket == null || queuedPacket.packet == null) return null;
        return queuedPacket.isExactReplay() ? queuedPacket.packet : PacketRegenerator.regenerate(queuedPacket.packet);
    }

    public record QueueRenderSnapshot(List<QueuedPacket> packets, int totalCount) {
    }

    public static class QueuedPacket {
        private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);

        public final Packet<?> packet;
        public final int id;
        private volatile ReplayMode replayMode;
        private volatile int delay;
        private volatile boolean sent;

        public QueuedPacket(Packet<?> packet, int delay) {
            this(packet, delay, ReplayMode.REGENERATE);
        }

        public QueuedPacket(Packet<?> packet, int delay, ReplayMode replayMode) {
            this.packet = packet;
            this.id = ID_COUNTER.getAndIncrement();
            this.delay = delay;
            this.sent = false;
            this.replayMode = replayMode == null ? ReplayMode.REGENERATE : replayMode;
        }

        public QueuedPacket(Packet<?> packet, int delay, int existingId) {
            this(packet, delay, existingId, ReplayMode.REGENERATE);
        }

        public QueuedPacket(Packet<?> packet, int delay, int existingId, ReplayMode replayMode) {
            this.packet = packet;
            this.id = existingId;
            this.delay = delay;
            this.sent = false;
            this.replayMode = replayMode == null ? ReplayMode.REGENERATE : replayMode;
        }

        public int getId() {
            return id;
        }

        public int getDelay() {
            return delay;
        }

        public ReplayMode getReplayMode() {
            return replayMode;
        }

        public boolean isExactReplay() {
            return replayMode == ReplayMode.EXACT;
        }

        public synchronized void setDelay(int newDelay) {
            if (!sent) {
                this.delay = newDelay;
            }
        }

        public synchronized void setReplayMode(ReplayMode mode) {
            if (!sent) {
                this.replayMode = mode == null ? ReplayMode.REGENERATE : mode;
            }
        }

        public boolean isSent() {
            return sent;
        }

        synchronized void markAsSent() {
            this.sent = true;
        }

        public static void resetIdCounter() {
            ID_COUNTER.set(1);
        }
    }

    public boolean isFlushing() {
        return isFlushing;
    }

    public void setFlushing(boolean value) {
        this.isFlushing = value;
    }

    public void clearStoredScreen() {
        storedScreen = null;
        storedAbstractContainerMenu = null;
    }

    public synchronized void resetAll() {
        sendGuiPackets = true;
        delayGuiPackets = false;
        allowSignEditing = true;
        allowBookUpdate = true;
        bypassResourcePack = false;
        resourcePackForceDeny = false;
        xCarryActive = false;
        xCarryForced = false;
        lastInteractedBlockPos = null;
        lastContainerTarget = null;
        delayedPackets.clear();
        staggeredQueue.clear();
        staggeredDisplayQueue.clear();
        staggeredNetworkHandler = null;
        staggeredTickCounter = 0;
        staggeredTotal = 0;
        QueuedPacket.resetIdCounter();
        activeSendUsesExplicitDelays = false;
        flushStartNanos = 0;
        flushStartClientTick = 0;
        activeCaptureLastNanos = -1;
        activeCaptureLastClientTick = -1;
        activeCaptureTailDelay = 0;
        staggeredDelayMode = DelayMode.TICKS;
        lastCaptureNanos = -1;
        lastCaptureClientTick = -1;
        markQueueRenderDirty();

        storedScreen = null;
        storedAbstractContainerMenu = null;
        serverPluginScans.clear();
        serverInfoOverlayActiveTab = 0;
        serverInfoOverlayPluginScrollOffset = 0;
        serverInfoOverlaySelectedPlugin = "";
        serverInfoOverlayStateAddress = "";
        realServerVersion = "";
        realServerVersionAddress = "";
        serverInfoOverlayProbeDelayMs = 50;
    }

    public static final class ServerPluginScan {
        private final String address;
        private final String contextSignature;
        private final List<String> plugins;
        private final Map<String, List<String>> pluginCommands;
        private final Map<String, String> pluginEvidence;

        private ServerPluginScan(String address, String contextSignature, List<String> plugins, Map<String, List<String>> pluginCommands, Map<String, String> pluginEvidence) {
            this.address = address;
            this.contextSignature = contextSignature == null ? "" : contextSignature;
            this.plugins = plugins == null ? List.of() : new ArrayList<>(plugins);
            this.pluginCommands = copyPluginCommands(pluginCommands);
            this.pluginEvidence = copyPluginEvidence(pluginEvidence);
        }

        public String getAddress() {
            return address;
        }

        public List<String> getPlugins() {
            return new ArrayList<>(plugins);
        }

        public String getContextSignature() {
            return contextSignature;
        }

        public Map<String, List<String>> getPluginCommands() {
            return copyPluginCommands(pluginCommands);
        }

        public Map<String, String> getPluginEvidence() {
            return copyPluginEvidence(pluginEvidence);
        }

        private ServerPluginScan copy() {
            return new ServerPluginScan(address, contextSignature, plugins, pluginCommands, pluginEvidence);
        }

        private static Map<String, List<String>> copyPluginCommands(Map<String, List<String>> source) {
            if (source == null || source.isEmpty()) return Collections.emptyMap();

            Map<String, List<String>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : source.entrySet()) {
                if (entry.getKey() == null) continue;
                copy.put(entry.getKey(), entry.getValue() == null ? List.of() : new ArrayList<>(entry.getValue()));
            }
            return copy;
        }

        private static Map<String, String> copyPluginEvidence(Map<String, String> source) {
            if (source == null || source.isEmpty()) return Collections.emptyMap();

            Map<String, String> copy = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : source.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) continue;
                copy.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
            return copy;
        }
    }

    public boolean shouldEditSigns() {
        return allowSignEditing;
    }

    public void setAllowSignEditing(boolean value) {
        this.allowSignEditing = value;
    }

    public boolean shouldUpdateBook() {
        return allowBookUpdate;
    }

    public void setAllowBookUpdate(boolean value) {
        this.allowBookUpdate = value;
    }

    public boolean shouldBypassResourcePack() {
        return bypassResourcePack;
    }

    public void setBypassResourcePack(boolean value) {
        this.bypassResourcePack = value;
    }

    public boolean shouldForceDenyResourcePack() {
        return resourcePackForceDeny;
    }

    public void setResourcePackForceDeny(boolean value) {
        this.resourcePackForceDeny = value;
    }

    public boolean shouldUseCustomPackets() {
        return useCustomPackets;
    }

    public void setUseCustomPackets(boolean value) {
        this.useCustomPackets = value;
    }

    public Set<Class<? extends Packet<?>>> getC2SPackets() {
        return c2sPackets;
    }

    public void setC2SPackets(Set<Class<? extends Packet<?>>> packets) {
        c2sPackets.clear();
        c2sPackets.addAll(packets);
    }

    public Set<Class<? extends Packet<?>>> getS2CPackets() {
        return s2cPackets;
    }

    public void setS2CPackets(Set<Class<? extends Packet<?>>> packets) {
        s2cPackets.clear();
        s2cPackets.addAll(packets);
    }

    public boolean isStaggeredPacketSend() {
        return staggeredPacketSend;
    }

    public void setStaggeredPacketSend(boolean value) {
        if (value && PackHideState.isHardLocked()) return;
        this.staggeredPacketSend = value;
    }

    public int getStaggeredSendDelay() {
        return staggeredSendDelay;
    }

    public void setStaggeredSendDelay(int delay) {
        this.staggeredSendDelay = Math.max(1, Math.min(40, delay));
    }

    public DelayMode getDelayMode() {
        return delayMode;
    }

    public synchronized DelayMode getQueueDisplayDelayMode() {
        return isActiveStaggerSendLocked() ? staggeredDelayMode : delayMode;
    }

    public void setDelayMode(DelayMode mode) {
        this.delayMode = mode;
    }

    public void toggleDelayMode() {
        this.delayMode = (delayMode == DelayMode.TICKS) ? DelayMode.MS : DelayMode.TICKS;
    }

    public boolean isFabricatorOverlayVisible() {
        return fabricatorOverlayVisible;
    }

    public void setFabricatorOverlayVisible(boolean visible) {
        this.fabricatorOverlayVisible = visible;
    }

    public int getFabricatorOverlayX() {
        return fabricatorOverlayX;
    }

    public void setFabricatorOverlayX(int x) {
        this.fabricatorOverlayX = x;
    }

    public int getFabricatorOverlayY() {
        return fabricatorOverlayY;
    }

    public void setFabricatorOverlayY(int y) {
        this.fabricatorOverlayY = y;
    }

    public String getFabricatorSlotValue() {
        return fabricatorSlotValue;
    }

    public void setFabricatorSlotValue(String value) {
        this.fabricatorSlotValue = value != null ? value : "0";
    }

    public String getFabricatorItemNameValue() {
        return fabricatorItemNameValue;
    }

    public void setFabricatorItemNameValue(String value) {
        this.fabricatorItemNameValue = value != null ? value : "";
    }

    public String getFabricatorTimesValue() {
        return fabricatorTimesValue;
    }

    public void setFabricatorTimesValue(String value) {
        this.fabricatorTimesValue = value != null ? value : "1";
    }

    public int getFabricatorActionIndex() {
        return fabricatorActionIndex;
    }

    public void setFabricatorActionIndex(int index) {
        this.fabricatorActionIndex = Math.max(0, index);
    }

    public boolean isFabricatorCraftUseMaxAmount() {
        return fabricatorCraftUseMaxAmount;
    }

    public void setFabricatorCraftUseMaxAmount(boolean useMaxAmount) {
        this.fabricatorCraftUseMaxAmount = useMaxAmount;
    }

    public int getFabricatorButtonIndex() {
        return fabricatorButtonIndex;
    }

    public void setFabricatorButtonIndex(int index) {
        this.fabricatorButtonIndex = Math.max(0, index);
    }

    public boolean isFabricatorDropWholeStack() {
        return fabricatorDropWholeStack;
    }

    public void setFabricatorDropWholeStack(boolean value) {
        this.fabricatorDropWholeStack = value;
    }

    public String getFabricatorCraftSearchValue() {
        return fabricatorCraftSearchValue;
    }

    public void setFabricatorCraftSearchValue(String value) {
        this.fabricatorCraftSearchValue = value != null ? value : "";
    }

    public int getFabricatorCraftSelectedRecipeId() {
        return fabricatorCraftSelectedRecipeId;
    }

    public void setFabricatorCraftSelectedRecipeId(int recipeId) {
        this.fabricatorCraftSelectedRecipeId = recipeId;
    }

    public String getFabricatorCraftSelectedRecipeKey() {
        return fabricatorCraftSelectedRecipeKey;
    }

    public void setFabricatorCraftSelectedRecipeKey(String recipeKey) {
        this.fabricatorCraftSelectedRecipeKey = recipeKey != null ? recipeKey : "";
    }

    public int getFabricatorCraftScrollOffset() {
        return fabricatorCraftScrollOffset;
    }

    public void setFabricatorCraftScrollOffset(int offset) {
        this.fabricatorCraftScrollOffset = Math.max(0, offset);
    }

    public synchronized List<CraftAction.CraftEntry> getFabricatorCraftPlanEntries() {
        List<CraftAction.CraftEntry> copies = new ArrayList<>(fabricatorCraftPlanEntries.size());
        for (CraftAction.CraftEntry entry : fabricatorCraftPlanEntries) {
            if (entry != null) copies.add(entry.copy());
        }
        return copies;
    }

    public synchronized void setFabricatorCraftPlanEntries(List<CraftAction.CraftEntry> entries) {
        fabricatorCraftPlanEntries.clear();
        if (entries == null) return;
        for (CraftAction.CraftEntry entry : entries) {
            if (entry != null) fabricatorCraftPlanEntries.add(entry.copy());
        }
    }

    public int getFabricatorCraftPlanSelectedIndex() {
        return fabricatorCraftPlanSelectedIndex;
    }

    public void setFabricatorCraftPlanSelectedIndex(int index) {
        fabricatorCraftPlanSelectedIndex = index;
    }

    public int getFabricatorCraftPlanScrollOffset() {
        return fabricatorCraftPlanScrollOffset;
    }

    public void setFabricatorCraftPlanScrollOffset(int offset) {
        fabricatorCraftPlanScrollOffset = Math.max(0, offset);
    }

    public boolean isLanSyncOverlayVisible() {
        return lanSyncOverlayVisible;
    }

    public void setLanSyncOverlayVisible(boolean visible) {
        this.lanSyncOverlayVisible = visible;
    }

    public int getLanSyncOverlayX() {
        return lanSyncOverlayX;
    }

    public void setLanSyncOverlayX(int x) {
        this.lanSyncOverlayX = x;
    }

    public int getLanSyncOverlayY() {
        return lanSyncOverlayY;
    }

    public void setLanSyncOverlayY(int y) {
        this.lanSyncOverlayY = y;
    }

    public int getLanSyncOverlayActiveTab() {
        return lanSyncOverlayActiveTab;
    }

    public void setLanSyncOverlayActiveTab(int activeTab) {
        this.lanSyncOverlayActiveTab = Math.max(0, activeTab);
    }

    public int getLanSyncOverlayScrollOffset() {
        return lanSyncOverlayScrollOffset;
    }

    public void setLanSyncOverlayScrollOffset(int scrollOffset) {
        this.lanSyncOverlayScrollOffset = Math.max(0, scrollOffset);
    }

    public boolean isLanSyncOverlayPerUserMode() {
        return lanSyncOverlayPerUserMode;
    }

    public void setLanSyncOverlayPerUserMode(boolean perUserMode) {
        this.lanSyncOverlayPerUserMode = perUserMode;
    }

    public String getLanSyncOverlaySelectedMacroName() {
        return lanSyncOverlaySelectedMacroName;
    }

    public void setLanSyncOverlaySelectedMacroName(String selectedMacroName) {
        this.lanSyncOverlaySelectedMacroName = selectedMacroName == null ? "" : selectedMacroName;
    }

    public String getLanSyncOverlayExpandedExecutePeer() {
        return lanSyncOverlayExpandedExecutePeer;
    }

    public void setLanSyncOverlayExpandedExecutePeer(String expandedExecutePeer) {
        this.lanSyncOverlayExpandedExecutePeer = expandedExecutePeer == null ? "" : expandedExecutePeer;
    }

    public String getLanSyncOverlaySelectedPeer() {
        return lanSyncOverlaySelectedPeer;
    }

    public void setLanSyncOverlaySelectedPeer(String selectedPeer) {
        this.lanSyncOverlaySelectedPeer = selectedPeer == null ? "" : selectedPeer;
    }

    public boolean isMacroListOverlayVisible() {
        return macroListOverlayVisible;
    }

    public void setMacroListOverlayVisible(boolean visible) {
        this.macroListOverlayVisible = visible;
    }

    public int getMacroListOverlayX() {
        return macroListOverlayX;
    }

    public void setMacroListOverlayX(int x) {
        this.macroListOverlayX = x;
    }

    public int getMacroListOverlayY() {
        return macroListOverlayY;
    }

    public void setMacroListOverlayY(int y) {
        this.macroListOverlayY = y;
    }

    public int getMacroListOverlayScrollOffset() {
        return macroListOverlayScrollOffset;
    }

    public void setMacroListOverlayScrollOffset(int scrollOffset) {
        this.macroListOverlayScrollOffset = Math.max(0, scrollOffset);
    }

    public String getMacroListOverlaySearch() {
        return macroListOverlaySearch;
    }

    public void setMacroListOverlaySearch(String search) {
        this.macroListOverlaySearch = search == null ? "" : search;
    }

    public boolean isQueueEditorOverlayVisible() {
        return queueEditorOverlayVisible;
    }

    public void setQueueEditorOverlayVisible(boolean visible) {
        this.queueEditorOverlayVisible = visible;
    }

    public int getQueueEditorOverlayX() {
        return queueEditorOverlayX;
    }

    public void setQueueEditorOverlayX(int x) {
        this.queueEditorOverlayX = x;
    }

    public int getQueueEditorOverlayY() {
        return queueEditorOverlayY;
    }

    public void setQueueEditorOverlayY(int y) {
        this.queueEditorOverlayY = y;
    }

    public int getServerDataOverlayActiveTab() {
        return serverInfoOverlayActiveTab;
    }

    public void setServerDataOverlayActiveTab(int tab) {
        this.serverInfoOverlayActiveTab = Math.max(0, tab);
    }

    public int getServerDataOverlayPluginScrollOffset() {
        return serverInfoOverlayPluginScrollOffset;
    }

    public void setServerDataOverlayPluginScrollOffset(int offset) {
        this.serverInfoOverlayPluginScrollOffset = Math.max(0, offset);
    }

    public String getServerDataOverlaySelectedPlugin() {
        return serverInfoOverlaySelectedPlugin;
    }

    public void setServerDataOverlaySelectedPlugin(String plugin) {
        this.serverInfoOverlaySelectedPlugin = plugin == null ? "" : plugin;
    }

    public String getServerDataOverlayStateAddress() {
        return serverInfoOverlayStateAddress;
    }

    public void setServerDataOverlayStateAddress(String address) {
        this.serverInfoOverlayStateAddress = normalizeServerAddress(address);
    }

    public String getRealServerVersion(String address) {
        String normalizedAddress = normalizeServerAddress(address);
        if (realServerVersion == null || realServerVersion.isBlank()) return "";
        if (normalizedAddress.isEmpty()) return realServerVersion;
        String normalizedStored = normalizeServerAddress(realServerVersionAddress);
        if (normalizedStored.isEmpty()) return realServerVersion;
        String addrNoPort = stripPort(normalizedAddress);
        String storedNoPort = stripPort(normalizedStored);
        if (addrNoPort.equals(storedNoPort)) return realServerVersion;
        if (normalizedAddress.equals(normalizedStored)) return realServerVersion;
        return "";
    }

    private String stripPort(String address) {
        if (address == null || address.isBlank()) return "";
        int colonIdx = address.lastIndexOf(':');
        if (colonIdx >= 0) {
            String potentialPort = address.substring(colonIdx + 1);
            if (potentialPort.matches("\\d+")) {
                return address.substring(0, colonIdx);
            }
        }
        return address;
    }

    public void setRealServerVersion(String address, String version) {
        String normalizedAddress = normalizeServerAddress(address);
        this.realServerVersionAddress = normalizedAddress;
        this.realServerVersion = version == null ? "" : version.trim();
    }

    public void clearRealServerVersion() {
        this.realServerVersion = "";
        this.realServerVersionAddress = "";
    }

    public int getServerDataOverlayProbeDelayMs() {
        return serverInfoOverlayProbeDelayMs;
    }

    public void setServerDataOverlayProbeDelayMs(int delayMs) {
        this.serverInfoOverlayProbeDelayMs = Math.max(10, Math.min(500, delayMs));
    }

    public int getServerDataOverlayInfoWidth() {
        return serverInfoOverlayInfoWidth;
    }

    public void setServerDataOverlayInfoWidth(int width) {
        this.serverInfoOverlayInfoWidth = Math.max(252, width);
    }

    public int getServerDataOverlayInfoHeight() {
        return serverInfoOverlayInfoHeight;
    }

    public void setServerDataOverlayInfoHeight(int height) {
        this.serverInfoOverlayInfoHeight = Math.max(258, height);
    }

    public int getServerDataOverlayPluginWidth() {
        return serverInfoOverlayPluginWidth;
    }

    public void setServerDataOverlayPluginWidth(int width) {
        this.serverInfoOverlayPluginWidth = Math.max(280, width);
    }

    public int getServerDataOverlayPluginHeight() {
        return serverInfoOverlayPluginHeight;
    }

    public void setServerDataOverlayPluginHeight(int height) {
        this.serverInfoOverlayPluginHeight = Math.max(280, height);
    }

    private static final boolean PAYLOAD_TRACE = Boolean.getBoolean("autism.payload.trace");

    public void startGBreakCapture(Runnable onCaptureComplete) {
        gbreakCaptureMode = true;
        gbreakPacketCount = 0;
        gbreakCapturedPackets.clear();
        gbreakCallback = onCaptureComplete;
    }

    public boolean isGBreakCapturing() {
        return gbreakCaptureMode;
    }

    public void onGBreakPacket(Packet<?> packet) {
        if (!gbreakCaptureMode) return;

        gbreakPacketCount++;
        if (PAYLOAD_TRACE) {
            AutismClientAddon.LOG.info("[GBreak] Packet #{}: {}", gbreakPacketCount, packet.getClass().getSimpleName());
        }

        if (gbreakPacketCount == 2) {

            gbreakCapturedPackets.clear();
            gbreakCapturedPackets.add(new QueuedPacket(packet, 0));
            finishGBreakCapture();
        }
    }

    public List<QueuedPacket> getGBreakCapturedPackets() {
        return new ArrayList<>(gbreakCapturedPackets);
    }

    private void finishGBreakCapture() {
        gbreakCaptureMode = false;
        if (gbreakCallback != null) {
            gbreakCallback.run();
            gbreakCallback = null;
        }
    }

    public void cancelGBreakCapture() {
        gbreakCaptureMode = false;
        gbreakPacketCount = 0;
        gbreakCapturedPackets.clear();
        gbreakCallback = null;
    }

    private volatile boolean suppressNextContainerClosePacket = false;
    private volatile boolean suppressNextSignUpdatePacket = false;
    private volatile boolean suppressNextBookEditPacket = false;
    private volatile boolean forceNextSignUpdatePacket = false;
    private volatile boolean forceNextBookEditPacket = false;
    public void setSuppressNextClosePacket(boolean v) { setSuppressNextContainerClosePacket(v); }
    public void setSuppressNextContainerClosePacket(boolean v) { suppressNextContainerClosePacket = v; }
    public void setSuppressNextSignUpdatePacket(boolean v) { suppressNextSignUpdatePacket = v; }
    public void setSuppressNextBookEditPacket(boolean v) { suppressNextBookEditPacket = v; }
    public void setForceNextSignUpdatePacket(boolean v) { forceNextSignUpdatePacket = v; }
    public void setForceNextBookEditPacket(boolean v) { forceNextBookEditPacket = v; }

    public boolean consumeSuppressNextClosePacket() {
        boolean v = suppressNextContainerClosePacket;
        suppressNextContainerClosePacket = false;
        return v;
    }

    public boolean consumeSuppressNextContainerClosePacket() {
        return consumeSuppressNextClosePacket();
    }

    public boolean consumeSuppressNextSignUpdatePacket() {
        boolean v = suppressNextSignUpdatePacket;
        suppressNextSignUpdatePacket = false;
        return v;
    }

    public boolean consumeSuppressNextBookEditPacket() {
        boolean v = suppressNextBookEditPacket;
        suppressNextBookEditPacket = false;
        return v;
    }

    public boolean consumeForceNextSignUpdatePacket() {
        boolean v = forceNextSignUpdatePacket;
        forceNextSignUpdatePacket = false;
        return v;
    }

    public boolean consumeForceNextBookEditPacket() {
        boolean v = forceNextBookEditPacket;
        forceNextBookEditPacket = false;
        return v;
    }

    private volatile java.util.function.Consumer<net.minecraft.core.BlockPos> blockCaptureCallback = null;
    private volatile java.util.function.BiConsumer<net.minecraft.core.BlockPos, net.minecraft.core.Direction> directionalBlockCaptureCallback = null;

    public void setBlockCaptureCallback(java.util.function.Consumer<net.minecraft.core.BlockPos> cb) {
        this.blockCaptureCallback = cb;
    }

    public void setDirectionalBlockCaptureCallback(java.util.function.BiConsumer<net.minecraft.core.BlockPos, net.minecraft.core.Direction> cb) {
        this.directionalBlockCaptureCallback = cb;
    }

    public boolean consumeBlockCaptureCallback(net.minecraft.core.BlockPos pos) {
        return consumeBlockCaptureCallback(pos, net.minecraft.core.Direction.UP);
    }

    public boolean consumeBlockCaptureCallback(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
        java.util.function.BiConsumer<net.minecraft.core.BlockPos, net.minecraft.core.Direction> dcb = directionalBlockCaptureCallback;
        if (dcb != null) {
            directionalBlockCaptureCallback = null;
            dcb.accept(pos, direction == null ? net.minecraft.core.Direction.UP : direction);
            return true;
        }
        java.util.function.Consumer<net.minecraft.core.BlockPos> cb = blockCaptureCallback;
        if (cb != null) {
            blockCaptureCallback = null;
            cb.accept(pos);
            return true;
        }
        return false;
    }

    public boolean hasBlockCaptureCallback() { return blockCaptureCallback != null || directionalBlockCaptureCallback != null; }

    private volatile Runnable attackCaptureCallback = null;
    private volatile Runnable captureCancelCallback = null;

    public void setAttackCaptureCallback(Runnable cb) { this.attackCaptureCallback = cb; }

    public boolean consumeAttackCaptureCallback() {
        Runnable cb = attackCaptureCallback;
        if (cb != null) {
            attackCaptureCallback = null;
            cb.run();
            return true;
        }
        return false;
    }

    public boolean hasAttackCaptureCallback() { return attackCaptureCallback != null; }

    public void setCaptureCancelCallback(Runnable cb) {
        this.captureCancelCallback = cb;
    }

    public boolean consumeCaptureCancelCallback() {
        Runnable cb = captureCancelCallback;
        if (cb != null) {
            captureCancelCallback = null;
            cb.run();
            return true;
        }
        return false;
    }

    public boolean hasCaptureCancelCallback() { return captureCancelCallback != null; }

    private volatile java.util.function.Consumer<String> entityCaptureCallback = null;

    private volatile boolean entityCaptureSpecific = false;

    public void setEntityCaptureCallback(java.util.function.Consumer<String> cb) {
        this.entityCaptureCallback = cb;
    }

    public boolean consumeEntityCaptureCallback(String payload) {
        java.util.function.Consumer<String> cb = entityCaptureCallback;
        if (cb != null) {
            entityCaptureCallback = null;
            cb.accept(payload);
            return true;
        }
        return false;
    }

    public boolean consumeEntityCaptureCallback(net.minecraft.world.entity.Entity targeted) {
        if (targeted == null || entityCaptureCallback == null) return false;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && targeted == mc.player) return false;
        String typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
            .getKey(targeted.getType()).toString();
        String payload;
        if (entityCaptureSpecific) {
            String name = targeted.getDisplayName().getString().replaceAll("§.", "").trim();
            payload = "~" + targeted.getStringUUID() + "~" + typeId + "~" + name;
            entityCaptureSpecific = false;
        } else {
            payload = typeId;
        }
        return consumeEntityCaptureCallback(payload);
    }

    public boolean hasEntityCaptureCallback() { return entityCaptureCallback != null; }
    public void setEntityCaptureSpecific(boolean v) { entityCaptureSpecific = v; }
    public boolean isEntityCaptureSpecific() { return entityCaptureSpecific; }

    public boolean isCaptureMode() { return captureMode; }
    public void setCaptureMode(boolean v) {
        if (v && PackHideState.isHardLocked()) return;
        this.captureMode = v;
        if (v) {
            this.lastCaptureNanos = -1;
            this.lastCaptureClientTick = -1;
        }
    }

    public void onServerTimeSyncReceived() {
        long now = System.currentTimeMillis();
        if (lastTimeSyncMs > 0) {
            long wallDeltaMs = now - lastTimeSyncMs;
            if (wallDeltaMs > 0) {

                double measured = wallDeltaMs / 20.0;

                measured = Math.max(50.0, Math.min(200.0, measured));

                serverMsPerTick = serverMsPerTick * 0.3 + measured * 0.7;
            }
        }
        lastTimeSyncMs = now;
    }

    public double getServerMsPerTick() {
        return serverMsPerTick;
    }

    public double getEstimatedTps() {
        if (lastTimeSyncMs <= 0) return -1;
        return 1000.0 / serverMsPerTick;
    }

    public void onClientTickStart() {
        clientTickCounter++;
    }

    public int getClientTickCounter() {
        return clientTickCounter;
    }

    private volatile boolean placeCaptureActive = false;

    public boolean isPlaceCaptureActive() {
        return placeCaptureActive;
    }

    public void setPlaceCaptureActive(boolean active) {
        this.placeCaptureActive = active;
    }

}
