package autismclient.util.macro;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import autismclient.util.macro.MacroActionResources.SharedClientResource;
import autismclient.AutismClientAddon;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.util.PacketRegenerator;
import autismclient.util.AutismCompatManager;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismCraftingHelper;
import autismclient.util.AutismGuiActions;
import autismclient.util.AutismInstaBreakRenderer;
import autismclient.util.AutismInventoryClickHelper;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismMacro;
import autismclient.util.AutismPacketNamer;
import autismclient.util.AutismPacketRegistry;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismAutoTool;
import autismclient.mixin.accessor.AutismMultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.tags.ItemTags;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    private static final AtomicLong NEXT_RUN_ID = new AtomicLong(1L);
    static final java.util.LinkedHashMap<Long, RunState> ACTIVE_RUNS = new java.util.LinkedHashMap<>();

    private static final java.util.concurrent.ConcurrentHashMap<Long, RunState> RUNS_BY_ID = new java.util.concurrent.ConcurrentHashMap<>();
    private static final AtomicLong RUN_STATE_REVISION = new AtomicLong();
    static final ThreadLocal<RunState> CURRENT_RUN = new ThreadLocal<>();
    static final ThreadLocal<Boolean> SUPPRESS_POST_GUI_AFTER = ThreadLocal.withInitial(() -> false);
    private static volatile RunState primaryRun = null;
    private static volatile boolean visibleRunActive;
    private static AutismMacro currentMacro = null;
    private static Thread macroThread = null;

    static final AtomicReference<String> lastReceivedPacket = new AtomicReference<>("");
    private static String currentStatus = "";

    private static volatile int currentStepIndex = -1;
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

    private static final AtomicBoolean isRotating = new AtomicBoolean(false);
    private static final AtomicReference<RunState> ROTATION_OWNER = new AtomicReference<>();
    private static final Object ROTATION_STATE_LOCK = new Object();
    private static volatile float targetYaw, targetPitch;
    private static volatile double rotationSpeed;
    private static volatile int rotationAlignedFrames;

    private static final java.util.concurrent.CopyOnWriteArrayList<MacroAction> persistentActions =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final java.util.concurrent.ConcurrentHashMap<MoveAction, int[]> backgroundMoves =
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

    private static final int FLOW_JUMP_BURST_CAP = 100_000;
    private static final int FLOW_JUMP_HARD_CAP = 5_000_000;

    private static java.util.Map<String, Integer> flowLabels(RunState run, AutismMacro macro) {
        java.util.Map<String, Integer> cached = run.labelIndex;
        if (cached != null) return cached;
        java.util.LinkedHashMap<String, Integer> built = new java.util.LinkedHashMap<>();
        for (int k = 0; k < macro.actions.size(); k++) {
            if (macro.actions.get(k) instanceof LabelAction la) {
                String key = la.normalizedName();
                if (!key.isEmpty() && !built.containsKey(key)) built.put(key, k);
            }
        }
        run.labelIndex = built;
        return built;
    }

    private static Integer resolveFlowTarget(RunState run, AutismMacro macro, int i, FlowAction flow, Minecraft mc) {
        return switch (flow.target) {
            case STOP -> null;
            case TOP -> 0;
            case END -> macro.actions.size();
            case FORWARD -> i + 1 + Math.max(0, flow.amount);
            case BACK -> i - Math.max(0, flow.amount);
            case STEP -> Math.max(1, flow.amount) - 1;
            case LABEL -> {
                String name = LabelAction.normalize(MacroVariables.expand(flow.labelName, mc));
                Integer idx = flowLabels(run, macro).get(name);
                if (idx == null) {
                    AutismClientMessaging.sendPrefixed("§eFlow: label '" + flow.labelName + "' not found.");
                    yield flow.onMissingLabel == FlowAction.MissingPolicy.STOP ? null : i + 1;
                }
                yield idx;
            }
        };
    }

    static int performFlowJump(RunState run, AutismMacro macro, int currentI, FlowAction flow, Minecraft mc) {
        run.branchElseStart = -1;
        run.branchElseCount = 0;
        Integer dest = resolveFlowTarget(run, macro, currentI, flow, mc);
        if (dest == null) return Integer.MIN_VALUE;
        int size = macro.actions.size();
        int target = Math.max(0, Math.min(size, dest));
        if (target <= currentI) {
            run.totalJumpsThisRun++;
            run.jumpBudget++;
            if (FLOW_JUMP_HARD_CAP >= 0 && run.totalJumpsThisRun > FLOW_JUMP_HARD_CAP) {
                AutismClientMessaging.sendPrefixed("§cMacro aborted: jump limit exceeded (runaway loop).");
                return Integer.MIN_VALUE;
            }
            if (run.jumpBudget >= FLOW_JUMP_BURST_CAP) {
                run.jumpBudget = 0;

                try { MacroRunLoop.waitForCondition(MacroConditionRegistry.waitForNextTick()); } catch (Exception ignored) {  }
            }
        } else {
            run.jumpBudget = 0;
        }
        return target - 1;
    }

    static boolean isCurrentRunActive() {
        if (PackHideState.isHardLocked()) return false;
        RunState run = CURRENT_RUN.get();
        if (run != null) return run.running.get();
        return isRunning.get();
    }

    public static boolean isCurrentActionRunActive() {
        return isCurrentRunActive();
    }

    static CopyOnWriteArrayList<MacroAction> currentPersistentActions() {
        RunState run = CURRENT_RUN.get();
        return run != null ? run.persistentActions : persistentActions;
    }

    static ConcurrentHashMap<MoveAction, int[]> currentBackgroundMoves() {
        RunState run = CURRENT_RUN.get();
        return run != null ? run.backgroundMoves : backgroundMoves;
    }

    static void setCurrentStatus(String status) {
        RunState run = CURRENT_RUN.get();
        if (run != null) run.status = status == null ? "" : status;
        currentStatus = status == null ? "" : status;
    }

    static void setCurrentStep(int step, int total) {
        RunState run = CURRENT_RUN.get();
        if (run != null) {
            run.currentStepIndex = step;
            run.totalSteps = total;
        }
        currentStepIndex = step;
        totalSteps = total;
    }

    static void markStepCompleted(int stepOneBased) {
        RunState run = CURRENT_RUN.get();
        if (run != null) {
            run.lastCompletedStep = Math.max(run.lastCompletedStep, stepOneBased);
        }
    }

    static int stepOverrideEndIndex(AutismMacro macro, int headerIndex, int coveredRows) {
        int last = macro == null ? headerIndex : macro.actions.size() - 1;
        long end = (long) headerIndex + Math.max(0, coveredRows);
        return (int) Math.max(headerIndex, Math.min(last, end));
    }

    static void armFpsStepOverride(RunState run, int headerIndex, int endIndexInclusive, int fpsLimit) {
        if (run == null) return;
        run.fpsStepHeaderIndex = headerIndex;
        run.fpsStepEndIndex = endIndexInclusive;
        FpsLimitController.applyUntilCleared(run.id, fpsLimit);
    }

    static void armPingStepOverride(RunState run, int headerIndex, int endIndexInclusive,
                                            int delayMs, boolean realIncoming, boolean realOutgoing) {
        if (run == null) return;
        run.pingStepHeaderIndex = headerIndex;
        run.pingStepEndIndex = endIndexInclusive;
        PingSpoofController.applyUntilCleared(run.id, delayMs, realIncoming, realOutgoing);
    }

    static void clearFpsStepOverride(RunState run) {
        if (run == null || run.fpsStepHeaderIndex < 0) return;
        run.fpsStepHeaderIndex = -1;
        run.fpsStepEndIndex = -1;
        FpsLimitController.clear(run.id);
    }

    static void clearPingStepOverride(RunState run) {
        if (run == null || run.pingStepHeaderIndex < 0) return;
        run.pingStepHeaderIndex = -1;
        run.pingStepEndIndex = -1;
        PingSpoofController.clearMacro(run.id);
    }

    static void awaitBackgroundTimedOverrides(RunState run) throws InterruptedException {
        if (run == null) return;

        long deadline = Math.max(run.fpsBackgroundDeadlineNanos, run.pingBackgroundDeadlineNanos);
        long remaining;
        while (isCurrentRunActive() && (remaining = deadline - System.nanoTime()) > 0L) {
            Thread.sleep(Math.min(25L, remaining / 1_000_000L + 1L));
        }
    }

    static void updateStepBoundOverrides(RunState run, int index) {
        if (run == null) return;
        if (run.fpsStepHeaderIndex >= 0 && (index > run.fpsStepEndIndex || index <= run.fpsStepHeaderIndex)) {
            clearFpsStepOverride(run);
        }
        if (run.pingStepHeaderIndex >= 0 && (index > run.pingStepEndIndex || index <= run.pingStepHeaderIndex)) {
            clearPingStepOverride(run);
        }
    }

    static void stopCurrentRun() {
        RunState run = CURRENT_RUN.get();
        if (run != null) {
            run.running.set(false);
        } else {
            stop();
        }
    }

    static void stopCurrentActionRun() {
        stopCurrentRun();
    }

    private static EnumSet<SharedClientResource> resourcesForAction(MacroAction action) {
        return MacroActionResources.resourcesForAction(action);
    }

    static EnumSet<SharedClientResource> acquireClientResources(MacroAction action) throws InterruptedException {
        return acquireClientResources(resourcesForAction(action));
    }

    static EnumSet<SharedClientResource> acquireClientResources(EnumSet<SharedClientResource> resources) throws InterruptedException {
        EnumSet<SharedClientResource> acquired = EnumSet.noneOf(SharedClientResource.class);
        try {
            for (SharedClientResource resource : SharedClientResource.values()) {
                if (resources.contains(resource)) {
                    if (!isCurrentRunActive()) throw new InterruptedException("Macro stopped");
                    SHARED_RESOURCE_LOCKS.get(resource).lockInterruptibly();
                    acquired.add(resource);
                    if (!isCurrentRunActive()) throw new InterruptedException("Macro stopped");
                }
            }
        } catch (InterruptedException e) {
            releaseClientResources(acquired);
            throw e;
        }
        return acquired;
    }

    static EnumSet<SharedClientResource> resourcesForActions(java.util.List<MacroAction> actions) {
        return MacroActionResources.resourcesForActions(actions);
    }

    static void releaseClientResources(EnumSet<SharedClientResource> resources) {
        if (resources == null || resources.isEmpty()) return;
        SharedClientResource[] ordered = SharedClientResource.values();
        for (int i = ordered.length - 1; i >= 0; i--) {
            SharedClientResource resource = ordered[i];
            if (resources.contains(resource)) {
                ReentrantLock lock = SHARED_RESOURCE_LOCKS.get(resource);
                if (lock.isHeldByCurrentThread()) lock.unlock();
            }
        }
    }

    static MacroVariableContext currentVariableContext() {
        RunState run = CURRENT_RUN.get();
        return run == null ? null : run.variables;
    }

    static void withRunContext(RunState run, Runnable task) {
        RunState previous = CURRENT_RUN.get();
        if (run != null) CURRENT_RUN.set(run);
        try {
            task.run();
        } finally {
            if (previous != null) CURRENT_RUN.set(previous);
            else CURRENT_RUN.remove();
        }
    }

    static InlineNextActions collectInlineNextActions(java.util.List<MacroAction> actions, int currentIndex) {
        if (actions == null || currentIndex < 0 || currentIndex >= actions.size() - 1) return InlineNextActions.empty();
        java.util.ArrayList<MacroAction> collected = new java.util.ArrayList<>();
        int lastIndex = -1;
        for (int j = currentIndex + 1; j < actions.size(); j++) {
            MacroAction candidate = actions.get(j);
            if (candidate instanceof DisconnectAction
                    || candidate instanceof RaceAction
                    || candidate instanceof ReportAction
                    || candidate instanceof RepeatAction
                    || candidate instanceof BranchAction
                    || candidate instanceof FinallyAction) {
                break;
            }
            if (candidate == null || !candidate.isEnabled()) continue;
            if (!RaceAction.isBodyAction(candidate)) continue;
            collected.add(candidate);
            lastIndex = j;
        }
        return collected.isEmpty() ? InlineNextActions.empty() : new InlineNextActions(java.util.List.copyOf(collected), lastIndex);
    }

    private static volatile java.util.List<RunState> ACTIVE_RUN_LIST = java.util.List.of();

    private static void rebuildActiveRunListLocked() {
        ACTIVE_RUN_LIST = java.util.List.copyOf(ACTIVE_RUNS.values());
    }

    private static java.util.List<RunState> activeRunSnapshot() {
        return ACTIVE_RUN_LIST;
    }

    static void finishCurrentRun() {
        RunState run = CURRENT_RUN.get();
        if (run == null) return;
        if (!run.finished.compareAndSet(false, true)) return;
        requestStop(run, false);

        clearFpsStepOverride(run);
        clearPingStepOverride(run);

        synchronized (RUN_LOCK) {
            ACTIVE_RUNS.remove(run.id);
            RUNS_BY_ID.remove(run.id);
            rebuildActiveRunListLocked();
            if (ACTIVE_RUNS.isEmpty()) MacroCaptureAutofill.allRunsStoppedAtMs = System.currentTimeMillis();
            RUN_STATE_REVISION.incrementAndGet();
            refreshPrimaryRunLocked();
        }
        if (!isRunning.get()) {
            AutismInstaBreakRenderer.clear();

            FpsLimitController.clearAll();
            PingSpoofController.clearAllMacros();
        }
        CURRENT_RUN.remove();
    }

    private static boolean hasRunningRunsLocked() {
        for (RunState run : ACTIVE_RUNS.values()) {
            if (run.running.get()) return true;
        }
        return false;
    }

    private static RunState firstVisibleRunLocked() {
        for (RunState run : ACTIVE_RUNS.values()) {
            if (run.running.get() && !run.options.hiddenFromProgressHud()) return run;
        }
        return null;
    }

    private static int activeRunCountLocked() {
        int count = 0;
        for (RunState run : ACTIVE_RUNS.values()) {
            if (run.running.get()) count++;
        }
        return count;
    }

    static boolean hasMacroCapacity(int activeCount) {
        return Math.max(0, activeCount) < MAX_CONCURRENT_MACROS;
    }

    private static void refreshPrimaryRunLocked() {
        primaryRun = firstVisibleRunLocked();
        visibleRunActive = primaryRun != null;
        currentMacro = primaryRun != null ? primaryRun.macro : null;
        currentStatus = primaryRun != null ? primaryRun.status : "";
        currentStepIndex = primaryRun != null ? primaryRun.currentStepIndex : -1;
        totalSteps = primaryRun != null ? primaryRun.totalSteps : 0;
        isRunning.set(hasRunningRunsLocked());
    }

    private static boolean hasRunningRuns() {
        synchronized (RUN_LOCK) {
            return hasRunningRunsLocked();
        }
    }

    public static boolean isVisibleRunning() {
        return visibleRunActive;
    }

    public static boolean isControllingInput() {
        ReentrantLock inputLock = SHARED_RESOURCE_LOCKS.get(SharedClientResource.INPUT);
        if (inputLock != null && inputLock.isLocked()) return true;
        if (!backgroundMoves.isEmpty()) return true;
        for (MacroAction action : persistentActions) {
            if (action instanceof MoveAction || action instanceof JumpAction || action instanceof SneakAction || action instanceof SprintAction) return true;
        }
        synchronized (RUN_LOCK) {
            for (RunState run : ACTIVE_RUNS.values()) {
                if (run == null || !run.running.get()) continue;
                if (!run.backgroundMoves.isEmpty()) return true;
                for (MacroAction action : run.persistentActions) {
                    if (action instanceof MoveAction || action instanceof JumpAction || action instanceof SneakAction || action instanceof SprintAction) return true;
                }
            }
        }
        return false;
    }

    static void broadcastStepProgress(RunState run, int step, int total, String macroName) {
        if (run != null && run.options.skipLanProgress()) return;
        try {
            autismclient.util.AutismLANSync sync = autismclient.util.AutismLANSync.getInstance();
            if (sync.isInSession()) {
                sync.broadcastStepProgress(step, total, macroName);
            }
        } catch (Exception ignored) {  }
    }

    private static void requestStop(RunState run, boolean interruptThread) {
        if (run == null) return;
        if (run.running.getAndSet(false)) {
            RUN_STATE_REVISION.incrementAndGet();
        }
        run.persistentActions.clear();
        for (PrearmedWait wait : new ArrayList<>(run.prearmedWaits.values())) {
            wait.cleanup();
        }
        run.prearmedWaits.clear();
        releaseBackgroundMoves(run.backgroundMoves, Minecraft.getInstance(), run);
        run.backgroundMoves.clear();
        reconcilePersistentInput(Minecraft.getInstance());
        synchronized (run.packetWaitLock) {
            if (run.packetFuture != null) run.packetFuture.cancel(true);
            run.packetFuture = null;
            run.waitingForPacket.set(false);
            run.waitingPacketName.set("");
        }
        CompletableFuture<Void> chatFuture = run.chatFuture;
        if (chatFuture != null) chatFuture.cancel(true);
        run.chatFuture = null;
        run.waitingForChat.set(false);
        run.waitingChatAction = null;
        CompletableFuture<Void> baritoneFuture = run.baritoneGoalFuture;
        if (baritoneFuture != null) baritoneFuture.cancel(true);
        run.baritoneGoalFuture = null;
        run.activeMineTracker = null;
        Minecraft mc = Minecraft.getInstance();
        PacketGateManager.clearOwnerAndFlushConfigured(run.id, mc == null ? null : mc.getConnection());
        FpsLimitController.clear(run.id);
        PingSpoofController.clearMacro(run.id);
        cancelRotation(run);
        MacroWorldActions.requestOwnedBaritoneStop(run, mc);
        Thread thread = run.macroThread;
        if (interruptThread && thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
    }

    static void releaseBackgroundMoves(ConcurrentHashMap<MoveAction, int[]> moves, Minecraft mc, RunState owner) {
        if (moves == null || moves.isEmpty() || mc == null) return;
        List<MoveAction> toRelease = new ArrayList<>(moves.keySet());
        mc.execute(() -> {
            for (MoveAction move : toRelease) {
                try {
                    releaseMoveIfUnused(owner, move, mc);
                } catch (Exception ignored) {  }
            }
        });
    }

    static void releaseMoveIfUnused(RunState owner, MoveAction move, Minecraft mc) {
        if (move == null || mc == null) return;
        boolean stillOwned = false;
        synchronized (RUN_LOCK) {
            for (RunState active : ACTIVE_RUNS.values()) {
                if (active == owner || !active.running.get()) continue;
                for (MoveAction other : active.backgroundMoves.keySet()) {
                    if (other.direction == move.direction) {
                        stillOwned = true;
                        break;
                    }
                }
                if (stillOwned) break;
            }
        }
        if (!stillOwned && owner != null) {
            for (MoveAction other : backgroundMoves.keySet()) {
                if (other.direction == move.direction) {
                    stillOwned = true;
                    break;
                }
            }
        }
        if (!stillOwned) move.release(mc);
    }

    private static void reconcilePersistentInput(Minecraft mc) {
        if (mc == null) return;
        mc.execute(() -> {
            applyAggregateSneakState(mc);
            applyAggregateSprintState(mc);
        });
    }

    static void applyAggregateSneakState(Minecraft mc) {
        if (mc == null || mc.options == null || mc.options.keyShift == null) return;
        boolean held = hasPersistentInput(SneakAction.class);
        mc.options.keyShift.setDown(held);
    }

    static void applyAggregateSprintState(Minecraft mc) {
        if (mc == null) return;
        boolean held = hasPersistentInput(SprintAction.class);
        if (mc.options != null && mc.options.keySprint != null) mc.options.keySprint.setDown(held);
        if (mc.player != null) mc.player.setSprinting(held);
    }

    private static boolean hasPersistentInput(Class<? extends MacroAction> type) {
        for (MacroAction action : persistentActions) {
            if (type.isInstance(action)) return true;
        }
        synchronized (RUN_LOCK) {
            for (RunState run : ACTIVE_RUNS.values()) {
                if (!run.running.get()) continue;
                for (MacroAction action : run.persistentActions) {
                    if (type.isInstance(action)) return true;
                }
            }
        }
        return false;
    }

    private static String macroKey(String macroName) {
        return macroName == null ? "" : macroName.trim().toLowerCase(Locale.ROOT);
    }

    private static String macroName(AutismMacro macro) {
        return macro != null && macro.name != null ? macro.name : "";
    }

    private static boolean matchesMacro(RunState run, AutismMacro macro, String macroName) {
        if (run == null || !run.running.get()) return false;
        if (macro != null && (run.sourceMacro == macro || run.macro == macro)) return true;
        String targetKey = macroKey(macroName);
        if (targetKey.isEmpty() && macro != null) targetKey = macroKey(macro.name);
        return !targetKey.isEmpty() && macroKey(macroName(run.macro)).equals(targetKey);
    }

    public static void execute(AutismMacro macro) {
        executeTracked(macro);
    }

    public static long executeTracked(AutismMacro macro) {
        return executeTracked(macro, RunOptions.NORMAL);
    }

    public static long executeTracked(AutismMacro macro, RunOptions options) {
        if (PackHideState.isHardLocked()) return -1L;
        RunOptions runOptions = options == null ? RunOptions.NORMAL : options;
        if (macro == null || macro.actions == null || macro.actions.isEmpty()) {
            if (runOptions.silentLifecycle()) return -1L;
            AutismClientMessaging.sendPrefixed("\u00a7cMacro has no actions!");
            return -1L;
        }

        AutismMacro executionMacro;
        try {
            executionMacro = macro.deepCopy();
        } catch (Throwable copyError) {
            if (!runOptions.silentLifecycle()) AutismClientMessaging.sendPrefixed("\u00a7cCould not prepare macro run.");
            return -1L;
        }
        if (executionMacro == null || executionMacro.actions == null || executionMacro.actions.isEmpty()) return -1L;

        RunState runState;
        synchronized (RUN_LOCK) {
            String name = macro.name == null ? "" : macro.name;
            String nameKey = macroKey(name);
            if (!runOptions.backgroundRun()) {
                for (RunState active : ACTIVE_RUNS.values()) {
                    if (active.options.backgroundRun()) continue;
                    String activeName = active.macro != null && active.macro.name != null ? active.macro.name : "";
                    if (active.running.get() && macroKey(activeName).equals(nameKey)) {
                        if (!runOptions.silentLifecycle()) AutismClientMessaging.sendPrefixed("\u00a7eMacro already running: " + name);
                        return -1L;
                    }
                }
                if (!hasMacroCapacity(activeRunCountLocked())) {
                    if (!runOptions.silentLifecycle()) AutismClientMessaging.sendPrefixed("\u00a7cOnly " + MAX_CONCURRENT_MACROS + " macros can run at the same time.");
                    return -1L;
                }
            } else if (!hasMacroCapacity(activeRunCountLocked())) {
                return -1L;
            }
            runState = new RunState(NEXT_RUN_ID.getAndIncrement(), macro, executionMacro, runOptions);
            if (ACTIVE_RUNS.isEmpty()) MacroCaptureAutofill.expireSuggestionCacheAfterIdle();
            ACTIVE_RUNS.put(runState.id, runState);
            RUNS_BY_ID.put(runState.id, runState);
            rebuildActiveRunListLocked();
            RUN_STATE_REVISION.incrementAndGet();
            refreshPrimaryRunLocked();
        }
        if (!runOptions.silentLifecycle()) AutismClientMessaging.sendPrefixed("\u00a7aStarted macro: " + macro.name);

        broadcastStepProgress(runState, 0, executionMacro.actions.size(), executionMacro.name);

        Thread thread = new Thread(() -> MacroRunLoop.run(runState), "Autism-Macro-" + runState.id);
        thread.setDaemon(true);
        macroThread = thread;
        runState.macroThread = thread;
        try {
            thread.start();
        } catch (Throwable startError) {

            requestStop(runState, false);
            synchronized (RUN_LOCK) {
                ACTIVE_RUNS.remove(runState.id);
                RUNS_BY_ID.remove(runState.id);
                rebuildActiveRunListLocked();
                if (ACTIVE_RUNS.isEmpty()) MacroCaptureAutofill.allRunsStoppedAtMs = System.currentTimeMillis();
                RUN_STATE_REVISION.incrementAndGet();
                refreshPrimaryRunLocked();
            }
            return -1L;
        }
        return runState.id;
    }

    public static boolean isRunActive(long id) {
        if (id < 0L) return false;
        RunState run = RUNS_BY_ID.get(id);
        return run != null && run.running.get();
    }

    public static long runStateRevision() {
        return RUN_STATE_REVISION.get();
    }

    public static long currentRunId() {
        RunState run = CURRENT_RUN.get();
        return run == null ? -1L : run.id;
    }

    public static void stopRun(long id) {
        if (id < 0L) return;
        RunState run;
        synchronized (RUN_LOCK) {
            run = ACTIVE_RUNS.get(id);
        }
        if (run == null) return;
        requestStop(run, true);
        synchronized (RUN_LOCK) {
            refreshPrimaryRunLocked();
        }
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

    static void executeSingleActionWithWaits(Minecraft mc, MacroAction action, AutismModule module) throws InterruptedException {
        if (!MacroDynamicBindings.apply(action, mc)) return;
        if (!MacroActionRunner.dynamicTextInputsAvailable(action, mc)) return;
        MacroPrearm.clearOwnCaptureOutput(action);
        EnumSet<SharedClientResource> heldResources = acquireClientResources(action);
        try {

        if (!MacroActionRunner.waitForGuiBeforeAction(mc, action)) return;
        MacroActionRunner.beginRaycastAim(mc, action);
        java.util.concurrent.CompletableFuture<Void> postGuiFuture =
                (action instanceof ItemAction || action instanceof DropAction || MacroActionRunner.isTargetWaitInteractionAction(action)) ? null : MacroActionRunner.createPostGuiFuture(mc, action);

        if (action instanceof DelayAction) {
            DelayAction da = (DelayAction) action;
            if (da.useTicks) {
                for (int t = 0; t < da.delayTicks && isCurrentRunActive(); t++) {
                    CompletableFuture<Void> tf = MacroConditionRegistry.waitForNextTick();
                    MacroRunLoop.waitForCondition(tf);
                }
            } else {
                Thread.sleep(da.delayMs);
            }
        } else if (action instanceof CustomMenuAction customMenu) {
            setCurrentStatus(customMenu.getDisplayName());
            customMenu.execute(mc);
        } else if (action instanceof WaitForPacketAction packetAction) {
            packetAction.matchedPacket = null;
            packetAction.matchedDirection = "";
            RunState packetRun = CURRENT_RUN.get();
            if (packetRun != null) {
                packetRun.lastMatchedPacket = null;
                packetRun.lastMatchedPacketDirection = "";
            }
            java.util.List<String> targets = packetAction.effectiveList();
            if (targets.isEmpty()) {
                MacroPacketWait.awaitPacket("");
            } else {
                for (String target : targets) {
                    if (!isCurrentRunActive()) break;
                    MacroPacketWait.awaitPacket(target);
                }
            }
            MacroPrearm.copyLastMatchedPacket(packetAction);
        } else if (action instanceof WaitPacketMatchAction wpma) {
            setCurrentStatus(wpma.getDisplayName());
            MacroPrearm.waitForPacketMatch(wpma);
        } else if (action instanceof WaitInventoryPredicateAction wipa) {
            setCurrentStatus(wipa.getDisplayName());
            MacroRunLoop.waitForPredicate(() -> wipa.matches(mc), wipa.timeoutMs);
        } else if (action instanceof WaitDurabilityAction wda) {
            setCurrentStatus(wda.getDisplayName());
            if (wda.useNext) MacroDurability.waitForDurabilityUseNext(mc, wda);
            else MacroRunLoop.waitForPredicate(() -> wda.matches(mc), wda.timeoutMs);
        } else if (action instanceof WaitFreeSlotsAction wfsa) {
            setCurrentStatus(wfsa.getDisplayName());
            MacroRunLoop.waitForPredicate(() -> wfsa.matches(mc), wfsa.timeoutMs);
        } else if (action instanceof WaitEntityTargetAction weta) {
            setCurrentStatus(weta.getDisplayName());
            MacroRunLoop.waitForPredicate(() -> weta.matches(mc), weta.timeoutMs);
        } else if (action instanceof WaitGuiTypeAction wgta) {
            setCurrentStatus(wgta.getDisplayName());
            MacroPrearm.clearCaptureOutputs(wgta);
            if (wgta.waitMode == WaitGuiTypeAction.WaitMode.CLOSE) MacroRunLoop.waitForPredicate(MacroRunLoop.guiClosePredicate(wgta, mc), wgta.timeoutMs);
            else MacroRunLoop.waitForPredicate(() -> {
                var captured = wgta.capture(mc);
                captured.ifPresent(values -> MacroPrearm.stageCapture(wgta, values));
                return captured.isPresent();
            }, wgta.timeoutMs);
            MacroPrearm.commitStagedCapture(wgta);
        } else if (action instanceof PacketGateAction pga) {
            MacroRace.runPacketGateAction(mc, pga);
        } else if (MacroActionRunner.isTargetWaitInteractionAction(action)) {
            postGuiFuture = MacroActionRunner.runTargetWaitInteractionAction(mc, action);
        } else if (action instanceof WaitForHealthAction) {
            WaitForHealthAction wh = (WaitForHealthAction) action;
            setCurrentStatus(wh.waitingStatusText());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForHealth(wh.healthThreshold, wh.below);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForBlockAction) {
            WaitForBlockAction wba = (WaitForBlockAction) action;
            setCurrentStatus(wba.getDisplayName());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForBlock(wba);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForGuiAction) {
            WaitForGuiAction wga = (WaitForGuiAction) action;
            MacroPrearm.clearCaptureOutputs(wga);
            if (wga.waitMode == WaitForGuiAction.WaitMode.CLOSE) {
                setCurrentStatus("Waiting for GUI close: " + wga.guiTitle);
                MacroRunLoop.waitForPredicate(MacroRunLoop.guiClosePredicate(wga, mc), wga.timeoutMs);
            } else {
                setCurrentStatus("Waiting for GUI: " + wga.guiTitle);
                MacroRunLoop.waitForPredicate(() -> {
                    var captured = wga.captureGui(mc.gui.screen());
                    captured.ifPresent(values -> MacroPrearm.stageCapture(wga, values));
                    return captured.isPresent();
                }, wga.timeoutMs);
            }
            MacroPrearm.commitStagedCapture(wga);
        } else if (action instanceof WaitForCooldownAction) {
            WaitForCooldownAction wca = (WaitForCooldownAction) action;
            ItemTarget cooldownTarget = MacroWorldActions.resolveItemTarget(wca.itemTarget, wca.itemName).resolveTemplate(mc);
            if (cooldownTarget == null) return;
            String cooldownLabel = MacroWorldActions.describeItemTarget(cooldownTarget);
            setCurrentStatus("Waiting for cooldown: " +
                (!cooldownLabel.isEmpty() ? cooldownLabel : (wca.checkMainInteractionHand ? "Main Hand" : "Off Hand")));
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForCooldown(cooldownTarget, wca.checkMainInteractionHand);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitPosAction) {
            WaitPosAction wpa = (WaitPosAction) action;
            setCurrentStatus("Waiting for Pos: " + String.format("%.0f, %.0f, %.0f", wpa.x, wpa.y, wpa.z));
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForPos(
                    wpa.x, wpa.y, wpa.z, wpa.leeway, wpa.checkRotation, wpa.yaw, wpa.pitch, wpa.rotLeeway);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof CaptureValueAction captureValue) {
            MacroCaptureAutofill.runCaptureValueAction(mc, captureValue);
        } else if (action instanceof WaitForChatAction) {
            WaitForChatAction wca = (WaitForChatAction) action;
            setCurrentStatus("Waiting for chat: " + wca.pattern);
            MacroPacketWait.awaitChat(wca);
        } else if (action instanceof WaitForEntityAction) {
            WaitForEntityAction wea = (WaitForEntityAction) action;
            setCurrentStatus(wea.getDisplayName());
            try {
                MacroRunLoop.waitForPredicate(() -> wea.matchesEntityTarget(mc), wea.timeoutMs);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForSoundAction) {
            WaitForSoundAction wsa = (WaitForSoundAction) action;
            String sndDesc = wsa.soundIds.isEmpty() ? "any" : wsa.soundIds.get(0);
            setCurrentStatus("Waiting for sound: " + sndDesc);
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForSound(wsa);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForSlotChangeAction) {
            WaitForSlotChangeAction wsca = (WaitForSlotChangeAction) action;
            setCurrentStatus(wsca.getDisplayName());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForSlotChange(wsca);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForWorldChangeAction world) {
            setCurrentStatus(world.getDisplayName());
            try {
                CompletableFuture<?> future = MacroRace.raceConditionFuture(mc, world);
                MacroRace.waitForFutureDone(future, 0);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForPositionDeltaAction wpda) {
            setCurrentStatus(wpda.getDisplayName());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForPositionDelta(wpda.distance, wpda.horizontalOnly);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForTeleportAction teleport) {
            setCurrentStatus(teleport.getDisplayName());
            try {
                CompletableFuture<?> future = MacroRace.raceConditionFuture(mc, teleport);
                MacroRace.waitForFutureDone(future, 0);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitGamemodeChangeAction gm) {
            setCurrentStatus(gm.getDisplayName());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForGamemodeChange(gm);
                MacroRace.waitForFutureDone(future, gm.timeoutMs);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitMovementAction wm) {
            setCurrentStatus(wm.getDisplayName());
            try {
                CompletableFuture<?> future = MacroRace.raceConditionFuture(mc, wm.resolveSubAction());
                MacroRace.waitForFutureDone(future, 0);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForLanStepAction) {
            WaitForLanStepAction wla = (WaitForLanStepAction) action;
            setCurrentStatus(wla.getDisplayName());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForLanStep(wla);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForMacroStepAction) {
            WaitForMacroStepAction wma = (WaitForMacroStepAction) action;
            setCurrentStatus(wma.getDisplayName());
            MacroRace.waitForMacroStep(wma);
        } else if (action instanceof GoToAction) {
            GoToAction ga = (GoToAction) action;
            MacroWorldActions.runGoToAction(mc, ga);
        } else if (action instanceof MoveAction) {
            MoveAction ma = (MoveAction) action;
            if (ma.nonBlocking) {
                mc.execute(() -> ma.execute(mc));
                currentBackgroundMoves().put(ma, new int[]{ma.durationTicks - 1});
            } else {
                for (int t = 0; t < ma.durationTicks && isCurrentRunActive(); t++) {
                    mc.execute(() -> ma.execute(mc));
                    CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                    MacroRunLoop.waitForCondition(tickFuture);
                }
                RunState movementRun = CURRENT_RUN.get();
                mc.execute(() -> releaseMoveIfUnused(movementRun, ma, mc));
            }
        } else if (action instanceof LookAtBlockAction) {
            LookAtBlockAction la = (LookAtBlockAction) action;
            LookAtBlockAction.RotationTarget rotationTarget = resolveLookAtTargetOnClient(mc, la);
            if (rotationTarget == null) {
                return;
            }
            if (la.smooth) {
                startSmoothRotation(rotationTarget.yaw(), rotationTarget.pitch(), la.getRotationStep());
                if (la.waitForCompletion) {
                    waitForSmoothRotationCompletion();
                }
            } else {
                mc.execute(() -> {
                    if (mc.player == null) return;
                    mc.player.setYRot(rotationTarget.yaw());
                    mc.player.setXRot(rotationTarget.pitch());
                });
            }
        } else if (action instanceof RotateAction) {
            RotateAction ra = (RotateAction) action;
            if (ra.smooth) {
                startSmoothRotation(ra.yaw, ra.pitch, ra.getRotationStep());
                if (ra.waitForCompletion) {
                    waitForSmoothRotationCompletion();
                }
            } else {
                mc.execute(() -> action.execute(mc));
            }
        } else if (action instanceof MineAction) {
            MineAction mna = (MineAction) action;
            MacroWorldActions.runMineAction(mc, mna);
        } else if (action instanceof InstaBreakAction) {
            MacroBreak.runInstaBreakAction(mc, (InstaBreakAction) action);
        } else if (action instanceof BreakAction) {
            MacroBreak.runBreakAction(mc, (BreakAction) action);
        } else if (action instanceof PlaceAction) {
            MacroWorldActions.runPlaceAction(mc, (PlaceAction) action);
        } else if (action instanceof UseItemAction) {
            MacroActionRunner.runUseItemAction(mc, (UseItemAction) action);
        } else if (action instanceof JumpAction) {
            JumpAction ja = (JumpAction) action;
            mc.execute(() -> ja.execute(mc));
            if (ja.tap) {
                CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                MacroRunLoop.waitForCondition(tickFuture);
            } else {
                for (int t = 0; t < ja.durationTicks && isCurrentRunActive(); t++) {
                    CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                    MacroRunLoop.waitForCondition(tickFuture);
                }
            }
            mc.execute(() -> ja.release(mc));
        } else if (action instanceof SneakAction) {
            SneakAction sna = (SneakAction) action;
            if (sna.persistent && sna.sneak) {
                currentPersistentActions().removeIf(p -> p instanceof SneakAction);
                currentPersistentActions().add(sna);
            } else if (!sna.sneak) {
                currentPersistentActions().removeIf(p -> p instanceof SneakAction);
            }
            if (sna.persistent || !sna.sneak) MacroClientThread.postOnClientThread(mc, () -> applyAggregateSneakState(mc), sna.isTickAligned());
            else MacroClientThread.postOnClientThread(mc, () -> sna.execute(mc), sna.isTickAligned());
        } else if (action instanceof SprintAction) {
            SprintAction spa = (SprintAction) action;
            if (spa.persistent && spa.sprint) {
                currentPersistentActions().removeIf(p -> p instanceof SprintAction);
                currentPersistentActions().add(spa);
            } else if (!spa.sprint) {
                currentPersistentActions().removeIf(p -> p instanceof SprintAction);
            }
            if (spa.persistent || !spa.sprint) MacroClientThread.postOnClientThread(mc, () -> applyAggregateSprintState(mc), spa.isTickAligned());
            else MacroClientThread.postOnClientThread(mc, () -> spa.execute(mc), spa.isTickAligned());
        } else if (action instanceof NbtBookAction nba) {
            int totalBooks = Math.max(1, nba.bookCount);
            long delayMs = Math.max(1, nba.delayTicks) * 50L;
            boolean signedAny = false;
            for (int b = 0; b < totalBooks; b++) {
                if (!isCurrentRunActive()) break;
                final int bookIdx = b;
                java.util.concurrent.CompletableFuture<Boolean> result = new java.util.concurrent.CompletableFuture<>();
                mc.execute(() -> result.complete(nba.executeSingleBook(mc, bookIdx, totalBooks)));
                try {
                    Boolean success = result.get();
                    if (!success) break;
                    signedAny = true;
                } catch (java.util.concurrent.ExecutionException e) {
                    break;
                }
                if (b < totalBooks - 1) {
                    Thread.sleep(delayMs);
                }
            }
            if (signedAny && nba.disconnectAfter) {
                mc.execute(() -> nba.afterSigning(mc));
            }
        } else if (action instanceof ItemAction) {
            ItemAction ia = (ItemAction) action;
            if (ia.waitForItem) {
                try {
                    MacroWorldActions.waitForItemActionTargets(mc, ia);
                } catch (java.util.concurrent.CancellationException e) {  }
            }
            postGuiFuture = MacroActionRunner.createPostGuiFuture(mc, ia);
            MacroActionRunner.runMacroActionOnClientThread(mc, action, "ItemAction");
        } else if (action instanceof DropAction || action instanceof SwapSlotsAction) {
            if (action instanceof DropAction dropAction) {
                postGuiFuture = MacroActionRunner.createPostGuiFuture(mc, dropAction);
            }
            MacroActionRunner.runMacroActionOnClientThread(mc, action, action.getDisplayName());
        } else if (action instanceof SendPacketAction) {
            if (module != null && module.useInstantExecutionMode()) {
                try {
                    action.execute(mc);
                } catch (Exception e) {
                    AutismClientMessaging.sendPrefixed("\u00a7cError in SendPacket: " + e.getMessage());
                }
            } else {
                mc.execute(() -> {
                    try {
                        action.execute(mc);
                    } catch (Exception e) {
                        AutismClientMessaging.sendPrefixed("\u00a7cError in SendPacket: " + e.getMessage());
                    }
                });
            }
        } else if (action instanceof ClickAction) {
            ClickAction ca = (ClickAction) action;
            MacroActionRunner.runClickActionBurst(mc, ca);
        } else if (action instanceof PayAction) {
            MacroWorldActions.runPayAction(mc, (PayAction) action);
        } else if (action instanceof XCarryAction xsa) {
            if (xsa.mode == XCarryAction.Mode.PUT_IN
                    && (xsa.transferMode == XCarryAction.TransferMode.CLICK
                        || xsa.transferMode == XCarryAction.TransferMode.SAFE_CLICK)) {
                if (xsa.transferMode == XCarryAction.TransferMode.SAFE_CLICK) {
                    setCurrentStatus("XCarry Safe");
                    MacroXCarry.runXCarrySafeClickPutIn(mc, xsa);
                } else {
                    setCurrentStatus("XCarry Click");
                    MacroXCarry.runXCarryClickPutIn(mc, xsa);
                }
            } else {
                setCurrentStatus(switch (xsa.mode) {
                    case PUT_IN -> "XCarry";
                    case TAKE_OUT -> "XCarry Out";
                    case DROP -> "XCarry Drop";
                });
                MacroClientThread.runOnClientThread(mc, () -> {
                    try {
                        xsa.execute(mc);
                    } catch (Exception e) {
                        AutismClientMessaging.sendPrefixed("\u00a7cXCarry: " + e.getMessage());
                    }
                });
            }
        } else if (action instanceof CraftAction) {
            MacroWorldActions.runCraftAction(mc, (CraftAction) action);
        } else if (action instanceof autismclient.util.macro.StoreItemAction) {
            autismclient.util.macro.StoreItemAction sia = (autismclient.util.macro.StoreItemAction) action;
            setCurrentStatus((sia.mode == autismclient.util.macro.StoreItemAction.Mode.LOOT ? "Looting" : "Storing")
                + (sia.persistent ? " \u221e" : ""));
            if (sia.persistent) {
                while (isCurrentRunActive()) {
                    try { MacroActionRunner.runStoreItemAction(mc, sia); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    catch (Exception e) { AutismClientMessaging.sendPrefixed("Store error: " + e.getMessage()); }
                    CompletableFuture<Void> siTick = MacroConditionRegistry.waitForNextTick();
                    MacroRunLoop.waitForCondition(siTick);
                }
            } else {
                try { MacroActionRunner.runStoreItemAction(mc, sia); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (Exception e) { AutismClientMessaging.sendPrefixed("Store error: " + e.getMessage()); }
            }
        } else if (action instanceof InventoryAuditAction auditAction
                && (auditAction.mode == InventoryAuditAction.Mode.DUPE
                    || auditAction.mode == InventoryAuditAction.Mode.DUPE_SPAM)) {
            setCurrentStatus("Dupe: " + auditAction.dupeVector.name() + " " + auditAction.openMode.name());
            try {
                auditAction.executeDupe(mc);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                AutismClientMessaging.sendPrefixed("\u00a7cDupe error: "
                        + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
            }
        } else if (action instanceof ContainerClickSequenceAction sequence) {
            MacroActionRunner.runContainerClickSequenceAction(mc, sequence);
        } else if (action instanceof PacketBurstAction burst) {
            MacroActionRunner.runPacketBurstAction(mc, burst);
        } else if (action instanceof TickSyncAction tsa) {
            if (mc.level == null) return;
            java.util.List<net.minecraft.network.protocol.Packet<?>> preGenerated =
                MacroActionRunner.preGeneratePacketsForRepeat(tsa.preGenCount, mc);
            long targetTick = mc.level.getGameTime() + tsa.tickOffset;
            setCurrentStatus("Tick Sync -> " + targetTick + " (" + preGenerated.size() + " pkts)");
            while (isCurrentRunActive() && mc.level.getGameTime() < targetTick) {
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
            }
            if (isCurrentRunActive()) {
                executePreGeneratedBurst(mc, preGenerated);
                RepeatPacketContext context = REPEAT_PACKET_CONTEXT.get();
                if (context != null) REPEAT_PACKET_SKIP.set(countPreGeneratedActions(context.macro(), context.startIdx(), preGenerated.size(), context.endIdx()));
                AutismClientMessaging.sendPrefixed("Tick sync: " + preGenerated.size() + " pkts sent");
            }
        } else if (action instanceof RevisionSyncAction rsa) {
            if (mc.player == null || mc.player.containerMenu == null) return;
            java.util.List<net.minecraft.network.protocol.Packet<?>> preGenerated =
                MacroActionRunner.preGeneratePacketsForRepeat(rsa.preGenCount, mc);
            int baseRevision = mc.player.containerMenu.getStateId();
            int targetRevision = baseRevision + rsa.revisionOffset;
            setCurrentStatus("Rev Sync -> " + targetRevision + " (" + preGenerated.size() + " pkts)");
            while (isCurrentRunActive()) {
                if (mc.player == null || mc.player.containerMenu == null) break;
                if (mc.player.containerMenu.getStateId() >= targetRevision) break;
                java.util.concurrent.locks.LockSupport.parkNanos(500_000L);
            }
            if (isCurrentRunActive() && mc.player != null && mc.player.containerMenu != null) {
                executePreGeneratedBurst(mc, preGenerated);
                RepeatPacketContext context = REPEAT_PACKET_CONTEXT.get();
                if (context != null) REPEAT_PACKET_SKIP.set(countPreGeneratedActions(context.macro(), context.startIdx(), preGenerated.size(), context.endIdx()));
                AutismClientMessaging.sendPrefixed("Rev sync: " + preGenerated.size() + " pkts sent");
            }
        } else if (action instanceof ServerTickSyncAction stsa) {
            if (mc.getConnection() == null) return;
            java.util.List<net.minecraft.network.protocol.Packet<?>> preGenerated =
                MacroActionRunner.preGeneratePacketsForRepeat(stsa.preGenCount, mc);
            int sampleWaitMs = 0;
            while (isCurrentRunActive() && !ServerTickTracker.isReady() && sampleWaitMs < stsa.maxWaitMs) {
                float progress = ServerTickTracker.getWarmupProgress();
                setCurrentStatus(String.format("ServerSync warmup %.0f%% (%d samples, %dms)",
                    progress * 100, ServerTickTracker.getSampleCount(), ServerTickTracker.getTrackingTimeMs()));
                Thread.sleep(50);
                sampleWaitMs += 50;
            }
            if (!isCurrentRunActive()) return;
            long optimalTime = ServerTickTracker.getOptimalSendTime(stsa.bufferMs, stsa.ignorePing);
            long msUntil = (optimalTime - System.nanoTime()) / 1_000_000L;
            int ping = ServerTickTracker.getPingMs();
            String pingStr = stsa.ignorePing ? " (no ping)" : " ping:" + ping + "ms";
            setCurrentStatus("ServerSync in " + Math.max(0, msUntil) + "ms" + pingStr + " (" + preGenerated.size() + " pkts)");
            long startWait = System.nanoTime();
            long maxWaitNanos = stsa.maxWaitMs * 1_000_000L;
            while (isCurrentRunActive()) {
                long now = System.nanoTime();
                if ((now - startWait) >= maxWaitNanos) break;
                if (now >= optimalTime) break;
                long remaining = optimalTime - now;
                if (remaining > 2_000_000L) {
                    java.util.concurrent.locks.LockSupport.parkNanos(remaining - 2_000_000L);
                } else {
                    Thread.onSpinWait();
                }
            }
            if (isCurrentRunActive()) {
                executePreGeneratedBurst(mc, preGenerated);
                RepeatPacketContext context = REPEAT_PACKET_CONTEXT.get();
                if (context != null) REPEAT_PACKET_SKIP.set(countPreGeneratedActions(context.macro(), context.startIdx(), preGenerated.size(), context.endIdx()));
                long actualDelay = (System.nanoTime() - optimalTime) / 1_000_000L;
                AutismClientMessaging.sendPrefixed("ServerSync: " + preGenerated.size() + " pkts (+" + actualDelay + "ms)");
            }
        } else if (action instanceof BundleDupeV2Action bundleV2) {
            setCurrentStatus("Bundle Dupe V2");
            bundleV2.execute(mc);
        } else if (action instanceof RollbackAction rollback) {
            setCurrentStatus("Rollback");
            try {
                rollback.executeRollback(mc);
            } catch (InterruptedException e) {
                AutismClientMessaging.sendPrefixed("\u00a7eRollback canceled safely.");
            } catch (Exception e) {
                AutismClientMessaging.sendPrefixed("\u00a7cRollback failed: " + MacroRace.safeThrowableMessage(e));
            }
        } else if (action instanceof StopMacroAction) {
            StopMacroAction stopAction = (StopMacroAction) action;
            if (stopAction.target == StopMacroAction.StopTarget.SELF) stopCurrentRun();
            else stopAction.execute(mc);
        } else if (action instanceof UseItemPhaseAction phaseAction) {
            MacroActionRunner.runUseItemPhaseAction(mc, phaseAction);
        } else if (action instanceof autismclient.api.macro.ContextMacroAction contextAction) {
            MacroActionRunner.runContextMacroAction(contextAction);
        } else {

            if (MacroActionRunner.usesTickAlignedDispatch(action)) {

                MacroActionRunner.runMacroActionOnClientThread(mc, action, action.getDisplayName());
            } else if (module != null && module.useInstantExecutionMode()) {
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                RunState scheduledRun = CURRENT_RUN.get();
                mc.execute(() -> withRunContext(scheduledRun, () -> {
                    try {
                        action.execute(mc);
                    } catch (Exception e) {
                        AutismClientMessaging.sendPrefixed("\u00a7cError in action: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                }));
                try {
                    latch.await(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                MacroActionRunner.runMacroActionOnClientThread(mc, action, action.getDisplayName());
            }
        }

        MacroPrearm.commitConditionOutput(action, mc);

        if (postGuiFuture != null && isCurrentRunActive()) {
            try {
                MacroRunLoop.waitForCondition(postGuiFuture);
            } catch (java.util.concurrent.CancellationException e) {  }
        }

        if (isCurrentRunActive() && !MacroActionRunner.isInstantInventoryAction(action) && module != null && module.useInstantExecutionMode()) {
            int delayUs = module.getActionDelayUs();
            if (delayUs > 0) java.util.concurrent.locks.LockSupport.parkNanos(delayUs * 1000L);
            else Thread.onSpinWait();
        }
        } finally {
            MacroRaycastAim.release();
            releaseClientResources(heldResources);
        }
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
        for (RunState run : activeRunSnapshot()) {
            requestStop(run, true);
        }
        synchronized (RUN_LOCK) {
            ACTIVE_RUNS.clear();
            RUNS_BY_ID.clear();
            rebuildActiveRunListLocked();
            primaryRun = null;
            visibleRunActive = false;
        }
        isRunning.set(false);
        try {
            Minecraft mcInst = Minecraft.getInstance();
            autismclient.util.AutismContainerHold.releaseAllAndFlush(mcInst == null ? null : mcInst.getConnection());
        } catch (Throwable ignored) {  }
        persistentActions.clear();
        releaseBackgroundMoves(backgroundMoves, Minecraft.getInstance(), null);
        backgroundMoves.clear();
        reconcilePersistentInput(Minecraft.getInstance());
        MacroConditionRegistry.cancelAll();
        drainOneShotListeners();
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
        setCurrentStatus("");
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
            return activeRunCountLocked();
        }
    }

    public static boolean hasPacketObservationWork() {
        if (PackHideState.isHardLocked()) return false;
        return isRunning.get()
            || activePacketFuture != null
            || isWaitingForPacket.get()
            || !ONE_SHOT_RECV.isEmpty()
            || !ONE_SHOT_SEND.isEmpty()
            || !TIMED_ONE_SHOT_RECV.isEmpty()
            || !TIMED_ONE_SHOT_SEND.isEmpty();
    }

    public static boolean isMacroRunning(String macroName) {
        if (macroName == null) return false;
        String targetKey = macroKey(macroName);
        for (RunState run : RUNS_BY_ID.values()) {
            if (run.running.get() && macroKey(macroName(run.macro)).equals(targetKey)) return true;
        }
        return false;
    }

    public static boolean isMacroRunning(AutismMacro macro) {
        if (macro == null) return false;
        for (RunState run : RUNS_BY_ID.values()) {
            if (matchesMacro(run, macro, macro.name)) return true;
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
        String targetKey = macroKey(macroName);
        boolean stopped = false;
        for (RunState run : activeRunSnapshot()) {
            if (run.running.get() && macroKey(macroName(run.macro)).equals(targetKey)) {
                requestStop(run, true);
                stopped = true;
            }
        }
        if (stopped) {
            synchronized (RUN_LOCK) {
                refreshPrimaryRunLocked();
            }
            AutismClientMessaging.sendPrefixed("\u00a7cMacro stopped: " + macroName);
        }
    }

    public static void stopMacro(AutismMacro macro) {
        if (macro == null) return;
        boolean stopped = false;
        String displayName = macroName(macro);
        for (RunState run : activeRunSnapshot()) {
            if (matchesMacro(run, macro, displayName)) {
                requestStop(run, true);
                stopped = true;
            }
        }
        if (stopped) {
            synchronized (RUN_LOCK) {
                refreshPrimaryRunLocked();
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

    private static final long ONE_SHOT_EXPIRE_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(10);

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

    static final class TimedOneShotPacketListener {
        public final java.util.concurrent.CompletableFuture<MacroEvent> future = new java.util.concurrent.CompletableFuture<>();
        private final java.util.List<TimedOneShotPacketListener> registry;
        private final TimedPacketMatch match;
        private final long expireAtNanos;
        TimedOneShotPacketListener(java.util.List<TimedOneShotPacketListener> registry, TimedPacketMatch match) {
            this.registry = registry;
            this.match = match;
            this.expireAtNanos = System.nanoTime() + ONE_SHOT_EXPIRE_NANOS;
            registry.add(this);
        }

        boolean offer(MacroEvent event) {
            if (future.isDone()) return true;
            if (event.nanoTime() - expireAtNanos >= 0L) { future.cancel(true); return true; }
            if (match.test(event.packet(), event.direction(), event.nanoTime())) {
                future.complete(event);
                return true;
            }
            return false;
        }
        public void cancel() {
            registry.remove(this);
            if (!future.isDone()) future.cancel(true);
        }
    }

    interface TimedPacketMatch {
        boolean test(Packet<?> packet, String direction, long nanoTime);
    }

    private static final java.util.List<OneShotPacketListener> ONE_SHOT_RECV =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.List<OneShotPacketListener> ONE_SHOT_SEND =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.List<TimedOneShotPacketListener> TIMED_ONE_SHOT_RECV =
        new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.List<TimedOneShotPacketListener> TIMED_ONE_SHOT_SEND =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    public static OneShotPacketListener awaitReceive(java.util.function.Predicate<Packet<?>> match) {
        return new OneShotPacketListener(ONE_SHOT_RECV, match);
    }

    public static OneShotPacketListener awaitSend(java.util.function.Predicate<Packet<?>> match) {
        return new OneShotPacketListener(ONE_SHOT_SEND, match);
    }

    static TimedOneShotPacketListener awaitTimedReceive(TimedPacketMatch match) {
        return new TimedOneShotPacketListener(TIMED_ONE_SHOT_RECV, match);
    }

    static TimedOneShotPacketListener awaitTimedSend(java.util.function.Predicate<Packet<?>> match) {
        return awaitTimedSend((packet, direction, nanoTime) -> match.test(packet));
    }

    static TimedOneShotPacketListener awaitTimedSend(TimedPacketMatch match) {
        return new TimedOneShotPacketListener(TIMED_ONE_SHOT_SEND, match);
    }

    private static void fireOneShotObservers(java.util.List<OneShotPacketListener> list, Packet<?> packet) {
        if (list.isEmpty()) return;

        long now = System.nanoTime();
        java.util.ArrayList<OneShotPacketListener> matched = null;
        for (OneShotPacketListener listener : list) {
            boolean drop;
            try { drop = listener.offer(packet, now); }
            catch (Throwable ignored) { drop = true; }
            if (drop) {
                if (matched == null) matched = new java.util.ArrayList<>(4);
                matched.add(listener);
            }
        }
        if (matched != null) list.removeAll(matched);
    }

    private static void fireTimedObservers(java.util.List<TimedOneShotPacketListener> list, MacroEvent event) {
        if (list.isEmpty()) return;
        java.util.ArrayList<TimedOneShotPacketListener> matched = null;
        for (TimedOneShotPacketListener listener : list) {
            boolean drop;
            try { drop = listener.offer(event); }
            catch (Throwable ignored) { drop = true; }
            if (drop) {
                if (matched == null) matched = new java.util.ArrayList<>(4);
                matched.add(listener);
            }
        }
        if (matched != null) list.removeAll(matched);
    }

    private static void drainOneShotListeners() {
        for (OneShotPacketListener listener : ONE_SHOT_RECV) listener.cancel();
        for (OneShotPacketListener listener : ONE_SHOT_SEND) listener.cancel();
        for (TimedOneShotPacketListener listener : TIMED_ONE_SHOT_RECV) listener.cancel();
        for (TimedOneShotPacketListener listener : TIMED_ONE_SHOT_SEND) listener.cancel();
    }

    public static String oneShotCensus() {
        return "runs=" + getActiveRunCount()
            + " recv=" + ONE_SHOT_RECV.size()
            + " send=" + ONE_SHOT_SEND.size()
            + " timedRecv=" + TIMED_ONE_SHOT_RECV.size()
            + " timedSend=" + TIMED_ONE_SHOT_SEND.size();
    }

    public static void onPacketSent(Packet<?> packet) {
        if (PackHideState.isHardLocked()) return;
        if (!TIMED_ONE_SHOT_SEND.isEmpty()) {
            fireTimedObservers(TIMED_ONE_SHOT_SEND, new MacroEvent(packet, "C2S", System.nanoTime(), null));
        }
        fireOneShotObservers(ONE_SHOT_SEND, packet);
        onPacketObserved(packet, "C2S");
    }

    public static void onPacketReceived(Packet<?> packet) {
        if (PackHideState.isHardLocked()) return;
        if (!TIMED_ONE_SHOT_RECV.isEmpty()) {
            fireTimedObservers(TIMED_ONE_SHOT_RECV, new MacroEvent(packet, "S2C", System.nanoTime(), null));
        }
        fireOneShotObservers(ONE_SHOT_RECV, packet);

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundRespawnPacket
            || packet instanceof net.minecraft.network.protocol.game.ClientboundLoginPacket) {
            MacroConditionRegistry.onRespawnPacket();
        }
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket) {
            MacroConditionRegistry.onTeleportPacket();
        }
        onPacketObserved(packet, "S2C");
    }

    public static void onPacketObserved(Packet<?> packet, String direction) {
        if (PackHideState.isHardLocked()) return;
        MacroChatObserve.syncRecentChatServerContext();

        java.util.List<RunState> runs = activeRunSnapshot();
        boolean s2c = "S2C".equals(direction);
        boolean chatWork = false;
        boolean mineWork = false;
        boolean packetWork = false;
        for (int i = 0; i < runs.size(); i++) {
            RunState run = runs.get(i);
            if (!run.running.get()) continue;
            if (run.waitingForChat.get()) chatWork = true;
            if (run.activeMineTracker != null) mineWork = true;
            if (run.waitingForPacket.get()) packetWork = true;
        }

        if (s2c && (chatWork || isWaitingForChat.get())) {
            ChatCapture capture = MacroChatObserve.extractIncomingChat(packet);
            if (capture != null && capture.message() != null && !capture.message().isBlank()) {

                for (RunState run : runs) {
                    var matched = run.running.get() && run.waitingForChat.get()
                        ? MacroChatObserve.captureChatAction(run.waitingChatAction, capture, run.variables)
                        : java.util.Optional.<java.util.Map<String, MacroValue>>empty();
                    if (matched.isPresent()) {
                        run.stagedCaptures.put(run.waitingChatAction, matched.get());
                        CompletableFuture<Void> f = run.chatFuture;
                        if (f != null) f.complete(null);
                    }
                }
                if (isCurrentRunActive() && isWaitingForChat.get() && MacroChatObserve.matchesWaitingChat(capture)) {
                    lastMatchedChat.set(capture.displayText());
                    CompletableFuture<Void> f = activeChatFuture;
                    if (f != null) f.complete(null);
                }
            }
        }

        if (s2c && mineWork) {
            Minecraft mc = Minecraft.getInstance();
            for (RunState run : runs) {
                MacroWorldActions.MineProgressTracker tracker = run.running.get() ? run.activeMineTracker : null;
                if (tracker != null) tracker.observePacket(mc, packet);
            }
        }

        if (!isCurrentRunActive()) return;

        if (packetWork) {
            for (RunState run : runs) {
                if (!run.running.get() || !run.waitingForPacket.get()) continue;
                CompletableFuture<Void> f = null;
                synchronized (run.packetWaitLock) {
                    if (run.waitingForPacket.get() && MacroPacketWait.matchesPacketTarget(run.waitingPacketName.get(), packet, direction)) {
                        run.lastMatchedPacket = packet;
                        run.lastMatchedPacketDirection = direction;
                        f = run.packetFuture;
                    }
                }
                if (f != null) f.complete(null);
            }
        }

        if (isWaitingForPacket.get()) {
            CompletableFuture<Void> f = null;
            synchronized (packetWaitLock) {
                if (!isWaitingForPacket.get()) return;

                String target = waitingPacketName.get();
                if (MacroPacketWait.matchesPacketTarget(target, packet, direction)) {
                    lastReceivedPacket.set(direction + " " + AutismPacketNamer.getFriendlyName(packet, direction));
                    f = activePacketFuture;
                }
            }
            if (f != null) f.complete(null);
        }
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
        if (!isRotating.get()) return;
        RunState owner = ROTATION_OWNER.get();
        if (owner != null && !owner.running.get()) {
            cancelRotation(owner);
            return;
        }
        if (owner == null && !isRunning.get()) {
            synchronized (ROTATION_STATE_LOCK) {
                if (ROTATION_OWNER.get() == null) isRotating.set(false);
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        double deltaYaw = net.minecraft.util.Mth.wrapDegrees(targetYaw - currentYaw);
        double deltaPitch = targetPitch - currentPitch;
        double diagonalDistance = Math.hypot(deltaYaw, deltaPitch);
        if (diagonalDistance <= 0.35) {
            mc.player.setYRot(targetYaw);
            mc.player.setXRot(targetPitch);
            rotationAlignedFrames++;
            if (rotationAlignedFrames >= 2) {
                synchronized (ROTATION_STATE_LOCK) {
                    if (ROTATION_OWNER.get() == owner) {
                        isRotating.set(false);
                        ROTATION_OWNER.set(null);
                    }
                }
            }
            return;
        }
        rotationAlignedFrames = 0;

        double frameStep = Math.min(diagonalDistance, rotationSpeed * Math.max(tickDelta, 1.0f));
        double stepYaw = (deltaYaw / diagonalDistance) * frameStep;
        double stepPitch = (deltaPitch / diagonalDistance) * frameStep;

        mc.player.setYRot(currentYaw + (float) stepYaw);
        mc.player.setXRot(currentPitch + (float) stepPitch);
    }

    public static boolean hasRenderWork() {
        return isRotating.get();
    }

    static void startSmoothRotation(float yaw, float pitch, double speed) {
        RunState run = CURRENT_RUN.get();
        while (run != null && run.running.get()) {
            synchronized (ROTATION_STATE_LOCK) {
                RunState owner = ROTATION_OWNER.get();
                if (owner == null || owner == run || !owner.running.get()) {
                    ROTATION_OWNER.set(run);
                    targetYaw = yaw;
                    targetPitch = pitch;
                    rotationSpeed = speed;
                    rotationAlignedFrames = 0;
                    isRotating.set(true);
                    return;
                }
            }
            java.util.concurrent.locks.LockSupport.parkNanos(MACRO_WAIT_POLL_NANOS);
        }
        if (run == null) {
            synchronized (ROTATION_STATE_LOCK) {
                if (ROTATION_OWNER.get() != null) return;
                targetYaw = yaw;
                targetPitch = pitch;
                rotationSpeed = speed;
                rotationAlignedFrames = 0;
                isRotating.set(true);
            }
        }
    }

    static void waitForSmoothRotationCompletion() {
        RunState run = CURRENT_RUN.get();
        while (isCurrentRunActive() && isRotating.get()
            && (run == null || ROTATION_OWNER.get() == run)) {
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
        }
    }

    static void cancelRotation(RunState run) {
        if (run == null) return;
        synchronized (ROTATION_STATE_LOCK) {
            if (ROTATION_OWNER.get() != run) return;
            isRotating.set(false);
            rotationAlignedFrames = 0;
            ROTATION_OWNER.set(null);
        }
    }

    static LookAtBlockAction.RotationTarget resolveLookAtTargetOnClient(Minecraft mc, LookAtBlockAction action) {
        if (mc == null || action == null) return null;
        java.util.concurrent.atomic.AtomicReference<LookAtBlockAction.RotationTarget> targetRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        mc.execute(() -> {
            try {
                targetRef.set(action.resolveRotationTarget(mc));
            } finally {
                latch.countDown();
            }
        });
        while (isCurrentRunActive()) {
            try {
                if (latch.await(10, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return targetRef.get();
    }

    public static void stopAll() {
        stop();
    }

    static void executeBurstPackets(Minecraft mc, java.util.List<SendPacketAction> batch) {
        if (mc.getConnection() == null) {
            AutismClientMessaging.sendPrefixed("\u00a7cNo network connection for burst!");
            return;
        }

        java.util.List<autismclient.util.AutismSharedState.QueuedPacket> allQueuedPackets = new java.util.ArrayList<>();
        for (SendPacketAction action : batch) {
            allQueuedPackets.addAll(action.getPackets());
        }

        if (allQueuedPackets.isEmpty()) {
            return;
        }

        java.util.List<Packet<?>> regeneratedPackets = new java.util.ArrayList<>(allQueuedPackets.size());
        for (autismclient.util.AutismSharedState.QueuedPacket qp : allQueuedPackets) {
            if (qp.packet == null) continue;
            regeneratedPackets.add(qp.packet);
        }

        if (regeneratedPackets.isEmpty()) {
            AutismClientMessaging.sendPrefixed("\u00a7cBurst: All packets failed to regenerate!");
            return;
        }

        net.minecraft.client.multiplayer.ClientPacketListener handler = mc.getConnection();

        for (Packet<?> packet : regeneratedPackets) {
            Packet<?> toSend = packet;

            if (packet instanceof ServerboundContainerClickPacket) {
                toSend = PacketRegenerator.regenerate(packet);
                if (toSend == null) {
                    continue;
                }
            }

            handler.send(toSend);
        }

        if (regeneratedPackets.size() > 1) {
            AutismClientMessaging.sendPrefixed("\u00a7a Burst sent " + regeneratedPackets.size() + " packets");
        }
    }

    static java.util.List<Packet<?>> preGeneratePackets(AutismMacro macro, int startIdx, int count) {
        java.util.List<Packet<?>> result = new java.util.ArrayList<>();
        int maxIdx = count < 0 ? macro.actions.size() : Math.min(startIdx + count, macro.actions.size());
        return preGeneratePackets(macro, startIdx, count, maxIdx);
    }

    static java.util.List<Packet<?>> preGeneratePackets(AutismMacro macro, int startIdx, int count, int endIdx) {
        java.util.List<Packet<?>> result = new java.util.ArrayList<>();
        int maxIdx = Math.min(endIdx, count < 0 ? macro.actions.size() : Math.min(startIdx + count, macro.actions.size()));

        Minecraft mc = Minecraft.getInstance();

        for (int j = startIdx; j < maxIdx; j++) {
            MacroAction act = macro.actions.get(j);
            if (!(act instanceof SendPacketAction)) break;

            SendPacketAction spa = (SendPacketAction) act;
            for (autismclient.util.AutismSharedState.QueuedPacket qp : spa.getPackets()) {
                if (qp.packet == null) continue;

                result.add(qp.packet);
            }
        }

        return result;
    }

    static void executePreGeneratedBurst(Minecraft mc, java.util.List<Packet<?>> packets) {
        if (packets.isEmpty() || mc.getConnection() == null) return;

        net.minecraft.client.multiplayer.ClientPacketListener handler = mc.getConnection();

        for (Packet<?> p : packets) {
            Packet<?> toSend = p;

            if (p instanceof ServerboundContainerClickPacket) {
                toSend = PacketRegenerator.regenerate(p);
                if (toSend == null) {
                    continue;
                }
            }

            handler.send(toSend);
        }

        AutismModule module = AutismModule.get();
        if (module.shouldForceChannelFlush()) {
            try {
                net.minecraft.network.Connection connection = handler.getConnection();
                if (connection != null) {

                    connection.flushChannel();
                }
            } catch (Exception e) {  }
        }
    }

    static int countPreGeneratedActions(AutismMacro macro, int startIdx, int packetCount) {
        return countPreGeneratedActions(macro, startIdx, packetCount, macro.actions.size());
    }

    static int countPreGeneratedActions(AutismMacro macro, int startIdx, int packetCount, int endIdx) {
        int actionsToSkip = 0;
        int packetsRemaining = packetCount;

        for (int j = startIdx; j < endIdx && packetsRemaining > 0; j++) {
            MacroAction act = macro.actions.get(j);
            if (!(act instanceof SendPacketAction)) break;

            SendPacketAction spa = (SendPacketAction) act;
            int actionPackets = spa.getPackets().size();
            packetsRemaining -= actionPackets;
            actionsToSkip++;
        }

        return actionsToSkip;
    }
}
