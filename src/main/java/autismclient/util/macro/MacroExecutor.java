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
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    public static final int MAX_CONCURRENT_MACROS = 4;
    static final Object RUN_LOCK = new Object();
    private static final EnumMap<SharedClientResource, ReentrantLock> SHARED_RESOURCE_LOCKS = new EnumMap<>(SharedClientResource.class);
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
    private static volatile int totalSteps = 0;

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
    private static final AtomicReference<RunState> BARITONE_OWNER = new AtomicReference<>();

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
    private static final ThreadLocal<Integer> REPEAT_PACKET_SKIP = ThreadLocal.withInitial(() -> 0);

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
        volatile MineProgressTracker activeMineTracker = null;
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
    private record InlineNextActions(java.util.List<MacroAction> actions, int lastIndex) {
        static InlineNextActions empty() {
            return new InlineNextActions(java.util.List.of(), -1);
        }
    }
    private record BreakStartPlan(int result, boolean instant, boolean interactFirst) {}
    private record BreakContinuePlan(int result, boolean breaksThisTick, boolean breaksNextTick) {}
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

    private static int performFlowJump(RunState run, AutismMacro macro, int currentI, FlowAction flow, Minecraft mc) {
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

                try { waitForCondition(MacroConditionRegistry.waitForNextTick()); } catch (Exception ignored) {  }
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

    private static CopyOnWriteArrayList<MacroAction> currentPersistentActions() {
        RunState run = CURRENT_RUN.get();
        return run != null ? run.persistentActions : persistentActions;
    }

    private static ConcurrentHashMap<MoveAction, int[]> currentBackgroundMoves() {
        RunState run = CURRENT_RUN.get();
        return run != null ? run.backgroundMoves : backgroundMoves;
    }

    static void setCurrentStatus(String status) {
        RunState run = CURRENT_RUN.get();
        if (run != null) run.status = status == null ? "" : status;
        currentStatus = status == null ? "" : status;
    }

    private static void setCurrentStep(int step, int total) {
        RunState run = CURRENT_RUN.get();
        if (run != null) {
            run.currentStepIndex = step;
            run.totalSteps = total;
        }
        currentStepIndex = step;
        totalSteps = total;
    }

    private static void markStepCompleted(int stepOneBased) {
        RunState run = CURRENT_RUN.get();
        if (run != null) {
            run.lastCompletedStep = Math.max(run.lastCompletedStep, stepOneBased);
        }
    }

    private static int stepOverrideEndIndex(AutismMacro macro, int headerIndex, int coveredRows) {
        int last = macro == null ? headerIndex : macro.actions.size() - 1;
        long end = (long) headerIndex + Math.max(0, coveredRows);
        return (int) Math.max(headerIndex, Math.min(last, end));
    }

    private static void armFpsStepOverride(RunState run, int headerIndex, int endIndexInclusive, int fpsLimit) {
        if (run == null) return;
        run.fpsStepHeaderIndex = headerIndex;
        run.fpsStepEndIndex = endIndexInclusive;
        FpsLimitController.applyUntilCleared(run.id, fpsLimit);
    }

    private static void armPingStepOverride(RunState run, int headerIndex, int endIndexInclusive,
                                            int delayMs, boolean realIncoming, boolean realOutgoing) {
        if (run == null) return;
        run.pingStepHeaderIndex = headerIndex;
        run.pingStepEndIndex = endIndexInclusive;
        PingSpoofController.applyUntilCleared(run.id, delayMs, realIncoming, realOutgoing);
    }

    private static void clearFpsStepOverride(RunState run) {
        if (run == null || run.fpsStepHeaderIndex < 0) return;
        run.fpsStepHeaderIndex = -1;
        run.fpsStepEndIndex = -1;
        FpsLimitController.clear(run.id);
    }

    private static void clearPingStepOverride(RunState run) {
        if (run == null || run.pingStepHeaderIndex < 0) return;
        run.pingStepHeaderIndex = -1;
        run.pingStepEndIndex = -1;
        PingSpoofController.clearMacro(run.id);
    }

    private static void awaitBackgroundTimedOverrides(RunState run) throws InterruptedException {
        if (run == null) return;

        long deadline = Math.max(run.fpsBackgroundDeadlineNanos, run.pingBackgroundDeadlineNanos);
        long remaining;
        while (isCurrentRunActive() && (remaining = deadline - System.nanoTime()) > 0L) {
            Thread.sleep(Math.min(25L, remaining / 1_000_000L + 1L));
        }
    }

    private static void updateStepBoundOverrides(RunState run, int index) {
        if (run == null) return;
        if (run.fpsStepHeaderIndex >= 0 && (index > run.fpsStepEndIndex || index <= run.fpsStepHeaderIndex)) {
            clearFpsStepOverride(run);
        }
        if (run.pingStepHeaderIndex >= 0 && (index > run.pingStepEndIndex || index <= run.pingStepHeaderIndex)) {
            clearPingStepOverride(run);
        }
    }

    private static void stopCurrentRun() {
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

    private static InlineNextActions collectInlineNextActions(java.util.List<MacroAction> actions, int currentIndex) {
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

    private static void finishCurrentRun() {
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

    private static void broadcastStepProgress(RunState run, int step, int total, String macroName) {
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
        requestOwnedBaritoneStop(run, mc);
        Thread thread = run.macroThread;
        if (interruptThread && thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
    }

    private static void releaseBackgroundMoves(ConcurrentHashMap<MoveAction, int[]> moves, Minecraft mc, RunState owner) {
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

    private static void releaseMoveIfUnused(RunState owner, MoveAction move, Minecraft mc) {
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

    private static void applyAggregateSneakState(Minecraft mc) {
        if (mc == null || mc.options == null || mc.options.keyShift == null) return;
        boolean held = hasPersistentInput(SneakAction.class);
        mc.options.keyShift.setDown(held);
    }

    private static void applyAggregateSprintState(Minecraft mc) {
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

        Thread thread = new Thread(() -> run(runState), "Autism-Macro-" + runState.id);
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

    private static void run(RunState runState) {
        CURRENT_RUN.set(runState);
        try {
            runInternal(runState);
        } catch (Throwable fatal) {

            if (!runState.options.silentLifecycle()) {
                try { AutismClientMessaging.sendPrefixed("\u00a7cMacro crashed: " + fatal.getMessage()); } catch (Throwable ignored) {  }
            }
        } finally {
            finishCurrentRun();
            CURRENT_RUN.remove();
        }
    }

    private static void runInternal(RunState runState) {
        CURRENT_RUN.set(runState);
        MacroVariables.clear();
        AutismMacro macro = runState.macro;
        Minecraft mc = Minecraft.getInstance();
        int loopCount = 0;
        int maxLoops = macro.loop ? (macro.loopCount == -1 ? Integer.MAX_VALUE : macro.loopCount) : 1;

        currentPersistentActions().clear();
        currentBackgroundMoves().clear();
        Thread persistentThread = new Thread(() -> {
            CURRENT_RUN.set(runState);
            try {
                while (isCurrentRunActive()) {
                    if (!currentPersistentActions().isEmpty() || !currentBackgroundMoves().isEmpty()) {
                        RunState scheduledRun = CURRENT_RUN.get();
                        mc.execute(() -> withRunContext(scheduledRun, () -> {
                            ReentrantLock inputLock = SHARED_RESOURCE_LOCKS.get(SharedClientResource.INPUT);
                            inputLock.lock();
                            try {

                            for (MacroAction pa : currentPersistentActions()) {
                                try { pa.execute(mc); } catch (Exception ignored) {  }
                            }

                            java.util.Iterator<java.util.Map.Entry<MoveAction, int[]>> it = currentBackgroundMoves().entrySet().iterator();
                            while (it.hasNext()) {
                                java.util.Map.Entry<MoveAction, int[]> entry = it.next();
                                MoveAction bma = entry.getKey();
                                int[] ticks = entry.getValue();
                                if (ticks[0] > 0) {
                                    bma.execute(mc);
                                    ticks[0]--;
                                } else {
                                    releaseMoveIfUnused(scheduledRun, bma, mc);
                                    currentBackgroundMoves().remove(bma);
                                }
                            }
                            } finally {
                                inputLock.unlock();
                            }
                        }));
                    }

                    try {
                        CompletableFuture<Void> tf = MacroConditionRegistry.waitForNextTick();
                        tf.get(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        if (!isCurrentRunActive()) break;
                    }
                }
            } finally {
                releaseBackgroundMoves(currentBackgroundMoves(), mc, runState);
                currentBackgroundMoves().clear();
                CURRENT_RUN.remove();
            }
        }, "Autism-Macro-Persistent-" + runState.id);
        persistentThread.setDaemon(true);
        persistentThread.start();

        try {
            while (isCurrentRunActive() && loopCount < maxLoops) {
                if (macro.actions.isEmpty()) {
                    if (!runState.options.silentLifecycle()) AutismClientMessaging.sendPrefixed("\u00a7cMacro stopped: no actions left.");
                    stopCurrentRun();
                    break;
                }
                setCurrentStatus("Loop " + (loopCount + 1) + (maxLoops == Integer.MAX_VALUE ? "" : "/" + maxLoops));

                AutismModule module = AutismModule.get();
                boolean burstMode = module.usePacketBurstMode();

                totalSteps = macro.actions.size();

                runState.branchElseStart = -1;
                runState.branchElseCount = 0;
                runState.jumpBudget = 0;

                runState.lastCompletedStep = 0;
                for (int i = 0; i < macro.actions.size(); i++) {
                    if (!isCurrentRunActive()) break;

                    setCurrentStep(i, macro.actions.size());

                    updateStepBoundOverrides(runState, i);
                    if (runState.branchElseStart == i && runState.branchElseCount > 0) {
                        i += runState.branchElseCount - 1;
                        runState.branchElseStart = -1;
                        runState.branchElseCount = 0;
                        continue;
                    }
                    MacroAction action = macro.actions.get(i);

                    if (!action.isEnabled()) {
                        if (action instanceof RaceAction disabledRace) {
                            i += disabledRace.normalizedBodyCount(macro.actions, i);
                        } else if (action instanceof ReportAction disabledReport) {
                            i += disabledReport.normalizedConditionCount(macro.actions, i);
                        } else if (action instanceof MultiAction disabledMulti) {
                            i += disabledMulti.normalizedStepCount(macro.actions, i);
                        }
                        continue;
                    }

                    if (!MacroDynamicBindings.apply(action, mc)) continue;
                    if (!MacroActionRunner.dynamicTextInputsAvailable(action, mc)) continue;
                    if (!MacroPrearm.hasPrearmedWait(action)) MacroPrearm.clearOwnCaptureOutput(action);

                    if (action instanceof BranchAction branch) {
                        boolean branchMatches = branch.matches(mc);
                        if (branchMatches) {
                            runState.branchElseStart = i + 1 + Math.max(0, branch.thenSteps);
                            runState.branchElseCount = Math.max(0, branch.elseSteps);
                        } else {
                            i += Math.max(0, branch.thenSteps);
                        }
                        continue;
                    }

                    if (action instanceof FinallyAction cleanup) {
                        int bodyCount = Math.max(0, cleanup.bodyCount);
                        runState.finallyActions.clear();
                        for (int c = 1; c <= bodyCount && i + c < macro.actions.size(); c++) {
                            MacroAction body = macro.actions.get(i + c);
                            if (body != null && body.isEnabled()) runState.finallyActions.add(body);
                        }
                        i += bodyCount;
                        continue;
                    }

                    if (action instanceof IfAction ifA) {
                        runState.branchElseStart = -1;
                        runState.branchElseCount = 0;
                        if (ifA.evaluate(mc)) {
                            runState.branchElseStart = i + 1 + Math.max(0, ifA.thenSteps);
                            runState.branchElseCount = Math.max(0, ifA.elseSteps);
                        } else {
                            i += Math.max(0, ifA.thenSteps);
                        }
                        continue;
                    }

                    if (action instanceof LabelAction) {
                        continue;
                    }

                    if (action instanceof FlowAction flow) {
                        if (flow.shouldTake(mc)) {
                            int newI = performFlowJump(runState, macro, i, flow, mc);
                            if (newI == Integer.MIN_VALUE) {
                                broadcastStepProgress(runState, i + 1, macro.actions.size(), macro.name);
                                stopCurrentRun();
                                break;
                            }
                            i = newI;
                        }
                        continue;
                    }

                    MacroPrearm.prearmNextActionWait(mc, macro.actions, i);

                    EnumSet<SharedClientResource> heldResources = acquireClientResources(action);
                    try {

                        if (!MacroActionRunner.waitForGuiBeforeAction(mc, action)) continue;
                        MacroActionRunner.beginRaycastAim(mc, action);
                        java.util.concurrent.CompletableFuture<Void> postGuiFuture =
                                (action instanceof ItemAction || action instanceof DropAction || MacroActionRunner.isTargetWaitInteractionAction(action)) ? null : MacroActionRunner.createPostGuiFuture(mc, action);

                        if (burstMode && action instanceof SendPacketAction) {

                        java.util.List<SendPacketAction> packetBatch = new java.util.ArrayList<>();
                        int batchEnd = i;

                        while (batchEnd < macro.actions.size() &&
                               macro.actions.get(batchEnd) instanceof SendPacketAction) {
                            packetBatch.add((SendPacketAction) macro.actions.get(batchEnd));
                            batchEnd++;
                        }

                        executeBurstPackets(mc, packetBatch);

                        i = batchEnd - 1;
                        continue;
                    }

                    if (action instanceof DelayAction) {
                        DelayAction da = (DelayAction) action;
                        if (da.useTicks) {

                            for (int t = 0; t < da.delayTicks; t++) {
                                if (!isCurrentRunActive()) break;

                                CompletableFuture<Void> future = MacroConditionRegistry.waitForNextTick();
                                waitForCondition(future);
                            }
                        } else {

                            Thread.sleep(da.delayMs);
                        }
                    }
                    else if (action instanceof CustomMenuAction customMenu) {
                        setCurrentStatus(customMenu.getDisplayName());
                        customMenu.execute(mc);
                    }
                    else if (action instanceof FpsAction fps) {
                        setCurrentStatus(fps.getDisplayName());
                        if (fps.endMode() == FpsAction.EndMode.TIMEOUT) {

                            clearFpsStepOverride(runState);
                            long fpsDurationNanos = fps.durationMillis() * 1_000_000L;
                            long fpsDeadline = System.nanoTime() + fpsDurationNanos;
                            FpsLimitController.apply(runState.id, Math.max(0, fps.maxFps), fpsDurationNanos);
                            if (fps.continueNextActions) {

                                runState.fpsBackgroundDeadlineNanos = fpsDeadline;
                            } else {

                                long fpsRemaining;
                                while (isCurrentRunActive() && (fpsRemaining = fpsDeadline - System.nanoTime()) > 0L) {
                                    Thread.sleep(Math.min(25L, fpsRemaining / 1_000_000L + 1L));
                                }
                                FpsLimitController.clear(runState.id);
                                runState.fpsBackgroundDeadlineNanos = 0L;
                            }
                        } else {

                            int covered = fps.endMode() == FpsAction.EndMode.END
                                    ? macro.actions.size() - 1 - i
                                    : fps.normalizedStepCount();
                            int endIndex = stepOverrideEndIndex(macro, i, covered);

                            armFpsStepOverride(runState, i, endIndex, Math.max(1, fps.maxFps));
                            runState.fpsBackgroundDeadlineNanos = 0L;
                        }

                    }
                    else if (action instanceof SPingAction sping) {
                        setCurrentStatus(sping.getDisplayName());
                        if (sping.endMode() == SPingAction.EndMode.TIMEOUT) {
                            clearPingStepOverride(runState);
                            long spingDurationNanos = sping.durationMillis() * 1_000_000L;
                            long spingDeadline = System.nanoTime() + spingDurationNanos;
                            PingSpoofController.apply(runState.id, Math.max(0, sping.pingDelayMs),
                                sping.realIncoming, sping.realOutgoing, spingDurationNanos);
                            if (sping.continueNextActions) {

                                runState.pingBackgroundDeadlineNanos = spingDeadline;
                            } else {

                                long spingRemaining;
                                while (isCurrentRunActive() && (spingRemaining = spingDeadline - System.nanoTime()) > 0L) {
                                    Thread.sleep(Math.min(25L, spingRemaining / 1_000_000L + 1L));
                                }
                                PingSpoofController.clearMacro(runState.id);
                                runState.pingBackgroundDeadlineNanos = 0L;
                            }
                        } else {
                            int covered = sping.endMode() == SPingAction.EndMode.END
                                    ? macro.actions.size() - 1 - i
                                    : sping.normalizedStepCount();
                            int endIndex = stepOverrideEndIndex(macro, i, covered);
                            armPingStepOverride(runState, i, endIndex,
                                    Math.max(0, sping.pingDelayMs), sping.realIncoming, sping.realOutgoing);
                            runState.pingBackgroundDeadlineNanos = 0L;
                        }

                    }
                    else if (action instanceof WaitForPacketAction packetAction) {
                        packetAction.matchedPacket = null;
                        packetAction.matchedDirection = "";
                        RunState packetRun = CURRENT_RUN.get();
                        if (packetRun != null) {
                            packetRun.lastMatchedPacket = null;
                            packetRun.lastMatchedPacketDirection = "";
                        }
                        java.util.List<String> targets = packetAction.effectiveList();
                        PrearmedWait prearmed = MacroPrearm.consumePrearmedWait(action);
                        int startTarget = 0;
                        if (prearmed != null) {
                            try {
                                MacroRace.waitForFutureDone(prearmed.future, 0);
                                startTarget = prearmed.completedPacketTargets;
                            } finally {
                                prearmed.cleanup();
                            }
                        }
                        if (targets.isEmpty()) {
                            if (prearmed == null) MacroPacketWait.awaitPacket("");
                        } else {
                            for (int targetIndex = Math.min(startTarget, targets.size()); targetIndex < targets.size(); targetIndex++) {
                                if (!isCurrentRunActive()) break;
                                MacroPacketWait.awaitPacket(targets.get(targetIndex));
                            }
                        }
                        MacroPrearm.copyLastMatchedPacket(packetAction);
                    }
                    else if (action instanceof WaitPacketMatchAction wpma) {
                        setCurrentStatus(wpma.getDisplayName());
                        if (!MacroPrearm.waitForPrearmed(wpma, wpma.timeoutMs)) MacroPrearm.waitForPacketMatch(wpma);
                    }
                    else if (action instanceof WaitInventoryPredicateAction wipa) {
                        setCurrentStatus(wipa.getDisplayName());
                        if (!MacroPrearm.waitForPrearmed(wipa, wipa.timeoutMs)) waitForPredicate(() -> wipa.matches(mc), wipa.timeoutMs);
                    }
                    else if (action instanceof WaitDurabilityAction wda) {
                        setCurrentStatus(wda.getDisplayName());
                        if (wda.useNext) MacroDurability.waitForDurabilityUseNext(mc, wda);
                        else if (!MacroPrearm.waitForPrearmed(wda, wda.timeoutMs)) waitForPredicate(() -> wda.matches(mc), wda.timeoutMs);
                    }
                    else if (action instanceof WaitFreeSlotsAction wfsa) {
                        setCurrentStatus(wfsa.getDisplayName());
                        if (!MacroPrearm.waitForPrearmed(wfsa, wfsa.timeoutMs)) waitForPredicate(() -> wfsa.matches(mc), wfsa.timeoutMs);
                    }
                    else if (action instanceof WaitEntityTargetAction weta) {
                        setCurrentStatus(weta.getDisplayName());
                        if (!MacroPrearm.waitForPrearmed(weta, weta.timeoutMs)) waitForPredicate(() -> weta.matches(mc), weta.timeoutMs);
                    }
                    else if (action instanceof WaitGuiTypeAction wgta) {
                        setCurrentStatus(wgta.getDisplayName());
                        if (!MacroPrearm.hasPrearmedWait(wgta)) MacroPrearm.clearCaptureOutputs(wgta);
                        if (MacroPrearm.waitForPrearmed(wgta, wgta.timeoutMs)) {
                        } else if (wgta.waitMode == WaitGuiTypeAction.WaitMode.CLOSE) {
                            waitForPredicate(guiClosePredicate(wgta, mc), wgta.timeoutMs);
                            MacroPrearm.commitStagedCapture(wgta);
                        } else {
                            waitForPredicate(() -> {
                                var captured = wgta.capture(mc);
                                captured.ifPresent(values -> MacroPrearm.stageCapture(wgta, values));
                                return captured.isPresent();
                            }, wgta.timeoutMs);
                            MacroPrearm.commitStagedCapture(wgta);
                        }
                    }
                    else if (action instanceof WaitForHealthAction) {
                        WaitForHealthAction wh = (WaitForHealthAction) action;
                        setCurrentStatus(wh.waitingStatusText());
                        try {
                            if (!MacroPrearm.waitForPrearmed(wh, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForHealth(
                                    wh.healthThreshold, wh.below);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForBlockAction) {
                        WaitForBlockAction wba = (WaitForBlockAction) action;
                        setCurrentStatus(wba.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(wba, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForBlock(wba);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForGuiAction) {
                        WaitForGuiAction wga = (WaitForGuiAction) action;
                        if (!MacroPrearm.hasPrearmedWait(wga)) MacroPrearm.clearCaptureOutputs(wga);
                        if (MacroPrearm.waitForPrearmed(wga, 0)) {
                        } else if (wga.waitMode == WaitForGuiAction.WaitMode.CLOSE) {
                            setCurrentStatus("Waiting for GUI close: " + wga.guiTitle);
                            waitForPredicate(guiClosePredicate(wga, mc), wga.timeoutMs);
                            MacroPrearm.commitStagedCapture(wga);
                        } else {
                            setCurrentStatus("Waiting for GUI: " + wga.guiTitle);
                            waitForPredicate(() -> {
                                var captured = wga.captureGui(mc.gui.screen());
                                captured.ifPresent(values -> MacroPrearm.stageCapture(wga, values));
                                return captured.isPresent();
                            }, wga.timeoutMs);
                            MacroPrearm.commitStagedCapture(wga);
                        }
                    }
                    else if (action instanceof WaitForCooldownAction) {
                        WaitForCooldownAction wca = (WaitForCooldownAction) action;
                        ItemTarget cooldownTarget = resolveItemTarget(wca.itemTarget, wca.itemName).resolveTemplate(mc);
                        if (cooldownTarget == null) continue;
                        String cooldownLabel = describeItemTarget(cooldownTarget);
                        setCurrentStatus("Waiting for cooldown: " +
                            (!cooldownLabel.isEmpty() ? cooldownLabel : (wca.checkMainInteractionHand ? "Main Hand" : "Off Hand")));
                        try {
                            if (!MacroPrearm.waitForPrearmed(wca, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForCooldown(cooldownTarget, wca.checkMainInteractionHand);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitPosAction) {
                        WaitPosAction wpa = (WaitPosAction) action;
                        setCurrentStatus("Waiting for Pos: " + String.format("%.0f, %.0f, %.0f", wpa.x, wpa.y, wpa.z));
                        try {
                            if (!MacroPrearm.waitForPrearmed(wpa, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForPos(
                                    wpa.x, wpa.y, wpa.z, wpa.leeway, wpa.checkRotation, wpa.yaw, wpa.pitch, wpa.rotLeeway);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof JumpAction) {
                        JumpAction ja = (JumpAction) action;
                        mc.execute(() -> ja.execute(mc));
                        if (ja.tap) {
                            CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                            waitForCondition(tickFuture);
                        } else {
                            for (int t = 0; t < ja.durationTicks && isCurrentRunActive(); t++) {
                                CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                                waitForCondition(tickFuture);
                            }
                        }
                        mc.execute(() -> ja.release(mc));
                    }
                    else if (action instanceof SneakAction) {
                        SneakAction sna = (SneakAction) action;
                        if (sna.persistent && sna.sneak) {
                            currentPersistentActions().removeIf(p -> p instanceof SneakAction);
                            currentPersistentActions().add(sna);
                        } else if (!sna.sneak) {
                            currentPersistentActions().removeIf(p -> p instanceof SneakAction);
                        }
                        if (sna.persistent || !sna.sneak) postOnClientThread(mc, () -> applyAggregateSneakState(mc), sna.isTickAligned());
                        else postOnClientThread(mc, () -> sna.execute(mc), sna.isTickAligned());
                    }
                    else if (action instanceof SprintAction) {
                        SprintAction spa = (SprintAction) action;
                        if (spa.persistent && spa.sprint) {
                            currentPersistentActions().removeIf(p -> p instanceof SprintAction);
                            currentPersistentActions().add(spa);
                        } else if (!spa.sprint) {
                            currentPersistentActions().removeIf(p -> p instanceof SprintAction);
                        }
                        if (spa.persistent || !spa.sprint) postOnClientThread(mc, () -> applyAggregateSprintState(mc), spa.isTickAligned());
                        else postOnClientThread(mc, () -> spa.execute(mc), spa.isTickAligned());
                    }
                    else if (action instanceof DisconnectAction) {
                        DisconnectAction da2 = (DisconnectAction) action;

                        broadcastStepProgress(runState, i + 1, macro.actions.size(), macro.name);
                        if (da2.mode == DisconnectAction.DisconnectMode.DISCONNECT) {
                            if (da2.delayMs > 0) Thread.sleep(da2.delayMs);
                            mc.execute(() -> da2.execute(mc));
                        } else if (da2.mode == DisconnectAction.DisconnectMode.AUTO_DISCONNECT) {

                            setCurrentStatus("Auto Disconnect: waiting for " + da2.trigger.name() + "...");
                            da2.execute(mc);
                            setCurrentStatus("Auto Disconnect: executed");
                        } else if (da2.mode == DisconnectAction.DisconnectMode.KICK_DUPE && da2.useNextAction) {

                            java.util.List<MacroAction> nextActs = new java.util.ArrayList<>();
                            int lastSkip = -1;
                            for (int j = i + 1; j < macro.actions.size(); j++) {
                                MacroAction candidate = macro.actions.get(j);

                                if (candidate instanceof DisconnectAction) break;
                                if (!candidate.isEnabled()) continue;

                                if (candidate instanceof DelayAction
                                    || candidate instanceof WaitForHealthAction
                                    || candidate instanceof WaitForBlockAction
                                    || candidate instanceof WaitForGuiAction
                                    || candidate instanceof WaitForCooldownAction
                                    || candidate instanceof WaitPosAction
                                    || candidate instanceof WaitMovementAction
                                    || candidate instanceof WaitForEntityAction
                                    || candidate instanceof WaitForSoundAction
                                    || candidate instanceof WaitForSlotChangeAction
                                    || candidate instanceof WaitForPacketAction
                                    || candidate instanceof WaitForChatAction
                                    || candidate instanceof GoToAction) {
                                    continue;
                                }
                                nextActs.add(candidate);
                                lastSkip = j;
                            }
                            da2.setNextActions(nextActs);
                            mc.execute(() -> da2.execute(mc));

                            if (lastSkip > i) i = lastSkip;
                        } else {

                            mc.execute(() -> da2.execute(mc));
                        }
                        stopCurrentRun();
                        break;
                    }
                    else if (action instanceof RaceAction race) {
                        int bodyCount = MacroRace.runRaceGroup(mc, module, macro.actions, i, race);
                        if (bodyCount > 0) i += bodyCount;
                    }
                    else if (action instanceof MultiAction multi) {
                        int stepCount = MacroRace.runMultiGroup(mc, module, macro.name, macro.actions, i, multi);
                        if (stepCount > 0) i += stepCount;
                    }
                    else if (action instanceof ReportAction reportAction) {
                        int conditionCount = MacroRace.runReportGroup(mc, macro.actions, i, reportAction);
                        if (conditionCount > 0) i += conditionCount;
                    }
                    else if (action instanceof NbtBookAction nba) {
                        int totalBooks = Math.max(1, nba.bookCount);
                        long delayMs = Math.max(1, nba.delayTicks) * 50L;
                        boolean signedAny = false;
                        for (int b = 0; b < totalBooks; b++) {
                            if (!isCurrentRunActive()) break;
                            final int bookIdx = b;
                            java.util.concurrent.CompletableFuture<Boolean> result = new java.util.concurrent.CompletableFuture<>();
                            mc.execute(() -> result.complete(nba.executeSingleBook(mc, bookIdx, totalBooks)));
                            Boolean success = result.get();
                            if (!success) break;
                            signedAny = true;

                            if (b < totalBooks - 1) {
                                Thread.sleep(delayMs);
                            }
                        }
                        if (signedAny && nba.disconnectAfter) {
                            mc.execute(() -> nba.afterSigning(mc));
                        }
                    }
                    else if (action instanceof GoToAction) {
                        GoToAction ga = (GoToAction) action;
                        runGoToAction(mc, ga);
                    }

                    else if (action instanceof MoveAction) {
                        MoveAction ma = (MoveAction) action;
                        if (ma.nonBlocking) {

                            mc.execute(() -> ma.execute(mc));
                            currentBackgroundMoves().put(ma, new int[]{ma.durationTicks - 1});

                        } else {

                            for (int t = 0; t < ma.durationTicks && isCurrentRunActive(); t++) {
                                mc.execute(() -> ma.execute(mc));
                                CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                                waitForCondition(tickFuture);
                            }
                            RunState movementRun = CURRENT_RUN.get();
                            mc.execute(() -> releaseMoveIfUnused(movementRun, ma, mc));
                        }
                    }
                    else if (action instanceof LookAtBlockAction) {
                        LookAtBlockAction la = (LookAtBlockAction) action;
                        LookAtBlockAction.RotationTarget rotationTarget = resolveLookAtTargetOnClient(mc, la);
                        if (rotationTarget == null) {
                            continue;
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
                    }
                    else if (action instanceof RepeatAction) {
                        RepeatAction ra = (RepeatAction) action;
                        int startIdx = i + 1;
                        int endIdx = Math.min(startIdx + ra.stepCount, macro.actions.size());

                        for (int rep = 0; rep < ra.repeatCount && isCurrentRunActive(); rep++) {
                            setCurrentStatus("Repeat " + (rep + 1) + "/" + ra.repeatCount);
                            for (int j = startIdx; j < endIdx && isCurrentRunActive(); j++) {
                                MacroAction subAction = macro.actions.get(j);
                                if (!subAction.isEnabled()) continue;

                                REPEAT_PACKET_CONTEXT.set(new RepeatPacketContext(macro, j + 1, endIdx));
                                REPEAT_PACKET_SKIP.set(0);
                                try {
                                    executeSingleActionWithWaits(mc, subAction, module);
                                    j += Math.max(0, REPEAT_PACKET_SKIP.get());
                                } finally {
                                    REPEAT_PACKET_SKIP.set(0);
                                    REPEAT_PACKET_CONTEXT.remove();
                                }
                            }
                        }
                        i = endIdx - 1;
                    }
                    else if (action instanceof CaptureValueAction captureValue) {
                        MacroCaptureAutofill.runCaptureValueAction(mc, captureValue);
                    }
                    else if (action instanceof WaitForChatAction) {
                        WaitForChatAction wca = (WaitForChatAction) action;
                        setCurrentStatus("Waiting for chat: " + wca.pattern);
                        if (!MacroPrearm.waitForPrearmed(wca, wca.timeoutMs)) MacroPacketWait.awaitChat(wca);
                    }
                    else if (action instanceof WaitForEntityAction) {
                        WaitForEntityAction wea = (WaitForEntityAction) action;
                        setCurrentStatus(wea.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(wea, 0)) {
                                waitForPredicate(() -> wea.matchesEntityTarget(mc), wea.timeoutMs);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForSoundAction) {
                        WaitForSoundAction wsa = (WaitForSoundAction) action;
                        String sndDesc = wsa.soundIds.isEmpty() ? "any" : wsa.soundIds.get(0);
                        setCurrentStatus("Waiting for sound: " + sndDesc);
                        try {
                            if (!MacroPrearm.waitForPrearmed(wsa, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForSound(wsa);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof MineAction) {
                        MineAction mna = (MineAction) action;
                        runMineAction(mc, mna);
                    }
                    else if (action instanceof InstaBreakAction) {
                        runInstaBreakAction(mc, (InstaBreakAction) action);
                    }
                    else if (action instanceof BreakAction breakAction) {
                        InlineNextActions nextActions = InlineNextActions.empty();
                        if (breakAction.interact && breakAction.runNextSteps) {
                            nextActions = collectInlineNextActions(macro.actions, i);
                            breakAction.setNextActions(nextActions.actions());
                        }
                        runBreakAction(mc, breakAction);
                        if (breakAction.didRunNextActions() && nextActions.lastIndex() > i) i = nextActions.lastIndex();
                    }

                    else if (action instanceof WaitForSlotChangeAction) {
                        WaitForSlotChangeAction wsca = (WaitForSlotChangeAction) action;
                        setCurrentStatus(wsca.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(wsca, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForSlotChange(wsca);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForWorldChangeAction world) {
                        setCurrentStatus(world.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(world, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForWorldChange(world);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForPositionDeltaAction wpda) {
                        setCurrentStatus(wpda.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(wpda, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForPositionDelta(wpda.distance, wpda.horizontalOnly);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForTeleportAction teleport) {
                        setCurrentStatus(teleport.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(teleport, 0)) {
                                CompletableFuture<?> future = MacroRace.raceConditionFuture(mc, teleport);
                                MacroRace.waitForFutureDone(future, 0);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitGamemodeChangeAction gm) {
                        setCurrentStatus(gm.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(gm, gm.timeoutMs)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForGamemodeChange(gm);
                                MacroRace.waitForFutureDone(future, gm.timeoutMs);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitMovementAction wm) {
                        setCurrentStatus(wm.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(wm, 0)) {
                                CompletableFuture<?> future = MacroRace.raceConditionFuture(mc, wm.resolveSubAction());
                                MacroRace.waitForFutureDone(future, 0);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForLanStepAction) {
                        WaitForLanStepAction wla = (WaitForLanStepAction) action;
                        setCurrentStatus(wla.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(wla, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForLanStep(wla);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForMacroStepAction) {
                        WaitForMacroStepAction wma = (WaitForMacroStepAction) action;
                        setCurrentStatus(wma.getDisplayName());
                        if (!MacroPrearm.waitForPrearmed(wma, wma.timeoutMs)) MacroRace.waitForMacroStep(wma);
                    }
                    else if (action instanceof StopMacroAction) {
                        setCurrentStatus("Stopping Macro");
                        StopMacroAction stopAction = (StopMacroAction) action;
                        if (stopAction.target != StopMacroAction.StopTarget.SELF) {
                            stopAction.execute(mc);
                            break;
                        }

                        broadcastStepProgress(runState, i + 1, macro.actions.size(), macro.name);
                        stopCurrentRun();
                        break;
                    }

                    else if (action instanceof TickSyncAction) {
                        TickSyncAction tsa = (TickSyncAction) action;

                        if (mc.level == null) continue;

                        java.util.List<net.minecraft.network.protocol.Packet<?>> preGenerated =
                            preGeneratePackets(macro, i + 1, tsa.preGenCount);

                        long targetTick = mc.level.getGameTime() + tsa.tickOffset;
                        setCurrentStatus("Tick Sync -> " + targetTick + " (" + preGenerated.size() + " pkts)");

                        while (isCurrentRunActive() && mc.level.getGameTime() < targetTick) {
                            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
                        }

                        if (isCurrentRunActive()) {
                            executePreGeneratedBurst(mc, preGenerated);
                            i += countPreGeneratedActions(macro, i + 1, preGenerated.size());
                            AutismClientMessaging.sendPrefixed("Tick sync: " + preGenerated.size() + " pkts sent");
                        }
                    }
                    else if (action instanceof RevisionSyncAction) {
                        RevisionSyncAction rsa = (RevisionSyncAction) action;

                        if (mc.player == null || mc.player.containerMenu == null) continue;

                        java.util.List<net.minecraft.network.protocol.Packet<?>> preGenerated =
                            preGeneratePackets(macro, i + 1, rsa.preGenCount);

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
                            i += countPreGeneratedActions(macro, i + 1, preGenerated.size());
                            AutismClientMessaging.sendPrefixed("Rev sync: " + preGenerated.size() + " pkts sent");
                        }
                    }
                    else if (action instanceof ServerTickSyncAction) {
                        ServerTickSyncAction stsa = (ServerTickSyncAction) action;

                        if (mc.getConnection() == null) continue;

                        java.util.List<net.minecraft.network.protocol.Packet<?>> preGenerated =
                            preGeneratePackets(macro, i + 1, stsa.preGenCount);

                        int sampleWaitMs = 0;
                        while (isCurrentRunActive() && !ServerTickTracker.isReady() && sampleWaitMs < stsa.maxWaitMs) {

                            float progress = ServerTickTracker.getWarmupProgress();
                            setCurrentStatus(String.format("ServerSync warmup %.0f%% (%d samples, %dms)",
                                progress * 100, ServerTickTracker.getSampleCount(), ServerTickTracker.getTrackingTimeMs()));
                            Thread.sleep(50);
                            sampleWaitMs += 50;
                        }

                        if (!isCurrentRunActive()) continue;

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
                            i += countPreGeneratedActions(macro, i + 1, preGenerated.size());
                            long actualDelay = (System.nanoTime() - optimalTime) / 1_000_000L;
                            AutismClientMessaging.sendPrefixed("ServerSync: " + preGenerated.size() + " pkts (+" + actualDelay + "ms)");
                        }
                    }
                    else if (action instanceof RotateAction) {
                        RotateAction ra = (RotateAction) action;
                        if (ra.smooth) {
                            startSmoothRotation(ra.yaw, ra.pitch, ra.getRotationStep());
                            if (ra.waitForCompletion) {
                                waitForSmoothRotationCompletion();
                            }
                        } else {
                            mc.execute(() -> action.execute(mc));
                        }
                    }
                    else if (action instanceof PacedTpAction pacedTp) {
                        runPacedTpAction(mc, pacedTp);
                    }
                    else if (action instanceof LookAtBlockAction la) {
                        LookAtBlockAction.RotationTarget rotationTarget = resolveLookAtTargetOnClient(mc, la);
                        if (rotationTarget == null) {
                            continue;
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
                    }
                    else if (action instanceof UseItemAction) {
                        MacroActionRunner.runUseItemAction(mc, (UseItemAction) action);
                    }
                    else if (action instanceof ItemAction) {
                        ItemAction ia = (ItemAction) action;
                        if (ia.waitForItem) {
                            try {
                                waitForItemActionTargets(mc, ia);
                            } catch (java.util.concurrent.CancellationException e) {  }
                        }
                        postGuiFuture = MacroActionRunner.createPostGuiFuture(mc, ia);
                        if (ia.useClickDelay && ia.clickDelayMs > 0) {
                            MacroActionRunner.runItemActionPaced(mc, ia);
                        } else {
                            MacroActionRunner.runMacroActionOnClientThread(mc, action, "ItemAction");
                        }
                    }
                    else if (action instanceof DropAction || action instanceof SwapSlotsAction) {
                        if (action instanceof DropAction dropAction) {
                            postGuiFuture = MacroActionRunner.createPostGuiFuture(mc, dropAction);
                        }
                        MacroActionRunner.runMacroActionOnClientThread(mc, action, action.getDisplayName());
                    }
                    else if (action instanceof CraftAction craftAction) {
                        runCraftAction(mc, craftAction);
                    }
                    else if (action instanceof SendPacketAction) {

                        if (module != null && module.useInstantExecutionMode()) {

                            try {
                                action.execute(mc);
                            } catch (Exception e) {
                                AutismClientMessaging.sendPrefixed("\u00a7cError in SendPacket: " + e.getMessage());
                            }
                        } else {
                            runOnClientThread(mc, () -> {
                                try {
                                    action.execute(mc);
                                } catch (Exception e) {
                                    AutismClientMessaging.sendPrefixed("\u00a7cError in SendPacket: " + e.getMessage());
                                }
                            });
                        }
                    }
                    else if (action instanceof autismclient.util.macro.StoreItemAction) {
                        autismclient.util.macro.StoreItemAction sia = (autismclient.util.macro.StoreItemAction) action;
                        setCurrentStatus((sia.mode == autismclient.util.macro.StoreItemAction.Mode.LOOT ? "Looting" : "Storing")
                            + (sia.persistent ? " \u221e" : ""));
                        if (sia.persistent) {

                            while (isCurrentRunActive()) {
                                try { MacroActionRunner.runStoreItemAction(mc, sia); }
                                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                                catch (Exception e) { AutismClientMessaging.sendPrefixed("Store error: " + e.getMessage()); }

                                CompletableFuture<Void> siTick = MacroConditionRegistry.waitForNextTick();
                                waitForCondition(siTick);
                            }
                        } else {
                            try { MacroActionRunner.runStoreItemAction(mc, sia); }
                            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                            catch (Exception e) { AutismClientMessaging.sendPrefixed("Store error: " + e.getMessage()); }
                        }
                    }
                    else if (action instanceof ClickAction) {
                        ClickAction ca = (ClickAction) action;
                        MacroActionRunner.runClickActionBurst(mc, ca);
                    }
                    else if (action instanceof PayAction) {
                        runPayAction(mc, (PayAction) action);
                    }
                    else if (action instanceof XCarryAction xsa) {
                        if (xsa.mode == XCarryAction.Mode.PUT_IN
                                && (xsa.transferMode == XCarryAction.TransferMode.CLICK
                                    || xsa.transferMode == XCarryAction.TransferMode.SAFE_CLICK)) {
                            if (xsa.transferMode == XCarryAction.TransferMode.SAFE_CLICK) {
                                setCurrentStatus("XCarry Safe");
                                runXCarrySafeClickPutIn(mc, xsa);
                            } else {
                                setCurrentStatus("XCarry Click");
                                runXCarryClickPutIn(mc, xsa);
                            }
                        } else {
                            setCurrentStatus(switch (xsa.mode) {
                                case PUT_IN -> "XCarry";
                                case TAKE_OUT -> "XCarry Out";
                                case DROP -> "XCarry Drop";
                            });
                            runOnClientThread(mc, () -> {
                                try {
                                    xsa.execute(mc);
                                } catch (Exception e) {
                                    AutismClientMessaging.sendPrefixed("\u00a7cXCarry: " + e.getMessage());
                                }
                            });
                        }
                    }
                    else if (action instanceof InventoryAuditAction auditAction
                            && (auditAction.mode == InventoryAuditAction.Mode.DUPE
                                || auditAction.mode == InventoryAuditAction.Mode.DUPE_SPAM)) {
                        setCurrentStatus("Dupe: " + auditAction.dupeVector.name() + " " + auditAction.openMode.name());
                        try {
                            auditAction.executeDupe(mc);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        } catch (Exception e) {
                            AutismClientMessaging.sendPrefixed("\u00a7cDupe error: " + e.getMessage());
                        }
                    }
                    else if (action instanceof ContainerClickSequenceAction sequence) {
                        MacroActionRunner.runContainerClickSequenceAction(mc, sequence);
                    }
                    else if (action instanceof PacketBurstAction burst) {
                        MacroActionRunner.runPacketBurstAction(mc, burst);
                    }
                    else if (action instanceof BundleDupeV2Action bundleV2) {
                        setCurrentStatus("Bundle Dupe V2");
                        bundleV2.execute(mc);
                    }
                    else if (action instanceof RollbackAction rollback) {
                        setCurrentStatus("Rollback");
                        try {
                            rollback.executeRollback(mc);
                        } catch (InterruptedException e) {
                            AutismClientMessaging.sendPrefixed("\u00a7eRollback canceled safely.");
                        } catch (Exception e) {
                            AutismClientMessaging.sendPrefixed("\u00a7cRollback failed: " + MacroRace.safeThrowableMessage(e));
                        }
                    }
                    else if (action instanceof PacketGateAction pga) {
                        MacroRace.runPacketGateAction(mc, pga);
                    }
                    else if (MacroActionRunner.isTargetWaitInteractionAction(action)) {
                        postGuiFuture = MacroActionRunner.runTargetWaitInteractionAction(mc, action);
                    }
                    else if (action instanceof MacroVariablesAction || action instanceof AssertAction) {
                        action.execute(mc);
                    }
                    else if (action instanceof UseItemPhaseAction phaseAction) {
                        MacroActionRunner.runUseItemPhaseAction(mc, phaseAction);
                    }
                    else if (action instanceof autismclient.api.macro.ContextMacroAction contextAction) {
                        MacroActionRunner.runContextMacroAction(contextAction);
                    }
                    else if (action instanceof PlaceAction placeInteract && placeInteract.interact) {

                        runPlaceAction(mc, placeInteract);
                    }
                    else if (action instanceof PlaceAction place && place.waitForItem) {

                        ItemTarget placeTarget = place.resolvedItemTarget().resolveTemplate(mc);
                        if (placeTarget == null) {
                            postGuiFuture = null;
                        } else {
                        if (placeTarget.hasIdentity() || placeTarget.hasSlot()) {
                            setCurrentStatus("Waiting for " + describeItemTarget(placeTarget));
                            try {
                                waitForItemActionEntry(mc, placeTarget);
                            } catch (java.util.concurrent.CancellationException e) {  }
                        }
                        if (isCurrentRunActive()) {
                            final PlaceAction fPlace = place;
                            postOnClientThread(mc, () -> {
                                if (PackHideState.isHardLocked()) return;
                                try {
                                    fPlace.execute(mc);
                                } catch (Exception e) {
                                    AutismClientMessaging.sendPrefixed("\u00a7cError in Place: " + e.getMessage());
                                }
                            }, fPlace.isTickAligned());
                        }
                        }
                    }
                    else {

                        if (MacroActionRunner.usesTickAlignedDispatch(action)) {

                            MacroActionRunner.runMacroActionOnClientThread(mc, action, action.getDisplayName());
                        } else if (module != null && module.useInstantExecutionMode()) {

                            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                            RunState scheduledRun = CURRENT_RUN.get();
                            mc.execute(() -> withRunContext(scheduledRun, () -> {
                                try {
                                    if (PackHideState.isHardLocked()) return;
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
                            waitForCondition(postGuiFuture);
                        } catch (java.util.concurrent.CancellationException e) {  }
                    }

                    if (isCurrentRunActive()) {
                        markStepCompleted(i + 1);
                        broadcastStepProgress(runState, i + 1, macro.actions.size(), macro.name);
                    }

                    if (isCurrentRunActive() && !MacroActionRunner.isInstantInventoryAction(action)) {
                        if (module != null && module.useInstantExecutionMode()) {
                            int delayUs = module.getActionDelayUs();
                            if (delayUs > 0) {

                                java.util.concurrent.locks.LockSupport.parkNanos(delayUs * 1000L);
                            } else {

                                Thread.onSpinWait();
                            }
                            yieldToMainThread(mc);
                        }
                    }
                    } finally {
                        MacroRaycastAim.release();
                        releaseClientResources(heldResources);
                    }
                }

                if (macro.actions.isEmpty()) {
                    if (!runState.options.silentLifecycle()) AutismClientMessaging.sendPrefixed("\u00a7cMacro stopped: no actions left.");
                    stopCurrentRun();
                    break;
                }

                if (isCurrentRunActive()) {
                    PacketGateManager.clearOwnerAndFlushConfigured(runState.id, mc == null ? null : mc.getConnection());
                }

                if (macro.loop && loopCount < maxLoops - 1 && isCurrentRunActive()) {
                    if (module != null && module.useInstantExecutionMode()) {

                        int delayUs = module.getActionDelayUs();
                        if (delayUs > 0) {
                            java.util.concurrent.locks.LockSupport.parkNanos(delayUs * 1000L);
                        } else {
                            Thread.onSpinWait();
                        }
                        yieldToMainThread(mc);
                    } else if (module != null && module.useMsSleepMode()) {

                        int sleepMs = module.getMsSleepInterval();
                        Thread.sleep(sleepMs);
                    } else {

                        try {
                            CompletableFuture<Void> syncFuture = MacroConditionRegistry.waitForNextTick();
                            waitForCondition(syncFuture);
                        } catch (CancellationException e) {  }
                    }
                }

                loopCount++;
            }

            awaitBackgroundTimedOverrides(runState);

            if (isCurrentRunActive()) {
                if (!runState.options.silentLifecycle()) AutismClientMessaging.sendPrefixed("\u00a7aMacro finished: " + macro.name);
            }

        } catch (InterruptedException e) {  } catch (Exception e) {
            if (!runState.options.silentLifecycle()) AutismClientMessaging.sendPrefixed("\u00a7cMacro crashed: " + e.getMessage());
            AutismClientAddon.LOG.warn("[Autism] Macro crashed: {}", macro.name, e);
        } finally {
            if (!PackHideState.isHardLocked()) {
                MacroRace.runFinallyActions(runState, mc);
            }
            PacketGateManager.clearOwnerAndFlushConfigured(runState.id, mc == null ? null : mc.getConnection());
            finishCurrentRun();
            cancelRotation(runState);
            if (!isRunning.get()) MacroConditionRegistry.cancelAll();

            broadcastStepProgress(runState, -1, 0, "");
        }
    }

    static CaptureListSelector.State captureSelectionState(MacroAction action) {
        return MacroPacketWait.captureSelectionState(action);
    }


    static String buildAutofillQuery(String resolvedCommand, CaptureListSelector.Filter filter, String filterText) {
        return MacroCaptureAutofill.buildAutofillQuery(resolvedCommand, filter, filterText);
    }


    static void waitForCondition(CompletableFuture<Void> future) {

        Thread t = Thread.currentThread();
        future.whenComplete((v, e) -> java.util.concurrent.locks.LockSupport.unpark(t));
        while (!future.isDone() && isCurrentRunActive()) {

            java.util.concurrent.locks.LockSupport.parkNanos(MACRO_WAIT_POLL_NANOS);
            if (Thread.interrupted()) break;
        }
        if (!isCurrentRunActive()) {
            future.cancel(true);
        }
    }

    private static void runPacedTpAction(Minecraft mc, PacedTpAction action) throws InterruptedException {
        AtomicReference<autismclient.util.multi.PacketTeleportController.MacroHandle> handleRef = new AtomicReference<>();
        runOnClientThread(mc, () -> handleRef.set(
            autismclient.util.multi.PacketTeleportController.startMacro(action)));
        autismclient.util.multi.PacketTeleportController.MacroHandle handle = handleRef.get();
        if (handle == null) return;
        setCurrentStatus("TP " + action.commandArguments());
        try {
            while (isCurrentRunActive() && !handle.completion().isDone()) waitOneTick();
        } finally {
            if (!handle.completion().isDone()) {
                mc.execute(() -> autismclient.util.multi.PacketTeleportController.cancelMacro(handle, "macro stopped"));
            }
        }
    }

    private static final int MAIN_BARRIER_EVERY = 256;
    private static final ThreadLocal<CompletableFuture<Void>> MAIN_DRAIN_BARRIER = new ThreadLocal<>();
    private static final ThreadLocal<int[]> INSTANT_BURST = ThreadLocal.withInitial(() -> new int[1]);

    private static void yieldToMainThread(Minecraft mc) {
        if (mc == null || mc.isSameThread()) return;
        int[] burst = INSTANT_BURST.get();
        if (++burst[0] < MAIN_BARRIER_EVERY) return;
        burst[0] = 0;
        CompletableFuture<Void> prev = MAIN_DRAIN_BARRIER.get();
        if (prev != null && !prev.isDone()) waitForCondition(prev);
        if (!isCurrentRunActive()) return;
        CompletableFuture<Void> next = new CompletableFuture<>();
        MAIN_DRAIN_BARRIER.set(next);
        try {
            mc.execute(() -> next.complete(null));
        } catch (Throwable ignored) {
            next.complete(null);
        }
    }

    static void waitForPredicate(java.util.function.BooleanSupplier predicate, int timeoutMs) {
        long start = System.currentTimeMillis();
        while (isCurrentRunActive()) {
            try {
                if (predicate.getAsBoolean()) return;
            } catch (Throwable ignored) {  }
            if (timeoutMs > 0 && System.currentTimeMillis() - start >= timeoutMs) return;
            CompletableFuture<Void> tick = MacroConditionRegistry.waitForNextTick();
            waitForCondition(tick);
        }
    }

    static java.util.function.BooleanSupplier guiClosePredicate(WaitForGuiAction action, Minecraft mc) {
        AtomicBoolean sawMatchingGui = new AtomicBoolean(action.checkGui(mc));
        return () -> {
            var capture = mc == null ? java.util.Optional.<java.util.Map<String, MacroValue>>empty() : action.captureGui(mc.gui.screen());
            boolean matches = capture.isPresent();
            if (matches) {
                capture.ifPresent(values -> MacroPrearm.stageCapture(action, values));
                sawMatchingGui.set(true);
                return false;
            }
            return sawMatchingGui.get();
        };
    }

    static java.util.function.BooleanSupplier guiClosePredicate(WaitGuiTypeAction action, Minecraft mc) {
        AtomicBoolean sawMatchingGui = new AtomicBoolean(action.matches(mc));
        return () -> {
            var capture = action.capture(mc);
            boolean matches = capture.isPresent();
            if (matches) {
                capture.ifPresent(values -> MacroPrearm.stageCapture(action, values));
                sawMatchingGui.set(true);
                return false;
            }
            return sawMatchingGui.get();
        };
    }



    public static boolean waitForMacroCondition(Minecraft mc, MacroAction trigger, int timeoutMs) throws InterruptedException {
        return MacroRace.waitForMacroCondition(mc, trigger, timeoutMs);
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
                    waitForCondition(tf);
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
            waitForPredicate(() -> wipa.matches(mc), wipa.timeoutMs);
        } else if (action instanceof WaitDurabilityAction wda) {
            setCurrentStatus(wda.getDisplayName());
            if (wda.useNext) MacroDurability.waitForDurabilityUseNext(mc, wda);
            else waitForPredicate(() -> wda.matches(mc), wda.timeoutMs);
        } else if (action instanceof WaitFreeSlotsAction wfsa) {
            setCurrentStatus(wfsa.getDisplayName());
            waitForPredicate(() -> wfsa.matches(mc), wfsa.timeoutMs);
        } else if (action instanceof WaitEntityTargetAction weta) {
            setCurrentStatus(weta.getDisplayName());
            waitForPredicate(() -> weta.matches(mc), weta.timeoutMs);
        } else if (action instanceof WaitGuiTypeAction wgta) {
            setCurrentStatus(wgta.getDisplayName());
            MacroPrearm.clearCaptureOutputs(wgta);
            if (wgta.waitMode == WaitGuiTypeAction.WaitMode.CLOSE) waitForPredicate(guiClosePredicate(wgta, mc), wgta.timeoutMs);
            else waitForPredicate(() -> {
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
                waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForBlockAction) {
            WaitForBlockAction wba = (WaitForBlockAction) action;
            setCurrentStatus(wba.getDisplayName());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForBlock(wba);
                waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForGuiAction) {
            WaitForGuiAction wga = (WaitForGuiAction) action;
            MacroPrearm.clearCaptureOutputs(wga);
            if (wga.waitMode == WaitForGuiAction.WaitMode.CLOSE) {
                setCurrentStatus("Waiting for GUI close: " + wga.guiTitle);
                waitForPredicate(guiClosePredicate(wga, mc), wga.timeoutMs);
            } else {
                setCurrentStatus("Waiting for GUI: " + wga.guiTitle);
                waitForPredicate(() -> {
                    var captured = wga.captureGui(mc.gui.screen());
                    captured.ifPresent(values -> MacroPrearm.stageCapture(wga, values));
                    return captured.isPresent();
                }, wga.timeoutMs);
            }
            MacroPrearm.commitStagedCapture(wga);
        } else if (action instanceof WaitForCooldownAction) {
            WaitForCooldownAction wca = (WaitForCooldownAction) action;
            ItemTarget cooldownTarget = resolveItemTarget(wca.itemTarget, wca.itemName).resolveTemplate(mc);
            if (cooldownTarget == null) return;
            String cooldownLabel = describeItemTarget(cooldownTarget);
            setCurrentStatus("Waiting for cooldown: " +
                (!cooldownLabel.isEmpty() ? cooldownLabel : (wca.checkMainInteractionHand ? "Main Hand" : "Off Hand")));
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForCooldown(cooldownTarget, wca.checkMainInteractionHand);
                waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitPosAction) {
            WaitPosAction wpa = (WaitPosAction) action;
            setCurrentStatus("Waiting for Pos: " + String.format("%.0f, %.0f, %.0f", wpa.x, wpa.y, wpa.z));
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForPos(
                    wpa.x, wpa.y, wpa.z, wpa.leeway, wpa.checkRotation, wpa.yaw, wpa.pitch, wpa.rotLeeway);
                waitForCondition(future);
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
                waitForPredicate(() -> wea.matchesEntityTarget(mc), wea.timeoutMs);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForSoundAction) {
            WaitForSoundAction wsa = (WaitForSoundAction) action;
            String sndDesc = wsa.soundIds.isEmpty() ? "any" : wsa.soundIds.get(0);
            setCurrentStatus("Waiting for sound: " + sndDesc);
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForSound(wsa);
                waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForSlotChangeAction) {
            WaitForSlotChangeAction wsca = (WaitForSlotChangeAction) action;
            setCurrentStatus(wsca.getDisplayName());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForSlotChange(wsca);
                waitForCondition(future);
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
                waitForCondition(future);
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
                waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForMacroStepAction) {
            WaitForMacroStepAction wma = (WaitForMacroStepAction) action;
            setCurrentStatus(wma.getDisplayName());
            MacroRace.waitForMacroStep(wma);
        } else if (action instanceof GoToAction) {
            GoToAction ga = (GoToAction) action;
            runGoToAction(mc, ga);
        } else if (action instanceof MoveAction) {
            MoveAction ma = (MoveAction) action;
            if (ma.nonBlocking) {
                mc.execute(() -> ma.execute(mc));
                currentBackgroundMoves().put(ma, new int[]{ma.durationTicks - 1});
            } else {
                for (int t = 0; t < ma.durationTicks && isCurrentRunActive(); t++) {
                    mc.execute(() -> ma.execute(mc));
                    CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                    waitForCondition(tickFuture);
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
            runMineAction(mc, mna);
        } else if (action instanceof InstaBreakAction) {
            runInstaBreakAction(mc, (InstaBreakAction) action);
        } else if (action instanceof BreakAction) {
            runBreakAction(mc, (BreakAction) action);
        } else if (action instanceof PlaceAction) {
            runPlaceAction(mc, (PlaceAction) action);
        } else if (action instanceof UseItemAction) {
            MacroActionRunner.runUseItemAction(mc, (UseItemAction) action);
        } else if (action instanceof JumpAction) {
            JumpAction ja = (JumpAction) action;
            mc.execute(() -> ja.execute(mc));
            if (ja.tap) {
                CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                waitForCondition(tickFuture);
            } else {
                for (int t = 0; t < ja.durationTicks && isCurrentRunActive(); t++) {
                    CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                    waitForCondition(tickFuture);
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
            if (sna.persistent || !sna.sneak) postOnClientThread(mc, () -> applyAggregateSneakState(mc), sna.isTickAligned());
            else postOnClientThread(mc, () -> sna.execute(mc), sna.isTickAligned());
        } else if (action instanceof SprintAction) {
            SprintAction spa = (SprintAction) action;
            if (spa.persistent && spa.sprint) {
                currentPersistentActions().removeIf(p -> p instanceof SprintAction);
                currentPersistentActions().add(spa);
            } else if (!spa.sprint) {
                currentPersistentActions().removeIf(p -> p instanceof SprintAction);
            }
            if (spa.persistent || !spa.sprint) postOnClientThread(mc, () -> applyAggregateSprintState(mc), spa.isTickAligned());
            else postOnClientThread(mc, () -> spa.execute(mc), spa.isTickAligned());
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
                    waitForItemActionTargets(mc, ia);
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
            runPayAction(mc, (PayAction) action);
        } else if (action instanceof XCarryAction xsa) {
            if (xsa.mode == XCarryAction.Mode.PUT_IN
                    && (xsa.transferMode == XCarryAction.TransferMode.CLICK
                        || xsa.transferMode == XCarryAction.TransferMode.SAFE_CLICK)) {
                if (xsa.transferMode == XCarryAction.TransferMode.SAFE_CLICK) {
                    setCurrentStatus("XCarry Safe");
                    runXCarrySafeClickPutIn(mc, xsa);
                } else {
                    setCurrentStatus("XCarry Click");
                    runXCarryClickPutIn(mc, xsa);
                }
            } else {
                setCurrentStatus(switch (xsa.mode) {
                    case PUT_IN -> "XCarry";
                    case TAKE_OUT -> "XCarry Out";
                    case DROP -> "XCarry Drop";
                });
                runOnClientThread(mc, () -> {
                    try {
                        xsa.execute(mc);
                    } catch (Exception e) {
                        AutismClientMessaging.sendPrefixed("\u00a7cXCarry: " + e.getMessage());
                    }
                });
            }
        } else if (action instanceof CraftAction) {
            runCraftAction(mc, (CraftAction) action);
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
                    waitForCondition(siTick);
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
                waitForCondition(postGuiFuture);
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


    private static void runXCarryClickPutIn(Minecraft mc, XCarryAction xsa) throws InterruptedException {
        if (mc == null || mc.player == null || mc.gameMode == null) return;

        if (xsa.requiresCursorStorage()) {
            setCurrentStatus("XCarry Cursor");
            runOnClientThread(mc, () -> xsa.execute(mc));
            return;
        }

        autismclient.util.AutismSharedState sharedState = autismclient.util.AutismSharedState.get();
        boolean prevBypass = sharedState.isXCarryArmorBypass();
        sharedState.setXCarryArmorBypass(true);
        try {
        AtomicReference<net.minecraft.world.inventory.AbstractContainerMenu> containerHandlerRef = new AtomicReference<>();
        AtomicReference<autismclient.util.AutismContainerTarget> containerTargetRef = new AtomicReference<>();
        AtomicReference<List<Integer>> containerSlotIdsRef = new AtomicReference<>(List.of());
        AtomicBoolean usingContainer = new AtomicBoolean(false);

        runOnClientThread(mc, () -> {
            if (mc.player == null) return;
            net.minecraft.world.inventory.AbstractContainerMenu current = mc.player.containerMenu;
            if (current != null && current != mc.player.inventoryMenu) {
                usingContainer.set(true);
                containerHandlerRef.set(current);
                containerTargetRef.set(autismclient.util.AutismSharedState.get().getLastContainerTarget());
                containerSlotIdsRef.set(new ArrayList<>(xsa.collectContainerTransferSlots(current, mc.player.inventoryMenu)));
            }
        });

        if (usingContainer.get() && containerTargetRef.get() == null) {
            AutismClientMessaging.sendPrefixed("XCarry click mode: missing container target, using fast mode.");
            runOnClientThread(mc, () -> xsa.execute(mc));
            return;
        }

        if (usingContainer.get()) {
            for (int slotId : containerSlotIdsRef.get()) {
                if (!isCurrentRunActive()) return;
                setCurrentStatus("XCarry Click: collect");
                runOnClientThread(mc, () -> {
                    if (mc.player == null || mc.gameMode == null) return;
                    net.minecraft.world.inventory.AbstractContainerMenu handler = containerHandlerRef.get();
                    if (handler == null || mc.player.containerMenu != handler) return;
                    if (slotId < 0 || slotId >= handler.slots.size()) return;
                    if (handler.slots.get(slotId).getItem().isEmpty()) return;
                    mc.gameMode.handleContainerInput(handler.containerId, slotId, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player);
                });
                waitOneTick();
            }

            runOnClientThread(mc, () -> {
                if (mc.player == null || mc.getConnection() == null) return;
                net.minecraft.world.inventory.AbstractContainerMenu handler = containerHandlerRef.get();
                if (handler == null) return;
                mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(handler.containerId));
            });
            waitOneTick();

            runOnClientThread(mc, () -> {
                if (mc.player != null) {
                    mc.player.containerMenu = mc.player.inventoryMenu;
                }
            });
            waitOneTick();
        }

        runOnClientThread(mc, xsa::preparePutInStorageGuard);

        int xCarryAttempts = 0;
        while (isCurrentRunActive() && xCarryAttempts++ < 512) {
            XCarryAction.PutInMove move = callOnClientThread(mc, () -> {
                if (mc.player == null) return null;
                return xsa.findNextPutInMove(mc.player.inventoryMenu);
            }, null);
            if (move == null) break;

            setCurrentStatus("XCarry Click -> " + move.targetSlotId + " x" + move.count);
            int targetBefore = callOnClientThread(mc, () -> {
                if (mc.player == null) return 0;
                net.minecraft.world.inventory.InventoryMenu handler = mc.player.inventoryMenu;
                if (move.targetSlotId < 0 || move.targetSlotId >= handler.slots.size()) return 0;
                return handler.slots.get(move.targetSlotId).getItem().getCount();
            }, 0);

            boolean moved = callOnClientThread(mc, () -> {
                if (mc.player == null || mc.gameMode == null) return false;
                net.minecraft.world.inventory.InventoryMenu handler = mc.player.inventoryMenu;
                mc.player.containerMenu = handler;
                return XCarryAction.movePlannedCount(handler, mc, move.sourceSlotId, move.targetSlotId, move.count);
            }, Boolean.FALSE);
            if (!moved) break;

            boolean placed = waitForXCarryCondition(mc, 500L, () -> {
                if (mc.player == null) return false;
                net.minecraft.world.inventory.InventoryMenu handler = mc.player.inventoryMenu;
                if (move.targetSlotId < 0 || move.targetSlotId >= handler.slots.size()) return false;
                return handler.getCarried().isEmpty()
                        && handler.slots.get(move.targetSlotId).getItem().getCount() >= targetBefore + move.count;
            });
            if (!placed) {
                rollbackXCarryCursor(mc, move.sourceSlotId);
                break;
            }

            waitOneTick();
        }

        runOnClientThread(mc, () -> {
            if (mc.player != null) {
                boolean active = XCarryAction.hasStoredItems(mc.player.inventoryMenu, xsa.carryCursor, xsa.activeStorageSlotIds());
                autismclient.util.AutismSharedState shared = autismclient.util.AutismSharedState.get();
                if (active) {
                    shared.mergeXCarryForcedTargets(xsa.activeStorageSlotIds(), xsa.carryCursor);
                }
                shared.setXCarryForced(active);
                shared.setXCarryActive(active);
            }
        });

        if (usingContainer.get()) {
            runOnClientThread(mc, () -> {
                if (mc.player == null) return;
                net.minecraft.world.inventory.AbstractContainerMenu containerHandler = containerHandlerRef.get();
                if (containerHandler != null) {
                    mc.player.containerMenu = containerHandler;
                }
                autismclient.util.AutismContainerTarget containerTarget = containerTargetRef.get();
                if (containerTarget != null) {
                    XCarryAction.sendOpenTarget(mc, containerTarget);
                }
            });
            waitOneTick();
        }
        } finally {
            sharedState.setXCarryArmorBypass(prevBypass);
        }
    }

    private static void runXCarrySafeClickPutIn(Minecraft mc, XCarryAction xsa) throws InterruptedException {
        if (mc == null || mc.player == null || mc.gameMode == null) return;

        if (xsa.requiresCursorStorage()) {
            setCurrentStatus("XCarry Safe: Cursor");
            runOnClientThread(mc, () -> xsa.execute(mc));
            return;
        }

        autismclient.util.AutismSharedState sharedState = autismclient.util.AutismSharedState.get();
        boolean prevBypass = sharedState.isXCarryArmorBypass();
        sharedState.setXCarryArmorBypass(true);
        try {
        AtomicReference<net.minecraft.world.inventory.AbstractContainerMenu> containerHandlerRef = new AtomicReference<>();
        AtomicReference<autismclient.util.AutismContainerTarget> containerTargetRef = new AtomicReference<>();
        AtomicReference<List<Integer>> containerSlotIdsRef = new AtomicReference<>(List.of());
        AtomicBoolean usingContainer = new AtomicBoolean(false);

        runOnClientThread(mc, () -> {
            if (mc.player == null) return;
            net.minecraft.world.inventory.AbstractContainerMenu current = mc.player.containerMenu;
            if (current != null && current != mc.player.inventoryMenu) {
                usingContainer.set(true);
                containerHandlerRef.set(current);
                containerTargetRef.set(autismclient.util.AutismSharedState.get().getLastContainerTarget());
                containerSlotIdsRef.set(new ArrayList<>(xsa.collectContainerTransferSlots(current, mc.player.inventoryMenu)));
            }
        });

        if (usingContainer.get() && containerTargetRef.get() == null) {
            AutismClientMessaging.sendPrefixed("XCarry safe mode: missing container target, using fast mode.");
            runOnClientThread(mc, () -> xsa.execute(mc));
            return;
        }

        if (usingContainer.get()) {
            for (int slotId : containerSlotIdsRef.get()) {
                if (!isCurrentRunActive()) return;
                setCurrentStatus("XCarry Safe: collect");
                sendContainerClickThenTick(mc, containerHandlerRef.get(), slotId, 0, ContainerInput.QUICK_MOVE);
            }

            runOnClientThread(mc, () -> {
                if (mc.player == null || mc.getConnection() == null) return;
                net.minecraft.world.inventory.AbstractContainerMenu handler = containerHandlerRef.get();
                if (handler == null) return;
                mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(handler.containerId));
            });
            waitOneTick();

            runOnClientThread(mc, () -> {
                if (mc.player != null) {
                    mc.player.containerMenu = mc.player.inventoryMenu;
                }
            });
            waitOneTick();
        }

        runOnClientThread(mc, xsa::preparePutInStorageGuard);

        int safeDelayTicks = XCarryAction.clampSafeClickDelayTicks(xsa.safeClickDelayTicks);
        boolean delayAfterPickup = xsa.safeClickDelayAfterPickup;
        boolean delayBeforeReturn = xsa.safeClickDelayBeforeReturn;
        int attempts = 0;
        while (isCurrentRunActive() && attempts++ < 512) {
            List<XCarryAction.PutInMove> group = callOnClientThread(mc, () -> nextXCarrySafeGroup(mc, xsa), List.of());
            if (group.isEmpty()) break;

            XCarryAction.PutInMove first = group.get(0);
            int total = 0;
            for (XCarryAction.PutInMove move : group) total += Math.max(0, move.count());
            setCurrentStatus("XCarry Safe -> " + group.size() + " slots x" + total);

            if (!executeXCarrySafeGroup(mc, group, safeDelayTicks, delayAfterPickup, delayBeforeReturn)) {
                rollbackXCarryCursorSafe(mc, first.sourceSlotId(), safeDelayTicks, delayBeforeReturn);
                break;
            }
        }

        runOnClientThread(mc, () -> {
            if (mc.player != null) {
                boolean active = XCarryAction.hasStoredItems(mc.player.inventoryMenu, xsa.carryCursor, xsa.activeStorageSlotIds());
                autismclient.util.AutismSharedState shared = autismclient.util.AutismSharedState.get();
                if (active) {
                    shared.mergeXCarryForcedTargets(xsa.activeStorageSlotIds(), xsa.carryCursor);
                }
                shared.setXCarryForced(active);
                shared.setXCarryActive(active);
            }
        });

        if (usingContainer.get()) {
            runOnClientThread(mc, () -> {
                if (mc.player == null) return;
                net.minecraft.world.inventory.AbstractContainerMenu containerHandler = containerHandlerRef.get();
                if (containerHandler != null) {
                    mc.player.containerMenu = containerHandler;
                }
                autismclient.util.AutismContainerTarget containerTarget = containerTargetRef.get();
                if (containerTarget != null) {
                    XCarryAction.sendOpenTarget(mc, containerTarget);
                }
            });
            waitOneTick();
        }
        } finally {
            sharedState.setXCarryArmorBypass(prevBypass);
        }
    }

    private static List<XCarryAction.PutInMove> nextXCarrySafeGroup(Minecraft mc, XCarryAction xsa) {
        if (mc == null || mc.player == null || xsa == null) return List.of();
        InventoryMenu handler = mc.player.inventoryMenu;
        List<XCarryAction.PutInMove> planned = xsa.planPutInMoves(handler);
        if (planned.isEmpty()) return List.of();
        XCarryAction.PutInMove first = planned.get(0);
        if (first == null) return List.of();
        if (first.sourceSlotId() < 0 || first.sourceSlotId() >= handler.slots.size()) return List.of();
        ItemStack source = handler.slots.get(first.sourceSlotId()).getItem();
        if (source.isEmpty()) return List.of();
        ArrayList<XCarryAction.PutInMove> group = new ArrayList<>();
        int remainingSource = source.getCount();
        for (XCarryAction.PutInMove move : planned) {
            if (move == null || move.sourceSlotId() != first.sourceSlotId()) break;
            if (move.count() <= 0 || move.count() > remainingSource) break;
            if (!canSafelyPlaceXCarryMove(handler, source, move)) break;
            group.add(move);
            remainingSource -= move.count();
        }
        return group;
    }

    private static boolean executeXCarrySafeGroup(Minecraft mc, List<XCarryAction.PutInMove> group, int delayTicks,
                                                  boolean delayAfterPickup, boolean delayBeforeReturn) throws InterruptedException {
        if (mc == null || mc.player == null || mc.gameMode == null || group == null || group.isEmpty()) return false;
        SafeGroupSnapshot snapshot = callOnClientThread(mc, () -> snapshotXCarrySafeGroup(mc, group), SafeGroupSnapshot.invalid());
        if (!snapshot.valid) return false;

        SafeGroupSnapshot expected = executeXCarrySafeScript(mc, group, snapshot, delayTicks, delayAfterPickup, delayBeforeReturn);
        return expected.valid;
    }

    private static SafeGroupSnapshot executeXCarrySafeScript(Minecraft mc, List<XCarryAction.PutInMove> group,
                                                             SafeGroupSnapshot snapshot,
                                                             int delayTicks,
                                                             boolean delayAfterPickup,
                                                             boolean delayBeforeReturn) throws InterruptedException {
        if (mc == null || mc.player == null || mc.gameMode == null || group == null || group.isEmpty() || snapshot == null || !snapshot.valid) {
            return SafeGroupSnapshot.invalid();
        }
        XCarryAction.PutInMove first = group.get(0);
        if (!Boolean.TRUE.equals(callOnClientThread(mc, () -> {
            InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
            return handler != null
                    && handler.getCarried().isEmpty()
                    && xcarrySafeSnapshotStillMatches(handler, group, snapshot);
        }, Boolean.FALSE))) {
            return SafeGroupSnapshot.invalid();
        }

        SafeQuickCraftPlan quickCraftPlan = callOnClientThread(mc, () -> {
            InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
            return createXCarryQuickCraftPlan(handler, group, snapshot);
        }, SafeQuickCraftPlan.invalid());

        SafeGroupSnapshot expected = snapshot;
        if (quickCraftPlan.valid) {
            setCurrentStatus("XCarry Safe drag -> " + group.size() + " slots x" + quickCraftPlan.placeCount);
            int start = net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(0, quickCraftPlan.quickCraftType);
            int add = net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(1, quickCraftPlan.quickCraftType);
            int end = net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(2, quickCraftPlan.quickCraftType);
            int nextDragIndex = 0;
            if (delayAfterPickup) {
                if (!sendXCarrySafeInventoryClick(mc, first.sourceSlotId(), 0, ContainerInput.PICKUP)) {
                    return SafeGroupSnapshot.invalid();
                }
                waitXCarrySafeAfterPickupDelay(delayTicks, true);
                if (!Boolean.TRUE.equals(callOnClientThread(mc, () -> {
                    InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
                    return xcarrySafeCarryingNow(handler, snapshot.sourceStack, snapshot.sourceCount);
                }, Boolean.FALSE))) {
                    return SafeGroupSnapshot.invalid();
                }
                if (!sendXCarrySafeInventoryClick(mc, -999, start, ContainerInput.QUICK_CRAFT)) return SafeGroupSnapshot.invalid();
            } else {
                ArrayList<SafeInventoryClick> dragClicks = new ArrayList<>(group.size() + 3);
                dragClicks.add(new SafeInventoryClick(first.sourceSlotId(), 0, ContainerInput.PICKUP));
                dragClicks.add(new SafeInventoryClick(-999, start, ContainerInput.QUICK_CRAFT));
                for (XCarryAction.PutInMove move : group) {
                    if (move != null) {
                        dragClicks.add(new SafeInventoryClick(move.targetSlotId(), add, ContainerInput.QUICK_CRAFT));
                    }
                }
                dragClicks.add(new SafeInventoryClick(-999, end, ContainerInput.QUICK_CRAFT));
                if (!sendXCarrySafeInventoryClickBatch(mc, dragClicks.toArray(SafeInventoryClick[]::new))) {
                    return SafeGroupSnapshot.invalid();
                }
                nextDragIndex = group.size();
            }
            for (int i = nextDragIndex; i < group.size(); i++) {
                XCarryAction.PutInMove move = group.get(i);
                if (i > nextDragIndex || nextDragIndex > 0) waitXCarrySafeDelayTicks(delayTicks);
                if (!sendXCarrySafeInventoryClick(mc, move.targetSlotId(), add, ContainerInput.QUICK_CRAFT)) {
                    return SafeGroupSnapshot.invalid();
                }
            }
            if (nextDragIndex < group.size()
                    && !sendXCarrySafeInventoryClick(mc, -999, end, ContainerInput.QUICK_CRAFT)) {
                return SafeGroupSnapshot.invalid();
            }
            boolean firstResidual = true;
            for (java.util.Map.Entry<Integer, Integer> residual : quickCraftPlan.residualCounts.entrySet()) {
                int targetSlotId = residual.getKey();
                int count = residual.getValue() == null ? 0 : residual.getValue();
                if (count <= 0) continue;
                if (!firstResidual) waitXCarrySafeDelayTicks(delayTicks);
                firstResidual = false;
                if (!placeXCarrySafeDirectCount(mc, targetSlotId, count, delayTicks)) {
                    return SafeGroupSnapshot.invalid();
                }
            }
            expected = snapshot.withExpectedTargetCounts(quickCraftPlan.expectedTargetCounts);
        } else {
            int nextMoveIndex = 0;
            if (delayAfterPickup) {
                if (!sendXCarrySafeInventoryClick(mc, first.sourceSlotId(), 0, ContainerInput.PICKUP)) {
                    return SafeGroupSnapshot.invalid();
                }
                waitXCarrySafeAfterPickupDelay(delayTicks, true);
            } else {
                XCarryAction.PutInMove firstMove = group.get(0);
                int firstButton = firstMove.count() >= snapshot.sourceCount ? 0 : 1;
                if (!sendXCarrySafeInventoryClickBatch(mc,
                        new SafeInventoryClick(first.sourceSlotId(), 0, ContainerInput.PICKUP),
                        new SafeInventoryClick(firstMove.targetSlotId(), firstButton, ContainerInput.PICKUP))) {
                    return SafeGroupSnapshot.invalid();
                }
                int remainingFirst = firstButton == 0 ? 0 : Math.max(0, firstMove.count() - 1);
                if (remainingFirst > 0) {
                    waitXCarrySafeDelayTicks(delayTicks);
                    if (!placeXCarrySafeDirectCount(mc, firstMove.targetSlotId(), remainingFirst, delayTicks)) {
                        return SafeGroupSnapshot.invalid();
                    }
                }
                nextMoveIndex = 1;
            }
            for (int i = nextMoveIndex; i < group.size(); i++) {
                XCarryAction.PutInMove move = group.get(i);
                if (move == null || move.count() <= 0) continue;
                if (!placeXCarrySafeDirectCount(mc, move.targetSlotId(), move.count(), delayTicks)) {
                    return SafeGroupSnapshot.invalid();
                }
            }
        }

        boolean hasLeftover = Boolean.TRUE.equals(callOnClientThread(mc, () -> {
            InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
            return handler != null && !handler.getCarried().isEmpty();
        }, Boolean.FALSE));
        if (hasLeftover) {
            if (delayBeforeReturn) {
                waitXCarrySafeDelayTicks(delayTicks);
            }
            int returnSlot = callOnClientThread(mc, () -> findXCarrySafeReturnSlot(mc, first.sourceSlotId()), -1);
            if (returnSlot < 0) return SafeGroupSnapshot.invalid();
            if (!sendXCarrySafeInventoryClick(mc, returnSlot, 0, ContainerInput.PICKUP)) return SafeGroupSnapshot.invalid();
        }

        boolean cursorEmpty = Boolean.TRUE.equals(callOnClientThread(mc, () -> {
            InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
            return handler != null && handler.getCarried().isEmpty();
        }, Boolean.FALSE));
        return cursorEmpty ? expected : SafeGroupSnapshot.invalid();
    }

    private static boolean placeXCarrySafeDirectCount(Minecraft mc, int targetSlotId, int count, int delayTicks) throws InterruptedException {
        if (mc == null || count <= 0) return false;
        int remaining = count;
        while (remaining > 0 && isCurrentRunActive()) {
            int carriedCount = callOnClientThread(mc, () -> {
                InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
                return handler == null ? 0 : handler.getCarried().getCount();
            }, 0);
            if (carriedCount <= 0) return false;

            int button = remaining >= carriedCount ? 0 : 1;
            if (!sendXCarrySafeInventoryClick(mc, targetSlotId, button, ContainerInput.PICKUP)) {
                return false;
            }

            if (button == 0) {
                remaining = 0;
            } else {
                remaining--;
            }

            if (remaining > 0) {
                waitXCarrySafeDelayTicks(delayTicks);
            }
        }
        return remaining <= 0;
    }

    private static SafeGroupSnapshot snapshotXCarrySafeGroup(Minecraft mc, List<XCarryAction.PutInMove> group) {
        if (mc == null || mc.player == null || group == null || group.isEmpty()) return SafeGroupSnapshot.invalid();
        InventoryMenu handler = mc.player.inventoryMenu;
        if (handler == null || !handler.getCarried().isEmpty()) return SafeGroupSnapshot.invalid();
        XCarryAction.PutInMove first = group.get(0);
        if (first.sourceSlotId() < 0 || first.sourceSlotId() >= handler.slots.size()) return SafeGroupSnapshot.invalid();
        Slot sourceSlot = handler.slots.get(first.sourceSlotId());
        ItemStack source = sourceSlot.getItem();
        if (source.isEmpty()) return SafeGroupSnapshot.invalid();
        int total = 0;
        java.util.LinkedHashMap<Integer, Integer> startCounts = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<Integer, Integer> expectedCounts = new java.util.LinkedHashMap<>();
        for (XCarryAction.PutInMove move : group) {
            if (move == null || move.sourceSlotId() != first.sourceSlotId()) return SafeGroupSnapshot.invalid();
            if (!canSafelyPlaceXCarryMove(handler, source, move)) return SafeGroupSnapshot.invalid();
            int start = startCounts.computeIfAbsent(move.targetSlotId(), slotId -> safeSlotCount(handler, slotId));
            if (start < 0) return SafeGroupSnapshot.invalid();
            expectedCounts.put(move.targetSlotId(), expectedCounts.getOrDefault(move.targetSlotId(), start) + move.count());
            total += move.count();
        }
        if (total <= 0 || total > source.getCount()) return SafeGroupSnapshot.invalid();
        return new SafeGroupSnapshot(true, source.copy(), source.getCount(), startCounts, expectedCounts);
    }

    private static boolean canSafelyPlaceXCarryMove(InventoryMenu handler, ItemStack source, XCarryAction.PutInMove move) {
        if (handler == null || source == null || source.isEmpty() || move == null || move.count() <= 0) return false;
        if (move.targetSlotId() < 0 || move.targetSlotId() >= handler.slots.size()) return false;
        if (move.sourceSlotId() == move.targetSlotId()) return false;
        Slot targetSlot = handler.slots.get(move.targetSlotId());
        if (targetSlot == null || !targetSlot.mayPlace(source)) return false;
        ItemStack target = targetSlot.getItem();
        if (!target.isEmpty() && !ItemStack.isSameItemSameComponents(target, source)) return false;
        int current = target.isEmpty() ? 0 : target.getCount();
        int max = Math.min(source.getMaxStackSize(), targetSlot.getMaxStackSize(source));
        return current + move.count() <= max;
    }

    private static boolean xcarrySafeCarryingNow(InventoryMenu handler, ItemStack expected, int count) {
        if (handler == null || expected == null || expected.isEmpty()) return false;
        ItemStack carried = handler.getCarried();
        return !carried.isEmpty()
                && carried.getCount() == count
                && ItemStack.isSameItemSameComponents(carried, expected);
    }

    private static boolean xcarrySafeSnapshotStillMatches(InventoryMenu handler, List<XCarryAction.PutInMove> group, SafeGroupSnapshot snapshot) {
        if (handler == null || group == null || snapshot == null || !snapshot.valid) return false;
        XCarryAction.PutInMove first = group.isEmpty() ? null : group.get(0);
        if (first == null || first.sourceSlotId() < 0 || first.sourceSlotId() >= handler.slots.size()) return false;
        ItemStack source = handler.slots.get(first.sourceSlotId()).getItem();
        if (source.isEmpty()
                || source.getCount() != snapshot.sourceCount
                || !ItemStack.isSameItemSameComponents(source, snapshot.sourceStack)) {
            return false;
        }
        for (XCarryAction.PutInMove move : group) {
            if (move == null || move.sourceSlotId() != first.sourceSlotId()) return false;
            int current = safeSlotCount(handler, move.targetSlotId());
            Integer expectedStart = snapshot.startTargetCounts.get(move.targetSlotId());
            if (expectedStart == null || current != expectedStart) return false;
            if (!canSafelyPlaceXCarryMove(handler, source, move)) return false;
        }
        return true;
    }

    private static SafeQuickCraftPlan createXCarryQuickCraftPlan(InventoryMenu handler, List<XCarryAction.PutInMove> group, SafeGroupSnapshot snapshot) {
        if (handler == null || group == null || group.size() < 2 || snapshot == null || !snapshot.valid) {
            return SafeQuickCraftPlan.invalid();
        }

        java.util.LinkedHashMap<Integer, Integer> desiredAdds = new java.util.LinkedHashMap<>();
        for (XCarryAction.PutInMove move : group) {
            if (move == null || move.count() <= 0) return SafeQuickCraftPlan.invalid();
            if (move.targetSlotId() < 0 || move.targetSlotId() >= handler.slots.size()) return SafeQuickCraftPlan.invalid();
            Slot targetSlot = handler.slots.get(move.targetSlotId());
            if (!isXCarryQuickCraftTarget(handler, targetSlot, snapshot.sourceStack)) return SafeQuickCraftPlan.invalid();
            int start = snapshot.startTargetCounts.getOrDefault(move.targetSlotId(), safeSlotCount(handler, move.targetSlotId()));
            int finalForMove = snapshot.expectedTargetCounts.getOrDefault(move.targetSlotId(), start);
            int desiredAdd = finalForMove - start;
            if (desiredAdd <= 0) return SafeQuickCraftPlan.invalid();
            int max = Math.min(snapshot.sourceStack.getMaxStackSize(), targetSlot.getMaxStackSize(snapshot.sourceStack));
            if (start + desiredAdd > max) return SafeQuickCraftPlan.invalid();
            desiredAdds.put(move.targetSlotId(), desiredAdd);
        }

        SafeQuickCraftPlan best = SafeQuickCraftPlan.invalid();
        SafeQuickCraftPlan even = createXCarryQuickCraftCandidate(handler, snapshot, desiredAdds, 0);
        SafeQuickCraftPlan rightDrag = createXCarryQuickCraftCandidate(handler, snapshot, desiredAdds, 1);
        if (even.valid) best = even;
        if (rightDrag.valid && (!best.valid || rightDrag.cost < best.cost)) best = rightDrag;
        return best;
    }

    private static SafeQuickCraftPlan createXCarryQuickCraftCandidate(InventoryMenu handler, SafeGroupSnapshot snapshot,
                                                                     java.util.LinkedHashMap<Integer, Integer> desiredAdds,
                                                                     int quickCraftType) {
        if (handler == null || snapshot == null || !snapshot.valid || desiredAdds == null || desiredAdds.size() < 2) {
            return SafeQuickCraftPlan.invalid();
        }

        int slotCount = desiredAdds.size();
        int placeCount = switch (quickCraftType) {
            case 0 -> snapshot.sourceCount / slotCount;
            case 1 -> 1;
            default -> 0;
        };
        if (placeCount <= 0 || snapshot.sourceCount < placeCount * slotCount) {
            return SafeQuickCraftPlan.invalid();
        }

        java.util.LinkedHashMap<Integer, Integer> residual = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<Integer, Integer> expected = new java.util.LinkedHashMap<>();
        int residualTotal = 0;

        for (java.util.Map.Entry<Integer, Integer> entry : desiredAdds.entrySet()) {
            int targetSlotId = entry.getKey();
            int desiredAdd = entry.getValue();
            if (desiredAdd < placeCount) return SafeQuickCraftPlan.invalid();
            Slot targetSlot = targetSlotId >= 0 && targetSlotId < handler.slots.size() ? handler.slots.get(targetSlotId) : null;
            if (!isXCarryQuickCraftTarget(handler, targetSlot, snapshot.sourceStack)) return SafeQuickCraftPlan.invalid();

            int start = snapshot.startTargetCounts.getOrDefault(targetSlotId, safeSlotCount(handler, targetSlotId));
            int max = Math.min(snapshot.sourceStack.getMaxStackSize(), targetSlot.getMaxStackSize(snapshot.sourceStack));
            if (start + placeCount > max) return SafeQuickCraftPlan.invalid();

            int leftover = desiredAdd - placeCount;
            if (leftover > 0) {
                residual.put(targetSlotId, leftover);
                residualTotal += leftover;
            }
            expected.put(targetSlotId, start + desiredAdd);
        }

        int carriedAfterQuickCraft = snapshot.sourceCount - placeCount * slotCount;
        if (residualTotal > carriedAfterQuickCraft) return SafeQuickCraftPlan.invalid();
        int cost = slotCount + 2 + residualTotal;
        return new SafeQuickCraftPlan(true, quickCraftType, placeCount, cost, residual, expected);
    }

    private static boolean isXCarryQuickCraftTarget(InventoryMenu handler, Slot targetSlot, ItemStack sourceStack) {
        if (handler == null || targetSlot == null || sourceStack == null || sourceStack.isEmpty()) return false;
        if (targetSlot.index == XCarryAction.CRAFT_RESULT_SLOT) return false;
        if (!targetSlot.mayPlace(sourceStack) || !handler.canDragTo(targetSlot)) return false;
        return net.minecraft.world.inventory.AbstractContainerMenu.canItemQuickReplace(targetSlot, sourceStack, true);
    }

    private record SafeInventoryClick(int slotId, int button, ContainerInput input) {}

    private static boolean sendXCarrySafeInventoryClick(Minecraft mc, int slotId, int button, ContainerInput input) throws InterruptedException {
        return sendXCarrySafeInventoryClickBatch(mc, new SafeInventoryClick(slotId, button, input));
    }

    private static boolean sendXCarrySafeInventoryClickBatch(Minecraft mc, SafeInventoryClick... clicks) throws InterruptedException {
        if (mc == null || clicks == null || clicks.length == 0) return false;
        return Boolean.TRUE.equals(callOnClientThread(mc, () -> {
            if (mc.player == null || mc.gameMode == null) return false;
            InventoryMenu handler = mc.player.inventoryMenu;
            if (handler == null) return false;
            mc.player.containerMenu = handler;
            for (SafeInventoryClick click : clicks) {
                if (click == null || click.input == null) return false;
                if (click.slotId != -999 && (click.slotId < 0 || click.slotId >= handler.slots.size())) return false;
                mc.gameMode.handleContainerInput(handler.containerId, click.slotId, click.button, click.input, mc.player);
            }
            return true;
        }, Boolean.FALSE));
    }

    private static void waitXCarrySafeDelayTicks(int delayTicks) {
        int ticks = XCarryAction.clampSafeClickDelayTicks(delayTicks);
        for (int i = 0; i < ticks && isCurrentRunActive(); i++) {
            waitOneTick();
        }
    }

    private static void waitXCarrySafeAfterPickupDelay(int delayTicks, boolean enabled) {
        if (enabled) {
            waitXCarrySafeDelayTicks(delayTicks);
        }
    }

    private static boolean sendInventoryClickThenTick(Minecraft mc, int slotId, int button, ContainerInput input) throws InterruptedException {
        if (mc == null || mc.player == null || mc.gameMode == null) return false;
        boolean clicked = callOnClientThread(mc, () -> {
            if (mc.player == null || mc.gameMode == null) return false;
            InventoryMenu handler = mc.player.inventoryMenu;
            mc.player.containerMenu = handler;
            if (slotId < 0 || slotId >= handler.slots.size()) return false;
            mc.gameMode.handleContainerInput(handler.containerId, slotId, button, input, mc.player);
            return true;
        }, Boolean.FALSE);
        if (!clicked) return false;
        waitOneTick();
        return true;
    }

    private static boolean sendContainerClickThenTick(Minecraft mc, net.minecraft.world.inventory.AbstractContainerMenu handler,
                                                      int slotId, int button, ContainerInput input) throws InterruptedException {
        if (mc == null || handler == null || input == null) return false;
        boolean clicked = callOnClientThread(mc, () -> {
            if (mc.player == null || mc.gameMode == null || mc.player.containerMenu != handler) return false;
            if (slotId < 0 || slotId >= handler.slots.size()) return false;
            if (handler.slots.get(slotId).getItem().isEmpty()) return false;
            mc.gameMode.handleContainerInput(handler.containerId, slotId, button, input, mc.player);
            return true;
        }, Boolean.FALSE);
        if (!clicked) return false;
        waitOneTick();
        return true;
    }

    private static boolean waitForXCarrySafeLocal(Minecraft mc, int ticks, java.util.function.BooleanSupplier accepted) throws InterruptedException {
        int maxTicks = Math.max(1, ticks);
        for (int i = 0; i < maxTicks && isCurrentRunActive(); i++) {
            if (Boolean.TRUE.equals(callOnClientThread(mc, () -> accepted != null && accepted.getAsBoolean(), Boolean.FALSE))) return true;
            waitOneTick();
        }
        return Boolean.TRUE.equals(callOnClientThread(mc, () -> accepted != null && accepted.getAsBoolean(), Boolean.FALSE));
    }

    private static void waitForXCarrySafeSettle(Minecraft mc, long startRevision, int minTicks, long maxMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(50L, maxMs));
        int ticks = Math.max(1, minTicks);
        for (int i = 0; i < ticks && isCurrentRunActive(); i++) waitOneTick();
        while (isCurrentRunActive() && System.nanoTime() < deadline) {
            long revision = MacroConditionRegistry.inventorySyncRevision();
            if (revision != startRevision) {
                waitOneTick();
                return;
            }
            waitOneTick();
        }
    }

    private static boolean waitForXCarrySafeFinalState(Minecraft mc, SafeGroupSnapshot snapshot, long startRevision) throws InterruptedException {
        waitForXCarrySafeSettle(mc, startRevision, 2, 250L);
        if (Boolean.TRUE.equals(callOnClientThread(mc, () -> xcarrySafeFinalMatches(mc, snapshot), Boolean.FALSE))) return true;
        waitForXCarrySafeSettle(mc, MacroConditionRegistry.inventorySyncRevision(), 3, 350L);
        return Boolean.TRUE.equals(callOnClientThread(mc, () -> xcarrySafeFinalMatches(mc, snapshot), Boolean.FALSE));
    }

    private static boolean xcarrySafeFinalMatches(Minecraft mc, SafeGroupSnapshot snapshot) {
        if (mc == null || mc.player == null || snapshot == null || !snapshot.valid) return false;
        InventoryMenu handler = mc.player.inventoryMenu;
        if (handler == null || !handler.getCarried().isEmpty()) return false;
        for (java.util.Map.Entry<Integer, Integer> entry : snapshot.expectedTargetCounts.entrySet()) {
            int slotId = entry.getKey();
            if (slotId < 0 || slotId >= handler.slots.size()) return false;
            ItemStack stack = handler.slots.get(slotId).getItem();
            if (entry.getValue() <= 0) {
                if (!stack.isEmpty()) return false;
                continue;
            }
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, snapshot.sourceStack)) return false;
            if (stack.getCount() != entry.getValue()) return false;
        }
        return true;
    }

    private static boolean returnXCarrySafeLeftovers(Minecraft mc, int preferredSlotId, ItemStack expected,
                                                     int delayTicks, boolean delayBeforeReturn) throws InterruptedException {
        if (mc == null || mc.player == null || expected == null || expected.isEmpty()) return false;
        if (callOnClientThread(mc, () -> mc.player != null && mc.player.inventoryMenu.getCarried().isEmpty(), Boolean.FALSE)) {
            return true;
        }

        int returnSlot = callOnClientThread(mc, () -> findXCarrySafeReturnSlot(mc, preferredSlotId), -1);
        if (returnSlot < 0) return false;
        if (delayBeforeReturn) waitXCarrySafeDelayTicks(delayTicks);
        return sendInventoryClickThenTick(mc, returnSlot, 0, ContainerInput.PICKUP)
                && waitForXCarrySafeLocal(mc, 3, () -> mc.player != null && mc.player.inventoryMenu.getCarried().isEmpty());
    }

    private static int findXCarrySafeReturnSlot(Minecraft mc, int preferredSlotId) {
        if (mc == null || mc.player == null) return -1;
        InventoryMenu handler = mc.player.inventoryMenu;
        ItemStack carried = handler.getCarried();
        if (carried.isEmpty()) return -1;
        if (canAcceptXCarryLeftover(handler, preferredSlotId, carried)) return preferredSlotId;
        for (Slot slot : handler.slots) {
            if (slot == null) continue;
            if (slot.index < 9 || slot.index == 45) continue;
            if (canAcceptXCarryLeftover(handler, slot.index, carried)) return slot.index;
        }
        return -1;
    }

    private static boolean canAcceptXCarryLeftover(InventoryMenu handler, int slotId, ItemStack carried) {
        if (handler == null || carried == null || carried.isEmpty()) return false;
        if (slotId < 0 || slotId >= handler.slots.size()) return false;
        Slot slot = handler.slots.get(slotId);
        if (slot == null || !slot.mayPlace(carried)) return false;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return carried.getCount() <= slot.getMaxStackSize(carried);
        return ItemStack.isSameItemSameComponents(stack, carried)
                && stack.getCount() + carried.getCount() <= Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried));
    }

    private static int safeSlotCount(Minecraft mc, int slotId) {
        if (mc == null || mc.player == null) return -1;
        return safeSlotCount(mc.player.inventoryMenu, slotId);
    }

    private static int safeSlotCount(InventoryMenu handler, int slotId) {
        if (handler == null) return -1;
        if (slotId < 0 || slotId >= handler.slots.size()) return -1;
        ItemStack stack = handler.slots.get(slotId).getItem();
        return stack.isEmpty() ? 0 : stack.getCount();
    }

    private static void rollbackXCarryCursorSafe(Minecraft mc, int sourceSlotId, int delayTicks, boolean delayBeforeReturn) throws InterruptedException {
        if (mc == null || mc.player == null || mc.gameMode == null) return;
        if (callOnClientThread(mc, () -> mc.player != null && mc.player.inventoryMenu.getCarried().isEmpty(), Boolean.FALSE)) return;
        int returnSlot = callOnClientThread(mc, () -> findXCarrySafeReturnSlot(mc, sourceSlotId), -1);
        if (returnSlot >= 0) {
            if (delayBeforeReturn) waitXCarrySafeDelayTicks(delayTicks);
            sendInventoryClickThenTick(mc, returnSlot, 0, ContainerInput.PICKUP);
            waitForXCarrySafeLocal(mc, 3, () -> mc.player != null && mc.player.inventoryMenu.getCarried().isEmpty());
        } else {
            rollbackXCarryCursor(mc, sourceSlotId);
        }
    }

    private static final class SafeGroupSnapshot {
        final boolean valid;
        final ItemStack sourceStack;
        final int sourceCount;
        final java.util.Map<Integer, Integer> startTargetCounts;
        final java.util.Map<Integer, Integer> expectedTargetCounts;

        SafeGroupSnapshot(boolean valid, ItemStack sourceStack, int sourceCount,
                          java.util.Map<Integer, Integer> startTargetCounts,
                          java.util.Map<Integer, Integer> expectedTargetCounts) {
            this.valid = valid;
            this.sourceStack = sourceStack == null ? ItemStack.EMPTY : sourceStack;
            this.sourceCount = sourceCount;
            this.startTargetCounts = startTargetCounts == null ? java.util.Map.of() : java.util.Map.copyOf(startTargetCounts);
            this.expectedTargetCounts = expectedTargetCounts == null ? java.util.Map.of() : java.util.Map.copyOf(expectedTargetCounts);
        }

        SafeGroupSnapshot withExpectedTargetCounts(java.util.Map<Integer, Integer> expectedTargetCounts) {
            return new SafeGroupSnapshot(valid, sourceStack, sourceCount, startTargetCounts, expectedTargetCounts);
        }

        static SafeGroupSnapshot invalid() {
            return new SafeGroupSnapshot(false, ItemStack.EMPTY, 0, java.util.Map.of(), java.util.Map.of());
        }
    }

    private static final class SafeQuickCraftPlan {
        final boolean valid;
        final int quickCraftType;
        final int placeCount;
        final int cost;
        final java.util.Map<Integer, Integer> residualCounts;
        final java.util.Map<Integer, Integer> expectedTargetCounts;

        SafeQuickCraftPlan(boolean valid, int quickCraftType, int placeCount, int cost,
                           java.util.Map<Integer, Integer> residualCounts,
                           java.util.Map<Integer, Integer> expectedTargetCounts) {
            this.valid = valid;
            this.quickCraftType = quickCraftType;
            this.placeCount = placeCount;
            this.cost = cost;
            this.residualCounts = residualCounts == null
                    ? java.util.Map.of()
                    : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(residualCounts));
            this.expectedTargetCounts = expectedTargetCounts == null
                    ? java.util.Map.of()
                    : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(expectedTargetCounts));
        }

        static SafeQuickCraftPlan invalid() {
            return new SafeQuickCraftPlan(false, 0, 0, Integer.MAX_VALUE, java.util.Map.of(), java.util.Map.of());
        }
    }

    private static void rollbackXCarryCursor(Minecraft mc, int sourceSlotId) throws InterruptedException {
        runOnClientThread(mc, () -> {
            if (mc.player == null || mc.gameMode == null) return;
            net.minecraft.world.inventory.InventoryMenu handler = mc.player.inventoryMenu;
            mc.player.containerMenu = handler;
            if (handler.getCarried().isEmpty()) return;
            if (sourceSlotId < 0 || sourceSlotId >= handler.slots.size()) return;
            mc.gameMode.handleContainerInput(handler.containerId, sourceSlotId, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
        });
        waitForXCarryCondition(mc, 300L, () -> mc.player != null && mc.player.inventoryMenu.getCarried().isEmpty());
    }

    private static boolean waitForXCarryCondition(Minecraft mc, long timeoutMs, java.util.function.Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (isCurrentRunActive()) {
            if (Boolean.TRUE.equals(callOnClientThread(mc, condition, Boolean.FALSE))) {
                return true;
            }
            if (System.nanoTime() >= deadline) break;
            waitOneTick();
        }
        return Boolean.TRUE.equals(callOnClientThread(mc, condition, Boolean.FALSE));
    }

    static void waitOneTick() {
        CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
        waitForCondition(tickFuture);
    }

    private static void runInstaBreakAction(Minecraft mc, InstaBreakAction action) throws InterruptedException {
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null || mc.gameMode == null) {
            AutismClientMessaging.sendPrefixed("\u00a7cInstaBreak: missing world or connection");
            return;
        }

        BlockPos target = action.blockPos == null ? BlockPos.ZERO : action.blockPos;
        Direction direction = action.direction == null ? Direction.UP : action.direction;
        int delayTicks = Math.max(0, action.delayTicks);
        int targetTimes = Math.max(0, action.times);
        int completed = 0;
        boolean wasSolid = !isInstaBreakAir(mc, target);

        if (!ensureInstaBreakPickaxe(mc, action)) return;

        AutismInstaBreakRenderer.setTarget(target);
        try {
            long lastAttemptTick = AutismSharedState.get().getClientTickCounter() - (delayTicks + 1L);
            long lastSneakTick = Long.MIN_VALUE;
            while (isCurrentRunActive() && isActionStillInCurrentMacro(action)) {

                if (action.sneak) {
                    long sneakTick = AutismSharedState.get().getClientTickCounter();
                    if (sneakTick != lastSneakTick) {
                        lastSneakTick = sneakTick;
                        final String sneakMode = action.sneakMode;
                        runOnClientThread(mc, () -> holdMacroSneak(mc, sneakMode), action.isTickAligned());
                    }
                }

                boolean isAir = isInstaBreakAir(mc, target);
                if (wasSolid && isAir) {
                    completed++;
                    setCurrentStatus("InstaBreak " + completed + "/" + (targetTimes == 0 ? "\u221e" : targetTimes));
                    if (targetTimes > 0 && completed >= targetTimes) return;
                    wasSolid = false;
                } else if (!isAir) {
                    wasSolid = true;
                }

                if (!isAir) {
                    long currentTick = AutismSharedState.get().getClientTickCounter();
                    if (currentTick != lastAttemptTick && (currentTick - lastAttemptTick) >= delayTicks) {
                        lastAttemptTick = currentTick;
                        if (!ensureInstaBreakPickaxe(mc, action)) return;
                        if (action.interact) {

                            InteractTiming t = action.interactTiming;
                            boolean interactFirst = t == InteractTiming.BEFORE || t == InteractTiming.WITH
                                || (t == InteractTiming.CUSTOM && action.interactCustomMs < 0);
                            long ns = t == InteractTiming.CUSTOM
                                ? Math.abs((long) action.interactCustomMs) * 1_000_000L : 0L;
                            if (interactFirst) {
                                runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction), action.isTickAligned());
                                if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
                                sendInstaBreakAttempt(mc, target, direction, action.isTickAligned());
                            } else if (t == InteractTiming.AFTER_PLUS) {
                                sendInstaBreakAttempt(mc, target, direction, action.isTickAligned());
                                waitOneTick();
                                runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction), action.isTickAligned());
                            } else {
                                sendInstaBreakAttempt(mc, target, direction, action.isTickAligned());
                                if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
                                runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction), action.isTickAligned());
                            }
                        } else {
                            sendInstaBreakAttempt(mc, target, direction, action.isTickAligned());
                        }
                    }
                }

                setCurrentStatus("InstaBreak " + target.getX() + "," + target.getY() + "," + target.getZ()
                    + " " + completed + "/" + (targetTimes == 0 ? "\u221e" : targetTimes));

                Thread.sleep(1);
            }
        } finally {
            if (action.sneak) {
                final String sneakMode = action.sneakMode;
                try {
                    runOnClientThread(mc, () -> releaseMacroSneak(mc, sneakMode));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            AutismInstaBreakRenderer.clearTarget(target);
        }
    }

    private static boolean isInstaBreakAir(Minecraft mc, BlockPos pos) {

        return mc.level == null || mc.level.isOutsideBuildHeight(pos) || mc.level.getBlockState(pos).isAir();
    }

    private static boolean isActionStillInCurrentMacro(MacroAction action) {
        RunState run = CURRENT_RUN.get();
        if (run == null || run.macro == null || run.macro.actions == null) return true;
        return run.macro.actions.contains(action);
    }

    private static boolean ensureInstaBreakPickaxe(Minecraft mc, InstaBreakAction action) throws InterruptedException {

        return callOnClientThread(mc, () -> {
            if (mc.player == null) return false;
            if (mc.player.getMainHandItem().is(ItemTags.PICKAXES)) return true;
            if (!action.autoPickaxe) return false;

            int preferred = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
            for (int slot = 0; slot < 9; slot++) {
                if (mc.player.getInventory().getItem(slot).is(ItemTags.PICKAXES)) {
                    AutismInventoryHelper.selectHotbarSlot(mc, slot);
                    return true;
                }
            }

            for (int slot = 9; slot < 36; slot++) {
                if (mc.player.getInventory().getItem(slot).is(ItemTags.PICKAXES)) {
                    if (!AutismInventoryHelper.swapInventorySlots(mc, slot, preferred)) return false;
                    AutismInventoryHelper.selectHotbarSlot(mc, preferred);
                    return true;
                }
            }

            return false;
        }, false, action.isTickAligned());
    }

    private static void sendInstaBreakAttempt(Minecraft mc, BlockPos pos, Direction direction, boolean tickAligned) throws InterruptedException {
        runOnClientThread(mc, () -> {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) return;
            BlockPos blockPos = pos.immutable();
            Direction dir = direction == null ? Direction.UP : direction;

            if (mc.gameMode instanceof AutismMultiPlayerGameModeAccessor accessor) {
                accessor.autism$startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos, dir, sequence));
                accessor.autism$startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, blockPos, dir, sequence));
            } else {

                mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos, dir));
                mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, blockPos, dir));
            }

            mc.player.swing(InteractionHand.MAIN_HAND);
        }, tickAligned);
    }

    private static void runBreakAction(Minecraft mc, BreakAction action) throws InterruptedException {
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null || mc.gameMode == null) {
            AutismClientMessaging.sendPrefixed("\u00a7cBreak: missing world or connection");
            return;
        }

        final BlockPos target = action.blockPos == null ? BlockPos.ZERO : action.blockPos;
        final Direction direction = action.direction == null ? Direction.UP : action.direction;
        int startDelay = Math.max(0, action.delayTicks);
        int targetTimes = Math.max(0, action.times);
        int completed = 0;

        if (startDelay > 0) waitBreakClientTicks(startDelay);

        final boolean[] started = { false };
        final boolean useNextSteps = action.interact && action.runNextSteps;
        final boolean[] nextStepsFired = { false };
        final boolean[] missingNextStepsWarned = { false };
        AutismInstaBreakRenderer.setTarget(target);
        long lastTick = -1L;
        try {
            while (isCurrentRunActive() && isActionStillInCurrentMacro(action)) {
                long now = AutismSharedState.get().getClientTickCounter();
                if (now == lastTick) {
                    Thread.sleep(1);
                    continue;
                }
                lastTick = now;

                int result;
                if (!started[0]) {
                    result = useNextSteps
                        ? startBreakWithNextSteps(mc, action, target, direction, started, nextStepsFired, missingNextStepsWarned)
                        : callOnClientThread(mc, () -> startBreakOnClient(mc, action, target, direction, started), -1, action.isTickAligned());
                } else if (action.interact && action.interactTiming == InteractTiming.CUSTOM) {
                    result = useNextSteps
                        ? breakCustomTickWithNextSteps(mc, action, target, direction, nextStepsFired, missingNextStepsWarned)
                        : breakCustomTick(mc, action, target, direction);
                } else {
                    result = useNextSteps
                        ? breakContinueWithNextSteps(mc, action, target, direction, nextStepsFired, missingNextStepsWarned)
                        : callOnClientThread(mc, () -> breakContinueOnClient(mc, action, target, direction), -1, action.isTickAligned());
                }

                if (result < 0) {
                    return;
                } else if (result == 1) {
                    boolean completedStartedBreak = started[0];
                    if (useNextSteps && completedStartedBreak) {
                        runBreakNextStepsAfterCompletion(mc, action, nextStepsFired, missingNextStepsWarned);
                    } else if (action.interact && action.interactTiming == InteractTiming.AFTER_PLUS) {
                        waitOneTick();
                        runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction), action.isTickAligned());
                    }
                    completed++;
                    setCurrentStatus("Break " + completed + "/" + (targetTimes == 0 ? "\u221e" : targetTimes));
                    if (targetTimes > 0 && completed >= targetTimes) return;
                    started[0] = false;
                    nextStepsFired[0] = false;
                    waitForBreakSolid(mc, target);
                } else {
                    setCurrentStatus("Break " + target.getX() + "," + target.getY() + "," + target.getZ()
                        + " " + completed + "/" + (targetTimes == 0 ? "\u221e" : targetTimes));
                }
            }
        } finally {
            final boolean wasMining = started[0];
            runOnClientThread(mc, () -> {
                if (wasMining && mc.gameMode != null && mc.gameMode.isDestroying()) mc.gameMode.stopDestroyBlock();
                if (action.sneak) releaseMacroSneak(mc, action.sneakMode);
            }, action.isTickAligned());
            AutismInstaBreakRenderer.clearTarget(target);
        }
    }

    private static int startBreakWithNextSteps(Minecraft mc, BreakAction action, BlockPos pos, Direction dir,
                                               boolean[] started, boolean[] nextStepsFired,
                                               boolean[] missingNextStepsWarned) throws InterruptedException {
        BreakStartPlan plan = callOnClientThread(mc, () -> prepareBreakStartOnClient(mc, action, pos), new BreakStartPlan(-1, false, false));
        if (plan.result() != 0) return plan.result();

        if (plan.instant() && plan.interactFirst()) {
            runBreakNextStepsOnce(mc, action, nextStepsFired, missingNextStepsWarned);
            if (!isCurrentRunActive()) return -1;
        }

        int result = callOnClientThread(mc, () -> startPreparedBreakOnClient(mc, action, pos, dir, started), -1, action.isTickAligned());
        return result;
    }

    private static BreakStartPlan prepareBreakStartOnClient(Minecraft mc, BreakAction action, BlockPos pos) {
        if (mc.level == null || mc.gameMode == null || mc.player == null || mc.getConnection() == null) {
            return new BreakStartPlan(-1, false, false);
        }
        if (mc.level.isOutsideBuildHeight(pos)) return new BreakStartPlan(1, false, false);
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return new BreakStartPlan(1, false, false);

        if (action.sneak) holdMacroSneak(mc, action.sneakMode);
        if (action.autoTool) {
            AutismAutoTool.equipBestTool(mc, state, action.considerInventory, true);
            state = mc.level.getBlockState(pos);
            if (state.isAir()) return new BreakStartPlan(1, false, false);
        }

        float startDelta = state.getDestroyProgress(mc.player, mc.player.level(), pos);
        boolean instant = startDelta >= 1.0f;
        boolean interactFirst = action.interact && (action.interactTiming == InteractTiming.BEFORE
            || action.interactTiming == InteractTiming.WITH
            || (action.interactTiming == InteractTiming.CUSTOM && action.interactCustomMs < 0));
        return new BreakStartPlan(0, instant, interactFirst);
    }

    private static int startPreparedBreakOnClient(Minecraft mc, BreakAction action, BlockPos pos, Direction dir, boolean[] started) {
        if (mc.level == null || mc.gameMode == null || mc.player == null || mc.getConnection() == null) return -1;
        if (mc.level.isOutsideBuildHeight(pos) || mc.level.getBlockState(pos).isAir()) return 1;
        if (action.sneak) holdMacroSneak(mc, action.sneakMode);
        mc.gameMode.startDestroyBlock(pos, dir);
        mc.player.swing(InteractionHand.MAIN_HAND);
        started[0] = true;
        return mc.level.getBlockState(pos).isAir() ? 1 : 0;
    }

    private static int startBreakOnClient(Minecraft mc, BreakAction action, BlockPos pos, Direction dir, boolean[] started) {
        if (mc.level == null || mc.gameMode == null || mc.player == null || mc.getConnection() == null) return -1;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || mc.level.isOutsideBuildHeight(pos)) return 1;

        if (action.sneak) holdMacroSneak(mc, action.sneakMode);
        if (action.autoTool) {
            AutismAutoTool.equipBestTool(mc, state, action.considerInventory, true);
            state = mc.level.getBlockState(pos);
        }

        float startDelta = state.getDestroyProgress(mc.player, mc.player.level(), pos);
        boolean instant = startDelta >= 1.0f;
        boolean interactFirst = action.interact && (action.interactTiming == InteractTiming.BEFORE
            || action.interactTiming == InteractTiming.WITH
            || (action.interactTiming == InteractTiming.CUSTOM && action.interactCustomMs < 0));
        if (instant && interactFirst) sendBlockInteract(mc, pos, dir);
        mc.gameMode.startDestroyBlock(pos, dir);
        mc.player.swing(InteractionHand.MAIN_HAND);
        started[0] = true;
        if (instant && action.interact && !interactFirst && action.interactTiming != InteractTiming.AFTER_PLUS) sendBlockInteract(mc, pos, dir);
        return mc.level.getBlockState(pos).isAir() ? 1 : 0;
    }

    private static int breakContinueWithNextSteps(Minecraft mc, BreakAction action, BlockPos pos, Direction dir,
                                                  boolean[] nextStepsFired,
                                                  boolean[] missingNextStepsWarned) throws InterruptedException {
        BreakContinuePlan plan = callOnClientThread(mc, () -> inspectBreakContinueOnClient(mc, action, pos), new BreakContinuePlan(-1, false, false));
        if (plan.result() != 0) return plan.result();

        InteractTiming timing = action.interactTiming;
        if (timing == InteractTiming.BEFORE && plan.breaksNextTick()) {
            runBreakNextStepsOnce(mc, action, nextStepsFired, missingNextStepsWarned);
            if (!isCurrentRunActive()) return -1;
        }
        if (timing == InteractTiming.WITH && plan.breaksThisTick()) {
            runBreakNextStepsOnce(mc, action, nextStepsFired, missingNextStepsWarned);
            if (!isCurrentRunActive()) return -1;
        }

        int result = callOnClientThread(mc, () -> continueBreakOnClient(mc, action, pos, dir), -1, action.isTickAligned());
        return result;
    }

    private static BreakContinuePlan inspectBreakContinueOnClient(Minecraft mc, BreakAction action, BlockPos pos) {
        if (mc.level == null || mc.gameMode == null || mc.player == null || mc.getConnection() == null) {
            return new BreakContinuePlan(-1, false, false);
        }
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return new BreakContinuePlan(1, false, false);
        if (action.sneak) holdMacroSneak(mc, action.sneakMode);

        boolean breaksThisTick = willBreakThisTick(mc, pos, state);
        boolean breaksNextTick = !breaksThisTick && willBreakNextTick(mc, pos, state);
        return new BreakContinuePlan(0, breaksThisTick, breaksNextTick);
    }

    private static int continueBreakOnClient(Minecraft mc, BreakAction action, BlockPos pos, Direction dir) {
        if (mc.level == null || mc.gameMode == null || mc.player == null || mc.getConnection() == null) return -1;
        if (mc.level.getBlockState(pos).isAir()) return 1;
        if (action.sneak) holdMacroSneak(mc, action.sneakMode);
        mc.gameMode.continueDestroyBlock(pos, dir);
        mc.player.swing(InteractionHand.MAIN_HAND);
        return mc.level.getBlockState(pos).isAir() ? 1 : 0;
    }

    private static int breakContinueOnClient(Minecraft mc, BreakAction action, BlockPos pos, Direction dir) {
        if (mc.level == null || mc.gameMode == null || mc.player == null || mc.getConnection() == null) return -1;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return 1;
        if (action.sneak) holdMacroSneak(mc, action.sneakMode);

        boolean breaksThisTick = willBreakThisTick(mc, pos, state);
        boolean breaksNextTick = !breaksThisTick && willBreakNextTick(mc, pos, state);
        InteractTiming t = action.interactTiming;
        if (action.interact && t == InteractTiming.BEFORE && breaksNextTick) sendBlockInteract(mc, pos, dir);
        if (action.interact && t == InteractTiming.WITH && breaksThisTick) sendBlockInteract(mc, pos, dir);

        mc.gameMode.continueDestroyBlock(pos, dir);
        mc.player.swing(InteractionHand.MAIN_HAND);

        if (action.interact && t == InteractTiming.AFTER && breaksThisTick) sendBlockInteract(mc, pos, dir);
        return mc.level.getBlockState(pos).isAir() ? 1 : 0;
    }

    private static int breakCustomTickWithNextSteps(Minecraft mc, BreakAction action, BlockPos pos, Direction dir,
                                                    boolean[] nextStepsFired,
                                                    boolean[] missingNextStepsWarned) throws InterruptedException {
        boolean willBreak = Boolean.TRUE.equals(callOnClientThread(mc, () -> {
            if (mc.level == null || mc.player == null) return Boolean.FALSE;
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) return Boolean.FALSE;
            return willBreakThisTick(mc, pos, state);
        }, Boolean.FALSE));

        if (!willBreak) {
            return callOnClientThread(mc, () -> continueBreakOnClient(mc, action, pos, dir), -1, action.isTickAligned());
        }

        long ns = Math.abs((long) action.interactCustomMs) * 1_000_000L;
        int broke;
        if (action.interactCustomMs < 0) {
            runBreakNextStepsOnce(mc, action, nextStepsFired, missingNextStepsWarned);
            if (!isCurrentRunActive()) return -1;
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            broke = callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1, action.isTickAligned());
        } else {
            broke = callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1, action.isTickAligned());
        }
        return broke;
    }

    private static int breakCustomTick(Minecraft mc, BreakAction action, BlockPos pos, Direction dir) throws InterruptedException {
        boolean willBreak = Boolean.TRUE.equals(callOnClientThread(mc, () -> {
            if (mc.level == null || mc.player == null) return Boolean.FALSE;
            BlockState s = mc.level.getBlockState(pos);
            if (s.isAir()) return Boolean.FALSE;
            return willBreakThisTick(mc, pos, s);
        }, Boolean.FALSE));

        if (!willBreak) {

            return callOnClientThread(mc, () -> {
                if (mc.level == null || mc.gameMode == null || mc.player == null) return -1;
                if (mc.level.getBlockState(pos).isAir()) return 1;
                if (action.sneak) holdMacroSneak(mc, action.sneakMode);
                mc.gameMode.continueDestroyBlock(pos, dir);
                mc.player.swing(InteractionHand.MAIN_HAND);
                return mc.level.getBlockState(pos).isAir() ? 1 : 0;
            }, -1, action.isTickAligned());
        }

        long ns = Math.abs((long) action.interactCustomMs) * 1_000_000L;
        int broke;
        if (action.interactCustomMs < 0) {

            runOnClientThread(mc, () -> sendBlockInteract(mc, pos, dir), action.isTickAligned());
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            broke = callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1, action.isTickAligned());
        } else {

            broke = callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1, action.isTickAligned());
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            runOnClientThread(mc, () -> sendBlockInteract(mc, pos, dir), action.isTickAligned());
        }
        return broke;
    }

    private static int breakAndDetectOnClient(Minecraft mc, BlockPos pos, Direction dir) {
        if (mc.level == null || mc.gameMode == null || mc.player == null) return -1;
        if (mc.level.getBlockState(pos).isAir()) return 1;
        mc.gameMode.continueDestroyBlock(pos, dir);
        mc.player.swing(InteractionHand.MAIN_HAND);
        return mc.level.getBlockState(pos).isAir() ? 1 : 0;
    }

    private static boolean willBreakThisTick(Minecraft mc, BlockPos pos, BlockState state) {
        float progress = (mc.gameMode instanceof AutismMultiPlayerGameModeAccessor acc) ? acc.autism$getDestroyProgress() : 0f;
        float delta = state.getDestroyProgress(mc.player, mc.player.level(), pos);
        return progress > 0f && (progress + delta) >= 1.0f;
    }

    private static boolean willBreakNextTick(Minecraft mc, BlockPos pos, BlockState state) {
        float progress = (mc.gameMode instanceof AutismMultiPlayerGameModeAccessor acc) ? acc.autism$getDestroyProgress() : 0f;
        float delta = state.getDestroyProgress(mc.player, mc.player.level(), pos);
        return progress > 0f && (progress + 2f * delta) >= 1.0f;
    }

    private static void runBreakNextStepsOnce(Minecraft mc, BreakAction action, boolean[] fired, boolean[] missingWarned) throws InterruptedException {
        if (fired != null && fired[0]) return;
        java.util.List<MacroAction> actions = action.getNextActions();
        if (actions == null || actions.isEmpty()) {
            if (missingWarned != null && !missingWarned[0]) {
                missingWarned[0] = true;
                AutismClientMessaging.sendPrefixed("\u00a7cBreak: no next steps found. Add action(s) after this Break step.");
            }
            return;
        }

        if (fired != null) fired[0] = true;
        action.markNextActionsRan();
        String names = actions.size() == 1 ? actions.get(0).getDisplayName() : actions.size() + " steps";
        setCurrentStatus("Break: next " + names);
        AutismModule module = AutismModule.get();
        boolean previousSuppressPostGui = Boolean.TRUE.equals(SUPPRESS_POST_GUI_AFTER.get());
        try {
            SUPPRESS_POST_GUI_AFTER.set(true);
            for (MacroAction next : actions) {
                if (next == null || !next.isEnabled() || !isCurrentRunActive()) continue;
                try {
                    executeSingleActionWithWaits(mc, next, module);
                } catch (InterruptedException e) {
                    throw e;
                } catch (Throwable t) {
                    AutismClientMessaging.sendPrefixed("\u00a7cBreak next step failed (" + next.getDisplayName() + "): " + MacroRace.safeThrowableMessage(t));
                }
            }
        } finally {
            SUPPRESS_POST_GUI_AFTER.set(previousSuppressPostGui);
        }
    }

    private static void runBreakNextStepsAfterCompletion(Minecraft mc, BreakAction action, boolean[] fired,
                                                         boolean[] missingWarned) throws InterruptedException {
        if (!action.interact || !action.runNextSteps || action.interactTiming == null) return;
        switch (action.interactTiming) {
            case AFTER -> runBreakNextStepsOnce(mc, action, fired, missingWarned);
            case AFTER_PLUS -> {
                waitOneTick();
                runBreakNextStepsOnce(mc, action, fired, missingWarned);
            }
            case CUSTOM -> {
                if (action.interactCustomMs >= 0) {
                    long ns = Math.abs((long) action.interactCustomMs) * 1_000_000L;
                    if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
                    runBreakNextStepsOnce(mc, action, fired, missingWarned);
                }
            }
            default -> {
            }
        }
    }

    private static void sendBlockInteract(Minecraft mc, BlockPos pos, Direction dir) {
        if (mc.gameMode == null || mc.player == null) return;
        Vec3 hit = Vec3.atCenterOf(pos).add(dir.getStepX() * 0.5, dir.getStepY() * 0.5, dir.getStepZ() * 0.5);
        BlockHitResult blockHit = new BlockHitResult(hit, dir, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
    }

    private static void waitForBreakSolid(Minecraft mc, BlockPos pos) throws InterruptedException {
        while (isCurrentRunActive()) {
            if (!isInstaBreakAir(mc, pos)) return;
            Thread.sleep(1);
        }
    }

    private static void waitBreakClientTicks(int ticks) throws InterruptedException {
        long start = AutismSharedState.get().getClientTickCounter();
        while (isCurrentRunActive() && (AutismSharedState.get().getClientTickCounter() - start) < ticks) {
            Thread.sleep(1);
        }
    }

    private static void holdMacroSneak(Minecraft mc, String mode) {
        autismclient.util.AutismMacroSneak.hold(mc, mode);
    }

    private static void releaseMacroSneak(Minecraft mc, String mode) {
        autismclient.util.AutismMacroSneak.release(mc, mode);
        if (!autismclient.util.AutismMacroSneak.isPacket(mode)) applyAggregateSneakState(mc);
    }

    static void runOnClientThread(Minecraft mc, Runnable runnable) throws InterruptedException {
        if (PackHideState.isHardLocked()) return;
        RunState scheduledRun = CURRENT_RUN.get();
        Runnable contextual = () -> withRunContext(scheduledRun, runnable);
        if (mc.isSameThread()) {
            if (PackHideState.isHardLocked()) return;
            contextual.run();
            return;
        }
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean pending = new AtomicBoolean(true);
        mc.execute(() -> {
            try {
                if (!pending.compareAndSet(true, false)) return;
                if (PackHideState.isHardLocked()) return;
                contextual.run();
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(2000, TimeUnit.MILLISECONDS)) pending.set(false);
    }

    static void runOnClientThread(Minecraft mc, Runnable runnable, boolean tickAligned) throws InterruptedException {
        if (tickAligned) runOnClientTick(mc, runnable);
        else runOnClientThread(mc, runnable);
    }

    private static void postOnClientThread(Minecraft mc, Runnable runnable, boolean tickAligned) throws InterruptedException {
        if (tickAligned) {
            runOnClientTick(mc, runnable);
            return;
        }
        if (PackHideState.isHardLocked()) return;
        RunState scheduledRun = CURRENT_RUN.get();
        mc.execute(() -> {
            if (PackHideState.isHardLocked()) return;
            withRunContext(scheduledRun, runnable);
        });
    }

    static void runOnClientTick(Minecraft mc, Runnable runnable) throws InterruptedException {
        if (PackHideState.isHardLocked()) return;
        RunState scheduledRun = CURRENT_RUN.get();
        Runnable contextual = () -> withRunContext(scheduledRun, runnable);

        if (mc.isSameThread()) {
            if (PackHideState.isHardLocked()) return;
            contextual.run();
            return;
        }
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean pending = new AtomicBoolean(true);
        TICK_ALIGNED.add(() -> {
            try {
                if (!pending.compareAndSet(true, false)) return;
                if (PackHideState.isHardLocked()) return;
                contextual.run();
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(2000, TimeUnit.MILLISECONDS)) pending.set(false);
    }

    private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> TICK_ALIGNED =
        new java.util.concurrent.ConcurrentLinkedQueue<>();

    public static void drainTickAligned() {

        if (autismclient.modules.AutoTotemModule.operationActive()) return;
        Runnable task;
        while ((task = TICK_ALIGNED.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                AutismClientAddon.LOG.debug("[Autism] Tick-aligned macro task failed", t);
            }
        }
    }

    static <T> T callOnClientThread(Minecraft mc, java.util.function.Supplier<T> supplier, T fallback) throws InterruptedException {
        if (PackHideState.isHardLocked()) return fallback;
        RunState scheduledRun = CURRENT_RUN.get();
        java.util.function.Supplier<T> contextual = () -> {
            AtomicReference<T> value = new AtomicReference<>(fallback);
            withRunContext(scheduledRun, () -> value.set(supplier.get()));
            return value.get();
        };
        if (mc.isSameThread()) {
            if (PackHideState.isHardLocked()) return fallback;
            return contextual.get();
        }
        AtomicReference<T> result = new AtomicReference<>(fallback);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean pending = new AtomicBoolean(true);
        mc.execute(() -> {
            try {
                if (!pending.compareAndSet(true, false)) return;
                if (PackHideState.isHardLocked()) return;
                result.set(contextual.get());
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(2000, TimeUnit.MILLISECONDS)) pending.set(false);
        return result.get();
    }

    static <T> T callOnClientThread(Minecraft mc, java.util.function.Supplier<T> supplier, T fallback,
                                            boolean tickAligned) throws InterruptedException {
        return tickAligned ? callOnClientTick(mc, supplier, fallback) : callOnClientThread(mc, supplier, fallback);
    }

    private static <T> T callOnClientTick(Minecraft mc, java.util.function.Supplier<T> supplier, T fallback) throws InterruptedException {
        if (PackHideState.isHardLocked()) return fallback;
        RunState scheduledRun = CURRENT_RUN.get();
        java.util.function.Supplier<T> contextual = () -> {
            AtomicReference<T> value = new AtomicReference<>(fallback);
            withRunContext(scheduledRun, () -> value.set(supplier.get()));
            return value.get();
        };
        if (mc.isSameThread()) {
            if (PackHideState.isHardLocked()) return fallback;
            return contextual.get();
        }
        AtomicReference<T> result = new AtomicReference<>(fallback);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean pending = new AtomicBoolean(true);
        TICK_ALIGNED.add(() -> {
            try {
                if (!pending.compareAndSet(true, false)) return;
                if (PackHideState.isHardLocked()) return;
                result.set(contextual.get());
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(2000, TimeUnit.MILLISECONDS)) pending.set(false);
        return result.get();
    }

    private static void runPlaceAction(Minecraft mc, PlaceAction action) throws InterruptedException {
        if (mc == null || mc.player == null || mc.level == null) return;

        ItemTarget target = action.resolvedItemTarget().resolveTemplate(mc);
        if (target == null) return;
        if (action.waitForItem && target != null && (target.hasIdentity() || target.hasSlot())) {
            setCurrentStatus("Waiting for " + describeItemTarget(target));
            waitForItemActionEntry(mc, target);
        }

        if (!action.interact) {

            MacroActionRunner.runMacroActionOnClientThread(mc, action, "Place");
            return;
        }

        final BlockPos placePos = action.blockPos == null ? BlockPos.ZERO : action.blockPos;
        final Direction face = action.manualDirection && action.direction != null ? action.direction : Direction.UP;
        InteractTiming t = action.interactTiming;
        boolean interactFirst = t == InteractTiming.BEFORE || (t == InteractTiming.CUSTOM && action.interactCustomMs < 0);
        long ns = t == InteractTiming.CUSTOM ? Math.abs((long) action.interactCustomMs) * 1_000_000L : 0L;

        if (interactFirst) {

            runOnClientThread(mc, () -> sendBlockInteract(mc, placePos, face), action.isTickAligned());
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            MacroActionRunner.runMacroActionOnClientThread(mc, action, "Place");
        } else if (t == InteractTiming.WITH) {

            runOnClientThread(mc, () -> {
                try { action.execute(mc); } catch (Exception e) { AutismClientAddon.LOG.debug("[Autism] Macro WITH-interact action failed", e); }
                sendBlockInteract(mc, placePos, face);
            }, action.isTickAligned());
        } else if (t == InteractTiming.AFTER_PLUS) {
            MacroActionRunner.runMacroActionOnClientThread(mc, action, "Place");
            waitOneTick();
            runOnClientThread(mc, () -> sendBlockInteract(mc, placePos, face), action.isTickAligned());
        } else {

            MacroActionRunner.runMacroActionOnClientThread(mc, action, "Place");
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            runOnClientThread(mc, () -> sendBlockInteract(mc, placePos, face), action.isTickAligned());
        }
    }

    private static void waitForItemInSpecificHandlerSlot(Minecraft mc, int slotId, ItemTarget target) throws InterruptedException {
        while (isCurrentRunActive()) {
            if (Boolean.TRUE.equals(callOnClientThread(mc, () -> handlerSlotMatchesItem(mc, slotId, target), Boolean.FALSE))) return;
            waitOneTick();
        }
    }

    private static void waitForAnyItemInSpecificHandlerSlot(Minecraft mc, int slotId) throws InterruptedException {
        while (isCurrentRunActive()) {
            if (Boolean.TRUE.equals(callOnClientThread(mc, () -> handlerSlotHasAnyItem(mc, slotId), Boolean.FALSE))) return;
            waitOneTick();
        }
    }

    private static boolean handlerSlotMatchesItem(Minecraft mc, int slotId, ItemTarget target) {
        if (mc.player == null || mc.player.containerMenu == null) return false;
        int handlerSlot = autismclient.util.AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, slotId);
        if (handlerSlot < 0 || handlerSlot >= mc.player.containerMenu.slots.size()) return false;
        var slot = mc.player.containerMenu.slots.get(handlerSlot);
        if (slot == null || slot.getItem().isEmpty()) return false;
        ItemTarget resolvedTarget = target == null ? ItemTarget.slotOnly(slotId) : target.copy();
        if (!resolvedTarget.hasSlot()) resolvedTarget.slot = slotId;
        return resolvedTarget.matches(slot.getItem(), slotId);
    }

    private static boolean handlerSlotHasAnyItem(Minecraft mc, int slotId) {
        if (mc.player == null || mc.player.containerMenu == null) return false;
        int handlerSlot = autismclient.util.AutismInventoryHelper.resolveConfiguredHandlerSlot(mc, slotId);
        if (handlerSlot < 0 || handlerSlot >= mc.player.containerMenu.slots.size()) return false;
        var slot = mc.player.containerMenu.slots.get(handlerSlot);
        return slot != null && !slot.getItem().isEmpty();
    }

    private static void waitForItemActionTargets(Minecraft mc, ItemAction ia) throws InterruptedException {
        if (ia == null) return;

        List<ItemTarget> targets = resolveItemTargets(mc, ia.itemTargets, ia.itemNames);
        if (!targets.isEmpty()) {
            for (ItemTarget target : targets) {
                waitForItemActionEntry(mc, target);
            }
            return;
        }

        if (ia.useSlot && ia.targetSlot >= 0) {
            setCurrentStatus("Waiting for slot " + ia.targetSlot);
            waitForAnyItemInSpecificHandlerSlot(mc, ia.targetSlot);
        }
    }

    private static void waitForItemActionEntry(Minecraft mc, ItemTarget target) throws InterruptedException {
        ItemTarget resolvedTarget = target == null ? null : target.resolveTemplate(mc);
        if (resolvedTarget == null) return;
        String itemLabel = describeItemTarget(resolvedTarget);
        int slot = resolvedTarget.hasSlot() ? resolvedTarget.slot : -1;

        if (slot >= 0 && resolvedTarget.hasIdentity()) {
            setCurrentStatus("Waiting for " + itemLabel);
            waitForItemInSpecificHandlerSlot(mc, slot, resolvedTarget);
            return;
        }

        if (slot >= 0) {
            setCurrentStatus("Waiting for slot " + slot);
            waitForAnyItemInSpecificHandlerSlot(mc, slot);
            return;
        }

        if (resolvedTarget.hasIdentity()) {
            setCurrentStatus("Waiting for item in GUI: " + itemLabel);
            CompletableFuture<Void> waitFuture = MacroConditionRegistry.waitForItemInHandler(resolvedTarget);
            waitForCondition(waitFuture);
        }
    }

    static ItemTarget resolveItemTarget(ItemTarget target, String legacyEntry) {
        if (target != null && (target.hasSlot() || target.hasIdentity())) return target.copy();
        return ItemTarget.fromLegacyEntry(legacyEntry);
    }

    private static List<ItemTarget> resolveItemTargets(Minecraft mc, List<ItemTarget> targets, List<String> legacyEntries) {
        List<ItemTarget> resolved = new ArrayList<>();
        if (targets != null) {
            for (ItemTarget target : targets) {
                if (target == null || (!target.hasSlot() && !target.hasIdentity())) continue;
                ItemTarget runtimeTarget = target.resolveTemplate(mc);
                if (runtimeTarget != null) resolved.add(runtimeTarget);
            }
        }
        if (!resolved.isEmpty()) return resolved;
        if (legacyEntries == null) return resolved;
        for (String entry : legacyEntries) {
            ItemTarget parsed = ItemTarget.fromLegacyEntry(entry);
            ItemTarget runtimeTarget = parsed.resolveTemplate(mc);
            if (runtimeTarget != null && (runtimeTarget.hasSlot() || runtimeTarget.hasIdentity())) resolved.add(runtimeTarget);
        }
        return resolved;
    }

    private static String describeItemTarget(ItemTarget target) {
        if (target == null) return "";
        String summary = target.summaryText();
        if (summary != null && !summary.isBlank()) return summary;
        String legacy = target.toLegacyEntry();
        return legacy == null ? "" : legacy;
    }

    private static void runCraftAction(Minecraft mc, CraftAction craftAction) throws InterruptedException {
        if (craftAction == null || !craftAction.hasEntries()) {
            setCurrentStatus("Crafting");
            AutismClientMessaging.sendPrefixed("\u00a7cNo craft recipes selected.");
            return;
        }

        for (int index = 0; index < craftAction.entries.size(); index++) {
            CraftAction.CraftEntry entry = craftAction.entries.get(index);
            if (entry == null || !entry.hasRecipe()) continue;

            setCurrentStatus(entry.useMaxAmount
                ? "Crafting " + entry.resultName + " [Max] (" + (index + 1) + "/" + craftAction.entries.size() + ")"
                : "Crafting " + entry.resultName + " x" + Math.max(1, entry.amount) + " (" + (index + 1) + "/" + craftAction.entries.size() + ")");

            AutismCraftingHelper.CraftableRecipeOption option = AutismCraftingHelper.findCraftableRecipe(mc, entry.recipeKey);
            if (option == null) option = AutismCraftingHelper.findCraftableRecipe(mc, entry.recipeId);
            if (option == null) {
                setCurrentStatus("Recipe not found");
                AutismClientMessaging.sendPrefixed("\u00a7cRecipe not found: " + (entry.resultName == null || entry.resultName.isBlank() ? "unknown" : entry.resultName));
                return;
            }

            int desiredAmount = AutismCraftingHelper.getEffectiveRequestedOutput(option, entry.amount, entry.useMaxAmount);
            if (desiredAmount <= 0) {
                setCurrentStatus("No space or materials");
                AutismClientMessaging.sendPrefixed("\u00a7cNo space or materials for " + entry.resultName + ".");
                return;
            }

            AutismCraftingHelper.CraftExecutionResult result = AutismCraftingHelper.executeCraftImmediately(
                mc,
                entry.recipeKey,
                entry.recipeId,
                desiredAmount
            );

            setCurrentStatus(result.message);
            if (!result.success) {
                AutismClientMessaging.sendPrefixed("\u00a7c" + result.message);
                return;
            }
            if (isCurrentRunActive()) {
                CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                waitForCondition(tickFuture);
            }
        }
    }

    private static void runGoToAction(Minecraft mc, GoToAction goToAction) throws InterruptedException {
        setCurrentStatus(String.format("Going to %.0f,%.0f,%.0f", goToAction.x, goToAction.y, goToAction.z));

        if (!AutismCompatManager.isBaritoneAvailable()) {
            AutismClientMessaging.sendPrefixed("Baritone not installed; skipping goto action.");
            return;
        }

        int goalX = (int) Math.floor(goToAction.x);
        int goalY = (int) Math.floor(goToAction.y);
        int goalZ = (int) Math.floor(goToAction.z);
        RunState run = CURRENT_RUN.get();

        if (!runBaritoneStart(mc, () -> AutismCompatManager.startBaritoneGoTo(mc, goalX, goalY, goalZ))) {
            AutismClientMessaging.sendPrefixed("Failed to start Baritone goto.");
            return;
        }

        try {
            if (!goToAction.waitForArrival) {
                setCurrentStatus("Goto started");
                return;
            }

            long startMs = System.currentTimeMillis();
            boolean sawBaritoneActivity = false;

            while (isCurrentRunActive()) {
                boolean goalActive = AutismCompatManager.isBaritoneGoalActive();
                boolean baritoneBusy = AutismCompatManager.isBaritoneBusy();
                if (goalActive || baritoneBusy) sawBaritoneActivity = true;

                if (sawBaritoneActivity && !goalActive && !baritoneBusy) {
                    setCurrentStatus("Goto finished");
                    return;
                }

                if (!sawBaritoneActivity && System.currentTimeMillis() - startMs >= 1500 && !goalActive && !baritoneBusy) {
                    setCurrentStatus("Goto finished");
                    return;
                }

                CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                waitForCondition(tickFuture);
            }
        } finally {
            if (!isCurrentRunActive()) requestOwnedBaritoneStop(run, mc);
            else releaseBaritoneOwner(run);
        }
    }

    private static void runMineAction(Minecraft mc, MineAction mineAction) throws InterruptedException {
        String mineDesc = mineAction.targetBlocks.isEmpty() ? "none" : mineAction.targetBlocks.get(0);
        setCurrentStatus("Mining: " + mineDesc);

        if (mineAction.targetBlocks.isEmpty()) return;
        if (!AutismCompatManager.isBaritoneAvailable()) {
            AutismClientMessaging.sendPrefixed("Baritone not installed; skipping mine action.");
            return;
        }

        java.util.List<String> blockNames = new java.util.ArrayList<>();
        for (String id : mineAction.targetBlocks) {
            if (id == null || id.isBlank()) continue;
            blockNames.add(id.startsWith("minecraft:") ? id.substring(10) : id);
        }
        if (blockNames.isEmpty()) return;

        RunState run = CURRENT_RUN.get();
        MineProgressTracker mineTracker = mineAction.stopMinedCount ? MineProgressTracker.create(mc, mineAction.targetBlocks) : null;
        if (mineTracker != null) {
            mineTracker.poll(mc);
            if (run != null) run.activeMineTracker = mineTracker;
        }

        try {
            if (!runBaritoneStart(mc, () -> AutismCompatManager.startBaritoneMine(mc, blockNames), mineAction.isTickAligned())) {
                AutismClientMessaging.sendPrefixed("Failed to start Baritone mine.");
                return;
            }

            long mineStartMs = System.currentTimeMillis();
            boolean sawBaritoneActivity = false;

            while (isCurrentRunActive()) {
                boolean mineActive = AutismCompatManager.isBaritoneMineActive();
                boolean baritoneBusy = AutismCompatManager.isBaritoneBusy();
                if (mineActive || baritoneBusy) sawBaritoneActivity = true;

                String stopReason = null;
                int minedDelta = 0;

                if (mineTracker != null) {
                    mineTracker.poll(mc);
                    minedDelta = mineTracker.destroyedCount();
                    setCurrentStatus("Mining: " + mineDesc + " (" + minedDelta + "/" + mineAction.minedCountTarget + ")");
                    if (minedDelta >= mineAction.minedCountTarget) {
                        stopReason = "Mine target reached";
                    }
                }

                if (stopReason == null && mineAction.stopInventoryFull && isInventoryFull(mc)) {
                    stopReason = "Mine stop: inventory full";
                }

                if (stopReason == null && mineAction.stopSlotsUsed) {
                    int used = countUsedMainInventorySlots(mc);
                    if (used >= mineAction.slotsUsedThreshold) {
                        stopReason = "Mine stop: slots used";
                    }
                }

                if (stopReason == null && mineAction.stopAfterTime
                    && System.currentTimeMillis() - mineStartMs >= mineAction.timeoutSeconds * 1000L) {
                    stopReason = "Mine timeout";
                }

                if (stopReason != null) {
                    setCurrentStatus(stopReason);
                    requestOwnedBaritoneStop(run, mc);
                    waitForBaritoneIdle();
                    return;
                }

                if (sawBaritoneActivity && !mineActive && !baritoneBusy) {
                    setCurrentStatus("Mine finished");
                    return;
                }

                if (!sawBaritoneActivity && System.currentTimeMillis() - mineStartMs >= 1500 && !mineActive && !baritoneBusy) {
                    setCurrentStatus("Mine finished");
                    return;
                }

                CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                waitForCondition(tickFuture);
            }
        } finally {
            if (run != null && run.activeMineTracker == mineTracker) run.activeMineTracker = null;
            if (!isCurrentRunActive()) requestOwnedBaritoneStop(run, mc);
            else releaseBaritoneOwner(run);
        }
    }

    private static boolean runBaritoneStart(Minecraft mc, java.util.function.BooleanSupplier starter) throws InterruptedException {
        return runBaritoneStart(mc, starter, false);
    }

    private static boolean runBaritoneStart(Minecraft mc, java.util.function.BooleanSupplier starter,
                                            boolean tickAligned) throws InterruptedException {
        RunState run = CURRENT_RUN.get();
        if (run != null) BARITONE_OWNER.set(run);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean started = new AtomicBoolean(false);

        Runnable task = () -> {
            try {
                started.set(starter.getAsBoolean());
            } finally {
                latch.countDown();
            }
        };
        if (tickAligned && !mc.isSameThread()) TICK_ALIGNED.add(task);
        else mc.execute(task);

        latch.await(2, TimeUnit.SECONDS);
        if (!started.get()) releaseBaritoneOwner(run);
        return started.get();
    }

    private static void requestBaritoneStop(Minecraft mc) {
        if (mc == null || !AutismCompatManager.isBaritoneAvailable()) return;
        mc.execute(() -> AutismCompatManager.stopBaritone(mc));
    }

    private static void requestOwnedBaritoneStop(RunState run, Minecraft mc) {
        if (run == null || !BARITONE_OWNER.compareAndSet(run, null)) return;
        requestBaritoneStop(mc);
    }

    private static void releaseBaritoneOwner(RunState run) {
        if (run != null) BARITONE_OWNER.compareAndSet(run, null);
    }

    private static void waitForBaritoneIdle() {
        long waitStartMs = System.currentTimeMillis();
        while (isCurrentRunActive() && AutismCompatManager.isBaritoneBusy() && System.currentTimeMillis() - waitStartMs < 3000L) {
            CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
            waitForCondition(tickFuture);
        }
    }

    private static boolean isInventoryFull(Minecraft mc) {
        if (mc.player == null) return false;

        for (int slot = 9; slot < 36; slot++) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) return false;
        }
        return true;
    }

    private static int countUsedMainInventorySlots(Minecraft mc) {
        if (mc.player == null) return 0;

        int used = 0;
        for (int slot = 9; slot < 36; slot++) {
            if (!mc.player.getInventory().getItem(slot).isEmpty()) used++;
        }
        return used;
    }

    private static final class MineProgressTracker {
        private static final int POLL_RADIUS = 8;
        private final java.util.Set<net.minecraft.world.level.block.Block> targetBlocks;
        private final java.util.HashMap<Long, Boolean> observedTargetPositions = new java.util.HashMap<>();
        private final java.util.HashSet<Long> countedPositions = new java.util.HashSet<>();
        private int destroyed;

        private MineProgressTracker(java.util.Set<net.minecraft.world.level.block.Block> targetBlocks) {
            this.targetBlocks = targetBlocks;
        }

        static MineProgressTracker create(Minecraft mc, java.util.List<String> rawTargets) {
            java.util.Set<net.minecraft.world.level.block.Block> blocks = new java.util.LinkedHashSet<>();
            if (rawTargets != null) {
                for (String raw : rawTargets) {
                    net.minecraft.world.level.block.Block block = resolveBlock(raw);
                    if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) blocks.add(block);
                }
            }
            if (blocks.isEmpty()) return null;
            return new MineProgressTracker(blocks);
        }

        private static net.minecraft.world.level.block.Block resolveBlock(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String value = raw.trim();
            try {
                net.minecraft.resources.Identifier id = value.contains(":")
                    ? net.minecraft.resources.Identifier.parse(value)
                    : net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", value);
                if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id)) return null;
                return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(id);
            } catch (Throwable ignored) {
                return null;
            }
        }

        synchronized int destroyedCount() {
            return destroyed;
        }

        synchronized void poll(Minecraft mc) {
            if (mc == null || mc.player == null || mc.level == null) return;
            BlockPos center = mc.player.blockPosition();
            int minY = Math.max(mc.level.getMinY(), center.getY() - POLL_RADIUS);
            int maxY = Math.min(mc.level.getMaxY() - 1, center.getY() + POLL_RADIUS);
            for (int x = center.getX() - POLL_RADIUS; x <= center.getX() + POLL_RADIUS; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = center.getZ() - POLL_RADIUS; z <= center.getZ() + POLL_RADIUS; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        updateObservedPosition(pos, mc.level.getBlockState(pos));
                    }
                }
            }
        }

        synchronized void observePacket(Minecraft mc, Packet<?> packet) {
            if (mc == null || mc.level == null || packet == null) return;

            if (packet instanceof net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket blockUpdate) {
                observeBlockChange(mc, blockUpdate.getPos(), blockUpdate.getBlockState());
            } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket sectionUpdate) {
                try {
                    sectionUpdate.runUpdates((pos, state) -> {
                        if (pos != null && state != null) observeBlockChange(mc, pos, state);
                    });
                } catch (Throwable ignored) {  }
            }
        }

        private void observeBlockChange(Minecraft mc, BlockPos pos, BlockState newState) {
            if (mc == null || mc.level == null || pos == null || newState == null) return;
            if (!isNearPlayer(mc, pos)) return;
            BlockPos stablePos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
            BlockState oldState = mc.level.getBlockState(stablePos);
            if (isTarget(oldState) && !isTarget(newState)) {
                countDestroyed(stablePos.asLong());
            }
            updateObservedPosition(stablePos, newState);
        }

        private boolean isNearPlayer(Minecraft mc, BlockPos pos) {
            if (mc == null || mc.player == null || pos == null) return false;
            BlockPos playerPos = mc.player.blockPosition();
            int radius = POLL_RADIUS + 4;
            return Math.abs(pos.getX() - playerPos.getX()) <= radius
                && Math.abs(pos.getY() - playerPos.getY()) <= radius
                && Math.abs(pos.getZ() - playerPos.getZ()) <= radius;
        }

        private void updateObservedPosition(BlockPos pos, BlockState state) {
            if (pos == null || state == null) return;
            long key = pos.asLong();
            boolean nowTarget = isTarget(state);
            boolean wasTarget = Boolean.TRUE.equals(observedTargetPositions.get(key));
            if (nowTarget) {
                observedTargetPositions.put(key, Boolean.TRUE);
            } else {
                if (wasTarget) countDestroyed(key);
                observedTargetPositions.remove(key);
            }
        }

        private boolean isTarget(BlockState state) {
            return state != null && targetBlocks.contains(state.getBlock());
        }

        private void countDestroyed(long posKey) {
            if (countedPositions.add(posKey)) destroyed++;
        }
    }

    private static void runPayAction(Minecraft mc, PayAction payAction) throws InterruptedException {
        payAction.delayMs = PayAction.normalizeDelay(payAction.delayMs);
        java.util.List<String> targets = new java.util.ArrayList<>();
        for (String player : payAction.players) {
            if (player == null) continue;
            MacroTemplate.Resolution playerResolution = MacroVariables.resolve(player, mc);
            if (!playerResolution.success()) continue;
            String trimmed = playerResolution.value().trim();
            if (!trimmed.isEmpty() && !targets.contains(trimmed)) targets.add(trimmed);
        }

        if (targets.isEmpty()) targets.addAll(autismclient.util.AutismPlayerProbe.everyone(mc, payAction.probeHidden));
        if (targets.isEmpty() || mc.getConnection() == null) return;

        MacroTemplate.Resolution amountResolution = MacroVariables.resolve(payAction.amountInput, mc);
        if (!amountResolution.success()) return;
        long resolvedAmount = Math.max(0L, PayAction.parseAmount(amountResolution.value()));

        long[] dividedAmounts = payAction.divideEnabled ? PayAction.distribute(resolvedAmount, targets.size()) : null;
        int sent = 0;

        for (int i = 0; i < targets.size(); i++) {
            if (!isCurrentRunActive()) break;
            String player = targets.get(i);

            long perAmount = dividedAmounts != null ? dividedAmounts[i] : resolvedAmount;
            if (perAmount <= 0L) continue;
            String amountValue = String.valueOf(perAmount);
            String amountLabel = PayAction.formatAmount(perAmount);

            String template = (payAction.commandTemplate == null || payAction.commandTemplate.isBlank())
                ? "/pay <player> <amount>"
                : payAction.commandTemplate;
            String command = template
                .replace("<player>", player)
                .replace("{player}", player)
                .replace("<amount>", amountValue)
                .replace("{amount}", amountValue)
                .trim();
            MacroTemplate.Resolution commandResolution = MacroVariables.resolve(command, mc);
            if (!commandResolution.success()) continue;
            command = commandResolution.value().trim();
            if (command.isEmpty()) continue;
            final String commandToSend = command;

            if (sent > 0 && payAction.delayEnabled && payAction.delayMs > 0) Thread.sleep(payAction.delayMs);

            setCurrentStatus("Paying " + player + " " + amountLabel + " (" + (sent + 1) + "/" + targets.size() + ")");

            runOnClientThread(mc, () -> {
                try {
                    if (PackHideState.isHardLocked() || mc.getConnection() == null) return;
                    if (commandToSend.startsWith("/") && commandToSend.length() > 1) {
                        mc.getConnection().sendCommand(commandToSend.substring(1));
                    } else {
                        mc.getConnection().sendChat(commandToSend);
                    }
                } catch (Exception e) {
                    AutismClientMessaging.sendPrefixed("\u00a7cPay failed: " + e.getMessage());
                }
            });

            sent++;

            if (payAction.confirmMacro != null && !payAction.confirmMacro.isBlank()) {
                setCurrentStatus("Confirming " + player);
                PayAction.runConfirmMacro(payAction.confirmMacro, () -> !isCurrentRunActive());
            }
        }
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
                MineProgressTracker tracker = run.running.get() ? run.activeMineTracker : null;
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

    private static void startSmoothRotation(float yaw, float pitch, double speed) {
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

    private static void waitForSmoothRotationCompletion() {
        RunState run = CURRENT_RUN.get();
        while (isCurrentRunActive() && isRotating.get()
            && (run == null || ROTATION_OWNER.get() == run)) {
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
        }
    }

    private static void cancelRotation(RunState run) {
        if (run == null) return;
        synchronized (ROTATION_STATE_LOCK) {
            if (ROTATION_OWNER.get() != run) return;
            isRotating.set(false);
            rotationAlignedFrames = 0;
            ROTATION_OWNER.set(null);
        }
    }

    private static LookAtBlockAction.RotationTarget resolveLookAtTargetOnClient(Minecraft mc, LookAtBlockAction action) {
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

    private static void executeBurstPackets(Minecraft mc, java.util.List<SendPacketAction> batch) {
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

    private static void executePreGeneratedBurst(Minecraft mc, java.util.List<Packet<?>> packets) {
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

    private static int countPreGeneratedActions(AutismMacro macro, int startIdx, int packetCount) {
        return countPreGeneratedActions(macro, startIdx, packetCount, macro.actions.size());
    }

    private static int countPreGeneratedActions(AutismMacro macro, int startIdx, int packetCount, int endIdx) {
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
