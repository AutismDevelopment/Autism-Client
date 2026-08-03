package autismclient.util.macro;

import com.google.gson.Gson;
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
    private static final Gson GSON = new Gson();

    private static final long MACRO_WAIT_POLL_NANOS = 500_000L;
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    public static final int MAX_CONCURRENT_MACROS = 4;
    private static final Object RUN_LOCK = new Object();
    private static final EnumMap<SharedClientResource, ReentrantLock> SHARED_RESOURCE_LOCKS = new EnumMap<>(SharedClientResource.class);
    static {
        for (SharedClientResource resource : SharedClientResource.values()) {
            SHARED_RESOURCE_LOCKS.put(resource, new ReentrantLock(true));
        }
    }
    private static final AtomicLong NEXT_RUN_ID = new AtomicLong(1L);
    private static final java.util.LinkedHashMap<Long, RunState> ACTIVE_RUNS = new java.util.LinkedHashMap<>();

    private static final java.util.concurrent.ConcurrentHashMap<Long, RunState> RUNS_BY_ID = new java.util.concurrent.ConcurrentHashMap<>();
    private static final AtomicLong RUN_STATE_REVISION = new AtomicLong();
    private static final ThreadLocal<RunState> CURRENT_RUN = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPPRESS_POST_GUI_AFTER = ThreadLocal.withInitial(() -> false);
    private static volatile RunState primaryRun = null;
    private static volatile boolean visibleRunActive;
    private static AutismMacro currentMacro = null;
    private static Thread macroThread = null;

    private static final AtomicReference<String> lastReceivedPacket = new AtomicReference<>("");
    private static String currentStatus = "";

    private static volatile int currentStepIndex = -1;
    private static volatile int totalSteps = 0;

    private static final AtomicBoolean isWaitingForPacket = new AtomicBoolean(false);
    private static final AtomicReference<String> waitingPacketName = new AtomicReference<>("");
    private static final Object packetWaitLock = new Object();

    private static final AtomicBoolean isWaitingForChat = new AtomicBoolean(false);
    private static final AtomicReference<String> waitingChatPattern = new AtomicReference<>("");
    private static final AtomicBoolean waitingChatIsRegex = new AtomicBoolean(false);
    private static final AtomicReference<String> waitingChatPatternJson = new AtomicReference<>("");
    private static final java.util.concurrent.atomic.AtomicInteger waitingChatFuzzyPercent = new java.util.concurrent.atomic.AtomicInteger(100);
    private static final AtomicReference<String> lastMatchedChat = new AtomicReference<>("");
    private static final Object recentChatLock = new Object();
    private static final Deque<RecentChatMessage> recentChatMessages = new ArrayDeque<>();
    private static final int MAX_RECENT_CHAT_MESSAGES = 256;
    private static final Object recentGuiLock = new Object();
    private static final Deque<RecentGuiTitle> recentGuiTitles = new ArrayDeque<>();
    private static final int MAX_RECENT_GUI_TITLES = 128;
    private static String recentChatServerKey = "";

    private static volatile CompletableFuture<Void> activePacketFuture = null;
    private static volatile CompletableFuture<Void> activeChatFuture = null;

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
    private static final ThreadLocal<RepeatPacketContext> REPEAT_PACKET_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Integer> REPEAT_PACKET_SKIP = ThreadLocal.withInitial(() -> 0);

    private static final class RunState {
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

    private static final class PrearmedWait {
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

    private record ChatCapture(String sender, String message, String displayText, Component displayComponent, ChatSource source) {}
    private record RepeatPacketContext(AutismMacro macro, int startIdx, int endIdx) {}
    private record InlineNextActions(java.util.List<MacroAction> actions, int lastIndex) {
        static InlineNextActions empty() {
            return new InlineNextActions(java.util.List.of(), -1);
        }
    }
    private record BreakStartPlan(int result, boolean instant, boolean interactFirst) {}
    private record BreakContinuePlan(int result, boolean breaksThisTick, boolean breaksNextTick) {}
    private record MacroEvent(Packet<?> packet, String direction, long nanoTime, ChatCapture chatCapture,
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

    private static boolean isCurrentRunActive() {
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

    private static void setCurrentStatus(String status) {
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

    private static EnumSet<SharedClientResource> acquireClientResources(MacroAction action) throws InterruptedException {
        return acquireClientResources(resourcesForAction(action));
    }

    private static EnumSet<SharedClientResource> acquireClientResources(EnumSet<SharedClientResource> resources) throws InterruptedException {
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

    private static EnumSet<SharedClientResource> resourcesForActions(java.util.List<MacroAction> actions) {
        return MacroActionResources.resourcesForActions(actions);
    }

    private static void releaseClientResources(EnumSet<SharedClientResource> resources) {
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

    private static void withRunContext(RunState run, Runnable task) {
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
            if (ACTIVE_RUNS.isEmpty()) allRunsStoppedAtMs = System.currentTimeMillis();
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
            if (ACTIVE_RUNS.isEmpty()) expireSuggestionCacheAfterIdle();
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
                if (ACTIVE_RUNS.isEmpty()) allRunsStoppedAtMs = System.currentTimeMillis();
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
                    if (!dynamicTextInputsAvailable(action, mc)) continue;
                    if (!hasPrearmedWait(action)) clearOwnCaptureOutput(action);

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

                    prearmNextActionWait(mc, macro.actions, i);

                    EnumSet<SharedClientResource> heldResources = acquireClientResources(action);
                    try {

                        if (!waitForGuiBeforeAction(mc, action)) continue;
                        java.util.concurrent.CompletableFuture<Void> postGuiFuture =
                                (action instanceof ItemAction || action instanceof DropAction || isTargetWaitInteractionAction(action)) ? null : createPostGuiFuture(mc, action);

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
                        PrearmedWait prearmed = consumePrearmedWait(action);
                        int startTarget = 0;
                        if (prearmed != null) {
                            try {
                                waitForFutureDone(prearmed.future, 0);
                                startTarget = prearmed.completedPacketTargets;
                            } finally {
                                prearmed.cleanup();
                            }
                        }
                        if (targets.isEmpty()) {
                            if (prearmed == null) awaitPacket("");
                        } else {
                            for (int targetIndex = Math.min(startTarget, targets.size()); targetIndex < targets.size(); targetIndex++) {
                                if (!isCurrentRunActive()) break;
                                awaitPacket(targets.get(targetIndex));
                            }
                        }
                        copyLastMatchedPacket(packetAction);
                    }
                    else if (action instanceof WaitPacketMatchAction wpma) {
                        setCurrentStatus(wpma.getDisplayName());
                        if (!waitForPrearmed(wpma, wpma.timeoutMs)) waitForPacketMatch(wpma);
                    }
                    else if (action instanceof WaitInventoryPredicateAction wipa) {
                        setCurrentStatus(wipa.getDisplayName());
                        if (!waitForPrearmed(wipa, wipa.timeoutMs)) waitForPredicate(() -> wipa.matches(mc), wipa.timeoutMs);
                    }
                    else if (action instanceof WaitDurabilityAction wda) {
                        setCurrentStatus(wda.getDisplayName());
                        if (wda.useNext) waitForDurabilityUseNext(mc, wda);
                        else if (!waitForPrearmed(wda, wda.timeoutMs)) waitForPredicate(() -> wda.matches(mc), wda.timeoutMs);
                    }
                    else if (action instanceof WaitFreeSlotsAction wfsa) {
                        setCurrentStatus(wfsa.getDisplayName());
                        if (!waitForPrearmed(wfsa, wfsa.timeoutMs)) waitForPredicate(() -> wfsa.matches(mc), wfsa.timeoutMs);
                    }
                    else if (action instanceof WaitEntityTargetAction weta) {
                        setCurrentStatus(weta.getDisplayName());
                        if (!waitForPrearmed(weta, weta.timeoutMs)) waitForPredicate(() -> weta.matches(mc), weta.timeoutMs);
                    }
                    else if (action instanceof WaitGuiTypeAction wgta) {
                        setCurrentStatus(wgta.getDisplayName());
                        if (!hasPrearmedWait(wgta)) clearCaptureOutputs(wgta);
                        if (waitForPrearmed(wgta, wgta.timeoutMs)) {
                        } else if (wgta.waitMode == WaitGuiTypeAction.WaitMode.CLOSE) {
                            waitForPredicate(guiClosePredicate(wgta, mc), wgta.timeoutMs);
                            commitStagedCapture(wgta);
                        } else {
                            waitForPredicate(() -> {
                                var captured = wgta.capture(mc);
                                captured.ifPresent(values -> stageCapture(wgta, values));
                                return captured.isPresent();
                            }, wgta.timeoutMs);
                            commitStagedCapture(wgta);
                        }
                    }
                    else if (action instanceof WaitForHealthAction) {
                        WaitForHealthAction wh = (WaitForHealthAction) action;
                        setCurrentStatus(wh.waitingStatusText());
                        try {
                            if (!waitForPrearmed(wh, 0)) {
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
                            if (!waitForPrearmed(wba, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForBlock(wba);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForGuiAction) {
                        WaitForGuiAction wga = (WaitForGuiAction) action;
                        if (!hasPrearmedWait(wga)) clearCaptureOutputs(wga);
                        if (waitForPrearmed(wga, 0)) {
                        } else if (wga.waitMode == WaitForGuiAction.WaitMode.CLOSE) {
                            setCurrentStatus("Waiting for GUI close: " + wga.guiTitle);
                            waitForPredicate(guiClosePredicate(wga, mc), wga.timeoutMs);
                            commitStagedCapture(wga);
                        } else {
                            setCurrentStatus("Waiting for GUI: " + wga.guiTitle);
                            waitForPredicate(() -> {
                                var captured = wga.captureGui(mc.gui.screen());
                                captured.ifPresent(values -> stageCapture(wga, values));
                                return captured.isPresent();
                            }, wga.timeoutMs);
                            commitStagedCapture(wga);
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
                            if (!waitForPrearmed(wca, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForCooldown(cooldownTarget, wca.checkMainInteractionHand);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitPosAction) {
                        WaitPosAction wpa = (WaitPosAction) action;
                        setCurrentStatus("Waiting for Pos: " + String.format("%.0f, %.0f, %.0f", wpa.x, wpa.y, wpa.z));
                        try {
                            if (!waitForPrearmed(wpa, 0)) {
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
                        if (sna.persistent || !sna.sneak) runOnClientThread(mc, () -> applyAggregateSneakState(mc));
                        else runOnClientThread(mc, () -> sna.execute(mc));
                    }
                    else if (action instanceof SprintAction) {
                        SprintAction spa = (SprintAction) action;
                        if (spa.persistent && spa.sprint) {
                            currentPersistentActions().removeIf(p -> p instanceof SprintAction);
                            currentPersistentActions().add(spa);
                        } else if (!spa.sprint) {
                            currentPersistentActions().removeIf(p -> p instanceof SprintAction);
                        }
                        if (spa.persistent || !spa.sprint) runOnClientThread(mc, () -> applyAggregateSprintState(mc));
                        else runOnClientThread(mc, () -> spa.execute(mc));
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
                        int bodyCount = runRaceGroup(mc, module, macro.actions, i, race);
                        if (bodyCount > 0) i += bodyCount;
                    }
                    else if (action instanceof MultiAction multi) {
                        int stepCount = runMultiGroup(mc, module, macro.name, macro.actions, i, multi);
                        if (stepCount > 0) i += stepCount;
                    }
                    else if (action instanceof ReportAction reportAction) {
                        int conditionCount = runReportGroup(mc, macro.actions, i, reportAction);
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
                        runCaptureValueAction(mc, captureValue);
                    }
                    else if (action instanceof WaitForChatAction) {
                        WaitForChatAction wca = (WaitForChatAction) action;
                        setCurrentStatus("Waiting for chat: " + wca.pattern);
                        if (!waitForPrearmed(wca, wca.timeoutMs)) awaitChat(wca);
                    }
                    else if (action instanceof WaitForEntityAction) {
                        WaitForEntityAction wea = (WaitForEntityAction) action;
                        setCurrentStatus(wea.getDisplayName());
                        try {
                            if (!waitForPrearmed(wea, 0)) {
                                waitForPredicate(() -> wea.matchesEntityTarget(mc), wea.timeoutMs);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForSoundAction) {
                        WaitForSoundAction wsa = (WaitForSoundAction) action;
                        String sndDesc = wsa.soundIds.isEmpty() ? "any" : wsa.soundIds.get(0);
                        setCurrentStatus("Waiting for sound: " + sndDesc);
                        try {
                            if (!waitForPrearmed(wsa, 0)) {
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
                            if (!waitForPrearmed(wsca, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForSlotChange(wsca);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForWorldChangeAction world) {
                        setCurrentStatus(world.getDisplayName());
                        try {
                            if (!waitForPrearmed(world, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForWorldChange(world);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForPositionDeltaAction wpda) {
                        setCurrentStatus(wpda.getDisplayName());
                        try {
                            if (!waitForPrearmed(wpda, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForPositionDelta(wpda.distance, wpda.horizontalOnly);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForTeleportAction teleport) {
                        setCurrentStatus(teleport.getDisplayName());
                        try {
                            if (!waitForPrearmed(teleport, 0)) {
                                CompletableFuture<?> future = raceConditionFuture(mc, teleport);
                                waitForFutureDone(future, 0);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitGamemodeChangeAction gm) {
                        setCurrentStatus(gm.getDisplayName());
                        try {
                            if (!waitForPrearmed(gm, gm.timeoutMs)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForGamemodeChange(gm);
                                waitForFutureDone(future, gm.timeoutMs);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitMovementAction wm) {
                        setCurrentStatus(wm.getDisplayName());
                        try {
                            if (!waitForPrearmed(wm, 0)) {
                                CompletableFuture<?> future = raceConditionFuture(mc, wm.resolveSubAction());
                                waitForFutureDone(future, 0);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForLanStepAction) {
                        WaitForLanStepAction wla = (WaitForLanStepAction) action;
                        setCurrentStatus(wla.getDisplayName());
                        try {
                            if (!waitForPrearmed(wla, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForLanStep(wla);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForMacroStepAction) {
                        WaitForMacroStepAction wma = (WaitForMacroStepAction) action;
                        setCurrentStatus(wma.getDisplayName());
                        if (!waitForPrearmed(wma, wma.timeoutMs)) waitForMacroStep(wma);
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
                        runUseItemAction(mc, (UseItemAction) action);
                    }
                    else if (action instanceof ItemAction) {
                        ItemAction ia = (ItemAction) action;
                        if (ia.waitForItem) {
                            try {
                                waitForItemActionTargets(mc, ia);
                            } catch (java.util.concurrent.CancellationException e) {  }
                        }
                        postGuiFuture = createPostGuiFuture(mc, ia);
                        if (ia.useClickDelay && ia.clickDelayMs > 0) {
                            runItemActionPaced(mc, ia);
                        } else {
                            runMacroActionOnClientThread(mc, action, "ItemAction");
                        }
                    }
                    else if (action instanceof DropAction || action instanceof SwapSlotsAction) {
                        if (action instanceof DropAction dropAction) {
                            postGuiFuture = createPostGuiFuture(mc, dropAction);
                        }
                        runMacroActionOnClientThread(mc, action, action.getDisplayName());
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
                                try { runStoreItemAction(mc, sia); }
                                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                                catch (Exception e) { AutismClientMessaging.sendPrefixed("Store error: " + e.getMessage()); }

                                CompletableFuture<Void> siTick = MacroConditionRegistry.waitForNextTick();
                                waitForCondition(siTick);
                            }
                        } else {
                            try { runStoreItemAction(mc, sia); }
                            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                            catch (Exception e) { AutismClientMessaging.sendPrefixed("Store error: " + e.getMessage()); }
                        }
                    }
                    else if (action instanceof ClickAction) {
                        ClickAction ca = (ClickAction) action;
                        runClickActionBurst(mc, ca);
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
                        runContainerClickSequenceAction(mc, sequence);
                    }
                    else if (action instanceof PacketBurstAction burst) {
                        runPacketBurstAction(mc, burst);
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
                            AutismClientMessaging.sendPrefixed("\u00a7cRollback failed: " + safeThrowableMessage(e));
                        }
                    }
                    else if (action instanceof PacketGateAction pga) {
                        runPacketGateAction(mc, pga);
                    }
                    else if (isTargetWaitInteractionAction(action)) {
                        postGuiFuture = runTargetWaitInteractionAction(mc, action);
                    }
                    else if (action instanceof MacroVariablesAction || action instanceof AssertAction) {
                        action.execute(mc);
                    }
                    else if (action instanceof UseItemPhaseAction phaseAction) {
                        runUseItemPhaseAction(mc, phaseAction);
                    }
                    else if (action instanceof autismclient.api.macro.ContextMacroAction contextAction) {
                        runContextMacroAction(contextAction);
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
                            runOnClientThread(mc, () -> {
                                if (PackHideState.isHardLocked()) return;
                                try {
                                    fPlace.execute(mc);
                                } catch (Exception e) {
                                    AutismClientMessaging.sendPrefixed("\u00a7cError in Place: " + e.getMessage());
                                }
                            });
                        }
                        }
                    }
                    else {

                        if (module != null && module.useInstantExecutionMode()) {

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
                            runMacroActionOnClientThread(mc, action, action.getDisplayName());
                        }
                    }

                    commitConditionOutput(action, mc);

                    if (postGuiFuture != null && isCurrentRunActive()) {
                        try {
                            waitForCondition(postGuiFuture);
                        } catch (java.util.concurrent.CancellationException e) {  }
                    }

                    if (isCurrentRunActive()) {
                        markStepCompleted(i + 1);
                        broadcastStepProgress(runState, i + 1, macro.actions.size(), macro.name);
                    }

                    if (isCurrentRunActive() && !isInstantInventoryAction(action)) {
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
                runFinallyActions(runState, mc);
            }
            PacketGateManager.clearOwnerAndFlushConfigured(runState.id, mc == null ? null : mc.getConnection());
            finishCurrentRun();
            cancelRotation(runState);
            if (!isRunning.get()) MacroConditionRegistry.cancelAll();

            broadcastStepProgress(runState, -1, 0, "");
        }
    }

    private static String normalizePacketKey(String value) {

        return AutismPacketNamer.normalizePacketKey(value);
    }

    private static boolean packetKeyMatches(String normalizedExpected, String candidate) {
        if (normalizedExpected.isEmpty() || candidate == null || candidate.isEmpty()) return false;

        String normalizedCandidate = normalizePacketKey(candidate);
        if (normalizedCandidate.isEmpty()) return false;
        if (normalizedExpected.equals(normalizedCandidate)) return true;

        if (normalizedCandidate.endsWith("packet")) {
            String strippedCandidate = normalizedCandidate.substring(0, normalizedCandidate.length() - "packet".length());
            if (normalizedExpected.equals(strippedCandidate)) return true;
        }

        return normalizedExpected.endsWith(normalizedCandidate) || normalizedCandidate.endsWith(normalizedExpected);
    }

    private static boolean packetNameMatches(String expected, String candidate) {
        if (expected == null || expected.isEmpty()) return false;
        return packetKeyMatches(normalizePacketKey(expected), candidate);
    }

    private record ParsedPacketTarget(String direction, String expectedName, String normalizedExpected) {}
    private static final java.util.concurrent.ConcurrentHashMap<String, ParsedPacketTarget> PARSED_TARGET_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static final ParsedPacketTarget EMPTY_PACKET_TARGET = new ParsedPacketTarget("", "", "");

    private static ParsedPacketTarget parsedPacketTarget(String target) {
        if (target == null) return EMPTY_PACKET_TARGET;
        if (PARSED_TARGET_CACHE.size() > 256) PARSED_TARGET_CACHE.clear();
        return PARSED_TARGET_CACHE.computeIfAbsent(target, t -> {
            String expectedName = WaitForPacketAction.getPacketName(t);
            return new ParsedPacketTarget(WaitForPacketAction.getDirection(t), expectedName, normalizePacketKey(expectedName));
        });
    }

    private static boolean matchesPacketTarget(String target, Packet<?> packet, String direction) {
        if (packet == null) return false;

        ParsedPacketTarget parsed = parsedPacketTarget(target);
        if (!parsed.direction().isEmpty() && !parsed.direction().equalsIgnoreCase(direction)) return false;
        if (parsed.expectedName().isEmpty()) return true;

        String normalizedExpected = parsed.normalizedExpected();
        if (packetKeyMatches(normalizedExpected, AutismPacketNamer.getFriendlyName(packet, direction))) return true;
        if (packetKeyMatches(normalizedExpected, AutismPacketNamer.getFriendlyName(packet))) return true;
        if (packetKeyMatches(normalizedExpected, packet.getClass().getSimpleName())) return true;

        @SuppressWarnings("unchecked")
        String fullName = AutismPacketRegistry.getName((Class<? extends Packet<?>>) packet.getClass());
        return packetKeyMatches(normalizedExpected, fullName);
    }

    private static void awaitPacket(String target) {
        RunState run = CURRENT_RUN.get();
        if (run != null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            String normalizedTarget = WaitForPacketAction.normalizeTarget(target);
            synchronized (run.packetWaitLock) {
                run.waitingPacketName.set(normalizedTarget);
                run.packetFuture = future;
                run.waitingForPacket.set(true);
            }
            try {
                setCurrentStatus(normalizedTarget.isEmpty()
                    ? "Waiting for packet: Any"
                    : "Waiting for packet: " + WaitForPacketAction.getDisplayLabel(normalizedTarget));
                waitForCondition(future);
            } finally {
                synchronized (run.packetWaitLock) {
                    if (run.packetFuture == future) {
                        run.waitingForPacket.set(false);
                        run.waitingPacketName.set("");
                        run.packetFuture = null;
                    }
                }
            }
            return;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        String normalizedTarget = WaitForPacketAction.normalizeTarget(target);
        synchronized (packetWaitLock) {
            waitingPacketName.set(normalizedTarget);
            lastReceivedPacket.set("");
            activePacketFuture = future;
            isWaitingForPacket.set(true);
        }
        try {
            setCurrentStatus(normalizedTarget.isEmpty()
                ? "Waiting for packet: Any"
                : "Waiting for packet: " + WaitForPacketAction.getDisplayLabel(normalizedTarget));
            waitForCondition(future);
        } finally {
            synchronized (packetWaitLock) {
                if (activePacketFuture == future) {
                    isWaitingForPacket.set(false);
                    waitingPacketName.set("");
                    activePacketFuture = null;
                }
            }
        }
    }

    private static void awaitChat(WaitForChatAction wca) {
        RunState run = CURRENT_RUN.get();
        if (run != null) {
            clearCaptureOutputs(wca);
            CompletableFuture<Void> future = new CompletableFuture<>();
            run.chatFuture = future;
            run.waitingChatAction = wca;
            run.waitingForChat.set(true);
            try {
                if (wca.timeoutMs > 0) future.completeOnTimeout(null, wca.timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                waitForCondition(future);
                commitStagedCapture(wca);
            } finally {
                if (run.chatFuture == future) {
                    run.waitingForChat.set(false);
                    run.waitingChatAction = null;
                    run.chatFuture = null;
                }
            }
            return;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        activeChatFuture = future;
        isWaitingForChat.set(true);
        waitingChatPattern.set(normalizeManualText(wca.pattern));
        waitingChatPatternJson.set(wca.patternJson == null ? "" : wca.patternJson);
        waitingChatIsRegex.set(wca.useRegex);
        waitingChatFuzzyPercent.set(WaitForChatAction.clampFuzzyPercent(wca.fuzzyPercent));
        lastMatchedChat.set("");
        try {
            if (wca.timeoutMs > 0) future.completeOnTimeout(null, wca.timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            waitForCondition(future);
        } finally {
            isWaitingForChat.set(false);
            waitingChatPattern.set("");
            waitingChatPatternJson.set("");
            waitingChatIsRegex.set(false);
            waitingChatFuzzyPercent.set(100);
            activeChatFuture = null;
        }
    }

    private static void awaitCaptureValueChat(CaptureValueAction action) {
        if (action == null) return;
        RunState run = CURRENT_RUN.get();
        clearCaptureOutputs(action);
        OneShotPacketListener listener = awaitReceive(packet -> {
            ChatCapture capture = extractIncomingChat(packet);
            if (capture == null) return false;
            RecentChatMessage message = new RecentChatMessage(
                capture.sender() == null ? "" : capture.sender(),
                capture.message() == null ? "" : capture.message(),
                capture.displayText() == null ? "" : capture.displayText(),
                capture.displayComponent(),
                capture.source(),
                System.currentTimeMillis()
            );
            CaptureValueAction.Preview preview = action.previewChat(message);
            if (!preview.success()) return false;
            if (run != null) run.stagedCaptures.put(action, preview.values());
            else MacroVariables.setAll(preview.values());
            return true;
        });
        try {
            if (waitForFutureDone(listener.future, 0) && run != null) commitStagedCapture(action);
        } finally {
            listener.cancel();
        }
    }

    static CaptureListSelector.State captureSelectionState(MacroAction action) {
        RunState run = CURRENT_RUN.get();
        if (run == null || action == null) return null;
        synchronized (run.captureSelection) {
            return run.captureSelection.computeIfAbsent(action, a -> new CaptureListSelector.State());
        }
    }

    private record CachedSuggestions(java.util.List<String> suggestions, Object connection) {}
    private record PendingFetch(CompletableFuture<java.util.List<String>> future, Object connection) {}
    private static final java.util.concurrent.ConcurrentHashMap<String, CachedSuggestions> SUGGESTION_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, PendingFetch> PENDING_FETCHES =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final int SUGGESTION_CACHE_MAX = 256;
    private static final long SUGGESTION_CACHE_IDLE_RESET_MS = 3000L;

    private static final int TRUNCATED_REPLY_SIZE = 1000;
    private static final int MIN_SWEEP_CAP = 8;
    private static final int MAX_SWEEP_NAMES = 60_000;
    private static final String DRILL_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_";
    private static volatile long allRunsStoppedAtMs;

    private static final class SuggestionUnion {
        final Object connection;
        final java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        final java.util.ArrayDeque<String> sweepQueue = new java.util.ArrayDeque<>();
        int alphabetCursor;
        int cap;

        SuggestionUnion(Object connection, String baseQuery) {
            this.connection = connection;
            sweepQueue.add(baseQuery);
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, SuggestionUnion> SUGGESTION_UNIONS =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static void expireSuggestionCacheAfterIdle() {
        long stoppedAt = allRunsStoppedAtMs;
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > SUGGESTION_CACHE_IDLE_RESET_MS) {
            SUGGESTION_CACHE.clear();
            PENDING_FETCHES.clear();
            SUGGESTION_UNIONS.clear();
        }
    }

    static String buildAutofillQuery(String resolvedCommand, CaptureListSelector.Filter filter, String filterText) {
        String query = resolvedCommand.startsWith("/") ? resolvedCommand : "/" + resolvedCommand;

        if (!query.contains(" ")) query = query + " ";
        String prefix = filter == CaptureListSelector.Filter.PREFIX && filterText != null ? filterText.trim() : "";
        if (!prefix.isEmpty() && !prefix.contains(" ") && query.endsWith(" ")) {

            int end = query.length();
            while (end > 0 && query.charAt(end - 1) == ' ') end--;
            query = query.substring(0, end) + " " + prefix;
        }
        return query;
    }

    private static java.util.List<String> capturePool(net.minecraft.client.multiplayer.ClientPacketListener connection,
                                                      CaptureValueAction action, String query, java.util.List<String> base) {

        if (!action.autofillCacheList && base.size() < TRUNCATED_REPLY_SIZE) return base;
        SuggestionUnion union = SUGGESTION_UNIONS.compute(query, (key, existing) ->
            existing != null && existing.connection == connection ? existing : new SuggestionUnion(connection, query));
        synchronized (union) {
            union.names.addAll(base);
            union.cap = Math.max(union.cap, base.size());
            advanceSweep(union, connection, action.autofillTimeoutMs);
            java.util.List<String> pool = new java.util.ArrayList<>(union.names);
            boolean ordered = action.listSelection == CaptureListSelector.Selection.SEQUENTIAL
                || action.listSelection == CaptureListSelector.Selection.FIRST
                || action.listSelection == CaptureListSelector.Selection.LAST
                || action.listSelection == CaptureListSelector.Selection.POSITION;

            if (ordered) pool.sort(String.CASE_INSENSITIVE_ORDER);
            return pool;
        }
    }

    private static void advanceSweep(SuggestionUnion union, net.minecraft.client.multiplayer.ClientPacketListener connection, int timeoutMs) {
        if (union.names.size() >= MAX_SWEEP_NAMES) { union.sweepQueue.clear(); return; }
        while (!union.sweepQueue.isEmpty()) {
            if (union.alphabetCursor >= DRILL_ALPHABET.length()) {
                union.sweepQueue.poll();
                union.alphabetCursor = 0;
                continue;
            }
            String target = union.sweepQueue.peek() + DRILL_ALPHABET.charAt(union.alphabetCursor);
            CachedSuggestions cached = SUGGESTION_CACHE.get(target);
            if (cached == null || cached.connection() != connection) {
                fireSuggestionFetch(connection, target, timeoutMs);
                return;
            }
            union.names.addAll(cached.suggestions());
            int size = cached.suggestions().size();

            boolean truncated = size >= union.cap && union.cap >= MIN_SWEEP_CAP;
            union.cap = Math.max(union.cap, size);
            if (truncated) union.sweepQueue.add(target);
            union.alphabetCursor++;
        }
    }

    private static void applyCaptureSuggestions(RunState run, CaptureValueAction action,
                                                java.util.List<String> suggestions, String query) {
        CaptureValueAction.Preview preview = action.previewSuggestions(suggestions, query);
        if (!preview.success()) return;
        if (run != null) run.variables.setAll(preview.values());
        else MacroVariables.setAll(preview.values());
    }

    private static PendingFetch fireSuggestionFetch(net.minecraft.client.multiplayer.ClientPacketListener connection,
                                                    String query, int timeoutMs) {
        PendingFetch existing = PENDING_FETCHES.get(query);
        if (existing != null && existing.connection() == connection && !existing.future().isDone()) return existing;
        if (PENDING_FETCHES.size() > SUGGESTION_CACHE_MAX) return existing;
        int requestId = autismclient.util.AutismCommandSuggestionIds.nextMacroId();
        CompletableFuture<java.util.List<String>> future = new CompletableFuture<>();
        OneShotPacketListener listener = awaitReceive(packet -> {
            if (!(packet instanceof net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket reply)
                    || reply.id() != requestId) {
                return false;
            }
            java.util.List<String> texts = new java.util.ArrayList<>();
            if (reply.suggestions() != null) {
                for (var entry : reply.suggestions()) {
                    if (entry != null && entry.text() != null && !entry.text().isBlank()) texts.add(entry.text());
                }
            }
            future.complete(texts);
            return true;
        });
        PendingFetch pending = new PendingFetch(future, connection);
        PENDING_FETCHES.put(query, pending);
        future.completeOnTimeout(null, timeoutMs > 0 ? Math.max(1000, timeoutMs) : 30_000,
            java.util.concurrent.TimeUnit.MILLISECONDS);
        future.whenComplete((texts, error) -> {
            listener.cancel();
            PENDING_FETCHES.remove(query, pending);

            if (texts != null) {
                if (SUGGESTION_CACHE.size() >= SUGGESTION_CACHE_MAX) SUGGESTION_CACHE.clear();
                SUGGESTION_CACHE.put(query, new CachedSuggestions(java.util.List.copyOf(texts), connection));
            }
        });
        try {
            connection.send(new net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket(requestId, query));
        } catch (Throwable error) {
            future.complete(null);
        }
        return pending;
    }

    private static void runCaptureCommandAutofill(Minecraft mc, CaptureValueAction action) {
        RunState run = CURRENT_RUN.get();
        clearCaptureOutputs(action);
        MacroTemplate.Resolution resolved = MacroVariables.resolve(action.autofillCommand, mc);
        if (!resolved.success() || resolved.value().isBlank()) return;
        String query = buildAutofillQuery(resolved.value(), action.listFilter, action.listFilterText);
        var connection = mc.getConnection();
        if (connection == null) return;

        if (action.autofillCacheList) {
            CachedSuggestions listCache = SUGGESTION_CACHE.get(query);
            if (listCache != null && listCache.connection() == connection && !listCache.suggestions().isEmpty()) {
                setCurrentStatus("Using cached suggestions");
                applyCaptureSuggestions(run, action, listCache.suggestions(), query);
                return;
            }
            setCurrentStatus("Sweeping command suggestions");
            java.util.List<String> all = sweepAutofillCommand(connection, query);
            if (all.isEmpty()) return;
            if (SUGGESTION_CACHE.size() >= SUGGESTION_CACHE_MAX) SUGGESTION_CACHE.clear();
            SUGGESTION_CACHE.put(query, new CachedSuggestions(java.util.List.copyOf(all), connection));
            applyCaptureSuggestions(run, action, all, query);
            return;
        }

        CachedSuggestions cached = SUGGESTION_CACHE.get(query);

        if (cached != null && (cached.connection() != connection || cached.suggestions().isEmpty())) cached = null;

        if (cached != null) {

            setCurrentStatus("Using cached suggestions");
            applyCaptureSuggestions(run, action, capturePool(connection, action, query, cached.suggestions()), query);
            fireSuggestionFetch(connection, query, action.autofillTimeoutMs);
            return;
        }

        setCurrentStatus("Requesting command suggestions");
        PendingFetch pending = fireSuggestionFetch(connection, query, action.autofillTimeoutMs);
        if (pending == null) return;
        if (!waitForFutureDone(pending.future(), 0)) return;
        java.util.List<String> suggestions = pending.future().getNow(null);
        if (suggestions == null || suggestions.isEmpty()) return;
        applyCaptureSuggestions(run, action, capturePool(connection, action, query, suggestions), query);
    }

    private static java.util.List<String> sweepAutofillCommand(net.minecraft.client.multiplayer.ClientPacketListener connection, String query) {
        autismclient.util.AutismNameHarvest.Control control = new autismclient.util.AutismNameHarvest.Control() {
            @Override public boolean cancelled() {
                Minecraft mc = Minecraft.getInstance();
                return !isCurrentRunActive() || mc == null || mc.getConnection() != connection;
            }
            @Override public boolean paused() { return false; }
            @Override public int limit() { return MAX_SWEEP_NAMES; }
            @Override public int maxQueries() { return 50_000; }
            @Override public boolean usernamesOnly() { return false; }
        };
        java.util.List<String> all = autismclient.util.AutismNameHarvest.sweepCommand(connection, query, control);
        all.sort(String.CASE_INSENSITIVE_ORDER);
        return all;
    }

    private static void runCaptureNamescrape(Minecraft mc, CaptureValueAction action) {
        RunState run = CURRENT_RUN.get();
        clearCaptureOutputs(action);
        var connection = mc.getConnection();
        if (connection == null) return;
        String cacheKey = " namescrape";
        CachedSuggestions cached = SUGGESTION_CACHE.get(cacheKey);
        if (cached != null && cached.connection() == connection && !cached.suggestions().isEmpty()) {
            setCurrentStatus("Using cached names");
            applyCaptureSuggestions(run, action, cached.suggestions(), "");
            return;
        }
        setCurrentStatus("Scraping player names");
        java.util.List<String> names = scrapeAllNames(mc, connection);
        if (names.isEmpty()) return;
        if (SUGGESTION_CACHE.size() >= SUGGESTION_CACHE_MAX) SUGGESTION_CACHE.clear();
        SUGGESTION_CACHE.put(cacheKey, new CachedSuggestions(java.util.List.copyOf(names), connection));
        applyCaptureSuggestions(run, action, names, "");
    }

    private static java.util.List<String> scrapeAllNames(Minecraft mc, net.minecraft.client.multiplayer.ClientPacketListener connection) {
        String self = mc.player == null ? "" : mc.player.getName().getString();
        java.util.LinkedHashMap<String, String> names = autismclient.util.AutismNameHarvest.instantNames(mc, self);
        java.util.List<autismclient.util.AutismNameHarvest.Vector> vectors =
                autismclient.util.AutismNameHarvest.discoverVectors(connection);
        autismclient.util.AutismNameHarvest.Control control = new autismclient.util.AutismNameHarvest.Control() {
            @Override public boolean cancelled() {
                Minecraft m = Minecraft.getInstance();
                return !isCurrentRunActive() || m == null || m.getConnection() != connection;
            }
            @Override public boolean paused() { return false; }
            @Override public int limit() { return MAX_SWEEP_NAMES; }
            @Override public int maxQueries() { return 100_000; }
        };
        autismclient.util.AutismNameHarvest.sweep(connection, names, self, vectors, control);
        java.util.List<String> out = new java.util.ArrayList<>(names.values());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static void runCaptureValueAction(Minecraft mc, CaptureValueAction action) {
        if (action == null) return;
        if (!action.numberModifierError().isBlank()) return;
        if (action.source == CaptureValueAction.Source.COMMAND_AUTOFILL) {
            setCurrentStatus("Requesting command suggestions");
            runCaptureCommandAutofill(mc, action);
            return;
        }
        if (action.source == CaptureValueAction.Source.NAMESCRAPE) {
            setCurrentStatus("Scraping player names");
            runCaptureNamescrape(mc, action);
            return;
        }
        if (action.waitForTrigger) {
            CaptureValueAction.Source source = action.source == null ? CaptureValueAction.Source.GUI_TITLE : action.source;
            setCurrentStatus("Waiting for capture: " + source.name().toLowerCase(Locale.ROOT).replace('_', ' '));
            if (source == CaptureValueAction.Source.RECENT_CHAT) awaitCaptureValueChat(action);
            else if (source == CaptureValueAction.Source.SCOREBOARD) awaitCaptureValueScoreboard(mc, action);
            else awaitCaptureValueState(mc, action);
            return;
        }
        RunState run = CURRENT_RUN.get();
        CompletableFuture<Void> done = new CompletableFuture<>();
        mc.execute(() -> withRunContext(run, () -> {
            try {
                action.execute(mc);
                done.complete(null);
            } catch (Throwable error) {
                done.completeExceptionally(error);
            }
        }));
        waitForFutureDone(done, 0);
    }

    private static void awaitCaptureValueState(Minecraft mc, CaptureValueAction action) {
        RunState run = CURRENT_RUN.get();
        clearCaptureOutputs(action);
        while (isCurrentRunActive()) {
            CaptureValueAction.Preview preview;
            try {
                preview = callOnClientThread(mc, () -> action.previewCurrent(mc),
                    CaptureValueAction.Preview.unavailable("Not available"));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (preview.success()) {
                if (run != null) run.variables.setAll(preview.values());
                else MacroVariables.setAll(preview.values());
                return;
            }
            waitOneTick();
        }
    }

    private static void awaitCaptureValueScoreboard(Minecraft mc, CaptureValueAction action) {
        RunState run = CURRENT_RUN.get();
        clearCaptureOutputs(action);
        String trackedKey = action.scoreboardRow;
        int trackedRow = action.scoreboardRowIndex;
        String trackedObjective = action.scoreboardObjective;
        String previousText = null;
        boolean baselineReady = false;
        while (isCurrentRunActive()) {
            CaptureValueAction.ScoreboardLine line;
            try {
                String keySnapshot = trackedKey;
                int rowSnapshot = trackedRow;
                String objectiveSnapshot = trackedObjective;
                line = callOnClientThread(mc,
                    () -> action.resolveScoreboardLine(mc, keySnapshot, rowSnapshot, objectiveSnapshot), null);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (line != null) {
                String currentText = line.text() == null ? "" : line.text();
                if (!baselineReady) {
                    trackedKey = line.key();
                    trackedRow = line.row();
                    trackedObjective = line.objective();
                    previousText = currentText;
                    baselineReady = true;
                } else if (!currentText.equals(previousText)) {
                    CaptureValueAction.Preview preview = action.previewScoreboardLine(line);
                    if (preview.success()) {
                        if (run != null) run.variables.setAll(preview.values());
                        else MacroVariables.setAll(preview.values());
                        return;
                    }
                    previousText = currentText;
                }
            }
            waitOneTick();
        }
    }

    private static void waitForCondition(CompletableFuture<Void> future) {

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

    private static void waitForPredicate(java.util.function.BooleanSupplier predicate, int timeoutMs) {
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

    private static java.util.function.BooleanSupplier guiClosePredicate(WaitForGuiAction action, Minecraft mc) {
        AtomicBoolean sawMatchingGui = new AtomicBoolean(action.checkGui(mc));
        return () -> {
            var capture = mc == null ? java.util.Optional.<java.util.Map<String, MacroValue>>empty() : action.captureGui(mc.gui.screen());
            boolean matches = capture.isPresent();
            if (matches) {
                capture.ifPresent(values -> stageCapture(action, values));
                sawMatchingGui.set(true);
                return false;
            }
            return sawMatchingGui.get();
        };
    }

    private static java.util.function.BooleanSupplier guiClosePredicate(WaitGuiTypeAction action, Minecraft mc) {
        AtomicBoolean sawMatchingGui = new AtomicBoolean(action.matches(mc));
        return () -> {
            var capture = action.capture(mc);
            boolean matches = capture.isPresent();
            if (matches) {
                capture.ifPresent(values -> stageCapture(action, values));
                sawMatchingGui.set(true);
                return false;
            }
            return sawMatchingGui.get();
        };
    }

    private static void waitForDurabilityUseNext(Minecraft mc, WaitDurabilityAction action) throws InterruptedException {
        AtomicReference<DurabilityUseNextSession> sessionRef = new AtomicReference<>();
        long start = System.currentTimeMillis();
        while (isCurrentRunActive()) {
            if (Boolean.TRUE.equals(callOnClientThread(mc, () -> tickDurabilityUseNext(mc, action, sessionRef), Boolean.FALSE))) {
                return;
            }
            if (action.timeoutMs > 0 && System.currentTimeMillis() - start >= action.timeoutMs) return;
            waitOneTick();
        }
    }

    private static boolean durabilityObservationMatches(Minecraft mc, WaitDurabilityAction action) {
        if (action == null || !action.useNext) return action != null && action.matches(mc);
        DurabilityUseNextSession session = createDurabilityUseNextSession(mc, action);
        if (session == null || session.fallbackOnly) return action.matches(mc);
        if (session.firstSlotPending) {
            ItemStack stack = stackForVisibleSlot(mc, session.activeSlot);
            if (session.lockedRegistryId.isBlank() && canLockDurabilityInitialStack(action, session, stack)) {
                session.lockedRegistryId = stackRegistryId(stack);
            }
            if (session.lockedRegistryId.isBlank()) return false;
            return isSameDamageableItem(stack, session.lockedRegistryId)
                && action.matchesStack(stack)
                && findDurabilityReplacement(mc, action, session, session.activeSlot) == null;
        }
        if (session.lockedRegistryId.isBlank()) return action.matches(mc);
        return findDurabilityReplacement(mc, action, session, -1) == null;
    }

    private static boolean tickDurabilityUseNext(Minecraft mc, WaitDurabilityAction action, AtomicReference<DurabilityUseNextSession> sessionRef) {
        if (mc == null || mc.player == null || action == null) return false;
        DurabilityUseNextSession session = sessionRef.get();
        if (session == null) {
            session = createDurabilityUseNextSession(mc, action);
            sessionRef.set(session);
        }
        if (session == null || session.fallbackOnly) return action.matches(mc);

        if (session.firstSlotPending) {
            return tickDurabilityInitialSlot(mc, action, session);
        }

        adoptCurrentSameItem(mc, session);
        ItemStack active = stackForVisibleSlot(mc, session.activeSlot);
        if (isSameDamageableItem(active, session.lockedRegistryId) && !action.matchesStack(active)) {
            return false;
        }
        return switchToNextDurabilityItemOrComplete(mc, action, session, session.activeSlot);
    }

    private static boolean tickDurabilityInitialSlot(Minecraft mc, WaitDurabilityAction action, DurabilityUseNextSession session) {
        ItemStack stack = stackForVisibleSlot(mc, session.activeSlot);
        if (session.lockedRegistryId.isBlank() && canLockDurabilityInitialStack(action, session, stack)) {
            session.lockedRegistryId = stackRegistryId(stack);
        }

        if (!session.lockedRegistryId.isBlank() && isSameDamageableItem(stack, session.lockedRegistryId)) {
            session.observedInitialSlot = true;
            if (!action.matchesStack(stack)) return false;
            session.firstSlotPending = false;
            return switchToNextDurabilityItemOrComplete(mc, action, session, session.activeSlot);
        }

        if (session.observedInitialSlot) {
            session.firstSlotPending = false;
            return switchToNextDurabilityItemOrComplete(mc, action, session, session.activeSlot);
        }
        return false;
    }

    private static boolean switchToNextDurabilityItemOrComplete(Minecraft mc, WaitDurabilityAction action, DurabilityUseNextSession session, int excludeSlot) {
        DurabilityCandidate replacement = findDurabilityReplacement(mc, action, session, excludeSlot);
        if (replacement == null) return true;
        if (!switchToDurabilityReplacement(mc, session, replacement)) return false;
        return false;
    }

    private static DurabilityUseNextSession createDurabilityUseNextSession(Minecraft mc, WaitDurabilityAction action) {
        if (mc == null || mc.player == null || action == null) return null;
        DurabilityUseNextSession session = new DurabilityUseNextSession();
        session.preferredHotbarSlot = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
        ItemTarget target = action.targetMode == WaitDurabilityAction.TargetMode.ITEM
            ? ItemTarget.fromLegacyEntry(action.itemName)
            : new ItemTarget();
        session.initialTarget = target;

        switch (action.targetMode) {
            case SLOT -> {
                session.activeSlot = Math.max(0, Math.min(40, action.slot));
                session.firstSlotPending = true;
                ItemStack stack = stackForVisibleSlot(mc, session.activeSlot);
                if (canLockDurabilityInitialStack(action, session, stack)) {
                    session.lockedRegistryId = stackRegistryId(stack);
                    session.observedInitialSlot = true;
                }
            }
            case HELD -> initializeHeldDurabilitySession(mc, action, session);
            case ITEM -> initializeItemDurabilitySession(mc, action, session, target);
        }

        if (session.lockedRegistryId == null) session.lockedRegistryId = "";
        if (session.lockedRegistryId.isBlank() && !session.firstSlotPending) {
            session.fallbackOnly = true;
        }
        return session;
    }

    private static void initializeHeldDurabilitySession(Minecraft mc, WaitDurabilityAction action, DurabilityUseNextSession session) {
        int selected = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
        ItemStack main = mc.player.getInventory().getItem(selected);
        if (main != null && !main.isEmpty() && main.isDamageableItem()) {
            session.activeSlot = selected;
            session.lockedRegistryId = stackRegistryId(main);
            return;
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand != null && !offhand.isEmpty() && offhand.isDamageableItem()) {
            session.activeSlot = 40;
            session.lockedRegistryId = stackRegistryId(offhand);
            return;
        }
        session.fallbackOnly = true;
    }

    private static void initializeItemDurabilitySession(Minecraft mc, WaitDurabilityAction action, DurabilityUseNextSession session, ItemTarget target) {
        if (target != null && target.hasSlot()) {
            session.activeSlot = Math.max(0, Math.min(40, target.slot));
            session.firstSlotPending = true;
            String explicitId = target.hasRegistryId() ? canonicalItemRegistryId(target.registryId) : "";
            if (!explicitId.isBlank()) session.lockedRegistryId = explicitId;
            ItemStack stack = stackForVisibleSlot(mc, session.activeSlot);
            if (canLockDurabilityInitialStack(action, session, stack)) {
                session.lockedRegistryId = stackRegistryId(stack);
                session.observedInitialSlot = true;
            }
            return;
        }

        if (target != null && target.hasRegistryId()) {
            session.lockedRegistryId = canonicalItemRegistryId(target.registryId);
            session.activeSlot = findCurrentUsableDurabilitySlot(mc, action, session.lockedRegistryId, target);
            return;
        }

        if (target == null || !target.hasIdentity()) {
            initializeHeldDurabilitySession(mc, action, session);
            return;
        }

        int slot = findInitialDurabilitySlot(mc, action, "", target);
        if (slot >= 0) {
            session.activeSlot = slot;
            session.lockedRegistryId = stackRegistryId(stackForVisibleSlot(mc, slot));
        } else {
            session.fallbackOnly = true;
        }
    }

    private static int findCurrentUsableDurabilitySlot(Minecraft mc, WaitDurabilityAction action, String lockedRegistryId, ItemTarget target) {
        if (mc == null || mc.player == null) return -1;
        int selected = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
        if (initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, selected)) return selected;
        return initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, 40) ? 40 : -1;
    }

    private static boolean canLockDurabilityInitialStack(WaitDurabilityAction action, DurabilityUseNextSession session, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) return false;
        if (action != null && action.targetMode == WaitDurabilityAction.TargetMode.ITEM
            && session != null && session.initialTarget != null && session.initialTarget.hasIdentity()) {
            return session.initialTarget.score(stack, session.activeSlot) >= 0;
        }
        return true;
    }

    private static int findInitialDurabilitySlot(Minecraft mc, WaitDurabilityAction action, String lockedRegistryId, ItemTarget target) {
        if (mc == null || mc.player == null) return -1;
        int selected = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
        if (initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, selected)) return selected;
        for (int slot = 0; slot < 9; slot++) {
            if (slot != selected && initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, slot)) return slot;
        }
        for (int slot = 9; slot < Math.min(36, mc.player.getInventory().getContainerSize()); slot++) {
            if (initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, slot)) return slot;
        }
        return initialDurabilitySlotMatches(mc, action, lockedRegistryId, target, 40) ? 40 : -1;
    }

    private static boolean initialDurabilitySlotMatches(Minecraft mc, WaitDurabilityAction action, String lockedRegistryId, ItemTarget target, int slot) {
        ItemStack stack = stackForVisibleSlot(mc, slot);
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) return false;
        if (lockedRegistryId != null && !lockedRegistryId.isBlank() && !isSameDamageableItem(stack, lockedRegistryId)) return false;
        return target == null || !target.hasIdentity() || target.score(stack, slot) >= 0;
    }

    private static void adoptCurrentSameItem(Minecraft mc, DurabilityUseNextSession session) {
        if (mc == null || mc.player == null || session == null || session.lockedRegistryId.isBlank()) return;
        int selected = Math.max(0, Math.min(8, mc.player.getInventory().getSelectedSlot()));
        if (isSameDamageableItem(mc.player.getInventory().getItem(selected), session.lockedRegistryId)) {
            session.activeSlot = selected;
            session.preferredHotbarSlot = selected;
            return;
        }
        if (isSameDamageableItem(mc.player.getOffhandItem(), session.lockedRegistryId)) {
            ItemStack active = stackForVisibleSlot(mc, session.activeSlot);
            if (!isSameDamageableItem(active, session.lockedRegistryId)) session.activeSlot = 40;
        }
    }

    private static DurabilityCandidate findDurabilityReplacement(Minecraft mc, WaitDurabilityAction action, DurabilityUseNextSession session, int excludeSlot) {
        if (mc == null || mc.player == null || action == null || session == null || session.lockedRegistryId.isBlank()) return null;
        DurabilityCandidate hotbar = null;
        DurabilityCandidate inventory = null;
        DurabilityCandidate offhand = null;
        for (int slot = 0; slot < 9; slot++) {
            DurabilityCandidate candidate = durabilityCandidate(mc, action, session.lockedRegistryId, slot, excludeSlot);
            if (candidate != null && (hotbar == null || candidate.score() > hotbar.score())) hotbar = candidate;
        }
        int size = Math.min(36, mc.player.getInventory().getContainerSize());
        for (int slot = 9; slot < size; slot++) {
            DurabilityCandidate candidate = durabilityCandidate(mc, action, session.lockedRegistryId, slot, excludeSlot);
            if (candidate != null && (inventory == null || candidate.score() > inventory.score())) inventory = candidate;
        }
        offhand = durabilityCandidate(mc, action, session.lockedRegistryId, 40, excludeSlot);
        if (hotbar != null) return hotbar;
        if (inventory != null) return inventory;
        return offhand;
    }

    private static DurabilityCandidate durabilityCandidate(Minecraft mc, WaitDurabilityAction action, String registryId, int slot, int excludeSlot) {
        if (slot == excludeSlot) return null;
        ItemStack stack = stackForVisibleSlot(mc, slot);
        if (!isSameDamageableItem(stack, registryId)) return null;
        if (action.matchesStack(stack)) return null;
        return new DurabilityCandidate(slot, remainingDurability(stack));
    }

    private static boolean switchToDurabilityReplacement(Minecraft mc, DurabilityUseNextSession session, DurabilityCandidate replacement) {
        if (mc == null || mc.player == null || replacement == null) return false;
        int slot = replacement.slot();
        if (slot >= 0 && slot < 9) {
            AutismInventoryHelper.selectHotbarSlot(mc, slot);
            session.activeSlot = slot;
            session.preferredHotbarSlot = slot;
            return true;
        }
        if (slot >= 9 && slot < 36) {
            int target = Math.max(0, Math.min(8, session.preferredHotbarSlot));
            if (!AutismInventoryHelper.swapInventorySlots(mc, slot, target)) return false;
            AutismInventoryHelper.selectHotbarSlot(mc, target);
            session.activeSlot = target;
            session.preferredHotbarSlot = target;
            return true;
        }
        if (slot == 40) {
            session.activeSlot = 40;
            return true;
        }
        return false;
    }

    private static ItemStack stackForVisibleSlot(Minecraft mc, int visibleSlot) {
        if (mc == null || mc.player == null) return ItemStack.EMPTY;
        if (visibleSlot == 40) return mc.player.getOffhandItem();
        if (visibleSlot < 0 || visibleSlot >= mc.player.getInventory().getContainerSize()) return ItemStack.EMPTY;
        return mc.player.getInventory().getItem(visibleSlot);
    }

    private static boolean isSameDamageableItem(ItemStack stack, String registryId) {
        return stack != null && !stack.isEmpty() && stack.isDamageableItem() && stackRegistryId(stack).equals(registryId);
    }

    private static int remainingDurability(ItemStack stack) {
        return stack == null || !stack.isDamageableItem() ? 0 : Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    private static String stackRegistryId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static String canonicalItemRegistryId(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        Identifier parsed = Identifier.tryParse(trimmed.contains(":") ? trimmed : "minecraft:" + trimmed);
        if (parsed == null) return "";
        return BuiltInRegistries.ITEM.getOptional(parsed)
            .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
            .orElse("");
    }

    private static final class DurabilityUseNextSession {
        private String lockedRegistryId = "";
        private int activeSlot = -1;
        private int preferredHotbarSlot = 0;
        private boolean firstSlotPending = false;
        private boolean observedInitialSlot = false;
        private boolean fallbackOnly = false;
        private ItemTarget initialTarget = new ItemTarget();
    }

    private record DurabilityCandidate(int slot, int score) {}

    private static CompletableFuture<Void> predicateFuture(java.util.function.BooleanSupplier predicate, int timeoutMs) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        RunState run = CURRENT_RUN.get();
        Thread worker = new Thread(() -> withRunContext(run, () -> {
            long start = System.currentTimeMillis();
            while (!future.isDone() && isCurrentRunActive()) {
                try {
                    if (predicate.getAsBoolean()) {
                        future.complete(null);
                        return;
                    }
                } catch (Throwable ignored) {  }
                if (timeoutMs > 0 && System.currentTimeMillis() - start >= timeoutMs) {
                    future.complete(null);
                    return;
                }
                java.util.concurrent.locks.LockSupport.parkNanos(MACRO_WAIT_POLL_NANOS);
                if (Thread.interrupted()) return;
            }
        }), "MacroPredicateWait");
        worker.setDaemon(true);
        worker.start();
        future.whenComplete((v, e) -> worker.interrupt());
        return future;
    }

    private static void prearmNextActionWait(Minecraft mc, java.util.List<MacroAction> actions, int currentIndex) {
        RunState run = CURRENT_RUN.get();
        if (run == null || actions == null) return;
        int nextIndex = currentIndex + 1;
        if (nextIndex < 0 || nextIndex >= actions.size()) return;
        MacroAction next = actions.get(nextIndex);
        if (next == null || !next.isEnabled() || !next.listensDuringPreviousAction()) return;
        if (!MacroDynamicBindings.apply(next, mc)) return;
        if (!dynamicTextInputsAvailable(next, mc)) return;
        if (!isWaitConditionAction(next)) return;
        synchronized (run.prearmedWaits) {
            if (run.prearmedWaits.containsKey(next)) return;
            clearCaptureOutputs(next);
            clearOwnCaptureOutput(next);
            PrearmedWait wait = createPrearmedWait(mc, next);
            if (wait != null) run.prearmedWaits.put(next, wait);
        }
    }

    private static boolean isWaitConditionAction(MacroAction action) {
        return action instanceof WaitForPacketAction
            || action instanceof WaitPacketMatchAction
            || action instanceof WaitForChatAction
            || action instanceof WaitForGuiAction
            || action instanceof WaitGuiTypeAction
            || action instanceof WaitForSoundAction
            || action instanceof WaitForSlotChangeAction
            || action instanceof WaitInventoryPredicateAction
            || action instanceof WaitDurabilityAction
            || action instanceof WaitFreeSlotsAction
            || action instanceof WaitForHealthAction
            || action instanceof WaitForBlockAction
            || action instanceof WaitForEntityAction
            || action instanceof WaitEntityTargetAction
            || action instanceof WaitForCooldownAction
            || action instanceof WaitPosAction
            || action instanceof WaitForPositionDeltaAction
            || action instanceof WaitForTeleportAction
            || action instanceof WaitGamemodeChangeAction
            || action instanceof WaitForWorldChangeAction
            || action instanceof WaitMovementAction
            || action instanceof WaitForLanStepAction
            || action instanceof WaitForMacroStepAction;
    }

    private static PrearmedWait consumePrearmedWait(MacroAction action) {
        RunState run = CURRENT_RUN.get();
        if (run == null || action == null) return null;
        synchronized (run.prearmedWaits) {
            return run.prearmedWaits.remove(action);
        }
    }

    private static boolean waitForPrearmed(MacroAction action, long timeoutMs) {
        PrearmedWait wait = consumePrearmedWait(action);
        if (wait == null) return false;
        try {
            waitForFutureDone(wait.future, timeoutMs);
            commitStagedCapture(action);
            return true;
        } finally {
            wait.cleanup();
        }
    }

    private static PrearmedWait createPrearmedWait(Minecraft mc, MacroAction action) {
        if (action instanceof WaitForPacketAction packetAction) {
            java.util.List<String> targets = packetAction.effectiveList();
            String firstTarget = targets.isEmpty() ? "" : targets.get(0);
            return packetTargetFuture(packetAction, firstTarget, 1);
        }
        if (action instanceof WaitPacketMatchAction packetMatch) {
            return packetMatchFuture(packetMatch);
        }
        if (action instanceof WaitInventoryPredicateAction inv) {
            inv.resetBaseline();
            return predicatePrearm(action, () -> inv.matches(mc), inv.timeoutMs);
        }
        if (action instanceof WaitDurabilityAction durability) {
            if (durability.useNext) return null;
            return predicatePrearm(action, () -> durabilityObservationMatches(mc, durability), durability.timeoutMs);
        }
        if (action instanceof WaitFreeSlotsAction freeSlots) {
            return predicatePrearm(action, () -> freeSlots.matches(mc), freeSlots.timeoutMs);
        }
        if (action instanceof WaitEntityTargetAction entityTarget) {
            return predicatePrearm(action, () -> entityTarget.matches(mc), entityTarget.timeoutMs);
        }
        if (action instanceof WaitGuiTypeAction guiType) {
            return predicatePrearm(action,
                guiType.waitMode == WaitGuiTypeAction.WaitMode.CLOSE
                    ? guiClosePredicate(guiType, mc)
                    : () -> {
                        var capture = guiType.capture(mc);
                        capture.ifPresent(values -> stageCapture(guiType, values));
                        return capture.isPresent();
                    },
                guiType.timeoutMs);
        }
        if (action instanceof WaitForChatAction chat) {
            RunState run = CURRENT_RUN.get();
            OneShotPacketListener listener = awaitReceive(packet -> {
                ChatCapture capture = extractIncomingChat(packet);
                var matched = captureChatAction(chat, capture, run == null ? null : run.variables);
                if (matched.isEmpty()) return false;
                if (run != null) run.stagedCaptures.put(chat, matched.get());
                return true;
            });
            CompletableFuture<?> future = listener.future;
            if (chat.timeoutMs > 0) future.completeOnTimeout(null, chat.timeoutMs, TimeUnit.MILLISECONDS);
            return new PrearmedWait(action, future, listener::cancel, 0);
        }
        if (action instanceof WaitForMacroStepAction macroStep) {
            return predicatePrearm(action, () -> isMacroStepSatisfied(macroStep), macroStep.timeoutMs);
        }
        if (action instanceof WaitForLanStepAction lanStep) {
            CompletableFuture<Void> future = MacroConditionRegistry.waitForLanStep(lanStep);
            return new PrearmedWait(action, future, () -> {
                if (!future.isDone()) future.cancel(true);
            }, 0);
        }
        CompletableFuture<?> future = raceConditionFuture(mc, action);
        if (future == null) return null;
        int timeout = action instanceof WaitForChatAction chat ? chat.timeoutMs : 0;
        if (timeout > 0) future.completeOnTimeout(null, timeout, TimeUnit.MILLISECONDS);
        return new PrearmedWait(action, future, () -> {
            if (!future.isDone()) future.cancel(true);
        }, 0);
    }

    private static boolean hasPrearmedWait(MacroAction action) {
        RunState run = CURRENT_RUN.get();
        if (run == null || action == null) return false;
        synchronized (run.prearmedWaits) {
            return run.prearmedWaits.containsKey(action);
        }
    }

    private static void recordMatchedPacket(MacroAction action, Packet<?> packet, String direction) {
        if (action instanceof WaitForPacketAction wait) {
            wait.matchedPacket = packet;
            wait.matchedDirection = direction == null ? "" : direction;
        } else if (action instanceof WaitPacketMatchAction wait) {
            wait.matchedPacket = packet;
            wait.matchedDirection = direction == null ? "" : direction;
        }
    }

    private static void copyLastMatchedPacket(WaitForPacketAction action) {
        RunState run = CURRENT_RUN.get();
        if (action == null || run == null || run.lastMatchedPacket == null) return;
        action.matchedPacket = run.lastMatchedPacket;
        action.matchedDirection = run.lastMatchedPacketDirection;
    }

    private static void clearCaptureOutputs(MacroAction action) {
        RunState run = CURRENT_RUN.get();
        if (run == null || action == null) return;
        run.stagedCaptures.remove(action);
        if (action instanceof WaitForChatAction chat) {
            for (String name : MacroCapturePattern.declaredNames(chat.effectiveMatchMode(), chat.pattern)) run.variables.remove(name);
            if (chat.saveAs != null && !chat.saveAs.isBlank()) run.variables.remove(chat.saveAs);
        } else if (action instanceof WaitForGuiAction gui) {
            for (String name : MacroCapturePattern.declaredNames(gui.matchMode, gui.guiTitle)) run.variables.remove(name);
            if (gui.saveAs != null && !gui.saveAs.isBlank()) run.variables.remove(gui.saveAs);
        } else if (action instanceof WaitGuiTypeAction gui) {
            for (String name : MacroCapturePattern.declaredNames(gui.matchMode, gui.title)) run.variables.remove(name);
            if (gui.saveAs != null && !gui.saveAs.isBlank()) run.variables.remove(gui.saveAs);
        } else if (action instanceof CaptureValueAction capture) {
            for (String name : MacroCapturePattern.declaredNames(capture.matchMode, capture.pattern)) run.variables.remove(name);
            if (capture.saveAs != null && !capture.saveAs.isBlank()) run.variables.remove(capture.saveAs);
        }
    }

    private static void clearOwnCaptureOutput(MacroAction action) {
        if (!(action instanceof MacroCaptureOutput output)) return;
        String name = output.getSaveAs();
        if (name != null && !name.isBlank()) MacroVariables.remove(name);
        if (action instanceof WaitForSoundAction sound) sound.matchedSoundId = "";
        if (action instanceof WaitForPacketAction packet) {
            packet.matchedPacket = null;
            packet.matchedDirection = "";
        }
        if (action instanceof WaitPacketMatchAction packet) {
            packet.matchedPacket = null;
            packet.matchedDirection = "";
        }
    }

    private static void commitConditionOutput(MacroAction action, Minecraft mc) throws InterruptedException {
        if (!(action instanceof MacroCaptureOutput output)) return;
        String name = MacroVariableContext.cleanRootName(output.getSaveAs());
        if (name.isEmpty()) return;
        MacroValue value = callOnClientThread(mc, () -> conditionOutputValue(action, mc), null);
        if (value != null) MacroVariables.setValue(name, value);
    }

    private static MacroValue conditionOutputValue(MacroAction action, Minecraft mc) {
        if (action instanceof WaitForHealthAction healthAction && mc != null && mc.player != null) {
            float health = mc.player.getHealth();
            if (healthAction.below ? health >= healthAction.healthThreshold : health <= healthAction.healthThreshold) return null;
            return MacroValue.structured(MacroValue.Kind.NUMBER, Float.toString(health), java.util.Map.of(
                "health", MacroValue.number(health),
                "max", MacroValue.number(mc.player.getMaxHealth()),
                "absorption", MacroValue.number(mc.player.getAbsorptionAmount())
            ));
        }
        if (action instanceof WaitForEntityAction entityAction) {
            net.minecraft.world.entity.Entity entity = entityAction.findMatchingEntity(mc);
            if (entity == null) return null;
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            double distance = mc != null && mc.player != null ? mc.player.distanceTo(entity) : 0.0;
            return MacroValue.structured(MacroValue.Kind.ENTITY, entity.getName().getString(), java.util.Map.of(
                "name", MacroValue.text(entity.getName().getString()),
                "id", MacroValue.identifier(id),
                "uuid", MacroValue.text(entity.getStringUUID()),
                "x", MacroValue.number(entity.getX()),
                "y", MacroValue.number(entity.getY()),
                "z", MacroValue.number(entity.getZ()),
                "distance", MacroValue.number(distance)
            ));
        }
        if (action instanceof WaitForSoundAction sound) {
            if (sound.matchedSoundId == null || sound.matchedSoundId.isBlank()) return null;
            return MacroValue.structured(MacroValue.Kind.IDENTIFIER, sound.matchedSoundId, java.util.Map.of(
                "id", MacroValue.identifier(sound.matchedSoundId),
                "x", MacroValue.number(sound.matchedX),
                "y", MacroValue.number(sound.matchedY),
                "z", MacroValue.number(sound.matchedZ)
            ));
        }
        if (action instanceof WaitForPacketAction packet) {
            return packetMacroValue(packet.matchedPacket, packet.matchedDirection);
        }
        if (action instanceof WaitPacketMatchAction packet) {
            return packetMacroValue(packet.matchedPacket, packet.matchedDirection);
        }
        if (action instanceof WaitInventoryPredicateAction inventory) {
            if (!inventory.matches(mc)) return null;
            return inventoryMacroValue(mc, inventory.itemName, inventory.slot, inventory.condition.name());
        }
        if (action instanceof WaitForSlotChangeAction slotWait) {
            if (slotWait.entries.isEmpty()) return inventoryMacroValue(mc, "", -1, "slot_change");
            ItemTarget target = slotWait.entries.get(0).resolvedTarget().resolveTemplate(mc);
            return target == null ? null : itemTargetMacroValue(mc, target);
        }
        if (action instanceof WaitPosAction || action instanceof WaitForTeleportAction
                || action instanceof WaitForPositionDeltaAction) {
            return playerPositionMacroValue(mc);
        }
        if (action instanceof WaitForWorldChangeAction && mc != null && mc.level != null) {
            String dimension = mc.level.dimension().identifier().toString();
            return MacroValue.structured(MacroValue.Kind.IDENTIFIER, dimension, java.util.Map.of(
                "id", MacroValue.identifier(dimension), "name", MacroValue.text(dimension)));
        }
        if (action instanceof WaitGamemodeChangeAction && mc != null && mc.gameMode != null) {
            String mode = mc.gameMode.getPlayerMode().getName();
            WaitGamemodeChangeAction wait = (WaitGamemodeChangeAction) action;
            if (wait.match == WaitGamemodeChangeAction.Match.TO_MODE && !wait.accepts(mc.gameMode.getPlayerMode())) return null;
            return MacroValue.structured(MacroValue.Kind.TEXT, mode, java.util.Map.of("name", MacroValue.text(mode)));
        }
        return null;
    }

    private static MacroValue playerPositionMacroValue(Minecraft mc) {
        if (mc == null || mc.player == null) return null;
        String display = String.format(Locale.ROOT, "%.3f, %.3f, %.3f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
        return MacroValue.structured(MacroValue.Kind.POSITION, display, java.util.Map.of(
            "x", MacroValue.number(mc.player.getX()), "y", MacroValue.number(mc.player.getY()),
            "z", MacroValue.number(mc.player.getZ()), "yaw", MacroValue.number(mc.player.getYRot()),
            "pitch", MacroValue.number(mc.player.getXRot())
        ));
    }

    private static MacroValue inventoryMacroValue(Minecraft mc, String itemTemplate, int slot, String condition) {
        if (mc == null || mc.player == null) return null;
        MacroTemplate.Resolution item = MacroVariables.resolve(itemTemplate, mc);
        if (!item.success()) return null;
        ItemTarget target = ItemTarget.fromLegacyEntry(item.value());
        if (slot >= 0) {
            int safeSlot = Math.max(0, Math.min(mc.player.getInventory().getContainerSize() - 1, slot));
            MacroValue stack = stackMacroValue(mc.player.getInventory().getItem(safeSlot), safeSlot);
            return stack == null ? MacroValue.structured(MacroValue.Kind.SLOT, Integer.toString(slot), java.util.Map.of(
                "slot", MacroValue.slot(slot), "empty", MacroValue.text("true"), "condition", MacroValue.text(condition))) : stack;
        }
        if (target.hasIdentity()) {
            MacroValue matched = itemTargetMacroValue(mc, target);
            if (matched != null) return matched.withProperty("condition", MacroValue.text(condition));
        }
        return MacroValue.structured(MacroValue.Kind.TEXT, condition, java.util.Map.of(
            "condition", MacroValue.text(condition),
            "selected_slot", MacroValue.slot(mc.player.getInventory().getSelectedSlot())
        ));
    }

    private static MacroValue itemTargetMacroValue(Minecraft mc, ItemTarget target) {
        if (mc == null || mc.player == null || target == null) return null;
        if (mc.player.containerMenu != null) {
            for (Slot slot : mc.player.containerMenu.slots) {
                if (slot == null || slot.getItem().isEmpty()) continue;
                int visible = AutismInventoryHelper.toUserVisibleSlot(mc, slot.index);
                if (target.score(slot.getItem(), visible) >= 0) return stackMacroValue(slot.getItem(), visible);
            }
        }
        return null;
    }

    private static MacroValue stackMacroValue(ItemStack stack, int slot) {
        if (stack == null || stack.isEmpty()) return null;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemId = id == null ? "" : id.toString();
        String name = stack.getHoverName().getString();
        return MacroValue.structured(MacroValue.Kind.ITEM, name, java.util.Map.of(
            "name", MacroValue.text(name), "id", MacroValue.identifier(itemId),
            "count", MacroValue.number(stack.getCount()), "slot", MacroValue.slot(slot)
        ));
    }

    private static MacroValue packetMacroValue(Packet<?> packet, String direction) {
        if (packet == null) return null;
        java.util.LinkedHashMap<String, MacroValue> properties = new java.util.LinkedHashMap<>();
        String packetName = AutismPacketNamer.getFriendlyName(packet, direction);
        properties.put("name", MacroValue.text(packetName));
        properties.put("class", MacroValue.text(packet.getClass().getName()));
        properties.put("direction", MacroValue.text(direction));
        RecordComponent[] components = packet.getClass().getRecordComponents();
        if (components != null) {
            for (RecordComponent component : components) {
                if (properties.size() >= 64) break;
                try {
                    Object value = component.getAccessor().invoke(packet);
                    properties.put(component.getName().toLowerCase(Locale.ROOT), MacroValue.text(value));
                } catch (ReflectiveOperationException ignored) {  }
            }
        }
        return MacroValue.structured(MacroValue.Kind.PACKET, packetName, properties);
    }

    private static void stageCapture(MacroAction action, java.util.Map<String, MacroValue> values) {
        RunState run = CURRENT_RUN.get();
        if (run == null || action == null || values == null) return;
        run.stagedCaptures.put(action, java.util.Map.copyOf(values));
    }

    private static void commitStagedCapture(MacroAction action) {
        RunState run = CURRENT_RUN.get();
        if (run == null || action == null) return;
        java.util.Map<String, MacroValue> values = run.stagedCaptures.remove(action);
        if (values != null) run.variables.setAll(values);
    }

    private static PrearmedWait predicatePrearm(MacroAction action, java.util.function.BooleanSupplier predicate, int timeoutMs) {
        java.util.concurrent.atomic.AtomicBoolean active = new java.util.concurrent.atomic.AtomicBoolean(true);
        CompletableFuture<Void> future = new CompletableFuture<>();
        RunState run = CURRENT_RUN.get();
        Thread worker = new Thread(() -> withRunContext(run, () -> {
            long start = System.currentTimeMillis();
            while (active.get() && isCurrentRunActive() && !future.isDone()) {
                try {
                    if (predicate.getAsBoolean()) {
                        future.complete(null);
                        return;
                    }
                } catch (Throwable ignored) {  }
                if (timeoutMs > 0 && System.currentTimeMillis() - start >= timeoutMs) {
                    future.complete(null);
                    return;
                }
                java.util.concurrent.locks.LockSupport.parkNanos(MACRO_WAIT_POLL_NANOS);
                if (Thread.interrupted()) return;
            }
        }), "MacroPrearmedWait");
        worker.setDaemon(true);
        worker.start();
        return new PrearmedWait(action, future, () -> {
            active.set(false);
            if (!future.isDone()) future.cancel(true);
            worker.interrupt();
        }, 0);
    }

    private static PrearmedWait packetTargetFuture(MacroAction action, String target, int completedPacketTargets) {
        String normalizedTarget = WaitForPacketAction.normalizeTarget(target);
        String direction = WaitForPacketAction.getDirection(normalizedTarget);
        CompletableFuture<Packet<?>> matched = new CompletableFuture<>();
        OneShotPacketListener send = null;
        OneShotPacketListener recv = null;
        if (!"S2C".equalsIgnoreCase(direction)) {
            send = awaitSend(packet -> matchesPacketTarget(normalizedTarget, packet, "C2S"));
            send.future.thenAccept(packet -> {
                recordMatchedPacket(action, packet, "C2S");
                matched.complete(packet);
            });
        }
        if (!"C2S".equalsIgnoreCase(direction)) {
            recv = awaitReceive(packet -> matchesPacketTarget(normalizedTarget, packet, "S2C"));
            recv.future.thenAccept(packet -> {
                recordMatchedPacket(action, packet, "S2C");
                matched.complete(packet);
            });
        }
        OneShotPacketListener finalSend = send;
        OneShotPacketListener finalRecv = recv;
        return new PrearmedWait(action, matched, () -> {
            if (finalSend != null) finalSend.cancel();
            if (finalRecv != null) finalRecv.cancel();
            if (!matched.isDone()) matched.cancel(true);
        }, completedPacketTargets);
    }

    private static PrearmedWait packetMatchFuture(WaitPacketMatchAction action) {
        OneShotPacketListener send = null;
        OneShotPacketListener recv = null;
        CompletableFuture<Packet<?>> matched = new CompletableFuture<>();
        if (action.direction != WaitPacketMatchAction.Direction.S2C) {
            send = awaitSend(packet -> action.matches(packet, "C2S"));
            send.future.thenAccept(packet -> {
                recordMatchedPacket(action, packet, "C2S");
                matched.complete(packet);
            });
        }
        if (action.direction != WaitPacketMatchAction.Direction.C2S) {
            recv = awaitReceive(packet -> action.matches(packet, "S2C"));
            recv.future.thenAccept(packet -> {
                recordMatchedPacket(action, packet, "S2C");
                matched.complete(packet);
            });
        }
        if (send == null && recv == null) return null;
        if (action.timeoutMs > 0) matched.completeOnTimeout(null, action.timeoutMs, TimeUnit.MILLISECONDS);
        OneShotPacketListener finalSend = send;
        OneShotPacketListener finalRecv = recv;
        return new PrearmedWait(action, matched, () -> {
            if (finalSend != null) finalSend.cancel();
            if (finalRecv != null) finalRecv.cancel();
            if (!matched.isDone()) matched.cancel(true);
        }, 0);
    }

    private static void waitForPacketMatch(WaitPacketMatchAction action) {
        if (action == null) return;
        OneShotPacketListener send = null;
        OneShotPacketListener recv = null;
        try {
            if (action.direction != WaitPacketMatchAction.Direction.S2C) {
                send = awaitSend(packet -> {
                    boolean matches = action.matches(packet, "C2S");
                    if (matches) recordMatchedPacket(action, packet, "C2S");
                    return matches;
                });
            }
            if (action.direction != WaitPacketMatchAction.Direction.C2S) {
                recv = awaitReceive(packet -> {
                    boolean matches = action.matches(packet, "S2C");
                    if (matches) recordMatchedPacket(action, packet, "S2C");
                    return matches;
                });
            }
            CompletableFuture<?> future;
            if (send != null && recv != null) future = CompletableFuture.anyOf(send.future, recv.future);
            else if (send != null) future = send.future;
            else if (recv != null) future = recv.future;
            else return;
            if (action.timeoutMs > 0) future.completeOnTimeout(null, action.timeoutMs, TimeUnit.MILLISECONDS);
            waitForFutureDone(future, action.timeoutMs);
        } finally {
            if (send != null) send.cancel();
            if (recv != null) recv.cancel();
        }
    }

    private static void runPacketGateAction(Minecraft mc, PacketGateAction action) {
        if (action == null) return;
        action.execute(mc);
        if (action.mode == PacketGateAction.GateMode.DISABLE_GATE) return;

        switch (action.durationMode) {
            case UNTIL_PACKET -> {
                WaitPacketMatchAction wait = new WaitPacketMatchAction();
                wait.direction = switch (action.direction) {
                    case C2S -> WaitPacketMatchAction.Direction.C2S;
                    case S2C -> WaitPacketMatchAction.Direction.S2C;
                    case ANY -> WaitPacketMatchAction.Direction.ANY;
                };
                wait.packetName = action.untilPacketName == null || action.untilPacketName.isBlank()
                    ? (action.packetNames.isEmpty() ? "" : action.packetNames.get(0))
                    : action.untilPacketName;
                wait.fieldName = action.untilPacketField;
                wait.operator = MacroStringList.enumValue(WaitPacketMatchAction.Operator.class, action.untilPacketOperator, WaitPacketMatchAction.Operator.EXISTS);
                wait.value = action.untilPacketValue;
                wait.timeoutMs = Math.max(0, action.durationValue);
                waitForPacketMatch(wait);
                disablePacketGate(mc, action);
            }
            case UNTIL_GUI -> {
                waitForPredicate(() -> MacroGuiMatcher.matches(mc.gui.screen(), action.untilGuiType, action.untilGuiTitle), Math.max(0, action.durationValue));
                disablePacketGate(mc, action);
            }
            case UNTIL_INVENTORY -> {
                WaitInventoryPredicateAction wait = new WaitInventoryPredicateAction();
                wait.condition = MacroStringList.enumValue(WaitInventoryPredicateAction.InventoryCondition.class, action.untilInventoryCondition, WaitInventoryPredicateAction.InventoryCondition.ITEM_EXISTS);
                wait.itemName = action.untilInventoryItem;
                wait.count = action.untilInventoryCount;
                wait.slot = action.untilInventorySlot;
                wait.timeoutMs = Math.max(0, action.durationValue);
                wait.resetBaseline();
                waitForPredicate(() -> wait.matches(mc), wait.timeoutMs);
                disablePacketGate(mc, action);
            }
            default -> {}
        }
    }

    private static void disablePacketGate(Minecraft mc, PacketGateAction action) {
        PacketGateManager.disableAndFlushConfigured(
            action.gateId == null || action.gateId.isBlank() ? "auto" : action.gateId,
            currentRunId(),
            mc == null ? null : mc.getConnection(),
            action.flushOnDisable);
    }

    private static void runFinallyActions(RunState run, Minecraft mc) {
        if (run == null || run.finallyActions.isEmpty()) return;
        ArrayList<MacroAction> actions = new ArrayList<>(run.finallyActions);
        run.finallyActions.clear();
        for (MacroAction action : actions) {
            if (action == null || !action.isEnabled()) continue;
            try {
                executeSingleActionWithWaits(mc, action, AutismModule.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                AutismClientMessaging.sendPrefixed("\u00a7cCleanup failed: " + safeThrowableMessage(t));
            }
        }
    }

    private static void waitForMacroStep(WaitForMacroStepAction action) {
        long startMs = System.currentTimeMillis();
        while (isCurrentRunActive()) {
            if (isMacroStepSatisfied(action)) return;
            if (action.timeoutMs > 0 && System.currentTimeMillis() - startMs >= action.timeoutMs) return;
            CompletableFuture<Void> tick = MacroConditionRegistry.waitForNextTick();
            waitForCondition(tick);
        }
    }

    private static boolean isMacroStepSatisfied(WaitForMacroStepAction action) {
        String target = action.macroName == null ? "" : action.macroName.trim();
        if (target.isEmpty()) return true;
        synchronized (RUN_LOCK) {
            for (RunState run : ACTIVE_RUNS.values()) {
                String name = run.macro != null && run.macro.name != null ? run.macro.name : "";
                if (!name.equalsIgnoreCase(target)) continue;
                return switch (action.mode) {
                    case STARTED_STEP -> run.currentStepIndex + 1 >= Math.max(1, action.step);
                    case COMPLETED_STEP -> run.lastCompletedStep >= Math.max(1, action.step);
                    case FINISHED -> !run.running.get();
                };
            }
        }
        return action.mode == WaitForMacroStepAction.WaitMode.FINISHED;
    }

    private static int runRaceGroup(Minecraft mc, AutismModule module, java.util.List<MacroAction> actions, int headerIndex, RaceAction race) throws InterruptedException {
        int bodyCount = race.normalizedBodyCount(actions, headerIndex);

        java.util.List<MacroAction> conditions = new ArrayList<>();
        java.util.List<MacroAction> bodyActions = new ArrayList<>();
        for (int offset = 1; offset <= bodyCount && headerIndex + offset < actions.size(); offset++) {
            MacroAction child = actions.get(headerIndex + offset);
            if (child == null || !child.isEnabled()) continue;
            if (RaceAction.isConditionAction(child)) conditions.add(child);
            else if (RaceAction.isBodyAction(child)) bodyActions.add(child);
        }

        setCurrentStatus(conditions.isEmpty()
            ? "Race: dispatching"
            : "Race: waiting for " + (conditions.size() == 1 ? conditions.get(0).getDisplayName() : conditions.size() + " conditions"));

        RunState run = CURRENT_RUN.get();
        CompletableFuture<Void> bodyFuture = new CompletableFuture<>();
        AtomicBoolean dispatched = new AtomicBoolean(false);
        CompletableFuture<?> triggerFuture = raceTriggerBarrier(mc, conditions);
        triggerFuture.thenRun(() -> {
            if (!dispatched.compareAndSet(false, true)) return;
            if (run != null && !run.running.get()) {
                bodyFuture.cancel(true);
                return;
            }
            dispatchRaceBody(mc, module, bodyActions, run, bodyFuture);
        }).exceptionally(error -> {
            if (!bodyFuture.isDone()) bodyFuture.completeExceptionally(error);
            return null;
        });

        boolean fired = waitForTimedFuture(triggerFuture, Math.max(0, race.timeoutMs));
        if (!fired) {
            triggerFuture.cancel(true);
            bodyFuture.cancel(true);
            AutismClientMessaging.sendPrefixed("\u00A7eRace timed out: " + (race.label == null || race.label.isBlank() ? "Race" : race.label));
            setCurrentStatus("Race: timeout");
            return bodyCount;
        }

        setCurrentStatus("Race: fired");
        waitForTimedFuture(bodyFuture, 250);
        return bodyCount;
    }

    private static CompletableFuture<?> raceTriggerBarrier(Minecraft mc, java.util.List<MacroAction> conditions) {
        if (conditions == null || conditions.isEmpty()) return CompletableFuture.completedFuture(null);
        RunState run = CURRENT_RUN.get();

        java.util.List<CompletableFuture<MacroEvent>> baseFutures = new ArrayList<>();
        java.util.List<CompletableFuture<MacroEvent>> futures = new ArrayList<>();
        for (MacroAction condition : conditions) {
            clearOwnCaptureOutput(condition);
            CompletableFuture<MacroEvent> base = timedConditionFuture(mc, condition);
            baseFutures.add(base);
            futures.add(base.thenApply(event -> {
                if (event.packet() != null) recordMatchedPacket(condition, event.packet(), event.direction());
                if (!(condition instanceof MacroCaptureOutput output)) return event;
                String name = MacroVariableContext.cleanRootName(output.getSaveAs());
                if (name.isEmpty()) return event;
                MacroValue value = conditionOutputValue(condition, mc);
                if (value == null) return event;
                java.util.LinkedHashMap<String, MacroValue> captures = new java.util.LinkedHashMap<>(event.captures());
                captures.put(name, value);
                return new MacroEvent(event.packet(), event.direction(), event.nanoTime(), event.chatCapture(), captures);
            }));
        }
        CompletableFuture<Object> first = CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]));
        CompletableFuture<Object> out = new CompletableFuture<>();
        first.whenComplete((value, e) -> {
            if (e != null) {
                out.completeExceptionally(e);
                return;
            }
            if (value instanceof MacroEvent event && run != null && !event.captures().isEmpty()) {
                run.variables.setAll(event.captures());
            }
            out.complete(value);
        });
        out.whenComplete((v, e) -> {
            for (CompletableFuture<MacroEvent> future : baseFutures) {
                if (!future.isDone()) future.cancel(true);
            }
        });
        return out;
    }

    private static int runReportGroup(Minecraft mc, java.util.List<MacroAction> actions, int headerIndex, ReportAction report) throws InterruptedException {
        int conditionCount = report.normalizedConditionCount(actions, headerIndex);
        if (conditionCount < 2 || headerIndex + 2 >= actions.size()) {
            setCurrentStatus(report.getDisplayName());
            report.execute(mc);
            return conditionCount;
        }

        MacroAction startCondition = actions.get(headerIndex + 1);
        MacroAction endCondition = actions.get(headerIndex + 2);
        if (RaceAction.isBodyAction(startCondition) && RaceAction.isConditionAction(endCondition)) {
            int timeoutMs = Math.max(100, report.timeoutMs);
            String label = report.reportLabel == null || report.reportLabel.isBlank() ? "Report" : report.reportLabel.trim();
            setCurrentStatus("Report: start " + startCondition.getDisplayName());
            CompletableFuture<MacroEvent> startAnchor = startActionAnchorFuture(startCondition);
            executeSingleActionWithWaits(mc, startCondition, null);
            if (!isCurrentRunActive()) return conditionCount;
            MacroEvent startEvent = waitForAnchorOrNow(startAnchor, 250, startCondition.getDisplayName(), "client action");

            setCurrentStatus("Report: waiting end " + endCondition.getDisplayName());
            CompletableFuture<MacroEvent> endFuture = timedConditionFuture(mc, endCondition);
            MacroEvent endEvent = waitForTimedEvent(endFuture, timeoutMs);
            if (endEvent == null) {
                endFuture.cancel(true);
                AutismClientMessaging.sendPrefixed("\u00A7eReport \"" + label + "\": timeout waiting for end condition");
                return conditionCount;
            }

            long deltaNanos = Math.max(0L, endEvent.nanoTime() - startEvent.nanoTime());
            long deltaMs = deltaNanos / 1_000_000L;
            String start = startEvent.label();
            String end = endEvent.label();
            AutismClientMessaging.sendPrefixed("\u00A7b" + label + ": " + formatNanos(deltaNanos) + " (" + start + " -> " + end + ")");
            if (report.stashToSharedState) {
                AutismSharedState.get().pushReport(new AutismSharedState.ReportSample(label, deltaMs, start, end, System.currentTimeMillis()));
            }
            return conditionCount;
        }

        if (!RaceAction.isConditionAction(startCondition) || !RaceAction.isConditionAction(endCondition)) {
            report.execute(mc);
            return conditionCount;
        }

        int timeoutMs = Math.max(100, report.timeoutMs);
        long startWaitNanos = System.nanoTime();
        setCurrentStatus("Report: waiting start " + startCondition.getDisplayName());
        boolean startFired = waitForMacroCondition(mc, startCondition, timeoutMs);
        if (!startFired) {
            AutismClientMessaging.sendPrefixed("\u00A7eReport \"" + report.reportLabel + "\": timeout waiting for start condition");
            return conditionCount;
        }

        MacroEvent startEvent = new MacroEvent(null, "condition " + startCondition.getDisplayName(), System.nanoTime(), null);
        long spentMs = (startEvent.nanoTime() - startWaitNanos) / 1_000_000L;
        int remainingMs = (int) Math.max(1, timeoutMs - spentMs);
        setCurrentStatus("Report: waiting end " + endCondition.getDisplayName());
        CompletableFuture<MacroEvent> endFuture = timedConditionFuture(mc, endCondition);
        MacroEvent endEvent = waitForTimedEvent(endFuture, remainingMs);
        if (endEvent == null) {
            endFuture.cancel(true);
            AutismClientMessaging.sendPrefixed("\u00A7eReport \"" + report.reportLabel + "\": timeout waiting for end condition");
            return conditionCount;
        }

        long deltaNanos = Math.max(0L, endEvent.nanoTime() - startEvent.nanoTime());
        long deltaMs = deltaNanos / 1_000_000L;
        String label = report.reportLabel == null || report.reportLabel.isBlank() ? "Report" : report.reportLabel.trim();
        String start = startEvent.label();
        String end = endEvent.label();
        AutismClientMessaging.sendPrefixed("\u00A7b" + label + ": " + formatNanos(deltaNanos) + " (" + start + " -> " + end + ")");
        if (report.stashToSharedState) {
            AutismSharedState.get().pushReport(new AutismSharedState.ReportSample(label, deltaMs, start, end, System.currentTimeMillis()));
        }
        return conditionCount;
    }

    private static boolean waitForRaceTrigger(Minecraft mc, RaceAction race) throws InterruptedException {
        MacroAction trigger = race.triggerAction == null ? new DelayAction(0) : race.triggerAction;
        int timeoutMs = Math.max(0, race.timeoutMs);
        return waitForMacroCondition(mc, trigger, timeoutMs);
    }

    private static int runMultiGroup(Minecraft mc, AutismModule module, String macroName, java.util.List<MacroAction> actions, int headerIndex, MultiAction multi) throws InterruptedException {
        int stepCount = multi.normalizedStepCount(actions, headerIndex);
        if (stepCount <= 0) return 0;
        autismclient.util.multi.MultiManager manager = autismclient.util.multi.MultiManager.get();
        autismclient.util.multi.MultiProfile profile = manager.isActive()
            ? manager.activeProfile()
            : autismclient.util.multi.MultiProfileManager.get().find(autismclient.util.multi.MultiProfileManager.get().selectedId());
        if (profile == null) {

            AutismClientMessaging.sendPrefixed("\u00A7eMulti: no Multi profile to start");
            return stepCount;
        }

        java.util.LinkedHashSet<String> scope = new java.util.LinkedHashSet<>();
        java.util.Set<String> profileIds = new java.util.HashSet<>();
        for (autismclient.util.multi.MultiProfile.SessionSpec spec : profile.sessions) {
            if (spec != null && spec.accountId() != null && !spec.accountId().isBlank()) profileIds.add(spec.accountId());
        }
        for (String accountId : multi.effectiveAccountIds()) {
            if (profileIds.contains(accountId)) scope.add(accountId);
        }
        if (scope.isEmpty()) {
            AutismClientMessaging.sendPrefixed("\u00A7eMulti: no selected accounts in the active Multi profile");
            return stepCount;
        }

        if (multi.connectIfDown) {
            if (!manager.isActive()) {
                setCurrentStatus("Multi: starting batch");
                autismclient.util.multi.MultiManager.StartResult started = manager.start(profile);
                if (!started.ok()) {

                    AutismClientMessaging.sendPrefixed("\u00A7eMulti: could not start the Multi batch");
                    return stepCount;
                }
            } else {
                java.util.Map<String, autismclient.util.multi.MultiSession.Snapshot> byId = multiSnapshotsById(manager);
                for (String accountId : scope) {
                    autismclient.util.multi.MultiSession.Snapshot snapshot = byId.get(accountId);
                    if (snapshot == null || autismclient.util.multi.MultiManager.isRetryable(snapshot.status())) {
                        manager.retry(accountId);
                    }
                }
            }
        }
        if (!isCurrentRunActive()) return stepCount;

        int waitMs = Math.max(0, multi.waitMs);
        if (MultiAction.WAIT_UNTIL_READY.equals(multi.waitMode)) {
            setCurrentStatus("Multi: waiting for " + scope.size() + (scope.size() == 1 ? " account" : " accounts"));
            multiWaitUntilReady(manager, scope, waitMs);
        } else if (MultiAction.WAIT_FIXED_DELAY.equals(multi.waitMode) && waitMs > 0) {
            setCurrentStatus("Multi: delay " + waitMs + " ms");
            multiSleepCancellable(waitMs);
        }
        if (!isCurrentRunActive()) return stepCount;

        AutismMacro subMacro = new AutismMacro();
        subMacro.name = (macroName == null || macroName.isBlank() ? "Macro" : macroName) + " (Multi)";
        for (int offset = 1; offset <= stepCount && headerIndex + offset < actions.size(); offset++) {
            MacroAction child = actions.get(headerIndex + offset);
            if (child == null) continue;
            MacroAction copy = AutismMacro.createActionFromTag(AutismMacro.serializeAction(child));
            if (copy != null) subMacro.actions.add(copy);
        }

        setCurrentStatus("Multi: running on " + scope.size() + (scope.size() == 1 ? " account" : " accounts"));
        autismclient.util.multi.MultiManager.BroadcastResult result = manager.runMacroDirect(subMacro, scope);
        AutismClientMessaging.sendPrefixed("\u00A7bMulti: " + result.summary());

        setCurrentStatus("Multi: waiting for completion");
        multiWaitForCompletion(manager, scope, subMacro.name);
        if (!isCurrentRunActive()) return stepCount;

        if (multi.disconnectAfter) {
            for (String accountId : scope) {

                if (manager.hasQueuedMacroIntent(accountId, subMacro.name)) continue;
                manager.disconnectSession(accountId);
            }
        }
        return stepCount;
    }

    private static java.util.Map<String, autismclient.util.multi.MultiSession.Snapshot> multiSnapshotsById(autismclient.util.multi.MultiManager manager) {
        java.util.Map<String, autismclient.util.multi.MultiSession.Snapshot> byId = new java.util.HashMap<>();
        for (autismclient.util.multi.MultiSession.Snapshot snapshot : manager.snapshots()) {
            if (snapshot != null) byId.put(snapshot.accountId(), snapshot);
        }
        return byId;
    }

    private static void multiSleepCancellable(long totalMs) throws InterruptedException {
        long deadline = System.nanoTime() + Math.max(0L, totalMs) * 1_000_000L;
        long remaining;
        while ((remaining = deadline - System.nanoTime()) > 0L) {
            if (!isCurrentRunActive()) return;
            Thread.sleep(Math.min(100L, Math.max(1L, remaining / 1_000_000L)));
        }
    }

    private static void multiWaitUntilReady(autismclient.util.multi.MultiManager manager, java.util.Set<String> scope, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + Math.max(0L, timeoutMs) * 1_000_000L;
        while (isCurrentRunActive()) {
            boolean allReady = true;
            java.util.Map<String, autismclient.util.multi.MultiSession.Snapshot> byId = multiSnapshotsById(manager);
            for (String accountId : scope) {
                autismclient.util.multi.MultiSession.Snapshot snapshot = byId.get(accountId);
                if (snapshot == null || !snapshot.ready()) {
                    allReady = false;
                    break;
                }
            }
            if (allReady) return;
            if (System.nanoTime() >= deadline) {
                AutismClientMessaging.sendPrefixed("\u00A7eMulti: timed out waiting for accounts to be ready");
                return;
            }
            Thread.sleep(150L);
        }
    }

    private static void multiWaitForCompletion(autismclient.util.multi.MultiManager manager, java.util.Set<String> scope, String subMacroName) throws InterruptedException {
        long deadline = System.nanoTime() + 10L * 60L * 1_000_000_000L;
        Thread.sleep(200L);
        while (isCurrentRunActive()) {
            boolean anyPending = false;
            java.util.Map<String, autismclient.util.multi.MultiSession.Snapshot> byId = multiSnapshotsById(manager);
            for (String accountId : scope) {

                if (manager.hasQueuedMacroIntent(accountId, subMacroName)) {
                    anyPending = true;
                    break;
                }
                autismclient.util.multi.MultiSession.Snapshot snapshot = byId.get(accountId);
                autismclient.util.multi.MultiSession.MacroProgress progress = snapshot == null ? null : snapshot.macroProgress();
                if (progress != null && progress.running()) {
                    anyPending = true;
                    break;
                }
            }
            if (!anyPending) return;
            if (System.nanoTime() >= deadline) {
                AutismClientMessaging.sendPrefixed("\u00A7eMulti: timed out waiting for " + scope.size()
                    + (scope.size() == 1 ? " account" : " accounts"));
                return;
            }
            Thread.sleep(200L);
        }
    }

    public static boolean waitForMacroCondition(Minecraft mc, MacroAction trigger, int timeoutMs) throws InterruptedException {
        if (trigger == null) return true;
        CompletableFuture<?> future = raceConditionFuture(mc, trigger);
        if (future == null) {
            executeSingleActionWithWaits(mc, trigger, null);
            return true;
        }
        boolean fired = waitForFutureDone(future, timeoutMs);
        if (!fired) future.cancel(true);
        return fired;
    }

    private static void dispatchRaceBody(Minecraft mc, AutismModule module, java.util.List<MacroAction> bodyActions,
                                         RunState run, CompletableFuture<Void> bodyFuture) {
        if (bodyActions == null || bodyActions.isEmpty()) {
            bodyFuture.complete(null);
            return;
        }
        Runnable task = () -> {
            RunState previous = CURRENT_RUN.get();
            if (run != null) CURRENT_RUN.set(run);
            EnumSet<SharedClientResource> heldResources = EnumSet.noneOf(SharedClientResource.class);
            try {
                heldResources = acquireClientResources(resourcesForActions(bodyActions));
                Throwable firstFailure = null;
                for (MacroAction body : bodyActions) {
                    if (body == null || !body.isEnabled() || !RaceAction.isBodyAction(body)) continue;
                    if (run != null && !run.running.get()) {
                        bodyFuture.cancel(true);
                        return;
                    }
                    try {
                        executeRaceBodyActionFast(mc, body);
                    } catch (InterruptedException e) {
                        throw e;
                    } catch (Throwable t) {
                        if (firstFailure == null) firstFailure = t;
                        AutismClientMessaging.sendPrefixed("\u00A7cRace action failed (" + body.getDisplayName() + "): " + safeThrowableMessage(t));
                    }
                }
                if (firstFailure == null) bodyFuture.complete(null);
                else bodyFuture.completeExceptionally(firstFailure);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                bodyFuture.cancel(true);
            } catch (Throwable t) {
                AutismClientMessaging.sendPrefixed("\u00A7cRace action failed: " + safeThrowableMessage(t));
                bodyFuture.completeExceptionally(t);
            } finally {
                releaseClientResources(heldResources);
                if (previous != null) CURRENT_RUN.set(previous);
                else CURRENT_RUN.remove();
            }
        };

        if (bodyActions.stream().allMatch(MacroExecutor::isRaceDirectPacketAction)) {
            task.run();
        } else {
            mc.execute(task);
        }
    }

    private static void executeRaceBodyActionFast(Minecraft mc, MacroAction body) throws InterruptedException {
        if (body == null || !body.isEnabled() || !RaceAction.isBodyAction(body)) return;
        if (!MacroDynamicBindings.apply(body, mc)) return;
        body.execute(mc);
    }

    private static boolean isRaceDirectPacketAction(MacroAction action) {
        if (action instanceof SendChatAction chat) return !chat.isWaitForGuiBefore() && !chat.isWaitForGuiAfter();
        if (action instanceof SendPacketAction sendPacket) return !sendPacket.isWaitForGuiBefore() && !sendPacket.isWaitForGuiAfter();
        return action instanceof PacketClickAction;
    }

    private static CompletableFuture<MacroEvent> startActionAnchorFuture(MacroAction action) {
        if (action instanceof PacketClickAction) {
            return awaitTimedSend(packet -> packet instanceof ServerboundContainerClickPacket).future;
        }
        if (action instanceof ItemAction) {
            return awaitTimedSend(packet -> packet instanceof ServerboundContainerClickPacket).future;
        }
        if (action instanceof CloseGuiAction) {
            return awaitTimedSend(packet -> packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClosePacket).future;
        }
        if (action instanceof SendChatAction) {
            return awaitTimedSend(packet -> packet instanceof net.minecraft.network.protocol.game.ServerboundChatPacket
                || packet instanceof net.minecraft.network.protocol.game.ServerboundChatCommandPacket
                || packet instanceof net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket).future;
        }
        if (action instanceof SendPacketAction || action instanceof PayloadAction) {
            return awaitTimedSend(packet -> true).future;
        }
        return null;
    }

    private static MacroEvent waitForAnchorOrNow(CompletableFuture<MacroEvent> anchor, int timeoutMs, String label, String fallbackDirection) {
        if (anchor != null) {
            MacroEvent event = waitForTimedEvent(anchor, timeoutMs);
            if (event != null) return event;
            anchor.cancel(true);
        }
        return new MacroEvent(null, fallbackDirection + " " + (label == null ? "" : label), System.nanoTime(), null);
    }

    private static MacroEvent waitForTimedEvent(CompletableFuture<MacroEvent> future, long timeoutMs) {
        if (future == null) return null;
        try {
            if (timeoutMs <= 0) return future.get();
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return null;
        } catch (ExecutionException | TimeoutException | CancellationException e) {
            return null;
        }
    }

    private static boolean waitForTimedFuture(CompletableFuture<?> future, long timeoutMs) {
        if (future == null) return true;
        try {
            if (timeoutMs <= 0) future.get();
            else future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return !future.isCancelled() && isCurrentRunActive();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return false;
        } catch (ExecutionException | TimeoutException | CancellationException e) {
            return false;
        }
    }

    private static String formatNanos(long nanos) {
        return String.format(Locale.ROOT, "%.3f ms", nanos / 1_000_000.0);
    }

    private static String safeThrowableMessage(Throwable throwable) {
        if (throwable == null) return "unknown error";
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static boolean isWorldTransitionPacket(Packet<?> packet) {
        return packet instanceof net.minecraft.network.protocol.game.ClientboundRespawnPacket
            || packet instanceof net.minecraft.network.protocol.game.ClientboundLoginPacket;
    }

    private static CompletableFuture<MacroEvent> timedConditionFuture(Minecraft mc, MacroAction trigger) {
        if (trigger == null) {
            return CompletableFuture.completedFuture(new MacroEvent(null, "condition", System.nanoTime(), null));
        }
        if (trigger instanceof WaitForChatAction chat) {
            MacroVariableContext context = currentVariableContext();
            java.util.concurrent.atomic.AtomicReference<java.util.Map<String, MacroValue>> captures =
                new java.util.concurrent.atomic.AtomicReference<>(java.util.Map.of());
            TimedOneShotPacketListener listener = awaitTimedReceive((packet, direction, now) -> {
                ChatCapture capture = extractIncomingChat(packet);
                var matched = captureChatAction(chat, capture, context);
                matched.ifPresent(captures::set);
                return matched.isPresent();
            });

            CompletableFuture<MacroEvent> out = new CompletableFuture<>();
            listener.future.whenComplete((event, e) -> {
                if (e != null) out.completeExceptionally(e);
                else out.complete(new MacroEvent(event.packet(), event.direction(), event.nanoTime(),
                    extractIncomingChat(event.packet()), captures.get()));
            });
            out.whenComplete((v, e) -> listener.cancel());
            return out;
        }
        if (trigger instanceof WaitForPacketAction packetAction) {
            java.util.List<String> targets = packetAction.effectiveList();
            CompletableFuture<MacroEvent> matched = new CompletableFuture<>();
            TimedOneShotPacketListener recv = awaitTimedReceive((packet, direction, now) -> {
                if (targets.isEmpty()) return true;
                for (String target : targets) {
                    if (matchesPacketTarget(target, packet, "S2C")) return true;
                }
                return false;
            });
            TimedOneShotPacketListener send = awaitTimedSend((packet, direction, now) -> {
                if (targets.isEmpty()) return true;
                for (String target : targets) {
                    if (matchesPacketTarget(target, packet, "C2S")) return true;
                }
                return false;
            });
            recv.future.thenAccept(matched::complete);
            send.future.thenAccept(matched::complete);
            matched.whenComplete((v, e) -> {
                recv.cancel();
                send.cancel();
            });
            return matched;
        }
        if (trigger instanceof WaitForWorldChangeAction world) {
            CompletableFuture<MacroEvent> done = new CompletableFuture<>();
            CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForWorldChange(world);
            TimedOneShotPacketListener worldListener = awaitTimedReceive((packet, direction, now) -> isWorldTransitionPacket(packet));
            tickFuture.whenComplete((v, e) -> {
                if (e != null) {
                    if (!done.isDone()) done.cancel(true);
                } else {
                    done.complete(new MacroEvent(null, "world change", System.nanoTime(), null));
                }
            });
            worldListener.future.whenComplete((event, error) -> {
                if (error != null) {
                    if (!done.isDone()) done.cancel(true);
                    return;
                }
                if (world.targetDimension == null || world.targetDimension.isBlank()) {
                    done.complete(event);
                }
            });
            done.whenComplete((v, e) -> {
                worldListener.cancel();
                if (!tickFuture.isDone()) tickFuture.cancel(true);
            });
            return done;
        }
        if (trigger instanceof WaitForTeleportAction teleport) {
            net.minecraft.world.phys.Vec3 origin = mc.player == null ? net.minecraft.world.phys.Vec3.ZERO : mc.player.position();
            CompletableFuture<MacroEvent> done = new CompletableFuture<>();
            TimedOneShotPacketListener teleListener = awaitTimedReceive((packet, direction, now) ->
                packet instanceof ClientboundPlayerPositionPacket || isWorldTransitionPacket(packet));
            teleListener.future.whenComplete((event, error) -> {
                teleListener.cancel();
                if (error != null) {
                    done.cancel(true);
                    return;
                }
                if (teleport.minDistance <= 0.0) {
                    done.complete(event);
                    return;
                }
                MacroConditionRegistry.waitForPositionDeltaFrom(origin, teleport.minDistance, teleport.horizontalOnly)
                    .whenComplete((v, e) -> {
                        if (e != null) done.cancel(true);
                        else done.complete(new MacroEvent(event.packet(), event.direction(), System.nanoTime(), event.chatCapture()));
                    });
            });
            done.whenComplete((v, e) -> teleListener.cancel());
            return done;
        }

        CompletableFuture<?> base = raceConditionFuture(mc, trigger);
        if (base == null) {
            CompletableFuture<MacroEvent> done = new CompletableFuture<>();
            RunState run = CURRENT_RUN.get();
            mc.execute(() -> withRunContext(run, () -> {
                try {
                    executeSingleActionWithWaits(mc, trigger, null);
                    done.complete(new MacroEvent(null, trigger.getDisplayName(), System.nanoTime(), null));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    done.cancel(true);
                } catch (Throwable t) {
                    done.completeExceptionally(t);
                }
            }));
            return done;
        }
        CompletableFuture<MacroEvent> timed = new CompletableFuture<>();
        base.whenComplete((v, e) -> {
            if (e != null) timed.cancel(true);
            else timed.complete(new MacroEvent(null, trigger.getDisplayName(), System.nanoTime(), null));
        });
        timed.whenComplete((v, e) -> {
            if (!base.isDone()) base.cancel(true);
        });
        return timed;
    }

    private static CompletableFuture<?> raceConditionFuture(Minecraft mc, MacroAction trigger) {
        if (trigger instanceof WaitMovementAction wm) {
            return raceConditionFuture(mc, wm.resolveSubAction());
        }
        if (trigger instanceof WaitGamemodeChangeAction gm) {
            return MacroConditionRegistry.waitForGamemodeChange(gm);
        }
        if (trigger instanceof DelayAction delay) {
            RunState run = CURRENT_RUN.get();
            return CompletableFuture.runAsync(() -> withRunContext(run, () -> {
                try {
                    if (delay.useTicks) {
                        for (int i = 0; i < delay.delayTicks && isCurrentRunActive(); i++) {
                            waitForCondition(MacroConditionRegistry.waitForNextTick());
                        }
                    } else {
                        Thread.sleep(Math.max(0, delay.delayMs));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        if (trigger instanceof WaitForGuiAction gui) {
            CompletableFuture<MacroEvent> done = new CompletableFuture<>();
            RunState run = CURRENT_RUN.get();
            Thread worker = new Thread(() -> withRunContext(run, () -> {
                long start = System.currentTimeMillis();
                boolean saw = false;
                java.util.Map<String, MacroValue> observed = java.util.Map.of();
                while (!done.isDone() && isCurrentRunActive()) {
                    var captured = gui.captureGui(Minecraft.getInstance().gui.screen());
                    if (captured.isPresent()) {
                        saw = true;
                        observed = captured.get();
                        if (gui.waitMode == WaitForGuiAction.WaitMode.OPEN) {
                            done.complete(new MacroEvent(null, "GUI", System.nanoTime(), null, observed));
                            return;
                        }
                    } else if (saw && gui.waitMode == WaitForGuiAction.WaitMode.CLOSE) {
                        done.complete(new MacroEvent(null, "GUI close", System.nanoTime(), null, observed));
                        return;
                    }
                    if (gui.timeoutMs > 0 && System.currentTimeMillis() - start >= gui.timeoutMs) {
                        done.cancel(false);
                        return;
                    }
                    java.util.concurrent.locks.LockSupport.parkNanos(MACRO_WAIT_POLL_NANOS);
                }
            }), "MacroGuiCaptureWait");
            worker.setDaemon(true);
            worker.start();
            done.whenComplete((v, e) -> worker.interrupt());
            return done;
        }
        if (trigger instanceof WaitForChatAction chat) {
            MacroVariableContext context = currentVariableContext();
            OneShotPacketListener listener = awaitReceive(packet -> {
                ChatCapture capture = extractIncomingChat(packet);
                return capture != null && captureChatAction(chat, capture, context).isPresent();
            });
            listener.future.whenComplete((v, e) -> listener.cancel());
            return listener.future;
        }
        if (trigger instanceof WaitForPacketAction packetAction) {
            java.util.List<String> targets = packetAction.effectiveList();
            CompletableFuture<Packet<?>> matched = new CompletableFuture<>();
            OneShotPacketListener recv = awaitReceive(packet -> {
                if (targets.isEmpty()) return true;
                for (String target : targets) {
                    if (matchesPacketTarget(target, packet, "S2C")) return true;
                }
                return false;
            });
            OneShotPacketListener send = awaitSend(packet -> {
                if (targets.isEmpty()) return true;
                for (String target : targets) {
                    if (matchesPacketTarget(target, packet, "C2S")) return true;
                }
                return false;
            });
            recv.future.thenAccept(matched::complete);
            send.future.thenAccept(matched::complete);
            matched.whenComplete((v, e) -> {
                recv.cancel();
                send.cancel();
            });
            return matched;
        }
        if (trigger instanceof WaitForHealthAction health) {
            return MacroConditionRegistry.waitForHealth(health.healthThreshold, health.below);
        }
        if (trigger instanceof WaitForBlockAction block) {
            return MacroConditionRegistry.waitForBlock(block);
        }
        if (trigger instanceof WaitForCooldownAction cooldown) {
            ItemTarget target = resolveItemTarget(cooldown.itemTarget, cooldown.itemName);
            return MacroConditionRegistry.waitForCooldown(target, cooldown.checkMainInteractionHand);
        }
        if (trigger instanceof WaitPosAction pos) {
            return MacroConditionRegistry.waitForPos(pos.x, pos.y, pos.z, pos.leeway, pos.checkRotation, pos.yaw, pos.pitch, pos.rotLeeway);
        }
        if (trigger instanceof WaitForEntityAction entity) {
            return predicateFuture(() -> entity.matchesEntityTarget(Minecraft.getInstance()), entity.timeoutMs);
        }
        if (trigger instanceof WaitForSoundAction sound) {
            return MacroConditionRegistry.waitForSound(sound);
        }
        if (trigger instanceof WaitForSlotChangeAction slot) {
            return MacroConditionRegistry.waitForSlotChange(slot);
        }
        if (trigger instanceof WaitDurabilityAction durability) {
            return predicateFuture(() -> durabilityObservationMatches(Minecraft.getInstance(), durability), durability.timeoutMs);
        }
        if (trigger instanceof WaitFreeSlotsAction freeSlots) {
            return predicateFuture(() -> freeSlots.matches(Minecraft.getInstance()), freeSlots.timeoutMs);
        }
        if (trigger instanceof TickSyncAction) {
            return MacroConditionRegistry.waitForNextTick();
        }
        if (trigger instanceof ServerTickSyncAction serverTick) {
            return MacroConditionRegistry.waitForServerTick(serverTick.bufferMs, serverTick.maxWaitMs, serverTick.ignorePing);
        }
        if (trigger instanceof WaitForWorldChangeAction world) {
            CompletableFuture<Void> done = new CompletableFuture<>();
            CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForWorldChange(world);
            OneShotPacketListener worldListener = awaitReceive(MacroExecutor::isWorldTransitionPacket);

            tickFuture.whenComplete((v, e) -> {
                if (e != null) {
                    if (!done.isDone()) done.cancel(true);
                } else {
                    done.complete(null);
                }
            });

            worldListener.future.whenComplete((packet, error) -> {
                if (error != null) {
                    if (!done.isDone()) done.cancel(true);
                    return;
                }
                if (world.targetDimension == null || world.targetDimension.isBlank()) {
                    done.complete(null);
                }
            });

            done.whenComplete((v, e) -> {
                worldListener.cancel();
                if (!tickFuture.isDone()) tickFuture.cancel(true);
            });
            return done;
        }
        if (trigger instanceof WaitForPositionDeltaAction delta) {
            return MacroConditionRegistry.waitForPositionDelta(delta.distance, delta.horizontalOnly);
        }
        if (trigger instanceof WaitForTeleportAction teleport) {
            net.minecraft.world.phys.Vec3 origin = mc.player == null ? net.minecraft.world.phys.Vec3.ZERO : mc.player.position();
            CompletableFuture<Void> done = new CompletableFuture<>();

            OneShotPacketListener teleListener = awaitReceive(packet ->
                packet instanceof ClientboundPlayerPositionPacket
                || isWorldTransitionPacket(packet));
            teleListener.future.whenComplete((packet, error) -> {
                teleListener.cancel();
                if (error != null) {
                    done.cancel(true);
                    return;
                }

                if (teleport.minDistance <= 0.0) {
                    done.complete(null);
                    return;
                }
                MacroConditionRegistry.waitForPositionDeltaFrom(origin, teleport.minDistance, teleport.horizontalOnly)
                    .whenComplete((v, e) -> {
                        if (e != null) done.cancel(true);
                        else done.complete(null);
                    });
            });
            return done;
        }
        if (trigger instanceof RevisionSyncAction) {
            RunState run = CURRENT_RUN.get();
            return CompletableFuture.runAsync(() -> withRunContext(run, () -> {
                try {
                    executeSingleActionWithWaits(mc, trigger, null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        return null;
    }

    private static boolean waitForFutureDone(CompletableFuture<?> future, long timeoutMs) {
        Thread t = Thread.currentThread();
        long start = System.nanoTime();
        long timeoutNanos = timeoutMs <= 0 ? Long.MAX_VALUE : timeoutMs * 1_000_000L;
        future.whenComplete((v, e) -> java.util.concurrent.locks.LockSupport.unpark(t));
        while (!future.isDone() && isCurrentRunActive()) {
            if (System.nanoTime() - start >= timeoutNanos) break;
            java.util.concurrent.locks.LockSupport.parkNanos(MACRO_WAIT_POLL_NANOS);
            if (Thread.interrupted()) break;
        }
        return future.isDone() && !future.isCancelled() && isCurrentRunActive();
    }

    private static void executeSingleActionWithWaits(Minecraft mc, MacroAction action, AutismModule module) throws InterruptedException {
        if (!MacroDynamicBindings.apply(action, mc)) return;
        if (!dynamicTextInputsAvailable(action, mc)) return;
        clearOwnCaptureOutput(action);
        EnumSet<SharedClientResource> heldResources = acquireClientResources(action);
        try {

        if (!waitForGuiBeforeAction(mc, action)) return;
        java.util.concurrent.CompletableFuture<Void> postGuiFuture =
                (action instanceof ItemAction || action instanceof DropAction || isTargetWaitInteractionAction(action)) ? null : createPostGuiFuture(mc, action);

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
                awaitPacket("");
            } else {
                for (String target : targets) {
                    if (!isCurrentRunActive()) break;
                    awaitPacket(target);
                }
            }
            copyLastMatchedPacket(packetAction);
        } else if (action instanceof WaitPacketMatchAction wpma) {
            setCurrentStatus(wpma.getDisplayName());
            waitForPacketMatch(wpma);
        } else if (action instanceof WaitInventoryPredicateAction wipa) {
            setCurrentStatus(wipa.getDisplayName());
            waitForPredicate(() -> wipa.matches(mc), wipa.timeoutMs);
        } else if (action instanceof WaitDurabilityAction wda) {
            setCurrentStatus(wda.getDisplayName());
            if (wda.useNext) waitForDurabilityUseNext(mc, wda);
            else waitForPredicate(() -> wda.matches(mc), wda.timeoutMs);
        } else if (action instanceof WaitFreeSlotsAction wfsa) {
            setCurrentStatus(wfsa.getDisplayName());
            waitForPredicate(() -> wfsa.matches(mc), wfsa.timeoutMs);
        } else if (action instanceof WaitEntityTargetAction weta) {
            setCurrentStatus(weta.getDisplayName());
            waitForPredicate(() -> weta.matches(mc), weta.timeoutMs);
        } else if (action instanceof WaitGuiTypeAction wgta) {
            setCurrentStatus(wgta.getDisplayName());
            clearCaptureOutputs(wgta);
            if (wgta.waitMode == WaitGuiTypeAction.WaitMode.CLOSE) waitForPredicate(guiClosePredicate(wgta, mc), wgta.timeoutMs);
            else waitForPredicate(() -> {
                var captured = wgta.capture(mc);
                captured.ifPresent(values -> stageCapture(wgta, values));
                return captured.isPresent();
            }, wgta.timeoutMs);
            commitStagedCapture(wgta);
        } else if (action instanceof PacketGateAction pga) {
            runPacketGateAction(mc, pga);
        } else if (isTargetWaitInteractionAction(action)) {
            postGuiFuture = runTargetWaitInteractionAction(mc, action);
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
            clearCaptureOutputs(wga);
            if (wga.waitMode == WaitForGuiAction.WaitMode.CLOSE) {
                setCurrentStatus("Waiting for GUI close: " + wga.guiTitle);
                waitForPredicate(guiClosePredicate(wga, mc), wga.timeoutMs);
            } else {
                setCurrentStatus("Waiting for GUI: " + wga.guiTitle);
                waitForPredicate(() -> {
                    var captured = wga.captureGui(mc.gui.screen());
                    captured.ifPresent(values -> stageCapture(wga, values));
                    return captured.isPresent();
                }, wga.timeoutMs);
            }
            commitStagedCapture(wga);
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
            runCaptureValueAction(mc, captureValue);
        } else if (action instanceof WaitForChatAction) {
            WaitForChatAction wca = (WaitForChatAction) action;
            setCurrentStatus("Waiting for chat: " + wca.pattern);
            awaitChat(wca);
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
                CompletableFuture<?> future = raceConditionFuture(mc, world);
                waitForFutureDone(future, 0);
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
                CompletableFuture<?> future = raceConditionFuture(mc, teleport);
                waitForFutureDone(future, 0);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitGamemodeChangeAction gm) {
            setCurrentStatus(gm.getDisplayName());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForGamemodeChange(gm);
                waitForFutureDone(future, gm.timeoutMs);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitMovementAction wm) {
            setCurrentStatus(wm.getDisplayName());
            try {
                CompletableFuture<?> future = raceConditionFuture(mc, wm.resolveSubAction());
                waitForFutureDone(future, 0);
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
            waitForMacroStep(wma);
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
            runUseItemAction(mc, (UseItemAction) action);
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
            if (sna.persistent || !sna.sneak) runOnClientThread(mc, () -> applyAggregateSneakState(mc));
            else runOnClientThread(mc, () -> sna.execute(mc));
        } else if (action instanceof SprintAction) {
            SprintAction spa = (SprintAction) action;
            if (spa.persistent && spa.sprint) {
                currentPersistentActions().removeIf(p -> p instanceof SprintAction);
                currentPersistentActions().add(spa);
            } else if (!spa.sprint) {
                currentPersistentActions().removeIf(p -> p instanceof SprintAction);
            }
            if (spa.persistent || !spa.sprint) runOnClientThread(mc, () -> applyAggregateSprintState(mc));
            else runOnClientThread(mc, () -> spa.execute(mc));
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
            postGuiFuture = createPostGuiFuture(mc, ia);
            runMacroActionOnClientThread(mc, action, "ItemAction");
        } else if (action instanceof DropAction || action instanceof SwapSlotsAction) {
            if (action instanceof DropAction dropAction) {
                postGuiFuture = createPostGuiFuture(mc, dropAction);
            }
            runMacroActionOnClientThread(mc, action, action.getDisplayName());
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
            runClickActionBurst(mc, ca);
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
                    try { runStoreItemAction(mc, sia); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    catch (Exception e) { AutismClientMessaging.sendPrefixed("Store error: " + e.getMessage()); }
                    CompletableFuture<Void> siTick = MacroConditionRegistry.waitForNextTick();
                    waitForCondition(siTick);
                }
            } else {
                try { runStoreItemAction(mc, sia); }
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
            runContainerClickSequenceAction(mc, sequence);
        } else if (action instanceof PacketBurstAction burst) {
            runPacketBurstAction(mc, burst);
        } else if (action instanceof TickSyncAction tsa) {
            if (mc.level == null) return;
            java.util.List<net.minecraft.network.protocol.Packet<?>> preGenerated =
                preGeneratePacketsForRepeat(tsa.preGenCount, mc);
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
                preGeneratePacketsForRepeat(rsa.preGenCount, mc);
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
                preGeneratePacketsForRepeat(stsa.preGenCount, mc);
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
                AutismClientMessaging.sendPrefixed("\u00a7cRollback failed: " + safeThrowableMessage(e));
            }
        } else if (action instanceof StopMacroAction) {
            StopMacroAction stopAction = (StopMacroAction) action;
            if (stopAction.target == StopMacroAction.StopTarget.SELF) stopCurrentRun();
            else stopAction.execute(mc);
        } else if (action instanceof UseItemPhaseAction phaseAction) {
            runUseItemPhaseAction(mc, phaseAction);
        } else if (action instanceof autismclient.api.macro.ContextMacroAction contextAction) {
            runContextMacroAction(contextAction);
        } else {

            if (module != null && module.useInstantExecutionMode()) {
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
                runMacroActionOnClientThread(mc, action, action.getDisplayName());
            }
        }

        commitConditionOutput(action, mc);

        if (postGuiFuture != null && isCurrentRunActive()) {
            try {
                waitForCondition(postGuiFuture);
            } catch (java.util.concurrent.CancellationException e) {  }
        }

        if (isCurrentRunActive() && !isInstantInventoryAction(action) && module != null && module.useInstantExecutionMode()) {
            int delayUs = module.getActionDelayUs();
            if (delayUs > 0) java.util.concurrent.locks.LockSupport.parkNanos(delayUs * 1000L);
            else Thread.onSpinWait();
        }
        } finally {
            releaseClientResources(heldResources);
        }
    }

    private static boolean dynamicTextInputsAvailable(MacroAction action, Minecraft mc) {
        if (action instanceof WaitForChatAction chat && chat.effectiveMatchMode() == MacroCapturePattern.Mode.MATCH) {
            return MacroVariables.resolve(chat.pattern, mc).success();
        }
        if (action instanceof WaitForGuiAction gui && gui.matchMode == MacroCapturePattern.Mode.MATCH) {
            return MacroVariables.resolve(gui.guiTitle, mc).success();
        }
        if (action instanceof WaitGuiTypeAction gui && gui.matchMode == MacroCapturePattern.Mode.MATCH) {
            return MacroVariables.resolve(gui.title, mc).success();
        }
        if (action instanceof WaitInventoryPredicateAction inventory) {
            return MacroVariables.resolve(inventory.itemName, mc).success();
        }
        if (action instanceof WaitDurabilityAction durability && durability.targetMode == WaitDurabilityAction.TargetMode.ITEM) {
            return MacroVariables.resolve(durability.itemName, mc).success();
        }
        if (action instanceof WaitEntityTargetAction entity) {
            return MacroVariables.resolve(entity.entityId, mc).success();
        }
        return true;
    }

    private static java.util.List<net.minecraft.network.protocol.Packet<?>> preGeneratePacketsForRepeat(
            int count, Minecraft mc) {
        RepeatPacketContext context = REPEAT_PACKET_CONTEXT.get();
        if (context == null || context.macro() == null) return new java.util.ArrayList<>();
        return preGeneratePackets(context.macro(), context.startIdx(), count, context.endIdx());
    }

    private static boolean isTargetWaitInteractionAction(MacroAction action) {
        return action instanceof OpenContainerAction || action instanceof InteractEntityAction;
    }

    private static CompletableFuture<Void> runTargetWaitInteractionAction(Minecraft mc, MacroAction action) throws InterruptedException {
        boolean waitForTarget = switch (action) {
            case OpenContainerAction open -> open.waitForTarget;
            case InteractEntityAction interact -> interact.waitForTarget;
            default -> false;
        };

        if (!waitForTarget) {
            CompletableFuture<Void> postGuiFuture = createPostGuiFuture(mc, action);
            runTargetInteractionAttempt(mc, action);
            return postGuiFuture;
        }

        setCurrentStatus("Waiting target: " + action.getDisplayName());
        while (isCurrentRunActive()) {
            if (!canRunTargetInteractionNow(mc, action)) {
                java.util.concurrent.locks.LockSupport.parkNanos(MACRO_WAIT_POLL_NANOS);
                if (Thread.interrupted()) throw new InterruptedException();
                continue;
            }

            CompletableFuture<Void> postGuiFuture = createPostGuiFuture(mc, action);
            if (runTargetInteractionAttempt(mc, action)) {
                return postGuiFuture;
            }
            if (postGuiFuture != null) postGuiFuture.cancel(true);
            java.util.concurrent.locks.LockSupport.parkNanos(MACRO_WAIT_POLL_NANOS);
            if (Thread.interrupted()) throw new InterruptedException();
        }
        return null;
    }

    private static boolean canRunTargetInteractionNow(Minecraft mc, MacroAction action) throws InterruptedException {
        return callOnClientThread(mc, () -> {
            if (action instanceof OpenContainerAction open) return open.canExecuteNow(mc);
            if (action instanceof InteractEntityAction interact) return interact.canExecuteNow(mc);
            return true;
        }, Boolean.FALSE);
    }

    private static boolean runTargetInteractionAttempt(Minecraft mc, MacroAction action) throws InterruptedException {
        return callOnClientThread(mc, () -> {
            try {
                if (action instanceof OpenContainerAction open) return open.tryExecute(mc);
                if (action instanceof InteractEntityAction interact) return interact.tryExecute(mc);
                action.execute(mc);
                return true;
            } catch (Exception e) {
                AutismClientMessaging.sendPrefixed("\u00a7cError in " + action.getDisplayName() + ": " + e.getMessage());
                return true;
            }
        }, Boolean.FALSE);
    }

    private static CompletableFuture<Void> createPostGuiFuture(Minecraft mc, MacroAction action) {
        if (Boolean.TRUE.equals(SUPPRESS_POST_GUI_AFTER.get())) return null;
        if (!(action instanceof WaitsForGui wfg) || !wfg.isWaitForGuiAfter()) return null;
        return createWaitsForGuiFuture(mc, wfg);
    }

    private static CompletableFuture<Void> createWaitsForGuiFuture(Minecraft mc, WaitsForGui wfg) {
        MacroTemplate.Resolution guiName = MacroVariables.resolve(wfg.getWaitGuiName(), mc);
        if (!guiName.success()) return CompletableFuture.completedFuture(null);
        return wfg.isWaitForGuiChange()
                ? MacroConditionRegistry.waitForGuiChange(mc.gui.screen())
                : MacroConditionRegistry.waitForGui(guiName.value());
    }

    private static boolean waitForGuiBeforeAction(Minecraft mc, MacroAction action) throws InterruptedException {
        if (!(action instanceof WaitsForGui wfg)) return true;
        return waitForGuiBeforeAction(mc, wfg);
    }

    private static boolean waitForGuiBeforeAction(Minecraft mc, WaitsForGui action) throws InterruptedException {
        if (mc == null || action == null || !action.isWaitForGuiBefore()) return true;
        MacroTemplate.Resolution guiName = MacroVariables.resolve(action.getWaitGuiName(), mc);
        if (!guiName.success()) return false;
        if (isWaitGuiPresent(mc, guiName.value())) {
            waitForCurrentGuiReady(mc);
            return true;
        }
        CompletableFuture<Void> future = MacroConditionRegistry.waitForGui(guiName.value());
        waitForCondition(future);
        waitForCurrentGuiReady(mc);
        return true;
    }

    private static boolean isWaitGuiPresent(Minecraft mc, String guiName) {
        if (mc == null || mc.gui.screen() == null) return false;
        if (MacroGuiMatcher.isOwnScreen(mc.gui.screen())) return false;
        String expected = guiName == null ? "" : guiName.trim();
        if (expected.isEmpty()) return true;
        String actual = mc.gui.screen().getTitle() == null ? "" : mc.gui.screen().getTitle().getString();
        return matchesWaitGuiTitle(expected, actual);
    }

    private static boolean matchesWaitGuiTitle(String expected, String actual) {
        if (expected == null || expected.isBlank() || actual == null || actual.isBlank()) return false;
        String expectedLower = expected.toLowerCase(Locale.ROOT);
        String actualLower = actual.toLowerCase(Locale.ROOT);
        if (expectedLower.equals(actualLower) || actualLower.contains(expectedLower)) return true;
        for (String word : expectedLower.split("\\s+")) {
            if (!word.isBlank() && !actualLower.contains(word)) return false;
        }
        return true;
    }

    private static void waitForCurrentGuiReady(Minecraft mc) throws InterruptedException {

        long deadline = System.nanoTime() + 500_000_000L;
        while (isCurrentRunActive()) {
            Boolean ready = callOnClientThread(mc, () -> {
                if (mc.player == null || mc.gui.screen() == null) return false;
                if (mc.player.containerMenu != null && mc.player.containerMenu != mc.player.inventoryMenu) {
                    return mc.player.containerMenu.getStateId() > 0;
                }
                return true;
            }, Boolean.FALSE);
            if (Boolean.TRUE.equals(ready)) return;
            if (System.nanoTime() >= deadline) return;
            java.util.concurrent.locks.LockSupport.parkNanos(MACRO_WAIT_POLL_NANOS);
            if (Thread.interrupted()) return;
        }
    }

    private static void runMacroActionOnClientThread(Minecraft mc, MacroAction action, String label) throws InterruptedException {
        runOnClientThread(mc, () -> {
            try {
                action.execute(mc);
            } catch (Throwable t) {
                recordAddonActionError(action, "Action threw " + t.getClass().getSimpleName());
                AutismClientMessaging.sendPrefixed("\u00a7cError in " + label + ": "
                        + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
            }
        });
    }

    private static void runItemActionPaced(Minecraft mc, ItemAction ia) throws InterruptedException {
        setCurrentStatus(ia.getDisplayName());
        java.util.List<Runnable> plan = callOnClientThread(mc, () -> ia.buildClickPlan(mc), java.util.Collections.<Runnable>emptyList());
        if (plan == null || plan.isEmpty()) return;
        long delayMs = Math.max(0, ia.clickDelayMs);
        RunState scheduledRun = CURRENT_RUN.get();
        boolean first = true;
        for (Runnable op : plan) {
            if (!isCurrentRunActive()) break;
            if (!first && delayMs > 0) Thread.sleep(delayMs);
            first = false;
            mc.execute(() -> withRunContext(scheduledRun, () -> {
                if (PackHideState.isHardLocked()) return;
                try {
                    op.run();
                } catch (Throwable t) {
                    AutismClientMessaging.sendPrefixed("\u00a7cError in ItemAction: "
                            + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
                }
            }));
        }
    }

    private static void runContextMacroAction(autismclient.api.macro.ContextMacroAction action) {
        if (PackHideState.isHardLocked()) return;
        if (action == null) return;
        try {
            action.run(new ApiExecutionContext(CURRENT_RUN.get()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            recordAddonActionError(action, "Context action threw " + t.getClass().getSimpleName());
            AutismClientMessaging.sendPrefixed("\u00a7cAddon action error: "
                    + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        }
    }

    private static void recordAddonActionError(MacroAction action, String detail) {
        if (action == null) return;
        String owner = autismclient.api.macro.MacroActionRegistry.ownerOf(action.getTypeId());
        if (owner != null && !owner.isBlank()) autismclient.addons.AddonManager.recordRuntimeError(owner, detail);
    }

    private static final class ApiExecutionContext implements autismclient.api.macro.MacroExecutionContext {
        private final RunState run;

        private ApiExecutionContext(RunState run) {
            this.run = run;
        }

        @Override public Minecraft mc() { return Minecraft.getInstance(); }

        @Override public void runOnClientThread(Runnable r) {
            if (r == null) return;

            Runnable safe = () -> withRunContext(run, () -> {
                try { r.run(); }
                catch (Throwable t) { AutismClientMessaging.sendPrefixed("\u00a7cAddon action (client task) error: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage())); }
            });
            try { MacroExecutor.runOnClientThread(Minecraft.getInstance(), safe); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        @Override public <T> T callOnClientThread(java.util.function.Supplier<T> s) {
            if (s == null) return null;
            java.util.function.Supplier<T> safe = () -> {
                AtomicReference<T> value = new AtomicReference<>();
                withRunContext(run, () -> {
                    try { value.set(s.get()); }
                    catch (Throwable t) { AutismClientMessaging.sendPrefixed("\u00a7cAddon action (client task) error: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage())); }
                });
                return value.get();
            };
            try { return MacroExecutor.callOnClientThread(Minecraft.getInstance(), safe, null); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
        }

        @Override public void waitTicks(int ticks) {
            withRunContext(run, () -> {
                for (int i = 0; i < ticks && isCurrentRunActive(); i++) {
                    waitForCondition(MacroConditionRegistry.waitForNextTick());
                }
            });
        }

        @Override public void awaitCondition(CompletableFuture<Void> future) {
            if (future != null) withRunContext(run, () -> waitForCondition(future));
        }

        @Override public void awaitCondition(autismclient.api.macro.AddonCondition condition) {
            if (condition != null) withRunContext(run, () -> waitForCondition(MacroConditionRegistry.await(condition)));
        }

        @Override public boolean isActive() { return run != null && run.running.get() && !PackHideState.isHardLocked(); }

        @Override public void setStatus(String status) { withRunContext(run, () -> setCurrentStatus(status)); }

        @Override public void sendPacket(net.minecraft.network.protocol.Packet<?> packet) {
            if (packet == null) return;
            runOnClientThread(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.getConnection() != null) mc.getConnection().send(packet);
            });
        }

        @Override public java.util.Optional<MacroValue> variable(String name) {
            return run == null ? java.util.Optional.empty() : run.variables.get(name);
        }

        @Override public void setVariable(String name, MacroValue value) {
            if (run != null) run.variables.set(name, value);
        }

        @Override public void removeVariable(String name) {
            if (run != null) run.variables.remove(name);
        }

        @Override public java.util.Map<String, MacroValue> variables() {
            return run == null ? java.util.Map.of() : run.variables.snapshot();
        }

        @Override public MacroTemplate.Resolution resolveTemplate(String template) {
            return MacroTemplate.resolve(template, run == null ? null : run.variables, Minecraft.getInstance());
        }
    }

    private static void runUseItemPhaseAction(Minecraft mc, UseItemPhaseAction action) throws InterruptedException {
        int times = action.repeatTimes();
        for (int i = 0; i < times && isCurrentRunActive(); i++) {

            runOnClientThread(mc, () -> {
                try { action.sendUsePacket(mc); }
                catch (Exception e) { AutismClientMessaging.sendPrefixed("\u00a7cError in Use phase: " + e.getMessage()); }
            });

            final boolean[] gateInstalled = { false };
            if (action.shouldGate()) {
                runOnClientThread(mc, () -> {
                    try { gateInstalled[0] = action.installGate(mc); }
                    catch (Exception e) { AutismClientMessaging.sendPrefixed("\u00a7cError in Use gate: " + e.getMessage()); }
                });
            }
            try {

                for (int t = 0; t < action.holdTicks && isCurrentRunActive(); t++) {
                    CompletableFuture<Void> tf = MacroConditionRegistry.waitForNextTick();
                    waitForCondition(tf);
                }
            } finally {
                if (gateInstalled[0]) {
                    final boolean wasOnThread = mc.isSameThread();
                    if (wasOnThread) {
                        action.removeGate();
                    } else {
                        runOnClientThread(mc, action::removeGate);
                    }
                }
            }
            if (!isCurrentRunActive()) break;

            runOnClientThread(mc, () -> {
                try { action.finishRelease(mc); }
                catch (Exception e) { AutismClientMessaging.sendPrefixed("\u00a7cError in Use release: " + e.getMessage()); }
            });
        }
    }

    private static void runClickActionBurst(Minecraft mc, ClickAction action) throws InterruptedException {
        int times = Math.max(1, action.clickCount);
        runOnClientThread(mc, () -> {
            for (int c = 0; c < times && isCurrentRunActive(); c++) {
                try {
                    action.execute(mc);
                } catch (Exception e) {
                    AutismClientMessaging.sendPrefixed("\u00a7cError in Click: " + e.getMessage());
                    break;
                }
            }
        });
    }

    private static boolean isInstantInventoryAction(MacroAction action) {
        if (action instanceof ItemAction
                || action instanceof DropAction
                || action instanceof SwapSlotsAction
                || action instanceof ClickAction
                || action instanceof XCarryAction) {
            return true;
        }
        return action instanceof StoreItemAction storeItemAction && !storeItemAction.persistent;
    }

    private static void runStoreItemAction(Minecraft mc, StoreItemAction action) throws InterruptedException {
        if (action == null) return;
        int delayTicks = StoreItemAction.clampDelayTicks(action.delayTicks);
        if (delayTicks <= 0) {
            runOnClientThread(mc, () -> action.doTransfer(mc));
            return;
        }

        List<Integer> slots = callOnClientThread(mc, () -> action.collectTransferSlots(mc), List.of());
        for (int i = 0; i < slots.size() && isCurrentRunActive(); i++) {
            AutismInventoryClickHelper.click(mc, slots.get(i), 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
            if (i + 1 < slots.size()) waitTicks(delayTicks);
        }

        if (action.closeAfter && !action.persistent) {
            runOnClientThread(mc, () -> AutismGuiActions.closeCurrentScreen(mc, action.closeSendPkt, false));
        }
    }

    private static void waitTicks(int ticks) {
        for (int i = 0; i < ticks && isCurrentRunActive(); i++) {
            CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
            waitForCondition(tickFuture);
        }
    }

    private static void runUseItemAction(Minecraft mc, UseItemAction action) throws InterruptedException {
        if (action == null) return;

        boolean sneaking = action.sneak;
        try {
            if (action.useMode == UseItemAction.UseMode.CUSTOM_HOLD) {
                runOnClientThread(mc, () -> {
                    try {
                        if (sneaking) autismclient.util.AutismMacroSneak.forceSneak(mc, true);
                        action.execute(mc);
                    }
                    catch (Exception e) { AutismClientMessaging.sendPrefixed("\u00a7cError in UseItem: " + e.getMessage()); }
                });
                if (action.holdTicks > 0) {
                    waitTicks(action.holdTicks);
                    if (!isCurrentRunActive()) return;
                    runOnClientThread(mc, () -> {
                        try { action.sendRelease(mc); }
                        catch (Exception e) { AutismClientMessaging.sendPrefixed("\u00a7cError in UseItem release: " + e.getMessage()); }
                    });
                    if (action.waitForFinish) waitForUseItemFinish(mc, action.holdTicks + 8);
                }
                return;
            }

            int times = Math.max(1, action.useCount);
            for (int i = 0; i < times && isCurrentRunActive(); i++) {
                runOnClientThread(mc, () -> {
                    try {
                        if (sneaking) autismclient.util.AutismMacroSneak.forceSneak(mc, true);
                        action.execute(mc);
                    }
                    catch (Exception e) { AutismClientMessaging.sendPrefixed("\u00a7cError in UseItem: " + e.getMessage()); }
                });
                if (action.waitForFinish) waitForUseItemFinish(mc, 0);
                if (i + 1 < times && isCurrentRunActive()) waitOneTick();
            }
        } finally {
            if (sneaking) mc.execute(() -> autismclient.util.AutismMacroSneak.forceSneak(mc, false));
        }
    }

    private static void runContainerClickSequenceAction(Minecraft mc, ContainerClickSequenceAction action) throws InterruptedException {
        if (action == null) return;
        List<Integer> slots = action.resolvedSlots();
        int repeats = Math.max(1, action.repeatCount);
        int delay = Math.max(0, action.delayTicks);
        int total = repeats * slots.size();
        int sent = 0;
        for (int repeat = 0; repeat < repeats && isCurrentRunActive(); repeat++) {
            for (int slot : slots) {
                if (!isCurrentRunActive()) return;
                runOnClientThread(mc, () -> action.executeClick(mc, slot));
                sent++;
                if (delay > 0 && sent < total) waitTicks(delay);
            }
        }
    }

    private static void runPacketBurstAction(Minecraft mc, PacketBurstAction action) {
        if (action == null || mc == null || mc.getConnection() == null) return;
        if (action.flushBefore) AutismSharedState.get().flushDelayedPackets(mc.getConnection());
        int total = action.effectiveCount();
        int delay = Math.max(0, action.delayTicks);
        for (int i = 0; i < total && isCurrentRunActive(); i++) {
            action.sendOne(mc);
            if (delay > 0 && i + 1 < total) waitTicks(delay);
        }
    }

    private static void waitForUseItemFinish(Minecraft mc, int fallbackTicks) throws InterruptedException {
        if (mc == null || !isCurrentRunActive()) return;

        if (!Boolean.TRUE.equals(callOnClientThread(mc,
                () -> mc.player != null && mc.player.isUsingItem(),
                Boolean.FALSE))) {
            return;
        }

        int remaining = callOnClientThread(mc,
                () -> mc.player == null ? 0 : Math.max(0, mc.player.getUseItemRemainingTicks()),
                0);
        int maxTicks = Math.max(8, remaining > 0 ? remaining + 8 : fallbackTicks + 8);
        maxTicks = Math.min(maxTicks, 72000);
        for (int i = 0; i < maxTicks && isCurrentRunActive(); i++) {
            if (!Boolean.TRUE.equals(callOnClientThread(mc,
                    () -> mc.player != null && mc.player.isUsingItem(),
                    Boolean.FALSE))) {
                return;
            }
            CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
            waitForCondition(tickFuture);
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

    private static void waitOneTick() {
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
                        runOnClientThread(mc, () -> holdMacroSneak(mc, sneakMode));
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
                                runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction));
                                if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
                                sendInstaBreakAttempt(mc, target, direction);
                            } else if (t == InteractTiming.AFTER_PLUS) {
                                sendInstaBreakAttempt(mc, target, direction);
                                waitOneTick();
                                runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction));
                            } else {
                                sendInstaBreakAttempt(mc, target, direction);
                                if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
                                runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction));
                            }
                        } else {
                            sendInstaBreakAttempt(mc, target, direction);
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
        }, false);
    }

    private static void sendInstaBreakAttempt(Minecraft mc, BlockPos pos, Direction direction) throws InterruptedException {
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
        });
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
                        : callOnClientThread(mc, () -> startBreakOnClient(mc, action, target, direction, started), -1);
                } else if (action.interact && action.interactTiming == InteractTiming.CUSTOM) {
                    result = useNextSteps
                        ? breakCustomTickWithNextSteps(mc, action, target, direction, nextStepsFired, missingNextStepsWarned)
                        : breakCustomTick(mc, action, target, direction);
                } else {
                    result = useNextSteps
                        ? breakContinueWithNextSteps(mc, action, target, direction, nextStepsFired, missingNextStepsWarned)
                        : callOnClientThread(mc, () -> breakContinueOnClient(mc, action, target, direction), -1);
                }

                if (result < 0) {
                    return;
                } else if (result == 1) {
                    boolean completedStartedBreak = started[0];
                    if (useNextSteps && completedStartedBreak) {
                        runBreakNextStepsAfterCompletion(mc, action, nextStepsFired, missingNextStepsWarned);
                    } else if (action.interact && action.interactTiming == InteractTiming.AFTER_PLUS) {
                        waitOneTick();
                        runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction));
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
            });
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

        int result = callOnClientThread(mc, () -> startPreparedBreakOnClient(mc, action, pos, dir, started), -1);
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

        int result = callOnClientThread(mc, () -> continueBreakOnClient(mc, action, pos, dir), -1);
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
            return callOnClientThread(mc, () -> continueBreakOnClient(mc, action, pos, dir), -1);
        }

        long ns = Math.abs((long) action.interactCustomMs) * 1_000_000L;
        int broke;
        if (action.interactCustomMs < 0) {
            runBreakNextStepsOnce(mc, action, nextStepsFired, missingNextStepsWarned);
            if (!isCurrentRunActive()) return -1;
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            broke = callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1);
        } else {
            broke = callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1);
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
            }, -1);
        }

        long ns = Math.abs((long) action.interactCustomMs) * 1_000_000L;
        int broke;
        if (action.interactCustomMs < 0) {

            runOnClientThread(mc, () -> sendBlockInteract(mc, pos, dir));
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            broke = callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1);
        } else {

            broke = callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1);
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            runOnClientThread(mc, () -> sendBlockInteract(mc, pos, dir));
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
                    AutismClientMessaging.sendPrefixed("\u00a7cBreak next step failed (" + next.getDisplayName() + "): " + safeThrowableMessage(t));
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

    private static void runOnClientThread(Minecraft mc, Runnable runnable) throws InterruptedException {
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

    private static <T> T callOnClientThread(Minecraft mc, java.util.function.Supplier<T> supplier, T fallback) throws InterruptedException {
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

    private static void runPlaceAction(Minecraft mc, PlaceAction action) throws InterruptedException {
        if (mc == null || mc.player == null || mc.level == null) return;

        ItemTarget target = action.resolvedItemTarget().resolveTemplate(mc);
        if (target == null) return;
        if (action.waitForItem && target != null && (target.hasIdentity() || target.hasSlot())) {
            setCurrentStatus("Waiting for " + describeItemTarget(target));
            waitForItemActionEntry(mc, target);
        }

        if (!action.interact) {

            runMacroActionOnClientThread(mc, action, "Place");
            return;
        }

        final BlockPos placePos = action.blockPos == null ? BlockPos.ZERO : action.blockPos;
        final Direction face = action.manualDirection && action.direction != null ? action.direction : Direction.UP;
        InteractTiming t = action.interactTiming;
        boolean interactFirst = t == InteractTiming.BEFORE || (t == InteractTiming.CUSTOM && action.interactCustomMs < 0);
        long ns = t == InteractTiming.CUSTOM ? Math.abs((long) action.interactCustomMs) * 1_000_000L : 0L;

        if (interactFirst) {

            runOnClientThread(mc, () -> sendBlockInteract(mc, placePos, face));
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            runMacroActionOnClientThread(mc, action, "Place");
        } else if (t == InteractTiming.WITH) {

            runOnClientThread(mc, () -> {
                try { action.execute(mc); } catch (Exception e) { AutismClientAddon.LOG.debug("[Autism] Macro WITH-interact action failed", e); }
                sendBlockInteract(mc, placePos, face);
            });
        } else if (t == InteractTiming.AFTER_PLUS) {
            runMacroActionOnClientThread(mc, action, "Place");
            waitOneTick();
            runOnClientThread(mc, () -> sendBlockInteract(mc, placePos, face));
        } else {

            runMacroActionOnClientThread(mc, action, "Place");
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            runOnClientThread(mc, () -> sendBlockInteract(mc, placePos, face));
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

    private static ItemTarget resolveItemTarget(ItemTarget target, String legacyEntry) {
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
            if (!runBaritoneStart(mc, () -> AutismCompatManager.startBaritoneMine(mc, blockNames))) {
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
        RunState run = CURRENT_RUN.get();
        if (run != null) BARITONE_OWNER.set(run);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean started = new AtomicBoolean(false);

        mc.execute(() -> {
            try {
                started.set(starter.getAsBoolean());
            } finally {
                latch.countDown();
            }
        });

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

    private static final class TimedOneShotPacketListener {
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

    private interface TimedPacketMatch {
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

    private static TimedOneShotPacketListener awaitTimedReceive(TimedPacketMatch match) {
        return new TimedOneShotPacketListener(TIMED_ONE_SHOT_RECV, match);
    }

    private static TimedOneShotPacketListener awaitTimedSend(java.util.function.Predicate<Packet<?>> match) {
        return awaitTimedSend((packet, direction, nanoTime) -> match.test(packet));
    }

    private static TimedOneShotPacketListener awaitTimedSend(TimedPacketMatch match) {
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
        syncRecentChatServerContext();

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
            ChatCapture capture = extractIncomingChat(packet);
            if (capture != null && capture.message() != null && !capture.message().isBlank()) {

                for (RunState run : runs) {
                    var matched = run.running.get() && run.waitingForChat.get()
                        ? captureChatAction(run.waitingChatAction, capture, run.variables)
                        : java.util.Optional.<java.util.Map<String, MacroValue>>empty();
                    if (matched.isPresent()) {
                        run.stagedCaptures.put(run.waitingChatAction, matched.get());
                        CompletableFuture<Void> f = run.chatFuture;
                        if (f != null) f.complete(null);
                    }
                }
                if (isCurrentRunActive() && isWaitingForChat.get() && matchesWaitingChat(capture)) {
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
                    if (run.waitingForPacket.get() && matchesPacketTarget(run.waitingPacketName.get(), packet, direction)) {
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
                if (matchesPacketTarget(target, packet, direction)) {
                    lastReceivedPacket.set(direction + " " + AutismPacketNamer.getFriendlyName(packet, direction));
                    f = activePacketFuture;
                }
            }
            if (f != null) f.complete(null);
        }
    }

    public static void observeIncomingChat(Packet<?> packet) {
        if (PackHideState.isHardLocked()) return;
        ChatCapture capture = extractIncomingChat(packet);
        if (capture == null || capture.message() == null || capture.message().isBlank()) return;
        syncRecentChatServerContext();
        recordRecentChat(capture);
    }

    private static void recordRecentChat(ChatCapture capture) {
        RecentChatMessage message = new RecentChatMessage(
            capture.sender() == null ? "" : capture.sender(),
            capture.message(),
            capture.displayText(),
            capture.displayComponent(),
            capture.source(),
            System.currentTimeMillis()
        );
        synchronized (recentChatLock) {
            recentChatMessages.removeIf(existing -> existing.displayText().equals(message.displayText()));
            recentChatMessages.addFirst(message);
            while (recentChatMessages.size() > MAX_RECENT_CHAT_MESSAGES) {
                recentChatMessages.removeLast();
            }
        }
    }

    private static volatile String recentChatServerKeyMirror = "";

    private static void syncRecentChatServerContext() {
        String currentServerKey = getCurrentMultiplayerServerKey();
        if (currentServerKey.isEmpty()) return;

        if (currentServerKey.equals(recentChatServerKeyMirror)) return;
        synchronized (recentChatLock) {
            if (recentChatServerKey.isEmpty()) {
                recentChatServerKey = currentServerKey;
                recentChatServerKeyMirror = currentServerKey;
                return;
            }
            if (!recentChatServerKey.equals(currentServerKey)) {
                recentChatMessages.clear();
                recentChatServerKey = currentServerKey;
                recentChatServerKeyMirror = currentServerKey;
                synchronized (recentGuiLock) {
                    recentGuiTitles.clear();
                }
            } else {
                recentChatServerKeyMirror = currentServerKey;
            }
        }
    }

    private static String getCurrentMultiplayerServerKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.isLocalServer()) return "";

        ServerData entry = mc.getCurrentServer();
        if (entry != null && entry.ip != null && !entry.ip.isBlank()) {
            return normalizeServerKey(entry.ip);
        }

        if (mc.getConnection() != null && mc.getConnection().getConnection() != null) {
            SocketAddress address = mc.getConnection().getConnection().getRemoteAddress();
            if (address instanceof InetSocketAddress inet) {
                String host = inet.getHostString();
                if ((host == null || host.isBlank()) && inet.getAddress() != null) {
                    host = inet.getAddress().getHostAddress();
                }
                if (host != null && !host.isBlank()) {
                    return normalizeServerKey(host + ":" + inet.getPort());
                }
            } else if (address != null) {
                String raw = address.toString();
                if (raw != null && !raw.isBlank()) {
                    return normalizeServerKey(raw);
                }
            }
        }

        return "";
    }

    private static String normalizeServerKey(String address) {
        if (address == null) return "";
        String stripped = address.startsWith("/") ? address.substring(1) : address;
        return stripped.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesWaitingChat(ChatCapture capture) {
        String patternJson = waitingChatPatternJson.get();
        if (!waitingChatIsRegex.get() && patternJson != null && !patternJson.isBlank() && capture.displayComponent() != null) {
            String captureJson = serializeTextComponent(capture.displayComponent());
            if (patternJson.equals(captureJson)) return true;
        }
        String pattern = normalizeManualText(waitingChatPattern.get());
        if (pattern == null || pattern.isBlank()) return true;
        if (waitingChatIsRegex.get()) {
            try {
                java.util.regex.Pattern compiled = MacroCapturePattern.compiled(pattern);
                return compiled.matcher(capture.message()).matches() || compiled.matcher(capture.displayText()).matches();
            } catch (Exception ignored) {
                return false;
            }
        }
        int threshold = waitingChatFuzzyPercent.get();
        return fuzzyChatMatch(pattern, capture.message(), threshold)
            || fuzzyChatMatch(pattern, capture.displayText(), threshold);
    }

    private static boolean matchesChatAction(WaitForChatAction action, ChatCapture capture) {
        return captureChatAction(action, capture, currentVariableContext()).isPresent();
    }

    private static java.util.Optional<java.util.Map<String, MacroValue>> captureChatAction(WaitForChatAction action, ChatCapture capture) {
        return captureChatAction(action, capture, currentVariableContext());
    }

    private static java.util.Optional<java.util.Map<String, MacroValue>> captureChatAction(WaitForChatAction action, ChatCapture capture,
                                                                                           MacroVariableContext context) {
        if (action == null || capture == null) return java.util.Optional.empty();
        MacroCapturePattern.Mode mode = action.effectiveMatchMode();
        if (mode == MacroCapturePattern.Mode.MATCH && action.patternJson != null && !action.patternJson.isBlank() && capture.displayComponent() != null) {
            String captureJson = serializeTextComponent(capture.displayComponent());
            if (action.patternJson.equals(captureJson)) return java.util.Optional.of(chatValues(action, capture, java.util.Map.of()));
        }
        String pattern = normalizeManualText(action.pattern);
        if (mode == MacroCapturePattern.Mode.MATCH) {
            MacroTemplate.Resolution patternResolution = MacroTemplate.resolve(pattern, context, Minecraft.getInstance());
            if (!patternResolution.success()) return java.util.Optional.empty();
            pattern = patternResolution.value();
        }
        if (pattern == null || pattern.isBlank()) return java.util.Optional.of(chatValues(action, capture, java.util.Map.of()));
        if (mode != MacroCapturePattern.Mode.MATCH) {
            var messageResult = MacroCapturePattern.match(mode, pattern, capture.message());
            var result = messageResult.isPresent() ? messageResult : MacroCapturePattern.match(mode, pattern, capture.displayText());
            return result.map(value -> chatValues(action, capture, value.values()));
        }
        int threshold = WaitForChatAction.clampFuzzyPercent(action.fuzzyPercent);
        boolean matched = fuzzyChatMatch(pattern, capture.message(), threshold)
            || fuzzyChatMatch(pattern, capture.displayText(), threshold);
        return matched ? java.util.Optional.of(chatValues(action, capture, java.util.Map.of())) : java.util.Optional.empty();
    }

    private static java.util.Map<String, MacroValue> chatValues(WaitForChatAction action, ChatCapture capture,
                                                                  java.util.Map<String, MacroValue> captures) {
        java.util.Map<String, MacroValue> values = new java.util.LinkedHashMap<>(captures);
        if (action.saveAs != null && !action.saveAs.isBlank()) {
            java.util.Map<String, MacroValue> properties = new java.util.LinkedHashMap<>();
            properties.put("sender", MacroValue.text(capture.sender()));
            properties.put("message", MacroValue.text(capture.message()));
            properties.put("display", MacroValue.text(capture.displayText()));
            properties.put("source", MacroValue.text(capture.source().name()));
            values.put(action.saveAs, MacroValue.structured(MacroValue.Kind.TEXT, capture.displayText(), properties));
        }
        return java.util.Map.copyOf(values);
    }

    private static ChatCapture extractIncomingChat(Packet<?> packet) {

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket systemChat) {
            Component content = systemChat.content();
            if (content == null) return null;
            String raw = content.getString();
            if (raw == null || raw.isBlank()) return null;
            return buildChatCapture("", raw, raw, content, ChatSource.PLAYER);
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket disguisedChat) {
            Component content = disguisedChat.message();
            String text = content == null ? null : content.getString();
            if (text == null || text.isBlank()) return null;
            Component decorated = disguisedChat.chatType().decorate(content);
            String sender = disguisedChat.chatType().name().getString();
            return buildChatCapture(sender, text, null, decorated, ChatSource.PLAYER);
        }

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerChatPacket playerChat) {

            Component content = playerChat.unsignedContent() != null
                ? playerChat.unsignedContent()
                : Component.literal(playerChat.body().content());
            String message = content.getString();
            if (message == null || message.isBlank()) message = playerChat.body().content();
            if (message == null || message.isBlank()) return null;

            Component decorated = playerChat.chatType().decorate(content);
            String sender = playerChat.chatType().name().getString();
            if (sender == null || sender.isBlank()) {
                String byUuid = resolvePlayerName(playerChat.sender());
                if (byUuid != null && !byUuid.isBlank()) sender = byUuid;
            }
            return buildChatCapture(sender, message, null, decorated, ChatSource.PLAYER);
        }

        return null;
    }

    private static ChatCapture buildChatCapture(String sender, String message, String displayText, Component displayComponent, ChatSource source) {
        String cleanMessage = sanitizeChatText(message);
        if (cleanMessage.isBlank()) return null;
        String cleanSender = sanitizeChatText(sender);
        String cleanDisplay = sanitizeChatText(displayText);
        Component rendered = literalizeTextComponent(displayComponent);
        String display = cleanDisplay;
        if (rendered == null || rendered.getString().isBlank()) {
            rendered = cleanSender.isBlank()
                    ? Component.literal(cleanMessage)
                    : Component.literal("<" + cleanSender + "> ").append(Component.literal(cleanMessage));
        }
        if (display == null || display.isBlank()) {
            display = sanitizeChatText(rendered.getString());
        }
        ChatSource resolvedSource = source != null ? source : ChatSource.PLAYER;
        return new ChatCapture(cleanSender, cleanMessage, sanitizeChatText(display), rendered, resolvedSource);
    }

    private static String sanitizeChatText(String text) {
        if (text == null) return "";
        String stripped = stripLegacyFormatting(text).replace('\n', ' ').replace('\r', ' ');
        StringBuilder out = new StringBuilder(stripped.length());
        for (int offset = 0; offset < stripped.length();) {
            int cp = stripped.codePointAt(offset);
            offset += Character.charCount(cp);
            int type = Character.getType(cp);
            if (type == Character.FORMAT
                    || type == Character.CONTROL
                    || cp == 0x200B
                    || cp == 0x200C
                    || cp == 0x200D
                    || cp == 0x2060
                    || cp == 0xFE0E
                    || cp == 0xFE0F) {
                continue;
            }
            out.appendCodePoint(cp);
        }
        return out.toString().trim();
    }

    public static String normalizeManualText(String text) {
        if (text == null || text.isBlank()) return "";
        String stripped = stripLegacyFormatting(text).trim();
        String flattened = flattenManualTextComponentJson(stripped);
        return sanitizeChatText(flattened != null ? flattened : stripped);
    }

    private static String flattenManualTextComponentJson(String text) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\""))) return null;
        try {
            com.google.gson.JsonElement element = JsonParser.parseString(trimmed);
            if (element == null || element.isJsonNull()) return "";
            if (element.isJsonArray()) {
                StringBuilder out = new StringBuilder();
                for (com.google.gson.JsonElement child : element.getAsJsonArray()) {
                    Component component = parseTextComponentElement(child);
                    if (component != null) out.append(component.getString());
                }
                return out.toString();
            }
            Component component = parseTextComponentElement(element);
            return component == null ? null : component.getString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Component parseTextComponentElement(com.google.gson.JsonElement element) {
        try {
            Component component = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow();
            return literalizeTextComponent(component);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String stripLegacyFormatting(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch == '\u00A7' || ch == '&') && i + 1 < text.length()) {
                char next = Character.toLowerCase(text.charAt(i + 1));
                if ((next >= '0' && next <= '9')
                        || (next >= 'a' && next <= 'f')
                        || (next >= 'k' && next <= 'o')
                        || next == 'r'
                        || next == 'x') {
                    i++;
                    continue;
                }
            }
            out.append(ch);
        }
        return out.toString();
    }

    private static boolean isLikelyPlayerSender(String sender) {
        String cleanSender = sanitizeChatText(sender);
        if (cleanSender.isBlank()) return false;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                for (var entry : mc.getConnection().getListedOnlinePlayers()) {
                    String playerName = entry != null && entry.getProfile() != null ? extractTextValue(entry.getProfile()) : null;
                    if (playerName != null && cleanSender.equalsIgnoreCase(playerName)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {  }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                for (var player : mc.level.players()) {
                    String playerName = player != null && player.getGameProfile() != null ? extractTextValue(player.getGameProfile()) : null;
                    if (playerName != null && cleanSender.equalsIgnoreCase(playerName)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {  }
        return false;
    }

    public static boolean fuzzyChatMatch(String pattern, String candidate, int thresholdPercent) {
        String normalizedPattern = normalizeChatText(pattern);
        String normalizedCandidate = normalizeChatText(candidate);
        if (normalizedPattern.isBlank()) return true;
        if (normalizedCandidate.isBlank()) return false;
        if (normalizedCandidate.contains(normalizedPattern)) return true;

        List<String> patternTokens = tokenizeChat(normalizedPattern);
        List<String> candidateTokens = tokenizeChat(normalizedCandidate);
        if (patternTokens.isEmpty()) return true;

        if (thresholdPercent >= 100) {
            return allPatternTokensPresent(patternTokens, normalizedCandidate);
        }

        double tokenScore = computeTokenCoverage(patternTokens, candidateTokens);
        double charScore = similarityRatio(normalizedPattern.replace(" ", ""), normalizedCandidate.replace(" ", ""));
        double score = Math.max(tokenScore, tokenScore * 0.7 + charScore * 0.3);
        return score + 1.0e-9 >= (Math.max(40, Math.min(100, thresholdPercent)) / 100.0);
    }

    private static String normalizeChatForCompare(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isLetterOrDigit(ch)) out.append(ch);
            else if (Character.isWhitespace(ch)) out.append(' ');
            else if (ch == '\'' || ch == '"' || ch == '`' || ch == '-' || ch == '_' || ch == '\u2019') {

            } else {
                out.append(' ');
            }
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    public static String normalizeChatText(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(normalized.length());
        boolean lastWasSpace = true;
        for (int offset = 0; offset < normalized.length();) {
            int cp = normalized.codePointAt(offset);
            offset += Character.charCount(cp);

            cp = foldStyledLatinCodePoint(cp);
            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK
                    || type == Character.FORMAT
                    || type == Character.CONTROL
                    || type == Character.PRIVATE_USE
                    || type == Character.SURROGATE) {
                continue;
            }

            if (Character.isLetterOrDigit(cp)) {
                out.appendCodePoint(cp);
                lastWasSpace = false;
            } else if (Character.isWhitespace(cp)) {
                if (!lastWasSpace) out.append(' ');
                lastWasSpace = true;
            } else if (isJoinerPunctuation(cp)) {

            } else {
                if (!lastWasSpace) out.append(' ');
                lastWasSpace = true;
            }
        }
        int len = out.length();
        if (len > 0 && out.charAt(len - 1) == ' ') out.setLength(len - 1);
        return out.toString();
    }

    private static boolean isJoinerPunctuation(int cp) {
        return switch (cp) {
            case '\'', '"', '`', '-', '_', '\u2019', '\u2018', '\u201B', '\u2032', '\u02BC', '\uFF07',
                 '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2015', '\u2212' -> true;
            default -> false;
        };
    }

    private static int foldStyledLatinCodePoint(int cp) {
        return switch (cp) {
            case '\u1D00' -> 'a';
            case '\u0299' -> 'b';
            case '\u1D04' -> 'c';
            case '\u1D05' -> 'd';
            case '\u1D07' -> 'e';
            case '\uA730' -> 'f';
            case '\u0262' -> 'g';
            case '\u029C' -> 'h';
            case '\u026A' -> 'i';
            case '\u1D0A' -> 'j';
            case '\u1D0B' -> 'k';
            case '\u029F' -> 'l';
            case '\u1D0D' -> 'm';
            case '\u0274' -> 'n';
            case '\u1D0F' -> 'o';
            case '\u1D18' -> 'p';
            case '\u01EB' -> 'q';
            case '\u0280' -> 'r';
            case '\uA731' -> 's';
            case '\u1D1B' -> 't';
            case '\u1D1C' -> 'u';
            case '\u1D20' -> 'v';
            case '\u1D21' -> 'w';
            case '\u02E3' -> 'x';
            case '\u028F' -> 'y';
            case '\u1D22' -> 'z';
            case '\u00DF' -> 's';
            case '\u00E6' -> 'a';
            case '\u0153' -> 'o';
            default -> cp;
        };
    }

    private static List<String> tokenizeChat(String normalized) {
        if (normalized == null || normalized.isBlank()) return List.of();
        return List.of(normalized.split(" "));
    }

    private static boolean allPatternTokensPresent(List<String> patternTokens, String normalizedCandidate) {
        for (String token : patternTokens) {
            if (!normalizedCandidate.contains(token)) return false;
        }
        return true;
    }

    private static double computeTokenCoverage(List<String> patternTokens, List<String> candidateTokens) {
        if (patternTokens.isEmpty()) return 1.0;
        if (candidateTokens.isEmpty()) return 0.0;
        double total = 0.0;
        for (String patternToken : patternTokens) {
            double best = 0.0;
            for (String candidateToken : candidateTokens) {
                best = Math.max(best, tokenSimilarity(patternToken, candidateToken));
                if (best >= 1.0) break;
            }
            total += best;
        }
        return total / patternTokens.size();
    }

    private static double tokenSimilarity(String left, String right) {
        if (left.equals(right)) return 1.0;
        if (left.isBlank() || right.isBlank()) return 0.0;
        if (left.contains(right) || right.contains(left)) {
            return (double) Math.min(left.length(), right.length()) / Math.max(left.length(), right.length());
        }
        return similarityRatio(left, right);
    }

    private static double similarityRatio(String left, String right) {
        if (left.equals(right)) return 1.0;
        int maxLen = Math.max(left.length(), right.length());
        if (maxLen == 0) return 1.0;
        int distance = levenshteinDistance(left, right);
        return Math.max(0.0, 1.0 - (distance / (double) maxLen));
    }

    private static int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[right.length()];
    }

    private static String extractChatName(Object value) {
        Object direct = firstNonNull(
            invokeFirstNoArg(value, "name", "getName", "targetName", "getTargetName"),
            getRecordComponentValue(value, 0),
            findRecordComponentByMethodNames(value, 2, "name", "targetName", "type")
        );
        return sanitizeChatText(extractTextValue(direct));
    }

    private static String resolvePlayerName(UUID uuid) {
        if (uuid == null) return "";
        try {
            if (Minecraft.getInstance().getConnection() != null) {
                var entry = Minecraft.getInstance().getConnection().getPlayerInfo(uuid);
                if (entry != null && entry.getProfile() != null) {
                    String profileName = extractTextValue(entry.getProfile());
                    if (profileName != null && !profileName.isBlank()) return profileName;
                }
            }
        } catch (Throwable ignored) {  }
        try {
            if (Minecraft.getInstance().level != null) {
                for (var player : Minecraft.getInstance().level.players()) {
                    if (uuid.equals(player.getUUID()) && player.getGameProfile() != null) {
                        String profileName = extractTextValue(player.getGameProfile());
                        if (profileName != null && !profileName.isBlank()) return profileName;
                    }
                }
            }
        } catch (Throwable ignored) {  }
        return "";
    }

    private static Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private static Object invokeFirstNoArg(Object target, String... names) {
        if (target == null || names == null) return null;
        Class<?> type = target.getClass();
        for (String name : names) {
            try {
                Method method = type.getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Throwable ignored) {  }
        }
        return null;
    }

    private static Object getRecordComponentValue(Object target, int index) {
        if (target == null) return null;
        try {
            RecordComponent[] components = target.getClass().getRecordComponents();
            if (components == null || index < 0 || index >= components.length) return null;
            Method accessor = components[index].getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object findRecordComponentByValueType(Object target, Class<?> type) {
        if (target == null || type == null) return null;
        try {
            RecordComponent[] components = target.getClass().getRecordComponents();
            if (components == null) return null;
            for (RecordComponent component : components) {
                if (type.isAssignableFrom(component.getType())) {
                    Method accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    return accessor.invoke(target);
                }
            }
        } catch (Throwable ignored) {  }
        return null;
    }

    private static Object findRecordComponentByMethodNames(Object target, int fallbackIndex, String... names) {
        Object direct = invokeFirstNoArg(target, names);
        return direct != null ? direct : getRecordComponentValue(target, fallbackIndex);
    }

    private static Object findRecordTextComponent(Object target, Object... excludedValues) {
        if (target == null) return null;
        try {
            RecordComponent[] components = target.getClass().getRecordComponents();
            if (components == null) return null;
            outer:
            for (RecordComponent component : components) {
                Method accessor = component.getAccessor();
                accessor.setAccessible(true);
                Object value = accessor.invoke(target);
                if (value == null) continue;
                for (Object excluded : excludedValues) {
                    if (excluded == value) continue outer;
                }
                if (value instanceof Component || value instanceof CharSequence || value instanceof Optional<?>) {
                    return value;
                }
            }
        } catch (Throwable ignored) {  }
        return null;
    }

    private static Component extractTextComponent(Object value) {
        if (value == null) return null;
        if (value instanceof Optional<?> optional) {
            return optional.isPresent() ? extractTextComponent(optional.get()) : null;
        }
        if (value instanceof Component text) return literalizeTextComponent(text);
        if (value instanceof CharSequence chars) return Component.literal(chars.toString());
        if (value instanceof UUID uuid) return Component.literal(resolvePlayerName(uuid));
        try {
            Method getComponent = value.getClass().getMethod("getText");
            getComponent.setAccessible(true);
            Object result = getComponent.invoke(value);
            Component text = extractTextComponent(result);
            if (text != null) return text;
        } catch (Throwable ignored) {  }
        try {
            Method getString = value.getClass().getMethod("getString");
            getString.setAccessible(true);
            Object result = getString.invoke(value);
            if (result != null) return Component.literal(String.valueOf(result));
        } catch (Throwable ignored) {  }
        return null;
    }

    public static Component literalizeTextComponent(Component text) {
        if (text == null) return null;
        MutableComponent literalized = Component.empty();
        try {
            text.visit((style, string) -> {
                if (string != null && !string.isEmpty()) {
                    literalized.append(Component.literal(string).setStyle(copyVisualStyle(style)));
                }
                return Optional.empty();
            }, Style.EMPTY);
        } catch (Throwable ignored) {  }
        if (!literalized.getString().isEmpty()) {
            return literalized;
        }
        return Component.literal(text.getString());
    }

    private static Style copyVisualStyle(Style style) {
        if (style == null || style.isEmpty()) return Style.EMPTY;
        Style safe = Style.EMPTY;
        if (style.getFont() != null) safe = safe.withFont(style.getFont());
        if (style.getColor() != null) safe = safe.withColor(style.getColor());
        if (style.isBold()) safe = safe.withBold(true);
        if (style.isItalic()) safe = safe.withItalic(true);
        if (style.isUnderlined()) safe = safe.withUnderlined(true);
        if (style.isStrikethrough()) safe = safe.withStrikethrough(true);
        if (style.isObfuscated()) safe = safe.withObfuscated(true);
        return safe;
    }

    public static String serializeTextComponent(Component text) {
        if (text == null) return "";
        try {
            Component safeComponent = literalizeTextComponent(text);
            return safeComponent == null ? "" : GSON.toJson(ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, safeComponent).getOrThrow());
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static Component deserializeTextComponent(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return literalizeTextComponent(ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow().copy());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String extractTextValue(Object value) {
        if (value == null) return null;
        if (value instanceof Optional<?> optional) {
            return optional.isPresent() ? extractTextValue(optional.get()) : null;
        }
        if (value instanceof Component text) return text.getString();
        if (value instanceof CharSequence chars) return chars.toString();
        if (value instanceof UUID uuid) return resolvePlayerName(uuid);
        try {
            Method getString = value.getClass().getMethod("getString");
            getString.setAccessible(true);
            Object result = getString.invoke(value);
            if (result != null) return String.valueOf(result);
        } catch (Throwable ignored) {  }
        try {
            Method getName = value.getClass().getMethod("getName");
            getName.setAccessible(true);
            Object result = getName.invoke(value);
            if (result instanceof CharSequence chars) return chars.toString();
        } catch (Throwable ignored) {  }
        return null;
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

    private static java.util.List<Packet<?>> preGeneratePackets(AutismMacro macro, int startIdx, int count) {
        java.util.List<Packet<?>> result = new java.util.ArrayList<>();
        int maxIdx = count < 0 ? macro.actions.size() : Math.min(startIdx + count, macro.actions.size());
        return preGeneratePackets(macro, startIdx, count, maxIdx);
    }

    private static java.util.List<Packet<?>> preGeneratePackets(AutismMacro macro, int startIdx, int count, int endIdx) {
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
