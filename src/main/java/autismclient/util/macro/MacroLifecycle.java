package autismclient.util.macro;

import autismclient.modules.PackHideState;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismInstaBreakRenderer;
import autismclient.util.AutismMacro;
import autismclient.util.macro.MacroActionResources.SharedClientResource;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

final class MacroLifecycle {
    private MacroLifecycle() {
    }
    private static java.util.Map<String, Integer> flowLabels(MacroExecutor.RunState run, AutismMacro macro) {
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

    private static Integer resolveFlowTarget(MacroExecutor.RunState run, AutismMacro macro, int i, FlowAction flow, Minecraft mc) {
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

    static int performFlowJump(MacroExecutor.RunState run, AutismMacro macro, int currentI, FlowAction flow, Minecraft mc) {
        run.branchElseStart = -1;
        run.branchElseCount = 0;
        Integer dest = resolveFlowTarget(run, macro, currentI, flow, mc);
        if (dest == null) return Integer.MIN_VALUE;
        int size = macro.actions.size();
        int target = Math.max(0, Math.min(size, dest));
        if (target <= currentI) {
            run.totalJumpsThisRun++;
            run.jumpBudget++;
            if (MacroExecutor.FLOW_JUMP_HARD_CAP >= 0 && run.totalJumpsThisRun > MacroExecutor.FLOW_JUMP_HARD_CAP) {
                AutismClientMessaging.sendPrefixed("§cMacro aborted: jump limit exceeded (runaway loop).");
                return Integer.MIN_VALUE;
            }
            if (run.jumpBudget >= MacroExecutor.FLOW_JUMP_BURST_CAP) {
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
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run != null) return run.running.get();
        return MacroExecutor.isRunning.get();
    }

    public static boolean isCurrentActionRunActive() {
        return isCurrentRunActive();
    }

    static CopyOnWriteArrayList<MacroAction> currentPersistentActions() {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        return run != null ? run.persistentActions : MacroExecutor.persistentActions;
    }

    static ConcurrentHashMap<MoveAction, int[]> currentBackgroundMoves() {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        return run != null ? run.backgroundMoves : MacroExecutor.backgroundMoves;
    }

    static void setCurrentStatus(String status) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run != null) run.status = status == null ? "" : status;
        MacroExecutor.currentStatus = status == null ? "" : status;
    }

    static void setCurrentStep(int step, int total) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run != null) {
            run.currentStepIndex = step;
            run.totalSteps = total;
        }
        MacroExecutor.currentStepIndex = step;
        MacroExecutor.totalSteps = total;
    }

    static void markStepCompleted(int stepOneBased) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run != null) {
            run.lastCompletedStep = Math.max(run.lastCompletedStep, stepOneBased);
        }
    }

    static int stepOverrideEndIndex(AutismMacro macro, int headerIndex, int coveredRows) {
        int last = macro == null ? headerIndex : macro.actions.size() - 1;
        long end = (long) headerIndex + Math.max(0, coveredRows);
        return (int) Math.max(headerIndex, Math.min(last, end));
    }

    static void armFpsStepOverride(MacroExecutor.RunState run, int headerIndex, int endIndexInclusive, int fpsLimit) {
        if (run == null) return;
        run.fpsStepHeaderIndex = headerIndex;
        run.fpsStepEndIndex = endIndexInclusive;
        FpsLimitController.applyUntilCleared(run.id, fpsLimit);
    }

    static void armPingStepOverride(MacroExecutor.RunState run, int headerIndex, int endIndexInclusive,
                                            int delayMs, boolean realIncoming, boolean realOutgoing) {
        if (run == null) return;
        run.pingStepHeaderIndex = headerIndex;
        run.pingStepEndIndex = endIndexInclusive;
        PingSpoofController.applyUntilCleared(run.id, delayMs, realIncoming, realOutgoing);
    }

    static void clearFpsStepOverride(MacroExecutor.RunState run) {
        if (run == null || run.fpsStepHeaderIndex < 0) return;
        run.fpsStepHeaderIndex = -1;
        run.fpsStepEndIndex = -1;
        FpsLimitController.clear(run.id);
    }

    static void clearPingStepOverride(MacroExecutor.RunState run) {
        if (run == null || run.pingStepHeaderIndex < 0) return;
        run.pingStepHeaderIndex = -1;
        run.pingStepEndIndex = -1;
        PingSpoofController.clearMacro(run.id);
    }

    static void awaitBackgroundTimedOverrides(MacroExecutor.RunState run) throws InterruptedException {
        if (run == null) return;

        long deadline = Math.max(run.fpsBackgroundDeadlineNanos, run.pingBackgroundDeadlineNanos);
        long remaining;
        while (isCurrentRunActive() && (remaining = deadline - System.nanoTime()) > 0L) {
            Thread.sleep(Math.min(25L, remaining / 1_000_000L + 1L));
        }
    }

    static void updateStepBoundOverrides(MacroExecutor.RunState run, int index) {
        if (run == null) return;
        if (run.fpsStepHeaderIndex >= 0 && (index > run.fpsStepEndIndex || index <= run.fpsStepHeaderIndex)) {
            clearFpsStepOverride(run);
        }
        if (run.pingStepHeaderIndex >= 0 && (index > run.pingStepEndIndex || index <= run.pingStepHeaderIndex)) {
            clearPingStepOverride(run);
        }
    }

    static void stopCurrentRun() {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run != null) {
            run.running.set(false);
        } else {
            MacroExecutor.stop();
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
                    MacroExecutor.SHARED_RESOURCE_LOCKS.get(resource).lockInterruptibly();
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
                ReentrantLock lock = MacroExecutor.SHARED_RESOURCE_LOCKS.get(resource);
                if (lock.isHeldByCurrentThread()) lock.unlock();
            }
        }
    }

    static MacroVariableContext currentVariableContext() {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        return run == null ? null : run.variables;
    }

    static void withRunContext(MacroExecutor.RunState run, Runnable task) {
        MacroExecutor.RunState previous = MacroExecutor.CURRENT_RUN.get();
        if (run != null) MacroExecutor.CURRENT_RUN.set(run);
        try {
            task.run();
        } finally {
            if (previous != null) MacroExecutor.CURRENT_RUN.set(previous);
            else MacroExecutor.CURRENT_RUN.remove();
        }
    }

    static MacroExecutor.InlineNextActions collectInlineNextActions(java.util.List<MacroAction> actions, int currentIndex) {
        if (actions == null || currentIndex < 0 || currentIndex >= actions.size() - 1) return MacroExecutor.InlineNextActions.empty();
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
        return collected.isEmpty() ? MacroExecutor.InlineNextActions.empty() : new MacroExecutor.InlineNextActions(java.util.List.copyOf(collected), lastIndex);
    }

    private static volatile java.util.List<MacroExecutor.RunState> ACTIVE_RUN_LIST = java.util.List.of();

    public static void rebuildActiveRunListLocked() {
        ACTIVE_RUN_LIST = java.util.List.copyOf(MacroExecutor.ACTIVE_RUNS.values());
    }

    static java.util.List<MacroExecutor.RunState> activeRunSnapshot() {
        return ACTIVE_RUN_LIST;
    }

    static void finishCurrentRun() {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run == null) return;
        if (!run.finished.compareAndSet(false, true)) return;
        requestStop(run, false);

        clearFpsStepOverride(run);
        clearPingStepOverride(run);

        synchronized (MacroExecutor.RUN_LOCK) {
            MacroExecutor.ACTIVE_RUNS.remove(run.id);
            MacroExecutor.RUNS_BY_ID.remove(run.id);
            rebuildActiveRunListLocked();
            if (MacroExecutor.ACTIVE_RUNS.isEmpty()) MacroCaptureAutofill.allRunsStoppedAtMs = System.currentTimeMillis();
            MacroExecutor.RUN_STATE_REVISION.incrementAndGet();
            refreshPrimaryRunLocked();
        }
        if (!MacroExecutor.isRunning.get()) {
            AutismInstaBreakRenderer.clear();

            FpsLimitController.clearAll();
            PingSpoofController.clearAllMacros();
        }
        MacroExecutor.CURRENT_RUN.remove();
    }

    private static boolean hasRunningRunsLocked() {
        for (MacroExecutor.RunState run : MacroExecutor.ACTIVE_RUNS.values()) {
            if (run.running.get()) return true;
        }
        return false;
    }

    private static MacroExecutor.RunState firstVisibleRunLocked() {
        for (MacroExecutor.RunState run : MacroExecutor.ACTIVE_RUNS.values()) {
            if (run.running.get() && !run.options.hiddenFromProgressHud()) return run;
        }
        return null;
    }

    public static int activeRunCountLocked() {
        int count = 0;
        for (MacroExecutor.RunState run : MacroExecutor.ACTIVE_RUNS.values()) {
            if (run.running.get()) count++;
        }
        return count;
    }

    static boolean hasMacroCapacity(int activeCount) {
        return Math.max(0, activeCount) < MacroExecutor.MAX_CONCURRENT_MACROS;
    }

    public static void refreshPrimaryRunLocked() {
        MacroExecutor.primaryRun = firstVisibleRunLocked();
        MacroExecutor.visibleRunActive = MacroExecutor.primaryRun != null;
        MacroExecutor.currentMacro = MacroExecutor.primaryRun != null ? MacroExecutor.primaryRun.macro : null;
        MacroExecutor.currentStatus = MacroExecutor.primaryRun != null ? MacroExecutor.primaryRun.status : "";
        MacroExecutor.currentStepIndex = MacroExecutor.primaryRun != null ? MacroExecutor.primaryRun.currentStepIndex : -1;
        MacroExecutor.totalSteps = MacroExecutor.primaryRun != null ? MacroExecutor.primaryRun.totalSteps : 0;
        MacroExecutor.isRunning.set(hasRunningRunsLocked());
    }

    public static boolean hasRunningRuns() {
        synchronized (MacroExecutor.RUN_LOCK) {
            return hasRunningRunsLocked();
        }
    }

    public static boolean isVisibleRunning() {
        return MacroExecutor.visibleRunActive;
    }

    public static boolean isControllingInput() {
        ReentrantLock inputLock = MacroExecutor.SHARED_RESOURCE_LOCKS.get(SharedClientResource.INPUT);
        if (inputLock != null && inputLock.isLocked()) return true;
        if (!MacroExecutor.backgroundMoves.isEmpty()) return true;
        for (MacroAction action : MacroExecutor.persistentActions) {
            if (action instanceof MoveAction || action instanceof JumpAction || action instanceof SneakAction || action instanceof SprintAction) return true;
        }
        synchronized (MacroExecutor.RUN_LOCK) {
            for (MacroExecutor.RunState run : MacroExecutor.ACTIVE_RUNS.values()) {
                if (run == null || !run.running.get()) continue;
                if (!run.backgroundMoves.isEmpty()) return true;
                for (MacroAction action : run.persistentActions) {
                    if (action instanceof MoveAction || action instanceof JumpAction || action instanceof SneakAction || action instanceof SprintAction) return true;
                }
            }
        }
        return false;
    }

    static void broadcastStepProgress(MacroExecutor.RunState run, int step, int total, String macroName) {
        if (run != null && run.options.skipLanProgress()) return;
        try {
            autismclient.util.AutismLANSync sync = autismclient.util.AutismLANSync.getInstance();
            if (sync.isInSession()) {
                sync.broadcastStepProgress(step, total, macroName);
            }
        } catch (Exception ignored) {  }
    }

    public static void requestStop(MacroExecutor.RunState run, boolean interruptThread) {
        if (run == null) return;
        if (run.running.getAndSet(false)) {
            MacroExecutor.RUN_STATE_REVISION.incrementAndGet();
        }
        run.persistentActions.clear();
        for (MacroExecutor.PrearmedWait wait : new ArrayList<>(run.prearmedWaits.values())) {
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
        MacroRender.cancelRotation(run);
        MacroWorldActions.requestOwnedBaritoneStop(run, mc);
        Thread thread = run.macroThread;
        if (interruptThread && thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
    }

    static void releaseBackgroundMoves(ConcurrentHashMap<MoveAction, int[]> moves, Minecraft mc, MacroExecutor.RunState owner) {
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

    static void releaseMoveIfUnused(MacroExecutor.RunState owner, MoveAction move, Minecraft mc) {
        if (move == null || mc == null) return;
        boolean stillOwned = false;
        synchronized (MacroExecutor.RUN_LOCK) {
            for (MacroExecutor.RunState active : MacroExecutor.ACTIVE_RUNS.values()) {
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
            for (MoveAction other : MacroExecutor.backgroundMoves.keySet()) {
                if (other.direction == move.direction) {
                    stillOwned = true;
                    break;
                }
            }
        }
        if (!stillOwned) move.release(mc);
    }

    public static void reconcilePersistentInput(Minecraft mc) {
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
        for (MacroAction action : MacroExecutor.persistentActions) {
            if (type.isInstance(action)) return true;
        }
        synchronized (MacroExecutor.RUN_LOCK) {
            for (MacroExecutor.RunState run : MacroExecutor.ACTIVE_RUNS.values()) {
                if (!run.running.get()) continue;
                for (MacroAction action : run.persistentActions) {
                    if (type.isInstance(action)) return true;
                }
            }
        }
        return false;
    }

    public static String macroKey(String macroName) {
        return macroName == null ? "" : macroName.trim().toLowerCase(Locale.ROOT);
    }

    public static String macroName(AutismMacro macro) {
        return macro != null && macro.name != null ? macro.name : "";
    }

    public static boolean matchesMacro(MacroExecutor.RunState run, AutismMacro macro, String macroName) {
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
        return executeTracked(macro, MacroExecutor.RunOptions.NORMAL);
    }

    public static long executeTracked(AutismMacro macro, MacroExecutor.RunOptions options) {
        if (PackHideState.isHardLocked()) return -1L;
        MacroExecutor.RunOptions runOptions = options == null ? MacroExecutor.RunOptions.NORMAL : options;
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

        MacroExecutor.RunState runState;
        synchronized (MacroExecutor.RUN_LOCK) {
            String name = macro.name == null ? "" : macro.name;
            String nameKey = macroKey(name);
            if (!runOptions.backgroundRun()) {
                for (MacroExecutor.RunState active : MacroExecutor.ACTIVE_RUNS.values()) {
                    if (active.options.backgroundRun()) continue;
                    String activeName = active.macro != null && active.macro.name != null ? active.macro.name : "";
                    if (active.running.get() && macroKey(activeName).equals(nameKey)) {
                        if (!runOptions.silentLifecycle()) AutismClientMessaging.sendPrefixed("\u00a7eMacro already running: " + name);
                        return -1L;
                    }
                }
                if (!hasMacroCapacity(activeRunCountLocked())) {
                    if (!runOptions.silentLifecycle()) AutismClientMessaging.sendPrefixed("\u00a7cOnly " + MacroExecutor.MAX_CONCURRENT_MACROS + " macros can run at the same time.");
                    return -1L;
                }
            } else if (!hasMacroCapacity(activeRunCountLocked())) {
                return -1L;
            }
            runState = new MacroExecutor.RunState(MacroExecutor.NEXT_RUN_ID.getAndIncrement(), macro, executionMacro, runOptions);
            if (MacroExecutor.ACTIVE_RUNS.isEmpty()) MacroCaptureAutofill.expireSuggestionCacheAfterIdle();
            MacroExecutor.ACTIVE_RUNS.put(runState.id, runState);
            MacroExecutor.RUNS_BY_ID.put(runState.id, runState);
            rebuildActiveRunListLocked();
            MacroExecutor.RUN_STATE_REVISION.incrementAndGet();
            refreshPrimaryRunLocked();
        }
        if (!runOptions.silentLifecycle()) AutismClientMessaging.sendPrefixed("\u00a7aStarted macro: " + macro.name);

        broadcastStepProgress(runState, 0, executionMacro.actions.size(), executionMacro.name);

        Thread thread = new Thread(() -> MacroRunLoop.run(runState), "Autism-Macro-" + runState.id);
        thread.setDaemon(true);
        MacroExecutor.macroThread = thread;
        runState.macroThread = thread;
        try {
            thread.start();
        } catch (Throwable startError) {

            requestStop(runState, false);
            synchronized (MacroExecutor.RUN_LOCK) {
                MacroExecutor.ACTIVE_RUNS.remove(runState.id);
                MacroExecutor.RUNS_BY_ID.remove(runState.id);
                rebuildActiveRunListLocked();
                if (MacroExecutor.ACTIVE_RUNS.isEmpty()) MacroCaptureAutofill.allRunsStoppedAtMs = System.currentTimeMillis();
                MacroExecutor.RUN_STATE_REVISION.incrementAndGet();
                refreshPrimaryRunLocked();
            }
            return -1L;
        }
        return runState.id;
    }

    public static boolean isRunActive(long id) {
        if (id < 0L) return false;
        MacroExecutor.RunState run = MacroExecutor.RUNS_BY_ID.get(id);
        return run != null && run.running.get();
    }

    public static long runStateRevision() {
        return MacroExecutor.RUN_STATE_REVISION.get();
    }

    public static long currentRunId() {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        return run == null ? -1L : run.id;
    }

    public static void stopRun(long id) {
        if (id < 0L) return;
        MacroExecutor.RunState run;
        synchronized (MacroExecutor.RUN_LOCK) {
            run = MacroExecutor.ACTIVE_RUNS.get(id);
        }
        if (run == null) return;
        requestStop(run, true);
        synchronized (MacroExecutor.RUN_LOCK) {
            refreshPrimaryRunLocked();
        }
    }
}
