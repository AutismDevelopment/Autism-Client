package autismclient.util.macro;

import autismclient.util.macro.MacroActionResources.SharedClientResource;
import autismclient.modules.PackHideState;
import autismclient.util.AutismCompatManager;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismInstaBreakRenderer;
import autismclient.util.AutismMacro;
import autismclient.util.AutismPacketNamer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class MacroExecutor {

    static final long MACRO_WAIT_POLL_NANOS = 500_000L;

    static final long CAPTURE_SETTLE_MS = 300L;
    static final AtomicBoolean isRunning = new AtomicBoolean(false);
    public static final int MAX_CONCURRENT_MACROS = 4;
    static final Object RUN_LOCK = new Object();
    static final EnumMap<SharedClientResource, ReentrantLock> SHARED_RESOURCE_LOCKS = new EnumMap<>(SharedClientResource.class);
    static {
        for (SharedClientResource resource : SharedClientResource.values()) {
            SHARED_RESOURCE_LOCKS.put(resource, new ReentrantLock(true));
        }
    }
    static final AtomicLong NEXT_RUN_ID = new AtomicLong(1L);
    static final java.util.LinkedHashMap<Long, RunState> ACTIVE_RUNS = new java.util.LinkedHashMap<>();

    static final java.util.concurrent.ConcurrentHashMap<Long, RunState> RUNS_BY_ID = new java.util.concurrent.ConcurrentHashMap<>();
    static final AtomicLong RUN_STATE_REVISION = new AtomicLong();
    static final ThreadLocal<RunState> CURRENT_RUN = new ThreadLocal<>();
    static final ThreadLocal<Boolean> SUPPRESS_POST_GUI_AFTER = ThreadLocal.withInitial(() -> false);
    static volatile RunState primaryRun = null;
    static volatile boolean visibleRunActive;
    static AutismMacro currentMacro = null;
    static Thread macroThread = null;

    static final AtomicReference<String> lastReceivedPacket = new AtomicReference<>("");
    static String currentStatus = "";

    static volatile int currentStepIndex = -1;
    static volatile int totalSteps = 0;

    static final AtomicBoolean isWaitingForPacket = new AtomicBoolean(false);
    static final AtomicReference<String> waitingPacketName = new AtomicReference<>("");
    static final Object packetWaitLock = new Object();

    static final AtomicBoolean isWaitingForChat = new AtomicBoolean(false);
    static final AtomicReference<String> waitingChatPattern = new AtomicReference<>("");
    static final AtomicBoolean waitingChatIsRegex = new AtomicBoolean(false);
    static final AtomicReference<String> waitingChatPatternJson = new AtomicReference<>("");
    static final java.util.concurrent.atomic.AtomicInteger waitingChatFuzzyPercent = new java.util.concurrent.atomic.AtomicInteger(100);
    static final AtomicReference<String> lastMatchedChat = new AtomicReference<>("");
    static final Object recentChatLock = new Object();
    static final Deque<RecentChatMessage> recentChatMessages = new ArrayDeque<>();
    static final int MAX_RECENT_CHAT_MESSAGES = 256;
    static final Object recentGuiLock = new Object();
    static final Deque<RecentGuiTitle> recentGuiTitles = new ArrayDeque<>();
    private static final int MAX_RECENT_GUI_TITLES = 128;
    static String recentChatServerKey = "";
    static volatile String recentChatServerKeyMirror = "";

    static volatile CompletableFuture<Void> activePacketFuture = null;
    static volatile CompletableFuture<Void> activeChatFuture = null;

    private static volatile CompletableFuture<Void> activeBaritoneGoalFuture = null;
    static final AtomicReference<RunState> BARITONE_OWNER = new AtomicReference<>();

    static final AtomicBoolean isRotating = new AtomicBoolean(false);
    static final AtomicReference<RunState> ROTATION_OWNER = new AtomicReference<>();
    static final Object ROTATION_STATE_LOCK = new Object();
    static volatile float targetYaw, targetPitch;
    static volatile double rotationSpeed;
    static volatile int rotationAlignedFrames;

    static final java.util.concurrent.CopyOnWriteArrayList<MacroAction> persistentActions =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    static final java.util.concurrent.ConcurrentHashMap<MoveAction, int[]> backgroundMoves =
        new java.util.concurrent.ConcurrentHashMap<>();
    static final ThreadLocal<RepeatPacketContext> REPEAT_PACKET_CONTEXT = new ThreadLocal<>();
    static final ThreadLocal<Integer> REPEAT_PACKET_SKIP = ThreadLocal.withInitial(() -> 0);

    static final class RunState {
        final long id;
        final AutismMacro sourceMacro;
        final AutismMacro macro;
        final AtomicBoolean running = new AtomicBoolean(true);
        final AtomicBoolean finished = new AtomicBoolean(false);
        final CopyOnWriteArrayList<MacroAction> persistentActions = new CopyOnWriteArrayList<>();
        final ConcurrentHashMap<MoveAction, int[]> backgroundMoves = new ConcurrentHashMap<>();
        final CopyOnWriteArrayList<MacroAction> finallyActions = new CopyOnWriteArrayList<>();
        final Object packetWaitLock = new Object();
        final AtomicBoolean waitingForPacket = new AtomicBoolean(false);
        final AtomicReference<String> waitingPacketName = new AtomicReference<>("");
        volatile CompletableFuture<Void> packetFuture = null;
        volatile Packet<?> lastMatchedPacket = null;
        volatile String lastMatchedPacketDirection = "";
        final AtomicBoolean waitingForChat = new AtomicBoolean(false);
        volatile WaitForChatAction waitingChatAction = null;
        volatile CompletableFuture<Void> chatFuture = null;
        volatile CompletableFuture<Void> baritoneGoalFuture = null;
        volatile MacroWorldActions.MineProgressTracker activeMineTracker = null;
        volatile Thread macroThread = null;
        volatile String status = "";
        volatile int currentStepIndex = -1;
        volatile int totalSteps = 0;
        volatile int lastCompletedStep = 0;
        volatile int branchElseStart = -1;
        volatile int branchElseCount = 0;

        volatile int fpsStepHeaderIndex = -1;
        volatile int fpsStepEndIndex = -1;
        volatile int pingStepHeaderIndex = -1;
        volatile int pingStepEndIndex = -1;

        volatile long fpsBackgroundDeadlineNanos = 0L;
        volatile long pingBackgroundDeadlineNanos = 0L;
        volatile java.util.Map<String, Integer> labelIndex = null;
        volatile int totalJumpsThisRun = 0;
        volatile int jumpBudget = 0;
        final java.util.IdentityHashMap<MacroAction, PrearmedWait> prearmedWaits = new java.util.IdentityHashMap<>();
        final java.util.Map<MacroAction, java.util.Map<String, MacroValue>> stagedCaptures = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
        final java.util.Map<MacroAction, CaptureListSelector.State> captureSelection = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
        final RunOptions options;
        final MacroVariableContext variables = new MacroVariableContext();

        RunState(long id, AutismMacro sourceMacro, AutismMacro macro, RunOptions options) {
            this.id = id;
            this.sourceMacro = sourceMacro;
            this.macro = macro;
            this.options = options == null ? RunOptions.NORMAL : options;
            this.totalSteps = macro != null ? macro.actions.size() : 0;
        }
    }

    static final class PrearmedWait {
        final MacroAction action;
        final CompletableFuture<?> future;
        final Runnable cleanup;
        final int completedPacketTargets;

        PrearmedWait(MacroAction action, CompletableFuture<?> future, Runnable cleanup, int completedPacketTargets) {
            this.action = action;
            this.future = future;
            this.cleanup = cleanup == null ? () -> {} : cleanup;
            this.completedPacketTargets = Math.max(0, completedPacketTargets);
        }

        void cleanup() {
            try {
                cleanup.run();
            } catch (Throwable ignored) {  }
        }
    }

    public enum ChatSource { PLAYER, SERVER }

    public record RecentChatMessage(String sender, String message, String displayText, Component displayComponent, ChatSource source, long timestampMs) {}
    public record RecentGuiTitle(String title, String type, long timestampMs) {}
    public record MacroRunSnapshot(AutismMacro macro, String name, String status, int currentStepIndex, int totalSteps, int lastCompletedStep) {}
    public record RunOptions(boolean silentLifecycle, boolean hiddenFromProgressHud, boolean skipLanProgress, boolean backgroundRun) {
        public static final RunOptions NORMAL = new RunOptions(false, false, false, false);

        public static RunOptions silentBackground() {
            return new RunOptions(true, true, true, true);
        }
    }

    record ChatCapture(String sender, String message, String displayText, Component displayComponent, ChatSource source) {}
    record RepeatPacketContext(AutismMacro macro, int startIdx, int endIdx) {}
    record InlineNextActions(java.util.List<MacroAction> actions, int lastIndex) {
        static InlineNextActions empty() {
            return new InlineNextActions(java.util.List.of(), -1);
        }
    }
    record BreakStartPlan(int result, boolean instant, boolean interactFirst) {}
    record BreakContinuePlan(int result, boolean breaksThisTick, boolean breaksNextTick) {}
    record MacroEvent(Packet<?> packet, String direction, long nanoTime, ChatCapture chatCapture,
                              java.util.Map<String, MacroValue> captures) {
        MacroEvent(Packet<?> packet, String direction, long nanoTime, ChatCapture chatCapture) {
            this(packet, direction, nanoTime, chatCapture, java.util.Map.of());
        }

        MacroEvent {
            captures = captures == null ? java.util.Map.of() : java.util.Map.copyOf(captures);
        }

        String label() {
            if (packet == null) return direction == null || direction.isBlank() ? "client event" : direction;
            String dir = direction == null || direction.isBlank() ? "" : direction + " ";
            return dir + AutismPacketNamer.getFriendlyName(packet, direction == null ? "" : direction);
        }
    }

    static final int FLOW_JUMP_BURST_CAP = 100_000;
    static final int FLOW_JUMP_HARD_CAP = 5_000_000;

    public static boolean isCurrentActionRunActive() {
        return MacroLifecycle.isCurrentActionRunActive();
    }

    public static boolean isVisibleRunning() {
        return MacroLifecycle.isVisibleRunning();
    }

    public static boolean isControllingInput() {
        return MacroLifecycle.isControllingInput();
    }

    public static void execute(AutismMacro macro) {
        MacroLifecycle.execute(macro);
    }

    public static long executeTracked(AutismMacro macro) {
        return MacroLifecycle.executeTracked(macro);
    }

    public static long executeTracked(AutismMacro macro, RunOptions options) {
        return MacroLifecycle.executeTracked(macro, options);
    }

    public static boolean isRunActive(long id) {
        return MacroLifecycle.isRunActive(id);
    }

    public static long runStateRevision() {
        return MacroLifecycle.runStateRevision();
    }

    public static long currentRunId() {
        return MacroLifecycle.currentRunId();
    }

    public static void stopRun(long id) {
        MacroLifecycle.stopRun(id);
    }

    static boolean hasMacroCapacity(int activeCount) {
        return MacroLifecycle.hasMacroCapacity(activeCount);
    }

    static void stopCurrentActionRun() {
        MacroLifecycle.stopCurrentActionRun();
    }





    public static boolean waitForMacroCondition(Minecraft mc, MacroAction trigger, int timeoutMs) throws InterruptedException {
        return MacroRace.waitForMacroCondition(mc, trigger, timeoutMs);
    }


    static CaptureListSelector.State captureSelectionState(MacroAction action) {
        return MacroPacketWait.captureSelectionState(action);
    }

    static String buildAutofillQuery(String resolvedCommand, CaptureListSelector.Filter filter, String filterText) {
        return MacroCaptureAutofill.buildAutofillQuery(resolvedCommand, filter, filterText);
    }




    static void waitOneTick() {
        CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
        MacroRunLoop.waitForCondition(tickFuture);
    }


    public static void drainTickAligned() {
        MacroClientThread.drainTickAligned();
    }



    public static void stop() {
        String macroName = (currentMacro != null && currentMacro.name != null) ? currentMacro.name : "";
        for (RunState run : MacroLifecycle.activeRunSnapshot()) {
            MacroLifecycle.requestStop(run, true);
        }
        synchronized (RUN_LOCK) {
            ACTIVE_RUNS.clear();
            RUNS_BY_ID.clear();
            MacroLifecycle.rebuildActiveRunListLocked();
            primaryRun = null;
            visibleRunActive = false;
        }
        isRunning.set(false);
        try {
            Minecraft mcInst = Minecraft.getInstance();
            autismclient.util.AutismContainerHold.releaseAllAndFlush(mcInst == null ? null : mcInst.getConnection());
        } catch (Throwable ignored) {  }
        persistentActions.clear();
        MacroLifecycle.releaseBackgroundMoves(backgroundMoves, Minecraft.getInstance(), null);
        backgroundMoves.clear();
        MacroLifecycle.reconcilePersistentInput(Minecraft.getInstance());
        MacroConditionRegistry.cancelAll();
        MacroPacketObserve.drainOneShotListeners();
        isWaitingForChat.set(false);
        isWaitingForPacket.set(false);
        synchronized (packetWaitLock) {
            waitingPacketName.set("");
            CompletableFuture<Void> pf = activePacketFuture;
            if (pf != null) { pf.cancel(true); activePacketFuture = null; }
        }
        CompletableFuture<Void> cf = activeChatFuture;
        if (cf != null) { cf.cancel(true); activeChatFuture = null; }
        CompletableFuture<Void> bf = activeBaritoneGoalFuture;
        if (bf != null) { bf.cancel(true); activeBaritoneGoalFuture = null; }
        AutismCompatManager.stopBaritone(Minecraft.getInstance());
        BARITONE_OWNER.set(null);
        AutismInstaBreakRenderer.clear();
        Minecraft mc = Minecraft.getInstance();
        PacketGateManager.clearAllAndFlushConfigured(mc == null ? null : mc.getConnection());
        FpsLimitController.clearAll();
        PingSpoofController.clearAllMacros();
        MacroVariables.clear();

        try {
            autismclient.util.AutismLANSync sync = autismclient.util.AutismLANSync.getInstance();
            if (sync.isInSession()) {
                sync.broadcastStepProgress(-1, 0, "");
            }
        } catch (Exception ignored) {  }

        macroThread = null;
        currentMacro = null;
        MacroLifecycle.setCurrentStatus("");
        synchronized (ROTATION_STATE_LOCK) {
            ROTATION_OWNER.set(null);
            isRotating.set(false);
            rotationAlignedFrames = 0;
        }
        if (!macroName.isEmpty()) {
            AutismClientMessaging.sendPrefixed("\u00a7cMacro stopped: " + macroName);
        } else {
            AutismClientMessaging.sendPrefixed("\u00a7cMacro stopped.");
        }
    }

    public static boolean isRunning() {
        return isRunning.get();
    }

    public static int getActiveRunCount() {
        synchronized (RUN_LOCK) {
            return MacroLifecycle.activeRunCountLocked();
        }
    }

    public static boolean hasPacketObservationWork() {
        if (PackHideState.isHardLocked()) return false;
        return isRunning.get()
            || activePacketFuture != null
            || isWaitingForPacket.get()
            || !MacroPacketObserve.ONE_SHOT_RECV.isEmpty()
            || !MacroPacketObserve.ONE_SHOT_SEND.isEmpty()
            || !MacroPacketObserve.TIMED_ONE_SHOT_RECV.isEmpty()
            || !MacroPacketObserve.TIMED_ONE_SHOT_SEND.isEmpty();
    }

    public static boolean isMacroRunning(String macroName) {
        if (macroName == null) return false;
        String targetKey = MacroLifecycle.macroKey(macroName);
        for (RunState run : RUNS_BY_ID.values()) {
            if (run.running.get() && MacroLifecycle.macroKey(MacroLifecycle.macroName(run.macro)).equals(targetKey)) return true;
        }
        return false;
    }

    public static boolean isMacroRunning(AutismMacro macro) {
        if (macro == null) return false;
        for (RunState run : RUNS_BY_ID.values()) {
            if (MacroLifecycle.matchesMacro(run, macro, macro.name)) return true;
        }
        return false;
    }

    public static List<MacroRunSnapshot> getActiveRunSnapshots() {
        synchronized (RUN_LOCK) {
            ArrayList<MacroRunSnapshot> snapshots = new ArrayList<>(ACTIVE_RUNS.size());
            for (RunState run : ACTIVE_RUNS.values()) {
                if (!run.running.get()) continue;
                if (run.options.hiddenFromProgressHud()) continue;
                AutismMacro macro = run.macro;
                String name = macro != null && macro.name != null ? macro.name : "";
                snapshots.add(new MacroRunSnapshot(
                    macro,
                    name,
                    run.status == null ? "" : run.status,
                    run.currentStepIndex,
                    run.totalSteps,
                    run.lastCompletedStep
                ));
            }
            return snapshots;
        }
    }

    public static void stopMacro(String macroName) {
        if (macroName == null || macroName.isBlank()) return;
        String targetKey = MacroLifecycle.macroKey(macroName);
        boolean stopped = false;
        for (RunState run : MacroLifecycle.activeRunSnapshot()) {
            if (run.running.get() && MacroLifecycle.macroKey(MacroLifecycle.macroName(run.macro)).equals(targetKey)) {
                MacroLifecycle.requestStop(run, true);
                stopped = true;
            }
        }
        if (stopped) {
            synchronized (RUN_LOCK) {
                MacroLifecycle.refreshPrimaryRunLocked();
            }
            AutismClientMessaging.sendPrefixed("\u00a7cMacro stopped: " + macroName);
        }
    }

    public static void stopMacro(AutismMacro macro) {
        if (macro == null) return;
        boolean stopped = false;
        String displayName = MacroLifecycle.macroName(macro);
        for (RunState run : MacroLifecycle.activeRunSnapshot()) {
            if (MacroLifecycle.matchesMacro(run, macro, displayName)) {
                MacroLifecycle.requestStop(run, true);
                stopped = true;
            }
        }
        if (stopped) {
            synchronized (RUN_LOCK) {
                MacroLifecycle.refreshPrimaryRunLocked();
            }
            AutismClientMessaging.sendPrefixed("\u00a7cMacro stopped: " + (displayName.isBlank() ? "macro" : displayName));
        }
    }

    public static String getCurrentMacroName() {
        AutismMacro m = currentMacro;
        return (m != null && m.name != null) ? m.name : "";
    }

    public static AutismMacro getCurrentMacro() {
        return currentMacro;
    }

    public static String getCurrentStatus() {
        RunState run = primaryRun;
        return run != null ? run.status : currentStatus;
    }

    public static int getCurrentStepIndex() {
        RunState run = primaryRun;
        return run != null ? run.currentStepIndex : currentStepIndex;
    }

    public static int getTotalSteps() {
        RunState run = primaryRun;
        return run != null ? run.totalSteps : totalSteps;
    }

    public static List<RecentChatMessage> getRecentChatMessages() {
        synchronized (recentChatLock) {
            return new ArrayList<>(recentChatMessages);
        }
    }

    public static List<RecentGuiTitle> getRecentGuiTitles() {
        synchronized (recentGuiLock) {
            return new ArrayList<>(recentGuiTitles);
        }
    }

    public static void recordRecentGuiScreen(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            clearRecentCaptureHistory();
            return;
        }
        if (screen == null || MacroGuiMatcher.isOwnScreen(screen)) return;
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString().trim();
        if (title.isEmpty()) return;
        String type = MacroGuiMatcher.semanticName(screen);
        RecentGuiTitle entry = new RecentGuiTitle(title, type == null ? "" : type, System.currentTimeMillis());
        synchronized (recentGuiLock) {
            recentGuiTitles.removeIf(existing -> existing.title().equals(entry.title()) && existing.type().equals(entry.type()));
            recentGuiTitles.addFirst(entry);
            while (recentGuiTitles.size() > MAX_RECENT_GUI_TITLES) recentGuiTitles.removeLast();
        }
    }

    public static void clearRecentCaptureHistory() {
        synchronized (recentChatLock) {
            recentChatMessages.clear();
            recentChatServerKey = "";
            recentChatServerKeyMirror = "";
        }
        synchronized (recentGuiLock) {
            recentGuiTitles.clear();
        }
    }

    static final long ONE_SHOT_EXPIRE_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(10);

    public static final class OneShotPacketListener {
        public final java.util.concurrent.CompletableFuture<Packet<?>> future = new java.util.concurrent.CompletableFuture<>();
        private final java.util.List<OneShotPacketListener> registry;
        private final java.util.function.Predicate<Packet<?>> match;
        private final long expireAtNanos;
        OneShotPacketListener(java.util.List<OneShotPacketListener> registry,
                              java.util.function.Predicate<Packet<?>> match) {
            this.registry = registry;
            this.match = match;
            this.expireAtNanos = System.nanoTime() + ONE_SHOT_EXPIRE_NANOS;
            registry.add(this);
        }

        boolean offer(Packet<?> packet, long nowNanos) {
            if (future.isDone()) return true;
            if (nowNanos - expireAtNanos >= 0L) { future.cancel(true); return true; }
            if (match.test(packet)) { future.complete(packet); return true; }
            return false;
        }
        public boolean isDone() { return future.isDone(); }
        public Packet<?> getDoneOrNull() {
            if (!future.isDone()) return null;
            try { return future.getNow(null); } catch (Throwable ignored) { return null; }
        }
        public void cancel() {
            registry.remove(this);
            if (!future.isDone()) future.cancel(true);
        }
    }

    public static OneShotPacketListener awaitReceive(java.util.function.Predicate<Packet<?>> match) {
        return MacroPacketObserve.awaitReceive(match);
    }

    public static OneShotPacketListener awaitSend(java.util.function.Predicate<Packet<?>> match) {
        return MacroPacketObserve.awaitSend(match);
    }

    public static String oneShotCensus() {
        return MacroPacketObserve.oneShotCensus();
    }

    public static void onPacketSent(Packet<?> packet) {
        MacroPacketObserve.onPacketSent(packet);
    }

    public static void onPacketReceived(Packet<?> packet) {
        MacroPacketObserve.onPacketReceived(packet);
    }

    public static void onPacketObserved(Packet<?> packet, String direction) {
        MacroPacketObserve.onPacketObserved(packet, direction);
    }


    public static void observeIncomingChat(Packet<?> packet) {
        MacroChatObserve.observeIncomingChat(packet);
    }

    public static String normalizeManualText(String text) {
        return MacroChatText.normalizeManualText(text);
    }

    public static boolean fuzzyChatMatch(String pattern, String candidate, int thresholdPercent) {
        return MacroChatText.fuzzyChatMatch(pattern, candidate, thresholdPercent);
    }

    public static String normalizeChatText(String text) {
        return MacroChatText.normalizeChatText(text);
    }

    public static Component literalizeTextComponent(Component text) {
        return MacroChatText.literalizeTextComponent(text);
    }

    public static String serializeTextComponent(Component text) {
        return MacroChatText.serializeTextComponent(text);
    }

    public static Component deserializeTextComponent(String json) {
        return MacroChatText.deserializeTextComponent(json);
    }

    public static void onRender(float tickDelta) {
        MacroRender.onRender(tickDelta);
    }

    public static boolean hasRenderWork() {
        return MacroRender.hasRenderWork();
    }

    public static void stopAll() {
        MacroRender.stopAll();
    }

}
