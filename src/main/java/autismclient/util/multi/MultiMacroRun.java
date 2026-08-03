package autismclient.util.multi;

import autismclient.util.AutismMacro;
import autismclient.util.AutismPacketClick;
import autismclient.util.AutismBookPayloadBuilder;
import autismclient.util.macro.AssertAction;
import autismclient.util.macro.BranchAction;
import autismclient.util.macro.CaptureValueAction;
import autismclient.util.macro.CaptureListSelector;
import autismclient.util.macro.BreakAction;
import autismclient.util.macro.ClickAction;
import autismclient.util.macro.ContainerClickSequenceAction;
import autismclient.util.macro.CustomMenuAction;
import autismclient.util.macro.CustomMenuActionSupport;
import autismclient.util.macro.InstaBreakAction;
import autismclient.util.macro.RevisionSyncAction;
import autismclient.util.macro.MacroCapturePattern;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.macro.MacroValue;
import autismclient.util.macro.MacroVariableContext;
import autismclient.util.macro.MacroVariablesAction;
import autismclient.util.macro.PayAction;
import autismclient.util.macro.PayloadAction;
import autismclient.util.macro.RaceAction;
import autismclient.util.macro.WaitEntityTargetAction;
import autismclient.util.macro.WaitForBlockAction;
import autismclient.util.macro.WaitForChatAction;
import autismclient.util.macro.WaitForCooldownAction;
import autismclient.util.macro.WaitForEntityAction;
import autismclient.util.macro.WaitForPacketAction;
import autismclient.util.macro.WaitFreeSlotsAction;
import autismclient.util.macro.WaitGuiTypeAction;
import autismclient.util.macro.WaitPacketMatchAction;
import autismclient.util.macro.WaitGamemodeChangeAction;
import autismclient.util.macro.DelayAction;
import autismclient.util.macro.DropAction;
import autismclient.util.macro.FlowAction;
import autismclient.util.macro.HClipAction;
import autismclient.util.macro.IfAction;
import autismclient.util.macro.InteractEntityAction;
import autismclient.util.macro.InventoryAction;
import autismclient.util.macro.ItemAction;
import autismclient.util.macro.ItemTarget;
import autismclient.util.macro.OpenContainerAction;
import autismclient.util.macro.PickUpAllAction;
import autismclient.util.macro.StoreItemAction;
import autismclient.util.macro.SwapSlotsAction;
import autismclient.util.macro.JumpAction;
import autismclient.util.macro.LabelAction;
import autismclient.util.macro.LookAtBlockAction;
import autismclient.util.macro.MacroAction;
import autismclient.util.macro.MacroActionType;
import autismclient.util.macro.MoveAction;
import autismclient.util.macro.PacketClickAction;
import autismclient.util.macro.PacketBurstAction;
import autismclient.util.macro.PacedTpAction;
import autismclient.util.macro.PlaceAction;
import autismclient.util.macro.RepeatAction;
import autismclient.util.macro.RotateAction;
import autismclient.util.macro.SelectSlotAction;
import autismclient.util.macro.SendChatAction;
import autismclient.util.macro.SignEditAction;
import autismclient.util.macro.WaitForSoundAction;
import autismclient.util.macro.SendCommandPacketAction;
import autismclient.util.macro.SneakAction;
import autismclient.util.macro.SprintAction;
import autismclient.util.macro.WaitDurabilityAction;
import autismclient.util.macro.WaitForGuiAction;
import autismclient.util.macro.WaitForHealthAction;
import autismclient.util.macro.WaitForPositionDeltaAction;
import autismclient.util.macro.WaitForSlotChangeAction;
import autismclient.util.macro.WaitForTeleportAction;
import autismclient.util.macro.WaitForWorldChangeAction;
import autismclient.util.macro.WaitForMacroStepAction;
import autismclient.util.macro.WaitInventoryPredicateAction;
import autismclient.util.macro.WaitMovementAction;
import autismclient.util.macro.WaitPosAction;
import autismclient.util.macro.WaitsForGui;
import autismclient.util.macro.VClipAction;
import autismclient.util.macro.UseItemPhaseAction;
import autismclient.util.macro.NbtBookAction;
import autismclient.util.macro.XCarryAction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Random;

final class MultiMacroRun {
    private static final int STEP_BUDGET = 128;
    private static final int EMIT_BUDGET = 8;
    private static final int MAX_REPEAT_EMITS = 64;
    private static final int LEAF_BURST_CAP = 1000;
    private static final long GUI_WAIT_TIMEOUT_MS = 3000L;
    private static final long WAIT_TIMEOUT_MS = 10000L;
    private static final long WAIT_EVENT_TIMEOUT_MS = 600_000L;

    private static final long ITEM_RESOLVE_GRACE_MS = 2500L;
    private static final long CONTAINER_GRACE_MS = 1500L;
    private static final long ITEM_RESOLVE_POLL_MS = 50L;

    private final AutismMacro macro;
    private final List<MacroAction> actions;
    private final Map<String, Integer> labelIndex = new HashMap<>();
    private final Map<String, String> vars = new HashMap<>();
    private final Map<CaptureValueAction, CaptureListSelector.State> captureSelections = new IdentityHashMap<>();
    private final Map<String, List<String>> suggestionCache = new HashMap<>();

    private static final String DRILL_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_";
    private static final int MIN_SWEEP_CAP = 8;
    private static final int MAX_SWEEP_NAMES = 10_000;
    private final Map<String, AutofillUnion> autofillUnions = new HashMap<>();
    private AutofillUnion pendingAutofillUnion;
    private final Deque<int[]> repeatStack = new ArrayDeque<>();
    private final int maxLoops;

    private int ip;
    private int loopIndex;
    private int branchElseStart = -1;
    private int branchElseCount;
    private long delayUntil;
    private boolean waitingForGui;
    private String guiWaitName = "";
    private long guiWaitBaselineSeq;
    private long guiWaitDeadline;
    private UseItemPhaseAction.Phase pendingUsePhase;
    private boolean pendingUseOffhand;
    private boolean pendingUseRelease;
    private int pendingUseRemaining;
    private long pendingUseHoldMs;
    private CaptureValueAction pendingCapture;
    private int pendingSuggestionId = -1;
    private String pendingSuggestionQuery = "";
    private long pendingCaptureDeadline;
    private boolean pendingScoreboardBaselineReady;
    private String pendingScoreboardKey = "";
    private String pendingScoreboardText = "";
    private PacketBurstAction pendingBurst;
    private int pendingBurstRemaining;
    private long pendingBurstDelayMs;
    private NbtBookAction pendingBook;
    private int pendingBookIndex;
    private int pendingBookTotal;
    private int pendingBookHotbarMask;
    private long pendingBookDelayMs;
    private int pendingBookSwapAttempts;
    private XCarryAction pendingXCarry;
    private PacedTpAction pendingTp;
    private double pendingTpX, pendingTpY, pendingTpZ;
    private double pendingTpStep;
    private double pendingTpProgressDistance;
    private int pendingTpWindowPackets;
    private long pendingTpResumeAt;
    private long pendingTpTeleportSeq;
    private long pendingTpArrivalAt;
    private long pendingTpRecoveryNextAt;
    private long pendingTpRecoveryStableAt;
    private double pendingTpRecoveryAnchor;
    private boolean pendingTpRecovery;
    private String pendingTpDimension = "";
    private CustomMenuAction pendingCustomMenu;
    private long pendingCustomMenuDeadline;
    private MacroAction waitAction;
    private long waitDeadline;
    private int waitBaseCount;
    private boolean waitBaseSlotFilled;
    private double waitBaseX, waitBaseY, waitBaseZ;
    private long waitBaseTeleportSeq;
    private String waitBaseDimension = "";
    private String[] waitSlotBaseline;
    private int waitBaseGameMode;
    private long waitBaseChatSeq;
    private long waitBasePacketSeq;
    private boolean packetWaitArmed;
    private long waitBaseSoundSeq;
    private boolean soundWaitArmed;
    private long waitBaseRevision;
    private List<String> cmdQueue;
    private int cmdQueueIdx;
    private long cmdQueueDelayMs;
    private int leafBurstRemaining;
    private MultiMacroHost activeHost;
    private List<int[]> clickPlan;
    private int clickPlanIdx;
    private long clickResolveDeadline;
    private long clickPlanDelayMs;
    private boolean clickPlanCloseAfter;
    private boolean clickPlanCloseSilent;
    private boolean pendingClickCloseAfter;
    private boolean pendingClickCloseSilent;
    private int finallyStart = -1;
    private int finallyEnd;
    private boolean finallyRegistered;
    private boolean finallyRan;
    private boolean runningFinally;
    private String pendingFinishReason;
    private List<MacroAction> raceConditions;
    private Set<Integer> raceSkipIndices;
    private long raceDeadline;
    private int raceBlockEnd = -1;
    private boolean raceWaiting;
    private boolean done;
    private volatile String status = "idle";

    private final boolean hasCustomMenuAction;

    MultiMacroRun(AutismMacro macro) {
        this.macro = macro;
        this.actions = macro.actions;
        this.maxLoops = macro.loop ? (macro.loopCount < 0 ? Integer.MAX_VALUE : Math.max(1, macro.loopCount)) : 1;
        boolean anyCustomMenu = false;
        for (int i = 0; i < actions.size(); i++) {
            MacroAction a = actions.get(i);
            if (a instanceof LabelAction label) labelIndex.putIfAbsent(label.normalizedName(), i);
            if (a != null && a.getType() == MacroActionType.CUSTOM_MENU) anyCustomMenu = true;
        }
        this.hasCustomMenuAction = anyCustomMenu;
        status = "Running " + macro.name;
    }

    boolean done() {
        return done;
    }

    String status() {
        return status;
    }

    String macroName() {
        return macro.name;
    }

    int stepIndex() {
        return Math.max(0, Math.min(actions.size(), ip));
    }

    int totalSteps() {
        return actions.size();
    }

    int loopNumber() {
        return Math.max(1, loopIndex + 1);
    }

    MultiSession.MacroProgress progress() {
        return new MultiSession.MacroProgress(
            macro.name,
            !done,
            stepIndex(),
            totalSteps(),
            loopNumber(),
            MultiManager.singleLine(status, 64)
        );
    }

    void step(long now, MultiMacroHost host) {
        activeHost = host;
        if (done || actions.isEmpty()) {
            done = true;
            return;
        }
        boolean ready = host.macroReady();
        if (!ready && !host.customMenuPhaseActive()) return;
        if (pendingCustomMenu != null) {
            if (!pollCustomMenu(host, now)) return;
            pendingCustomMenu = null;
            pendingCustomMenuDeadline = 0L;
            if (consumeTerminate()) return;
        }
        ready = host.macroReady();
        if (ready) {
            MultiMacroHost.InventorySyncState inventorySync = host.inventorySyncState();
            if (inventorySync != MultiMacroHost.InventorySyncState.READY) {
                status = inventorySync == MultiMacroHost.InventorySyncState.BLOCKED
                    ? "Waiting for authoritative inventory update"
                    : "Waiting for inventory transaction";
                return;
            }
        }
        if (pendingClickCloseAfter || pendingClickCloseSilent) {
            boolean sendClose = pendingClickCloseAfter;
            pendingClickCloseAfter = false;
            pendingClickCloseSilent = false;
            host.runClient(sendClose ? "close" : "close-silent", "");
        }
        if (delayUntil > 0) {
            if (now < delayUntil) return;
            delayUntil = 0;
        }
        if (pendingTp != null) {
            if (!tickPendingTp(host, now)) return;
            clearPendingTp();
        }
        if (pendingUsePhase != null) {
            if (pendingUseRelease && pendingUsePhase != UseItemPhaseAction.Phase.RELEASE_USE) {
                host.useItemPhase(UseItemPhaseAction.Phase.RELEASE_USE, pendingUseOffhand);
            }
            if (pendingUseRemaining > 0) {
                host.useItemPhase(pendingUsePhase, pendingUseOffhand);
                pendingUseRemaining--;
                delayUntil = now + pendingUseHoldMs;
                return;
            }
            pendingUsePhase = null;
            pendingUseRelease = false;
            pendingUseHoldMs = 0L;
        }
        if (pendingCapture != null) {
            if (!pollPendingCapture(host, now)) return;
            pendingCapture = null;
            pendingSuggestionId = -1;
            pendingSuggestionQuery = "";
            pendingAutofillUnion = null;
            pendingCaptureDeadline = 0L;
            pendingScoreboardBaselineReady = false;
            pendingScoreboardKey = "";
            pendingScoreboardText = "";
        }
        if (pendingBurst != null) {
            boolean sent = host.sendPacketBurst(pendingBurst);
            if (!sent) {
                skip(MacroActionType.PACKET_BURST);
                pendingBurst = null;
                pendingBurstRemaining = 0;
                pendingBurstDelayMs = 0L;
            } else if (--pendingBurstRemaining > 0) {
                delayUntil = now + pendingBurstDelayMs;
                return;
            } else {
                pendingBurst = null;
                pendingBurstDelayMs = 0L;
            }
        }
        if (pendingBook != null) {
            int bookResult = sendBook(pendingBook, pendingBookIndex, pendingBookTotal, host);
            if (bookResult == -2) {
                if (++pendingBookSwapAttempts <= 2) return;
                skip(MacroActionType.NBT_BOOK);
                finishPendingBook(host, false);
                return;
            }
            if (bookResult < 0) {
                skip(MacroActionType.NBT_BOOK);
                finishPendingBook(host, false);
            } else if (++pendingBookIndex < pendingBookTotal) {
                pendingBookSwapAttempts = 0;
                delayUntil = now + pendingBookDelayMs;
                return;
            } else {
                finishPendingBook(host, true);
            }
        }
        if (pendingXCarry != null) {
            int result = host.runXCarry(pendingXCarry, now);
            if (result == 0) return;
            if (result < 0) skip(MacroActionType.XCARRY);
            pendingXCarry = null;
        }
        if (waitingForGui) {

            boolean opened = host.guiOpenSeq() != guiWaitBaselineSeq
                && (guiWaitName.isEmpty() || safe(host.openScreenTitle()).toLowerCase(Locale.ROOT).contains(guiWaitName));
            if (!opened && now < guiWaitDeadline) return;
            waitingForGui = false;
        }
        if (waitAction != null) {
            if (now < waitDeadline && !waitConditionMet(waitAction, host)) return;
            waitAction = null;
            if (packetWaitArmed) {
                host.setPacketCapture(false);
                packetWaitArmed = false;
            }
            if (soundWaitArmed) {
                host.setSoundCapture(false);
                soundWaitArmed = false;
            }
        }

        int budget = STEP_BUDGET;
        int emits = 0;
        while (budget-- > 0) {
            if (done) return;
            MultiMacroHost.InventorySyncState inventorySync = host.inventorySyncState();
            if (inventorySync != MultiMacroHost.InventorySyncState.READY) {
                status = inventorySync == MultiMacroHost.InventorySyncState.BLOCKED
                    ? "Waiting for authoritative inventory update"
                    : "Waiting for inventory transaction";
                return;
            }

            boolean looped = false;
            while (!repeatStack.isEmpty() && ip == repeatStack.peek()[1]) {
                int[] top = repeatStack.peek();
                if (top[2] > 0) {
                    top[2]--;
                    ip = top[0];
                    looped = true;
                    break;
                }
                repeatStack.pop();
            }
            if (looped) continue;
            if (runningFinally && ip >= finallyEnd) {
                finish(pendingFinishReason);
                return;
            }
            if (ip >= actions.size()) {
                if (++loopIndex >= maxLoops) {
                    if (finishOrRunFinally("done")) return;
                    continue;
                }
                resetForLoop();
                continue;
            }

            if (branchElseStart == ip && branchElseCount > 0) {
                ip += branchElseCount;
                branchElseStart = -1;
                branchElseCount = 0;
                continue;
            }

            if (raceSkipIndices != null && raceSkipIndices.contains(ip)) {
                ip++;
                continue;
            }
            if (raceBlockEnd >= 0 && !raceWaiting && ip >= raceBlockEnd) {
                raceBlockEnd = -1;
                raceSkipIndices = null;
                raceConditions = null;
            }

            MacroAction action = actions.get(ip);
            if (action == null || !action.isEnabled()) {

                if (action instanceof RaceAction disabledRace) ip += disabledRace.normalizedBodyCount(actions, ip);
                ip++;
                continue;
            }

            MacroActionType type = action.getType();
            if (type == null) {
                ip++;
                continue;
            }

            if (!ready && !isLoginPrefixType(type)) return;

            switch (type) {
                case IF -> {
                    IfAction ifA = (IfAction) action;
                    if (MultiMacroConditions.evaluate(ifA.condition, host, vars)) {
                        branchElseStart = ip + 1 + Math.max(0, ifA.thenSteps);
                        branchElseCount = Math.max(0, ifA.elseSteps);
                    } else {
                        ip += Math.max(0, ifA.thenSteps);
                    }
                    ip++;
                }
                case BRANCH -> {
                    BranchAction b = (BranchAction) action;
                    if (branchMatches(b, host)) {
                        branchElseStart = ip + 1 + Math.max(0, b.thenSteps);
                        branchElseCount = Math.max(0, b.elseSteps);
                    } else {
                        ip += Math.max(0, b.thenSteps);
                    }
                    ip++;
                }
                case WAIT_GUI, WAIT_GUI_TYPE, WAIT_HEALTH, WAIT_ITEM, WAIT_SLOT_CHANGE, WAIT_INVENTORY_PREDICATE,
                     WAIT_FREE_SLOTS, WAIT_POS, WAIT_DURABILITY, WAIT_PACKET, WAIT_CHAT, WAIT_BLOCK, WAIT_ENTITY,
                     WAIT_ENTITY_TARGET, WAIT_COOLDOWN, WAIT_SOUND, WAIT_WORLD_CHANGE, WAIT_POSITION_DELTA,
                     WAIT_TELEPORT, WAIT_GAMEMODE_CHANGE, WAIT_MOVEMENT, WAIT_PACKET_MATCH, WAIT_MACRO_STEP -> {
                    if (!waitEvaluable(effectiveWait(action), host)) {

                        skip(type);
                        ip++;
                        continue;
                    }
                    captureWaitBaseline(action, host);
                    waitAction = action;
                    waitDeadline = now + waitTimeout(action);
                    ip++;
                    return;
                }
                case FLOW -> {
                    FlowAction flow = (FlowAction) action;
                    boolean take = !flow.conditional || MultiMacroConditions.evaluate(flow.condition, host, vars);
                    if (take) {
                        Integer target = flowTarget(flow);
                        if (target == null) {
                            if (finishOrRunFinally("stopped")) return;
                            continue;
                        }
                        ip = target;
                        clearBookkeepingOutside(target);
                    } else {
                        ip++;
                    }
                }
                case LABEL -> ip++;
                case FINALLY -> {

                    int count = Math.max(0, ((autismclient.util.macro.FinallyAction) action).bodyCount);
                    finallyStart = Math.min(actions.size(), ip + 1);
                    finallyEnd = Math.min(actions.size(), ip + 1 + count);
                    finallyRegistered = finallyEnd > finallyStart;
                    ip = finallyEnd;
                }
                case RACE -> {

                    RaceAction race = (RaceAction) action;
                    if (!raceWaiting) {
                        int bodyCount = race.normalizedBodyCount(actions, ip);
                        raceBlockEnd = Math.min(actions.size(), ip + 1 + bodyCount);
                        raceConditions = new java.util.ArrayList<>();
                        raceSkipIndices = new HashSet<>();
                        for (int idx = ip + 1; idx < raceBlockEnd; idx++) {
                            MacroAction child = actions.get(idx);
                            if (child == null || !child.isEnabled()) continue;
                            if (RaceAction.isConditionAction(child)) {
                                raceConditions.add(child);
                                raceSkipIndices.add(idx);
                                captureWaitBaseline(child, host);
                            }
                        }
                        raceDeadline = now + Math.max(0, race.timeoutMs);
                        raceWaiting = true;
                    }
                    boolean fired = raceConditions.isEmpty();
                    for (int i = 0; !fired && i < raceConditions.size(); i++) {
                        if (waitConditionMet(raceConditions.get(i), host)) fired = true;
                    }
                    boolean timedOut = now >= raceDeadline;
                    if (!fired && !timedOut) {
                        delayUntil = now + ITEM_RESOLVE_POLL_MS;
                        return;
                    }
                    if (packetWaitArmed) { host.setPacketCapture(false); packetWaitArmed = false; }
                    if (soundWaitArmed) { host.setSoundCapture(false); soundWaitArmed = false; }
                    raceWaiting = false;
                    if (fired) {
                        ip = ip + 1;
                    } else {
                        skip(type);
                        ip = raceBlockEnd;
                        raceBlockEnd = -1;
                        raceSkipIndices = null;
                        raceConditions = null;
                    }
                }
                case REPEAT -> {
                    RepeatAction rep = (RepeatAction) action;
                    int bodyStart = ip + 1;
                    int bodyEnd = Math.min(actions.size(), bodyStart + Math.max(0, rep.stepCount));
                    int times = Math.max(0, rep.repeatCount);
                    if (times <= 0) {
                        ip = bodyEnd;
                    } else {
                        if (times > 1 && bodyEnd > bodyStart) repeatStack.push(new int[]{bodyStart, bodyEnd, times - 1});
                        ip++;
                    }
                }
                case DELAY -> {
                    DelayAction d = (DelayAction) action;
                    long ms = d.useTicks ? (long) d.delayTicks * 50L : d.delayMs;
                    delayUntil = now + Math.max(0, ms);
                    ip++;
                    return;
                }
                case TICK_SYNC, SERVER_TICK_SYNC -> {
                    delayUntil = now + 50L;
                    ip++;
                    return;
                }
                case REVISION_SYNC -> {

                    waitBaseRevision = host.containerRevision();
                    waitAction = action;
                    waitDeadline = now + WAIT_EVENT_TIMEOUT_MS;
                    ip++;
                    return;
                }
                case ITEM, STORE_ITEM, SWAP_SLOTS, PICK_UP_ALL, CONTAINER_CLICK_SEQUENCE -> {

                    if (clickPlan == null) {
                        ContainerPlan cp = resolveContainerPlan(action, type, host);
                        if (cp.clicks.isEmpty()) {

                            if (cp.graceMs > 0 && host.containerOpen()) {
                                if (clickResolveDeadline == 0L) clickResolveDeadline = now + cp.graceMs;
                                if (now < clickResolveDeadline) {
                                    delayUntil = now + ITEM_RESOLVE_POLL_MS;
                                    return;
                                }
                            }
                            clickResolveDeadline = 0L;
                            skip(type);
                            ip++;
                            continue;
                        }
                        clickResolveDeadline = 0L;
                        clickPlan = cp.clicks;
                        clickPlanIdx = 0;
                        clickPlanDelayMs = cp.perClickDelayMs;
                        clickPlanCloseAfter = cp.closeAfter;
                        clickPlanCloseSilent = cp.closeSilent;
                    }
                    int[] click = clickPlan.get(clickPlanIdx++);
                    host.clickResolved(click[0], click[1], click[2]);
                    if (clickPlanIdx >= clickPlan.size()) {
                        boolean closeAfter = clickPlanCloseAfter;
                        boolean closeSilent = clickPlanCloseSilent;
                        clickPlan = null;
                        clickPlanIdx = 0;
                        clickPlanDelayMs = 0;
                        clickPlanCloseAfter = false;
                        clickPlanCloseSilent = false;
                        pendingClickCloseAfter = closeAfter;
                        pendingClickCloseSilent = !closeAfter && closeSilent;
                        if (action instanceof WaitsForGui w && w.isWaitForGuiAfter()) {
                            armGuiWaitAfter(w, host, now);
                            ip++;
                            return;
                        }
                        ip++;
                        return;
                    } else if (clickPlanDelayMs > 0) {
                        delayUntil = now + Math.min(5000L, clickPlanDelayMs);
                        return;
                    } else {
                        return;
                    }
                }
                case PAY -> {

                    if (cmdQueue == null) {
                        PayAction p = (PayAction) action;
                        cmdQueue = buildPayCommands(p);
                        cmdQueueIdx = 0;
                        cmdQueueDelayMs = p.delayEnabled ? Math.max(0, p.delayMs) : 0;
                        if (cmdQueue.isEmpty()) { cmdQueue = null; ip++; continue; }
                    }
                    host.chat(cmdQueue.get(cmdQueueIdx++));
                    if (cmdQueueIdx >= cmdQueue.size()) {
                        cmdQueue = null;
                        cmdQueueIdx = 0;
                        cmdQueueDelayMs = 0;
                        ip++;
                        if (++emits >= EMIT_BUDGET) return;
                    } else if (cmdQueueDelayMs > 0) {
                        delayUntil = now + Math.min(60_000L, cmdQueueDelayMs);
                        return;
                    } else if (++emits >= EMIT_BUDGET) {
                        return;
                    }
                }
                default -> {
                    boolean yield;
                    try {
                        yield = executeLeaf(action, type, host, now);
                    } catch (RuntimeException ex) {
                        skip(type);
                        yield = false;
                        leafBurstRemaining = 0;
                    }
                    if (terminateRequested) {

                        terminateRequested = false;
                        if (finishOrRunFinally(terminateReason)) return;
                        continue;
                    }
                    if (yield) {
                        ip++;
                        return;
                    }
                    if (leafBurstRemaining > 0) {
                        if (++emits >= EMIT_BUDGET) return;
                        continue;
                    }
                    ip++;
                    if (++emits >= EMIT_BUDGET) return;
                }
            }
        }

    }

    private boolean executeLeaf(MacroAction action, MacroActionType type, MultiMacroHost host, long now) {
        switch (type) {
            case SEND_CHAT -> {
                autismclient.util.macro.MacroTemplate.Resolution r = resolveTemplate(((SendChatAction) action).message);
                if (r.success()) host.chat(r.value());
            }
            case CUSTOM_MENU -> {
                String matcherError = CustomMenuActionSupport.titleMatcherError((CustomMenuAction) action);
                if (!matcherError.isEmpty()) {
                    requestTerminate(matcherError);
                    break;
                }
                pendingCustomMenu = (CustomMenuAction) action;
                pendingCustomMenuDeadline = now + pendingCustomMenu.boundedTimeout();
                if (!pollCustomMenu(host, now)) return true;
                pendingCustomMenu = null;
                pendingCustomMenuDeadline = 0L;
            }
            case SEND_COMMAND_PACKET -> {
                SendCommandPacketAction c = (SendCommandPacketAction) action;
                autismclient.util.macro.MacroTemplate.Resolution r = resolveTemplate(c.command);
                if (!r.success()) break;
                String cmd = r.value().trim();
                if (c.stripLeadingSlash && cmd.startsWith("/")) cmd = cmd.substring(1);
                if (!cmd.isBlank()) host.chat("/" + cmd);
            }
            case SELECT_SLOT -> host.runClient("change-slot", String.valueOf(Math.max(0, Math.min(8, ((SelectSlotAction) action).slot)) + 1));
            case USE_ITEM -> host.runClient("use", "");
            case CLOSE_GUI -> host.runClient("close", "");
            case INVENTORY -> {
                if (((InventoryAction) action).mode == InventoryAction.InvMode.CLOSE) host.runClient("close", "");

            }
            case DROP -> {
                DropAction d = (DropAction) action;
                if (!d.hasSpecificTargets()) {
                    host.runClient("drop", d.mode == DropAction.DropMode.ALL
                        ? "fullinventory" : Integer.toString(Math.max(1, d.dropCount)));
                } else {
                    boolean any = false;
                    int dropped = 0;
                    List<ItemTarget> targets = d.resolvedTargets();
                    for (int i = 0; i < targets.size() && dropped < MAX_REPEAT_EMITS; i++) {
                        ItemTarget t = targets.get(i);
                        if (t == null || (!t.hasIdentity() && !t.hasSlot())) continue;
                        int count = d.getItemCount(i);
                        if (t.hasIdentity()) {
                            String name = t.hasRegistryId() ? t.registryId : t.display;
                            if (name == null || name.isBlank()) continue;
                            host.runClient("drop", count == 0 ? name : (count + " " + name));
                        } else {
                            host.runClient("click-slot", t.slot + (count == 0 ? " drop-stack" : " drop-item " + count));
                        }
                        any = true;
                        dropped++;
                    }
                    if (!any) skip(type);
                }
            }
            case PACKET_CLICK -> {
                PacketClickAction pc = (PacketClickAction) action;
                if (pc.target == null) {
                    skip(type);
                } else {

                    if (leafBurstRemaining <= 0) leafBurstRemaining = Math.max(1, Math.min(MAX_REPEAT_EMITS, pc.times));
                    host.runClient("click-slot", pc.target.visibleSlot() + " " + modeWord(pc.target.mode()));
                    leafBurstRemaining--;
                }
            }
            case ASSERT -> {
                AssertAction a = (AssertAction) action;
                if (!assertPasses(a, host) && a.failureBehavior == AssertAction.FailureBehavior.STOP_MACRO) {
                    requestTerminate("assert failed: " + a.check);
                }
            }
            case SIGN_EDIT -> {
                SignEditAction s = (SignEditAction) action;
                host.editSign(s, resolve(s.line1), resolve(s.line2), resolve(s.line3), resolve(s.line4));
                if (s.sendCommandAfter && s.commandAfter != null && !s.commandAfter.isBlank()) {
                    String cmd = resolve(s.commandAfter).trim();
                    if (cmd.startsWith("/")) cmd = cmd.substring(1);
                    if (!cmd.isBlank()) host.chat("/" + cmd);
                }
            }
            case CAPTURE_VALUE -> {
                if (beginCapture((CaptureValueAction) action, host, now)) return true;
            }
            case PAYLOAD -> {

                PayloadAction p = (PayloadAction) action;
                String data = p.payloadData == null ? "" : p.payloadData;
                if (data.isBlank()) {
                    skip(type);
                    break;
                }
                autismclient.util.macro.MacroTemplate.Resolution ch = resolveTemplate(p.channel);
                autismclient.util.macro.MacroTemplate.Resolution dr = resolveTemplate(data);
                if (!ch.success() || ch.value().isBlank() || !dr.success()) {
                    skip(type);
                    break;
                }
                host.sendRawPayload(ch.value().trim(), dr.value());
            }
            case MACRO_VARIABLES -> {
                MacroVariablesAction mv = (MacroVariablesAction) action;
                int count = Math.min(mv.names.size(), mv.values.size());
                Map<String, String> resolved = new java.util.LinkedHashMap<>();
                boolean ok = true;
                for (int i = 0; i < count; i++) {
                    autismclient.util.macro.MacroTemplate.Resolution r = resolveTemplate(mv.values.get(i));
                    if (!r.success()) { ok = false; break; }
                    String name = MacroVariableContext.cleanRootName(mv.names.get(i));
                    if (!name.isEmpty()) resolved.put(name, r.value());
                }
                if (ok) vars.putAll(resolved);
            }
            case PACKET_BURST -> {
                PacketBurstAction burst = (PacketBurstAction) action;
                int count = Math.max(1, Math.min(MAX_REPEAT_EMITS, burst.count));
                long delay = Math.max(0L, Math.min(60_000L, (long) burst.delayTicks * 50L));
                if (!host.sendPacketBurst(burst)) {
                    skip(type);
                    break;
                }
                if (count > 1 && delay > 0L) {
                    pendingBurst = burst;
                    pendingBurstRemaining = count - 1;
                    pendingBurstDelayMs = delay;
                    delayUntil = now + delay;
                    return true;
                }
                for (int i = 1; i < count; i++) {
                    if (!host.sendPacketBurst(burst)) {
                        skip(type);
                        break;
                    }
                }
            }
            case NBT_BOOK -> {
                NbtBookAction book = (NbtBookAction) action;
                int total = Math.max(1, Math.min(9, book.bookCount));
                pendingBookHotbarMask = 0;
                int bookResult = sendBook(book, 0, total, host);
                if (bookResult < -1) {
                    pendingBook = book;
                    pendingBookIndex = 0;
                    pendingBookTotal = total;
                    pendingBookDelayMs = Math.max(50L, Math.min(60_000L, (long) book.delayTicks * 50L));
                    pendingBookSwapAttempts = 1;
                    return true;
                }
                if (bookResult < 0) {
                    skip(type);
                    break;
                }
                if (total > 1) {
                    pendingBook = book;
                    pendingBookIndex = 1;
                    pendingBookTotal = total;
                    pendingBookDelayMs = Math.max(50L, Math.min(60_000L, (long) book.delayTicks * 50L));
                    delayUntil = now + pendingBookDelayMs;
                    return true;
                }
                if (book.disconnectAfter) host.disconnectBot("NBT book macro");
            }
            case SAVE_GUI -> {
                autismclient.util.macro.SaveGuiAction save = (autismclient.util.macro.SaveGuiAction) action;
                if (!host.saveGui(save.closeAfter, save.sendPacket)) skip(type);
            }
            case DESYNC -> {
                if (!host.desyncGui()) skip(type);
            }
            case RESTORE_GUI -> {
                if (!host.restoreGui()) skip(type);
                return false;
            }
            case XCARRY -> {
                XCarryAction resolved = resolveXCarry((XCarryAction) action);
                int result = host.runXCarry(resolved, now);
                if (result == 0) {
                    pendingXCarry = resolved;
                    return true;
                }
                if (result < 0) skip(type);
            }
            case DISCONNECT -> host.disconnectBot("Macro");
            case STOP_MACRO -> {
                host.stopSelfMacro();
                requestTerminate("stopped");
            }
            case START_MACRO -> {
                String next = ((autismclient.util.macro.StartMacroAction) action).macroName;
                if (host.startSelfMacro(next)) {
                    requestTerminate("chained");
                } else {
                    host.macroNote("Macro chain failed: macro not found: " + safe(next));
                    requestTerminate("error");
                }
            }
            case USE_ITEM_PHASE -> {
                UseItemPhaseAction phase = (UseItemPhaseAction) action;
                int repeats = Math.max(1, Math.min(LEAF_BURST_CAP, phase.repeat));
                boolean offhand = "OFF_HAND".equalsIgnoreCase(phase.hand);
                if (phase.holdTicks > 0) {

                    host.useItemPhase(phase.phase, offhand);
                    pendingUsePhase = phase.phase;
                    pendingUseOffhand = offhand;
                    pendingUseRelease = phase.releaseAfterHold;
                    pendingUseRemaining = repeats - 1;
                    pendingUseHoldMs = Math.min(60_000L, phase.holdTicks * 50L);
                    delayUntil = now + pendingUseHoldMs;
                    return true;
                }

                if (leafBurstRemaining <= 0) leafBurstRemaining = repeats;
                host.useItemPhase(phase.phase, offhand);
                if (phase.releaseAfterHold && phase.phase != UseItemPhaseAction.Phase.RELEASE_USE) {
                    host.useItemPhase(UseItemPhaseAction.Phase.RELEASE_USE, offhand);
                }
                leafBurstRemaining--;
            }
            case CLICK -> {

                ClickAction ca = (ClickAction) action;
                if (leafBurstRemaining <= 0) leafBurstRemaining = Math.max(1, Math.min(LEAF_BURST_CAP, ca.clickCount));
                String verb = ca.type == ClickAction.ContainerInput.LEFT ? "swing" : "use";
                host.runClient(verb, "");
                leafBurstRemaining--;
            }
            case INSTA_BREAK -> {

                InstaBreakAction ib = (InstaBreakAction) action;
                int n = Math.max(1, Math.min(MAX_REPEAT_EMITS, ib.times));
                int x = ib.blockPos.getX();
                int y = ib.blockPos.getY();
                int z = ib.blockPos.getZ();
                String face = ib.direction == null ? "UP" : ib.direction.name();
                for (int i = 0; i < n; i++) host.breakBlock(x, y, z, face);
                if (ib.interact) host.useOnBlock(x, y, z, face);
            }

            case ROTATE -> {
                RotateAction r = (RotateAction) action;
                host.look(r.yaw, r.pitch);
            }
            case LOOK_AT_BLOCK -> lookAtBlock((LookAtBlockAction) action, host);
            case MOVE -> {
                return doMove((MoveAction) action, host, now);
            }
            case JUMP -> {
                host.jump();
                delayUntil = now + Math.max(50L, (long) ((JumpAction) action).durationTicks * 50L);
                return true;
            }
            case SNEAK -> host.setSneak(((SneakAction) action).sneak);
            case SPRINT -> host.setSprint(((SprintAction) action).sprint);
            case INTERACT_ENTITY -> {
                if (!host.fullMode()) { skipFullOnly(type, host); break; }
                interactNearestEntity((InteractEntityAction) action, host);
            }
            case BREAK -> {

                BreakAction b = (BreakAction) action;
                int n = Math.max(1, Math.min(MAX_REPEAT_EMITS, b.times));
                int x = b.blockPos.getX();
                int y = b.blockPos.getY();
                int z = b.blockPos.getZ();
                String face = b.direction == null ? "UP" : b.direction.name();
                for (int i = 0; i < n; i++) host.breakBlock(x, y, z, face);
                if (b.interact) host.useOnBlock(x, y, z, face);
            }
            case PLACE -> {

                PlaceAction p = (PlaceAction) action;
                host.useOnBlock(p.blockPos.getX(), p.blockPos.getY(), p.blockPos.getZ(), p.direction.name());
            }
            case OPEN_CONTAINER -> {

                OpenContainerAction oc = (OpenContainerAction) action;
                if (oc.targetMode == OpenContainerAction.TargetMode.BLOCK) {
                    host.useOnBlock(oc.blockPos.getX(), oc.blockPos.getY(), oc.blockPos.getZ(), "UP");
                } else if (oc.targetMode == OpenContainerAction.TargetMode.ENTITY) {
                    String t = oc.entityTargets.isEmpty() ? typeOf(oc.entityTarget) : typeOf(oc.entityTargets.get(0));
                    int id = host.nearestEntity(t);
                    if (id < 0) { skipUnsupported(type, host, "no tracked entity of that type to open"); break; }
                    host.interactEntity(id, false);
                } else {
                    skipUnsupported(type, host, "Last-target container needs the client's crosshair; use Block coordinates");
                }
            }
            case VCLIP -> {
                VClipAction clip = (VClipAction) action;
                if (clip.mode != VClipAction.Mode.MANUAL) { skipUnsupported(type, host, "only Manual clip mode works on bots (Top/Bottom scan the client world)"); break; }
                int segments = clipSegments(clip.deltaY, clip.useSegmented, clip.segmentBlocks, clip.maxPackets);
                host.clip(0.0, clip.deltaY, 0.0, segments, clip.forceGrounded);
            }
            case HCLIP -> {
                HClipAction clip = (HClipAction) action;
                if (clip.mode != HClipAction.Mode.MANUAL) { skipUnsupported(type, host, "only Manual clip mode works on bots (Forward/Back scan the client world)"); break; }
                double yaw = Math.toRadians(host.currentYaw());
                double dx = -Math.sin(yaw) * clip.blocks;
                double dz = Math.cos(yaw) * clip.blocks;
                int segments = clipSegments(clip.blocks, clip.useSegmented, clip.segmentBlocks, clip.maxPackets);
                host.clip(dx, 0.0, dz, segments, clip.forceGrounded);
            }
            case TP -> {
                startPendingTp((PacedTpAction) action, host);
                return true;
            }

            case MULTI -> skipUnsupported(type, host, "orchestrates other bots from the main client");

            default -> skipUnsupported(type, host, "not supported on Multi bots (needs client world/state)");
        }

        if (action instanceof WaitsForGui w && w.isWaitForGuiAfter()) {
            armGuiWaitAfter(w, host, now);
            return true;
        }
        return false;
    }

    private static int clipSegments(double distance, boolean segmented, int segmentBlocks, int maxPackets) {
        if (!segmented) return 1;
        int packets = Math.max(1, (int) Math.ceil(Math.abs(distance) / Math.max(1, segmentBlocks)));
        return packets > Math.max(1, maxPackets) ? 1 : Math.min(64, packets);
    }

    private void startPendingTp(PacedTpAction action, MultiMacroHost host) {
        pendingTp = action;
        pendingTpX = action.relativeX ? host.posX() + action.x : action.x;
        pendingTpY = action.relativeY ? host.posY() + action.y : action.y;
        pendingTpZ = action.relativeZ ? host.posZ() + action.z : action.z;
        pendingTpStep = PacketTeleportController.DEFAULT_STEP;
        pendingTpProgressDistance = tpDistance(host);
        pendingTpWindowPackets = 0;
        pendingTpResumeAt = 0L;
        pendingTpTeleportSeq = host.teleportSeq();
        pendingTpArrivalAt = 0L;
        pendingTpRecoveryNextAt = 0L;
        pendingTpRecoveryStableAt = 0L;
        pendingTpRecoveryAnchor = pendingTpProgressDistance;
        pendingTpRecovery = false;
        pendingTpDimension = safe(host.dimension());
        status = String.format(Locale.ROOT, "TP %.1f %.1f %.1f", pendingTpX, pendingTpY, pendingTpZ);
    }

    private boolean tickPendingTp(MultiMacroHost host, long now) {
        if (!safe(host.dimension()).equals(pendingTpDimension)) {
            host.macroNote("TP stopped: dimension changed");
            return true;
        }
        long teleportSeq = host.teleportSeq();
        if (teleportSeq != pendingTpTeleportSeq) {
            pendingTpTeleportSeq = teleportSeq;
            double distance = tpDistance(host);
            if (pendingTpProgressDistance - distance >= 0.25D) {
                pendingTpProgressDistance = distance;
                pendingTpStep = Math.min(PacketTeleportController.DEFAULT_STEP, pendingTpStep * 1.25D);
                if (pendingTpRecovery) {
                    pendingTpRecoveryAnchor = distance;
                    pendingTpRecoveryNextAt = now + 200L;
                    pendingTpRecoveryStableAt = now + 1_200L;
                }
            } else {
                pendingTpStep = Math.max(0.0625D, pendingTpStep * 0.5D);
                pendingTpRecovery = true;
                pendingTpRecoveryAnchor = distance;
                pendingTpRecoveryNextAt = now + 200L;
                pendingTpRecoveryStableAt = now + 1_200L;
            }
            pendingTpWindowPackets = 0;
            pendingTpResumeAt = pendingTpRecovery ? now + 200L : now;
            pendingTpArrivalAt = 0L;
        }

        double dx = pendingTpX - host.posX();
        double dy = pendingTpY - host.posY();
        double dz = pendingTpZ - host.posZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= 0.05D) {
            if (pendingTpArrivalAt == 0L) pendingTpArrivalAt = now;
            long settle = 500L;
            return now - pendingTpArrivalAt >= settle;
        }
        pendingTpArrivalAt = 0L;
        if (pendingTpRecovery) {
            if (now >= pendingTpRecoveryStableAt
                && pendingTpRecoveryAnchor - distance >= 0.5D) {
                pendingTpRecovery = false;
                pendingTpStep = Math.min(PacketTeleportController.DEFAULT_STEP,
                    Math.max(1.0D, pendingTpStep * 2.0D));
                pendingTpWindowPackets = 0;
                pendingTpResumeAt = now;
            } else if (now < pendingTpRecoveryNextAt) {
                return false;
            }
        }
        if (now < pendingTpResumeAt) return false;
        int window = Math.max(1, Math.min(100, pendingTp.maxPackets));
        int remaining = pendingTpRecovery ? 1 : Math.max(1, window - pendingTpWindowPackets);
        double amount = pendingTpRecovery ? Math.min(pendingTpStep, distance) : distance;
        int packets = Math.max(1, Math.min(remaining, (int) Math.ceil(amount / pendingTpStep)));
        host.clip(dx / distance * amount, dy / distance * amount, dz / distance * amount, packets, true);
        if (pendingTpRecovery) pendingTpRecoveryNextAt = now + 200L;
        pendingTpWindowPackets += packets;
        if (pendingTpWindowPackets >= window) {
            pendingTpWindowPackets = 0;
            pendingTpResumeAt = now + Math.max(50, Math.min(10_000, pendingTp.pauseMs));
        }
        return false;
    }

    private double tpDistance(MultiMacroHost host) {
        double dx = pendingTpX - host.posX();
        double dy = pendingTpY - host.posY();
        double dz = pendingTpZ - host.posZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void clearPendingTp() {
        pendingTp = null;
        pendingTpWindowPackets = 0;
        pendingTpResumeAt = 0L;
        pendingTpArrivalAt = 0L;
        pendingTpRecoveryNextAt = 0L;
        pendingTpRecoveryStableAt = 0L;
        pendingTpRecoveryAnchor = 0.0D;
        pendingTpRecovery = false;
        pendingTpDimension = "";
    }

    private void armGuiWaitAfter(WaitsForGui w, MultiMacroHost host, long now) {
        waitingForGui = true;
        String name = w.getWaitGuiName();
        guiWaitName = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        guiWaitBaselineSeq = host.guiOpenSeq();
        guiWaitDeadline = now + GUI_WAIT_TIMEOUT_MS;
    }

    private record ContainerPlan(List<int[]> clicks, long graceMs, long perClickDelayMs,
                                 boolean closeAfter, boolean closeSilent) {}

    private ContainerPlan resolveContainerPlan(MacroAction action, MacroActionType type, MultiMacroHost host) {
        switch (type) {
            case ITEM -> {
                ItemAction ia = (ItemAction) action;
                long grace = ia.waitForItem ? WAIT_TIMEOUT_MS : ITEM_RESOLVE_GRACE_MS;
                long delay = ia.useClickDelay && ia.clickDelayMs > 0 ? ia.clickDelayMs : 0L;
                return new ContainerPlan(host.resolveItemClicks(ia), grace, delay, false, false);
            }
            case STORE_ITEM -> {
                StoreItemAction sa = (StoreItemAction) action;

                boolean closePacket = sa.closeAfter && !sa.persistent && sa.closeSendPkt;
                boolean closeLocal = sa.closeAfter && !sa.persistent && !sa.closeSendPkt;
                long delay = sa.delayTicks > 0 ? (long) sa.delayTicks * 50L : 0L;
                return new ContainerPlan(host.resolveStoreClicks(sa), CONTAINER_GRACE_MS, delay, closePacket, closeLocal);
            }
            case SWAP_SLOTS -> {
                return new ContainerPlan(host.resolveSwapClicks((SwapSlotsAction) action), CONTAINER_GRACE_MS, 0L, false, false);
            }
            case PICK_UP_ALL -> {

                return new ContainerPlan(host.resolvePickupAllClicks((PickUpAllAction) action), 0L, 0L, false, false);
            }
            case CONTAINER_CLICK_SEQUENCE -> {
                ContainerClickSequenceAction ca = (ContainerClickSequenceAction) action;
                long delay = ca.delayTicks > 0 ? (long) ca.delayTicks * 50L : 0L;
                return new ContainerPlan(host.resolveSequenceClicks(ca), CONTAINER_GRACE_MS, delay, false, false);
            }
            default -> {
                return new ContainerPlan(java.util.List.of(), 0L, 0L, false, false);
            }
        }
    }

    private Integer flowTarget(FlowAction flow) {
        return switch (flow.target) {
            case FORWARD -> Math.min(actions.size(), ip + 1 + Math.max(0, flow.amount));
            case BACK -> Math.max(0, ip - Math.max(0, flow.amount));
            case STEP -> Math.max(0, Math.min(actions.size(), flow.amount));
            case TOP -> 0;
            case END -> actions.size();
            case STOP -> null;
            case LABEL -> {
                Integer idx = labelIndex.get(LabelAction.normalize(flow.labelName));
                if (idx != null) yield idx;
                yield flow.onMissingLabel == FlowAction.MissingPolicy.STOP ? null : ip + 1;
            }
        };
    }

    private boolean doMove(MoveAction a, MultiMacroHost host, long now) {
        double yaw = Math.toRadians(host.currentYaw());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double dirX;
        double dirZ;
        switch (a.direction) {
            case BACKWARD -> { dirX = -fx; dirZ = -fz; }
            case LEFT -> { dirX = fz; dirZ = -fx; }
            case RIGHT -> { dirX = -fz; dirZ = fx; }
            default -> { dirX = fx; dirZ = fz; }
        }

        double speed = host.sprinting() ? 0.26 : 0.20;
        long durationMs = Math.max(50L, (long) a.durationTicks * 50L);
        host.move(dirX * speed, dirZ * speed, durationMs);
        if (a.nonBlocking) return false;
        delayUntil = now + durationMs;
        return true;
    }

    private void lookAtBlock(LookAtBlockAction a, MultiMacroHost host) {
        double tx;
        double ty;
        double tz;
        if (a.targetMode == LookAtBlockAction.TargetMode.ENTITY) {
            int id = host.nearestEntity(typeOf(a.entityIds.isEmpty() ? "" : a.entityIds.get(0)));
            double[] p = host.entityPos(id);
            if (p == null) {
                skip(MacroActionType.LOOK_AT_BLOCK);
                return;
            }
            tx = p[0];
            ty = p[1] + 1.0;
            tz = p[2];
        } else if (a.targetMode == LookAtBlockAction.TargetMode.SPECIFIC) {
            tx = a.blockX + 0.5;
            ty = a.blockY + 0.5;
            tz = a.blockZ + 0.5;
        } else {
            skip(MacroActionType.LOOK_AT_BLOCK);
            return;
        }
        float[] angles = anglesTo(host, tx, ty, tz);
        host.look(angles[0], angles[1]);
    }

    private void interactNearestEntity(InteractEntityAction a, MultiMacroHost host) {
        if (a.targetMode != InteractEntityAction.TargetMode.ENTITY) {
            skip(MacroActionType.INTERACT_ENTITY);
            return;
        }
        String type = a.entityTargets.isEmpty() ? "" : typeOf(a.entityTargets.get(0));
        int id = host.nearestEntity(type);
        if (id < 0) {
            skip(MacroActionType.INTERACT_ENTITY);
            return;
        }
        host.interactEntity(id, false);
    }

    private static float[] anglesTo(MultiMacroHost host, double tx, double ty, double tz) {
        double dx = tx - host.posX();
        double dy = ty - (host.posY() + 1.62);
        double dz = tz - host.posZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        return new float[]{yaw, pitch};
    }

    private boolean branchMatches(BranchAction b, MultiMacroHost host) {
        String v = b.value == null ? "" : b.value;
        return switch (b.conditionKind) {
            case ALWAYS -> true;
            case GUI_TYPE -> v.isBlank() || v.equalsIgnoreCase("ANY")
                ? host.containerOpen()
                : host.openScreenTitle().toLowerCase(Locale.ROOT).contains(v.toLowerCase(Locale.ROOT));
            case INVENTORY_ITEM -> host.countItem(v) > 0;
            case HELD_ITEM -> host.heldItemName().toLowerCase(Locale.ROOT).replace('_', ' ')
                .contains(typeOf(v).toLowerCase(Locale.ROOT).replace('_', ' '));
            case ENTITY_TARGET -> host.fullMode() && host.nearestEntity(typeOf(v)) >= 0;
        };
    }

    private static boolean guiOpenForWait(String guiType, MultiMacroHost host) {
        boolean menuOpen = host.customMenu() != null;
        if (autismclient.util.macro.MacroGuiMatcher.isCustomMenuType(guiType)) return menuOpen;
        boolean any = guiType == null || guiType.isBlank() || "ANY".equalsIgnoreCase(guiType);
        return host.containerOpen() || (any && menuOpen);
    }

    private boolean guiTitleMatches(String wanted, MultiMacroHost host) {
        String resolved = resolve(wanted == null ? "" : wanted);
        if (resolved == null || resolved.isBlank()) return true;
        return host.openScreenTitle().toLowerCase(Locale.ROOT).contains(resolved.toLowerCase(Locale.ROOT));
    }

    private boolean waitConditionMet(MacroAction action, MultiMacroHost host) {
        MacroAction eff = effectiveWait(action);
        if (eff instanceof WaitForGuiAction w) {
            boolean open = guiOpenForWait(w.guiType, host);
            if (w.waitMode == WaitForGuiAction.WaitMode.CLOSE) return !open;
            return open && guiTitleMatches(w.guiTitle, host);
        }
        if (eff instanceof autismclient.util.macro.WaitGuiTypeAction w) {
            boolean open = guiOpenForWait(w.guiType, host);
            if (w.waitMode == autismclient.util.macro.WaitGuiTypeAction.WaitMode.CLOSE) return !open;
            return open && guiTitleMatches(w.title, host);
        }
        if (eff instanceof WaitForHealthAction w) {
            return w.below ? host.health() < w.healthThreshold : host.health() > w.healthThreshold;
        }
        if (eff instanceof WaitInventoryPredicateAction w) return invPredicateMet(w, host);
        if (eff instanceof WaitPosAction w) return posReached(w, host);
        if (eff instanceof WaitDurabilityAction w) return durabilityMet(w, host);
        if (eff instanceof WaitForSlotChangeAction w) return host.slotChangeMet(w, waitSlotBaseline);
        if (eff instanceof WaitForPositionDeltaAction w) return positionDeltaMet(w, host);
        if (eff instanceof WaitForTeleportAction w) return teleportMet(w, host);
        if (eff instanceof WaitForWorldChangeAction w) return worldChangeMet(w, host);
        if (eff instanceof WaitForChatAction w) return chatMet(w, host);
        if (eff instanceof WaitGamemodeChangeAction w) return gamemodeMet(w, host);
        if (eff instanceof WaitForEntityAction w) return entityWaitMet(w, host);
        if (eff instanceof WaitEntityTargetAction w) return entityTargetWaitMet(w, host);
        if (eff instanceof WaitForPacketAction w) return host.packetSeen(waitBasePacketSeq, w.effectiveList());
        if (eff instanceof WaitForSoundAction w) return host.soundMatched(waitBaseSoundSeq, w.soundIds, w.checkDistance, w.maxDistance);
        if (eff instanceof WaitPacketMatchAction w) return host.packetMatched(waitBasePacketSeq, w);
        if (eff instanceof WaitForMacroStepAction w) return host.macroStepMet(w);
        if (eff instanceof RevisionSyncAction) return host.containerRevision() != waitBaseRevision;
        if (eff instanceof WaitForBlockAction w) {
            if (w.checkMode != WaitForBlockAction.CheckMode.AT_POSITION) return false;
            return host.blockAt(w.blockPos.getX(), w.blockPos.getY(), w.blockPos.getZ(),
                w.blockIds, w.anyBlock, w.waitBehavior == WaitForBlockAction.WaitBehavior.DESTROYED);
        }
        if (eff instanceof WaitForCooldownAction w) return !host.itemOnCooldown(w.itemTarget, w.checkMainInteractionHand);
        if (eff instanceof autismclient.util.macro.WaitFreeSlotsAction w) {

            int free = host.freeSlots();
            int actual = w.countMode == autismclient.util.macro.WaitFreeSlotsAction.CountMode.FILLED_SLOTS ? 36 - free : free;
            int target = Math.max(0, Math.min(36, w.slots));
            return switch (w.comparison == null ? autismclient.util.macro.WaitFreeSlotsAction.Comparison.AT_MOST : w.comparison) {
                case BELOW -> actual < target;
                case AT_MOST -> actual <= target;
                case EXACT -> actual == target;
                case AT_LEAST -> actual >= target;
                case ABOVE -> actual > target;
            };
        }
        return false;
    }

    private static MacroAction effectiveWait(MacroAction action) {
        return action instanceof WaitMovementAction wm ? wm.resolveSubAction() : action;
    }

    private void captureWaitBaseline(MacroAction action, MultiMacroHost host) {
        MacroAction eff = effectiveWait(action);
        if (eff instanceof WaitInventoryPredicateAction w) {
            waitBaseCount = host.countItemTarget(ItemTarget.fromLegacyEntry(w.itemName));
            waitBaseSlotFilled = host.slotFilled(w.slot);
        } else if (eff instanceof WaitForSlotChangeAction w) {
            waitSlotBaseline = host.slotChangeBaseline(w);
        } else if (eff instanceof WaitForPositionDeltaAction) {
            waitBaseX = host.posX();
            waitBaseY = host.posY();
            waitBaseZ = host.posZ();
        } else if (eff instanceof WaitForTeleportAction) {
            waitBaseX = host.posX();
            waitBaseY = host.posY();
            waitBaseZ = host.posZ();
            waitBaseTeleportSeq = host.teleportSeq();
        } else if (eff instanceof WaitForWorldChangeAction) {
            waitBaseDimension = host.dimension();
        } else if (eff instanceof WaitGamemodeChangeAction) {
            waitBaseGameMode = host.gameMode();
        } else if (eff instanceof WaitForChatAction) {
            waitBaseChatSeq = host.chatSeq();
        } else if (eff instanceof WaitForPacketAction) {
            host.setPacketCapture(true);
            waitBasePacketSeq = host.packetSeq();
            packetWaitArmed = true;
        } else if (eff instanceof WaitForSoundAction) {
            host.setSoundCapture(true);
            waitBaseSoundSeq = host.soundSeq();
            soundWaitArmed = true;
        } else if (eff instanceof WaitPacketMatchAction) {
            host.setPacketCapture(true);
            waitBasePacketSeq = host.packetSeq();
            packetWaitArmed = true;
        }
    }

    private boolean chatMet(WaitForChatAction w, MultiMacroHost host) {
        for (String line : host.chatSince(waitBaseChatSeq)) {
            if (chatMatches(w, line)) return true;
        }
        return false;
    }

    private static boolean chatMatches(WaitForChatAction w, String text) {
        if (text == null) return false;
        String pattern = (w.patternJson == null || w.patternJson.isBlank())
            ? MacroExecutor.normalizeManualText(w.pattern) : w.pattern;
        if (pattern == null || pattern.isBlank()) return true;
        MacroCapturePattern.Mode mode = w.effectiveMatchMode();
        if (mode == MacroCapturePattern.Mode.MATCH) {
            return MacroExecutor.fuzzyChatMatch(pattern, text, WaitForChatAction.clampFuzzyPercent(w.fuzzyPercent));
        }
        return MacroCapturePattern.match(mode, pattern, text).isPresent();
    }

    private boolean gamemodeMet(WaitGamemodeChangeAction w, MultiMacroHost host) {
        int cur = host.gameMode();
        if (cur < 0) return false;
        if (w.match == WaitGamemodeChangeAction.Match.TO_MODE) return cur == w.targetGameType().getId();
        return cur != waitBaseGameMode;
    }

    private boolean entityWaitMet(WaitForEntityAction w, MultiMacroHost host) {
        if (!host.fullMode()) return false;
        if (w.checkMode == WaitForEntityAction.CheckMode.LOOKING_AT
            || w.checkMode == WaitForEntityAction.CheckMode.MOUNTED_IN) return false;
        double radius = w.checkMode == WaitForEntityAction.CheckMode.WITHIN_REACH ? 4.5 : w.radius;
        return host.entityWithin(w.entityIds, w.containerEntitiesOnly, w.centerOnPlayer, w.x, w.y, w.z, radius);
    }

    private boolean entityTargetWaitMet(WaitEntityTargetAction w, MultiMacroHost host) {
        if (!host.fullMode()) return false;
        if (w.condition == WaitEntityTargetAction.EntityCondition.LOOKING_AT
            || w.condition == WaitEntityTargetAction.EntityCondition.MOUNTED_IN) return false;
        java.util.List<String> types = w.entityId == null || w.entityId.isBlank()
            ? java.util.List.of() : java.util.List.of(w.entityId);
        return host.entityWithin(types, w.containerEntitiesOnly, true, 0, 0, 0, w.range);
    }

    private boolean assertPasses(AssertAction a, MultiMacroHost host) {
        String item = resolve(a.itemName);
        return switch (a.check) {
            case CONNECTION -> host.macroReady();
            case GUI_TYPE -> {
                String g = resolve(a.guiType);
                yield g == null || g.isBlank() || g.equalsIgnoreCase("ANY")
                    ? host.containerOpen()
                    : host.openScreenTitle().toLowerCase(Locale.ROOT).contains(g.toLowerCase(Locale.ROOT));
            }
            case HELD_ITEM -> host.heldItemName().toLowerCase(Locale.ROOT).replace('_', ' ')
                .contains(stripNamespace(item).toLowerCase(Locale.ROOT).replace('_', ' '));
            case INVENTORY_ITEM -> host.countItemTarget(ItemTarget.fromLegacyEntry(item)) > 0;
            case HAS_BUNDLE -> host.countItem("bundle") > 0;
            case HAS_WRITABLE_BOOK -> host.countItem("writable_book") > 0;

            default -> true;
        };
    }

    private static String stripNamespace(String s) {
        if (s == null) return "";
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }

    private List<String> buildPayCommands(PayAction p) {
        List<String> out = new java.util.ArrayList<>();
        if (p.players == null || p.players.isEmpty()) return out;
        List<String> targets = new java.util.ArrayList<>();
        for (String player : p.players) {
            if (player == null) continue;
            String t = resolve(player).trim();
            if (!t.isEmpty() && !targets.contains(t)) targets.add(t);
        }
        if (targets.isEmpty()) return out;
        long amount = Math.max(0L, PayAction.parseAmount(resolve(p.amountInput)));
        long[] divided = p.divideEnabled ? PayAction.distribute(amount, targets.size()) : null;
        String template = p.commandTemplate == null || p.commandTemplate.isBlank() ? "/pay <player> <amount>" : p.commandTemplate;
        for (int i = 0; i < targets.size(); i++) {
            long per = divided != null ? divided[i] : amount;
            if (per <= 0L) continue;
            String amountValue = String.valueOf(per);
            String cmd = template.replace("<player>", targets.get(i)).replace("{player}", targets.get(i))
                .replace("<amount>", amountValue).replace("{amount}", amountValue).trim();
            cmd = resolve(cmd).trim();
            if (!cmd.isEmpty()) out.add(cmd);
        }
        return out;
    }

    private boolean positionDeltaMet(WaitForPositionDeltaAction w, MultiMacroHost host) {
        if (!host.hasPosition()) return false;
        double dx = host.posX() - waitBaseX;
        double dy = w.horizontalOnly ? 0.0 : host.posY() - waitBaseY;
        double dz = host.posZ() - waitBaseZ;
        double d2 = dx * dx + dy * dy + dz * dz;
        double dist = Math.max(0.0, w.distance);
        return dist <= 0.0 ? d2 > 0.0001 : d2 >= dist * dist;
    }

    private boolean teleportMet(WaitForTeleportAction w, MultiMacroHost host) {
        if (host.teleportSeq() == waitBaseTeleportSeq) return false;
        if (w.minDistance <= 0.0) return true;
        if (!host.hasPosition()) return false;
        double dx = host.posX() - waitBaseX;
        double dy = w.horizontalOnly ? 0.0 : host.posY() - waitBaseY;
        double dz = host.posZ() - waitBaseZ;
        return dx * dx + dy * dy + dz * dz >= w.minDistance * w.minDistance;
    }

    private boolean worldChangeMet(WaitForWorldChangeAction w, MultiMacroHost host) {
        String cur = normalizeDimension(host.dimension());
        if (cur.equals(normalizeDimension(waitBaseDimension))) return false;
        String target = w.targetDimension;
        return target == null || target.isBlank() || cur.equals(normalizeDimension(target));
    }

    private static String normalizeDimension(String raw) {
        if (raw == null) return "";
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) return "";
        if (v.equals("nether") || v.equals("the_nether")) return "minecraft:the_nether";
        if (v.equals("overworld") || v.equals("world")) return "minecraft:overworld";
        if (v.equals("end") || v.equals("the_end")) return "minecraft:the_end";
        return v.contains(":") ? v : "minecraft:" + v;
    }

    private boolean invPredicateMet(WaitInventoryPredicateAction w, MultiMacroHost host) {
        ItemTarget t = ItemTarget.fromLegacyEntry(w.itemName);
        return switch (w.condition) {
            case ITEM_EXISTS -> host.countItemTarget(t) > 0;
            case COUNT_AT_LEAST -> host.countItemTarget(t) >= Math.max(1, w.count);
            case COUNT_CHANGED -> host.countItemTarget(t) != waitBaseCount;
            case COUNT_INCREASED -> host.countItemTarget(t) > waitBaseCount;
            case COUNT_DECREASED -> host.countItemTarget(t) < waitBaseCount;
            case SLOT_EMPTY -> !host.slotFilled(w.slot);
            case SLOT_FILLED -> host.slotFilled(w.slot);
            case SLOT_CHANGED -> host.slotFilled(w.slot) != waitBaseSlotFilled;
            case INVENTORY_FULL -> host.freeSlots() <= 0;
            case INVENTORY_EMPTY -> host.countItem("") <= 0;
            case CURSOR_MATCHES -> host.cursorMatches(t);
            case CURSOR_EMPTY -> host.cursorEmpty();
            case CURSOR_FILLED -> !host.cursorEmpty();
            case SELECTED_SLOT -> host.selectedHotbar() == Math.max(0, Math.min(8, w.slot));
        };
    }

    private boolean posReached(WaitPosAction w, MultiMacroHost host) {
        if (!host.hasPosition()) return false;
        double dx = host.posX() - w.x;
        double dy = host.posY() - w.y;
        double dz = host.posZ() - w.z;
        double lee = Math.max(0.0, w.leeway);
        if (dx * dx + dy * dy + dz * dz > lee * lee) return false;
        if (!w.checkRotation) return true;
        float dyaw = Math.abs(wrapDegrees(host.currentYaw() - w.yaw));
        float dpitch = Math.abs(host.currentPitch() - w.pitch);
        return dyaw <= w.rotLeeway && dpitch <= w.rotLeeway;
    }

    static boolean durabilityMet(WaitDurabilityAction w, MultiMacroHost host) {
        return switch (w.targetMode) {
            case HELD -> durabilityStackMet(w, host.heldDurability()) || durabilityStackMet(w, host.durabilityAtInv(40));
            case SLOT -> durabilityStackMet(w, host.durabilityAtInv(w.slot));
            case ITEM -> {
                ItemTarget t = ItemTarget.fromLegacyEntry(w.itemName);
                if (!t.hasIdentity() && !t.hasSlot()) {
                    yield durabilityStackMet(w, host.heldDurability()) || durabilityStackMet(w, host.durabilityAtInv(40));
                }
                yield durabilityStackMet(w, host.itemDurability(t));
            }
        };
    }

    private static boolean durabilityStackMet(WaitDurabilityAction w, int[] dur) {
        if (dur == null || dur.length < 2 || dur[1] <= 0) return false;
        int damage = Math.max(0, dur[0]);
        int max = dur[1];
        int remaining = Math.max(0, max - damage);
        int metric = switch (w.measurement) {
            case REMAINING -> remaining;
            case DAMAGE_USED -> damage;
            case PERCENT_REMAINING -> Math.round(remaining * 100.0f / max);
        };
        int cap = w.measurement == WaitDurabilityAction.Measurement.PERCENT_REMAINING ? 100 : max;
        int target = Math.max(0, Math.min(cap, w.value));
        return switch (w.comparison) {
            case BELOW -> metric < target;
            case AT_MOST -> metric <= target;
            case EXACT -> metric == target;
            case AT_LEAST -> metric >= target;
            case ABOVE -> metric > target;
        };
    }

    private static float wrapDegrees(float deg) {
        float d = deg % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    private static boolean waitEvaluable(MacroAction eff, MultiMacroHost host) {
        if (eff instanceof WaitForEntityAction w) {
            return host.fullMode()
                && w.checkMode != WaitForEntityAction.CheckMode.LOOKING_AT
                && w.checkMode != WaitForEntityAction.CheckMode.MOUNTED_IN;
        }
        if (eff instanceof WaitEntityTargetAction w) {
            return host.fullMode()
                && w.condition != WaitEntityTargetAction.EntityCondition.LOOKING_AT
                && w.condition != WaitEntityTargetAction.EntityCondition.MOUNTED_IN;
        }
        if (eff instanceof WaitForBlockAction w) {
            return w.checkMode == WaitForBlockAction.CheckMode.AT_POSITION;
        }
        return true;
    }

    private static long waitTimeout(MacroAction action) {
        MacroAction eff = effectiveWait(action);
        if (eff instanceof WaitForGuiAction w && w.timeoutMs > 0) return w.timeoutMs;
        if (eff instanceof WaitGuiTypeAction w && w.timeoutMs > 0) return w.timeoutMs;
        if (eff instanceof WaitFreeSlotsAction w && w.timeoutMs > 0) return w.timeoutMs;
        if (eff instanceof WaitPacketMatchAction w && w.timeoutMs > 0) return w.timeoutMs;
        if (eff instanceof WaitInventoryPredicateAction w && w.timeoutMs > 0) return w.timeoutMs;
        if (eff instanceof WaitDurabilityAction w && w.timeoutMs > 0) return w.timeoutMs;
        if (eff instanceof WaitForChatAction w && w.timeoutMs > 0) return w.timeoutMs;
        if (eff instanceof WaitForEntityAction w && w.timeoutMs > 0) return w.timeoutMs;
        if (eff instanceof WaitEntityTargetAction w && w.timeoutMs > 0) return w.timeoutMs;
        if (eff instanceof WaitGamemodeChangeAction w && w.timeoutMs > 0) return w.timeoutMs;
        if (eff instanceof WaitForMacroStepAction w && w.timeoutMs > 0) return w.timeoutMs;

        if (eff instanceof WaitForSlotChangeAction || eff instanceof WaitForPositionDeltaAction
                || eff instanceof WaitForTeleportAction || eff instanceof WaitForWorldChangeAction
                || eff instanceof WaitPosAction || eff instanceof WaitForChatAction
                || eff instanceof WaitForEntityAction || eff instanceof WaitEntityTargetAction
                || eff instanceof WaitGamemodeChangeAction || eff instanceof WaitForPacketAction
                || eff instanceof WaitForSoundAction || eff instanceof WaitPacketMatchAction
                || eff instanceof WaitForCooldownAction || eff instanceof RevisionSyncAction
                || eff instanceof WaitForBlockAction || eff instanceof WaitForMacroStepAction) {
            return WAIT_EVENT_TIMEOUT_MS;
        }
        return WAIT_TIMEOUT_MS;
    }

    private static String typeOf(String ref) {
        if (ref == null || ref.isBlank()) return "";
        String s = ref.trim();
        if (s.startsWith("~")) {
            String[] parts = s.split("~");
            if (parts.length >= 3) s = parts[2];
        }
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);
        return s;
    }

    private void clearBookkeepingOutside(int target) {
        branchElseStart = -1;
        branchElseCount = 0;
        while (!repeatStack.isEmpty()) {
            int[] top = repeatStack.peek();
            if (target >= top[0] && target < top[1]) break;
            repeatStack.pop();
        }

        if (raceBlockEnd >= 0 && (target < 0 || target >= raceBlockEnd)) {
            raceBlockEnd = -1;
            raceSkipIndices = null;
            raceConditions = null;
            raceWaiting = false;
        }
    }

    private void resetForLoop() {
        ip = 0;
        branchElseStart = -1;
        branchElseCount = 0;
        repeatStack.clear();
        delayUntil = 0;
        waitingForGui = false;
        waitAction = null;
        pendingUsePhase = null;
        pendingUseRemaining = 0;
        pendingUseRelease = false;
        pendingUseHoldMs = 0L;
        pendingCapture = null;
        pendingSuggestionId = -1;
        pendingSuggestionQuery = "";
        pendingCaptureDeadline = 0L;
        pendingBurst = null;
        pendingBurstRemaining = 0;
        pendingBurstDelayMs = 0L;
        pendingBook = null;
        pendingBookIndex = 0;
        pendingBookTotal = 0;
        pendingBookHotbarMask = 0;
        pendingBookDelayMs = 0L;
        pendingBookSwapAttempts = 0;
        clearPendingTp();
        pendingCustomMenu = null;
        pendingCustomMenuDeadline = 0L;
        leafBurstRemaining = 0;
        clickPlan = null;
        clickPlanIdx = 0;
        clickResolveDeadline = 0L;
        clickPlanDelayMs = 0;
        clickPlanCloseAfter = false;
        clickPlanCloseSilent = false;
        pendingClickCloseAfter = false;
        pendingClickCloseSilent = false;
        cmdQueue = null;
        cmdQueueIdx = 0;
        cmdQueueDelayMs = 0;
        packetWaitArmed = false;
        soundWaitArmed = false;
        raceWaiting = false;
        raceBlockEnd = -1;
        raceSkipIndices = null;
        raceConditions = null;
    }

    private boolean terminateRequested;
    private String terminateReason = "done";

    private void requestTerminate(String why) {
        terminateRequested = true;
        terminateReason = why == null || why.isBlank() ? "done" : why;
    }

    private boolean consumeTerminate() {
        if (!terminateRequested) return done;
        terminateRequested = false;
        return finishOrRunFinally(terminateReason);
    }

    private void finish(String why) {
        done = true;
        status = why == null || why.isBlank() ? "done" : why;
    }

    private boolean finishOrRunFinally(String why) {
        if (finallyRegistered && !finallyRan && !runningFinally) {
            finallyRan = true;
            runningFinally = true;
            pendingFinishReason = why;
            ip = finallyStart;
            clearBookkeepingOutside(finallyStart);
            return false;
        }
        finish(why);
        return true;
    }

    private void skip(MacroActionType type) {
        status = "skipped " + type.name().toLowerCase(Locale.ROOT);
    }

    private final java.util.Set<MacroActionType> notedSkips = java.util.EnumSet.noneOf(MacroActionType.class);

    private void skipFullOnly(MacroActionType type, MultiMacroHost host) {
        skipUnsupported(type, host, "entity tracking is unavailable on this bot");
    }

    private void skipUnsupported(MacroActionType type, MultiMacroHost host, String reason) {
        skip(type);
        if (notedSkips.add(type)) host.macroNote("Skipped " + type.name() + ": " + reason + ".");
    }

    boolean hasActiveCustomMenuDeadline(long now) {
        return pendingCustomMenu != null && now < pendingCustomMenuDeadline;
    }

    boolean isHandlingCustomMenu() {
        return pendingCustomMenu != null;
    }

    boolean handlesCustomMenu() {
        return hasCustomMenuAction || pendingCustomMenu != null;
    }

    private static boolean isLoginPrefixType(MacroActionType type) {
        return type == MacroActionType.CUSTOM_MENU
            || type == MacroActionType.WAIT_GUI
            || type == MacroActionType.WAIT_GUI_TYPE
            || type == MacroActionType.DELAY;
    }

    private boolean pollCustomMenu(MultiMacroHost host, long now) {
        CustomMenuAction action = pendingCustomMenu;
        if (action == null) return true;
        autismclient.api.custommenu.CustomMenuSnapshot snapshot = host.customMenu();
        if (snapshot == null) {

            if (now < pendingCustomMenuDeadline) return false;
            requestTerminate("custom screen never appeared (timed out)");
            return true;
        }
        CustomMenuActionSupport.Prepared prepared = CustomMenuActionSupport.prepare(
            action, snapshot, value -> host.resolveCustomMenuValue(value, vars));
        if (!prepared.success()) {

            if (prepared.error() != null && prepared.error().contains("unavailable")) {
                pendingCustomMenuDeadline = now + action.boundedTimeout();
                status = "waiting for password";
                return false;
            }
            requestTerminate("custom screen failed: " + prepared.error());
            return true;
        }
        autismclient.api.custommenu.CustomMenuSubmitResult result =
            host.submitCustomMenu(snapshot, prepared.submission());
        if (result == null || !result.success()) {
            String error = result == null ? "no submission result" : result.error();
            requestTerminate("custom screen failed: " + error);
            return true;
        }
        status = "submitted custom screen";
        return true;
    }

    private boolean beginCapture(CaptureValueAction a, MultiMacroHost host, long now) {
        CaptureValueAction.Source source = a.source == null ? CaptureValueAction.Source.GUI_TITLE : a.source;
        if (source == CaptureValueAction.Source.COMMAND_AUTOFILL) {
            String query = autofillQuery(a);
            if (query.isBlank()) return false;
            if (a.autofillCacheList) return beginAutofillSweep(a, query, host, now);
            List<String> cached = suggestionCache.get(query);
            if (cached != null && applyListCapture(a, cached)) return false;
            int requestId = host.requestCommandSuggestions(query);
            if (requestId < 0) return false;
            pendingCapture = a;
            pendingSuggestionId = requestId;
            pendingSuggestionQuery = query;
            pendingCaptureDeadline = now + Math.max(100L, Math.min(60_000L, a.autofillTimeoutMs));
            return true;
        }
        if (source == CaptureValueAction.Source.SCOREBOARD && a.waitForTrigger) {
            pendingCapture = a;
            pendingCaptureDeadline = now + WAIT_EVENT_TIMEOUT_MS;
            CaptureValueAction.ScoreboardLine line = selectScoreboardLine(a, host.scoreboardLines());
            if (line != null) {
                pendingScoreboardBaselineReady = true;
                pendingScoreboardKey = line.key();
                pendingScoreboardText = safe(line.text());
            }
            return true;
        }
        if (tryCapture(a, host)) return false;
        if (!a.waitForTrigger) return false;
        pendingCapture = a;
        pendingCaptureDeadline = now + WAIT_EVENT_TIMEOUT_MS;
        return true;
    }

    private boolean pollPendingCapture(MultiMacroHost host, long now) {
        CaptureValueAction.Source source = pendingCapture.source == null
            ? CaptureValueAction.Source.GUI_TITLE : pendingCapture.source;
        if (source == CaptureValueAction.Source.COMMAND_AUTOFILL) {
            List<String> suggestions = host.commandSuggestions(pendingSuggestionId);
            if (suggestions == null) return now >= pendingCaptureDeadline;
            if (pendingAutofillUnion != null) {
                AutofillUnion union = pendingAutofillUnion;
                union.names.addAll(suggestions);
                union.cap = Math.max(union.cap, suggestions.size());
                union.baseFetched = true;
                applyListCapture(pendingCapture, unionPool(union, pendingCapture));
                driveAutofillSweep(union, host);
                return true;
            }
            applyListCapture(pendingCapture, suggestions);
            return true;
        }
        if (source == CaptureValueAction.Source.SCOREBOARD) {
            CaptureValueAction.ScoreboardLine line = selectScoreboardLine(
                pendingCapture, host.scoreboardLines(), pendingScoreboardKey);
            if (line == null) return now >= pendingCaptureDeadline;
            if (!pendingScoreboardBaselineReady) {
                pendingScoreboardBaselineReady = true;
                pendingScoreboardKey = line.key();
                pendingScoreboardText = safe(line.text());
                return now >= pendingCaptureDeadline;
            }
            String currentText = safe(line.text());
            if (!currentText.equals(pendingScoreboardText)) {
                pendingScoreboardKey = line.key();
                pendingScoreboardText = currentText;
                if (applyPreview(pendingCapture.previewScoreboardLine(line))) return true;
            }
            return now >= pendingCaptureDeadline;
        }
        if (tryCapture(pendingCapture, host)) return true;
        return now >= pendingCaptureDeadline;
    }

    private boolean tryCapture(CaptureValueAction a, MultiMacroHost host) {
        return switch (a.source == null ? CaptureValueAction.Source.GUI_TITLE : a.source) {
            case TABLIST -> applyListCapture(a, host.tablistNames(a.excludeSelf));
            case SCOREBOARD -> applyScoreboardCapture(a, host.scoreboardLines());
            case COMMAND_AUTOFILL -> false;
            default -> captureValue(a, host);
        };
    }

    private boolean captureValue(CaptureValueAction a, MultiMacroHost host) {
        String text = rawCaptureText(a, host);
        if (text == null || !captureMatches(a, text)) return false;
        Map<String, String> additions = new java.util.LinkedHashMap<>();
        if (isPatternMode(a) && a.pattern != null && !a.pattern.isBlank()) {
            MacroCapturePattern.match(a.matchMode, a.pattern, text).ifPresent(r ->
                r.values().forEach((k, v) -> additions.put(k, v.value())));
        }
        String saveAs = MacroVariableContext.cleanRootName(a.saveAs);
        if (!saveAs.isEmpty()) additions.putIfAbsent(saveAs, text);
        if (additions.isEmpty()) return false;
        if (a.normalizeNumbers) additions.replaceAll((k, v) -> normalizeCapturedNumber(v));
        if (!applyNumberModifier(a, additions)) return false;
        vars.putAll(additions);
        return true;
    }

    private String rawCaptureText(CaptureValueAction a, MultiMacroHost host) {
        return switch (a.source == null ? CaptureValueAction.Source.GUI_TITLE : a.source) {
            case GUI_TITLE -> {
                String t = host.openScreenTitle();
                yield t == null || t.isEmpty() ? null : t;
            }
            case RECENT_CHAT -> {
                List<String> lines = host.chatSince(0);
                for (int i = lines.size() - 1; i >= 0; i--) {
                    if (captureMatches(a, lines.get(i))) yield lines.get(i);
                }
                yield null;
            }
            case HELD_ITEM, CURSOR_ITEM, GUI_ITEM, PLAYER_ITEM -> {
                ItemTarget filter = a.itemFilter == null || a.itemFilter.isBlank()
                    ? null : ItemTarget.fromLegacyEntry(resolve(a.itemFilter));
                yield host.captureItemText(a, filter);
            }
            default -> null;
        };
    }

    private boolean applyListCapture(CaptureValueAction a, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()
            || !CaptureListSelector.filterError(a.listFilter, a.listFilterText).isBlank()) return false;
        List<String> pool = CaptureListSelector.filter(candidates, a.listFilter, a.listFilterText);
        pool = CaptureListSelector.exclude(pool, a.listExcludeText);
        CaptureListSelector.State state = captureSelections.computeIfAbsent(a, ignored -> new CaptureListSelector.State());
        java.util.Optional<CaptureListSelector.Pick> picked = CaptureListSelector.pick(
            pool, a.listSelection, a.listPickPosition, state);
        if (picked.isEmpty()) return false;
        String value = picked.get().value();
        if (a.listStripPrefix) value = CaptureListSelector.stripMatch(value, a.listFilter, a.listFilterText);
        String saveAs = MacroVariableContext.cleanRootName(a.saveAs);
        if (saveAs.isEmpty()) return false;
        vars.put(saveAs, value);
        return true;
    }

    private boolean applyScoreboardCapture(CaptureValueAction a, List<CaptureValueAction.ScoreboardLine> lines) {
        CaptureValueAction.ScoreboardLine selected = selectScoreboardLine(a, lines);
        return selected != null && applyPreview(a.previewScoreboardLine(selected));
    }

    private CaptureValueAction.ScoreboardLine selectScoreboardLine(
        CaptureValueAction a, List<CaptureValueAction.ScoreboardLine> lines
    ) {
        return selectScoreboardLine(a, lines, a.scoreboardRow);
    }

    private CaptureValueAction.ScoreboardLine selectScoreboardLine(
        CaptureValueAction a, List<CaptureValueAction.ScoreboardLine> lines, String preferredKeyValue
    ) {
        if (lines == null || lines.isEmpty()) return null;
        String preferredKey = preferredKeyValue == null ? "" : preferredKeyValue;
        for (CaptureValueAction.ScoreboardLine line : lines) {
            if (!preferredKey.isBlank() && preferredKey.equals(line.key())) return line;
        }
        CaptureValueAction.ScoreboardLine rowCandidate = null;
        for (CaptureValueAction.ScoreboardLine line : lines) {
            if (!sameScoreboardObjective(a, line)) continue;
            if (line.row() == a.scoreboardRowIndex) {
                rowCandidate = line;
                break;
            }
        }
        if (rowCandidate != null && a.previewScoreboardLine(rowCandidate).success()) return rowCandidate;
        CaptureValueAction.ScoreboardLine selected = bestScoreboardLine(a, lines, true);
        return selected != null ? selected : bestScoreboardLine(a, lines, false);
    }

    private CaptureValueAction.ScoreboardLine bestScoreboardLine(
        CaptureValueAction a, List<CaptureValueAction.ScoreboardLine> lines, boolean requireObjective
    ) {
        CaptureValueAction.ScoreboardLine selected = null;
        int bestDistance = Integer.MAX_VALUE;
        for (CaptureValueAction.ScoreboardLine line : lines) {
            if (requireObjective && !sameScoreboardObjective(a, line)) continue;
            CaptureValueAction.Preview preview = a.previewScoreboardLine(line);
            if (!preview.success()) continue;
            int distance = a.scoreboardRowIndex < 0 ? line.row() : Math.abs(line.row() - a.scoreboardRowIndex);
            if (selected == null || distance < bestDistance) {
                selected = line;
                bestDistance = distance;
            }
        }
        return selected;
    }

    private static boolean sameScoreboardObjective(
        CaptureValueAction action, CaptureValueAction.ScoreboardLine line
    ) {
        return action.scoreboardObjective == null || action.scoreboardObjective.isBlank()
            || action.scoreboardObjective.equals(line.objective());
    }

    private boolean applyPreview(CaptureValueAction.Preview preview) {
        if (preview == null || !preview.success() || preview.values().isEmpty()) return false;
        preview.values().forEach((name, value) -> vars.put(name, value == null ? "" : value.value()));
        return true;
    }

    private static final class AutofillUnion {
        final java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        final ArrayDeque<String> sweepQueue = new ArrayDeque<>();
        int alphabetCursor;
        int cap;
        boolean baseFetched;
        int inflightId = -1;
        String inflightQuery = "";

        AutofillUnion(String baseQuery) {
            sweepQueue.add(baseQuery);
        }
    }

    private boolean beginAutofillSweep(CaptureValueAction a, String query, MultiMacroHost host, long now) {
        AutofillUnion union = autofillUnions.computeIfAbsent(query, AutofillUnion::new);
        if (union.baseFetched) {
            applyListCapture(a, unionPool(union, a));
            driveAutofillSweep(union, host);
            return false;
        }
        int id = host.requestCommandSuggestions(query);
        if (id < 0) {
            applyListCapture(a, unionPool(union, a));
            return false;
        }
        pendingCapture = a;
        pendingSuggestionId = id;
        pendingSuggestionQuery = query;
        pendingAutofillUnion = union;
        pendingCaptureDeadline = now + Math.max(100L, Math.min(60_000L, a.autofillTimeoutMs));
        return true;
    }

    private void driveAutofillSweep(AutofillUnion union, MultiMacroHost host) {
        if (union.inflightId >= 0) {
            List<String> reply = host.commandSuggestions(union.inflightId);
            if (reply == null) return;
            union.names.addAll(reply);
            int size = reply.size();
            boolean truncated = size >= union.cap && union.cap >= MIN_SWEEP_CAP;
            union.cap = Math.max(union.cap, size);
            if (truncated) union.sweepQueue.add(union.inflightQuery);
            union.inflightId = -1;
            union.inflightQuery = "";
        }
        if (union.names.size() >= MAX_SWEEP_NAMES) { union.sweepQueue.clear(); return; }
        String target = nextSweepTarget(union);
        if (target == null) return;
        int id = host.requestCommandSuggestions(target);
        if (id >= 0) {
            union.inflightId = id;
            union.inflightQuery = target;
        }
    }

    private static String nextSweepTarget(AutofillUnion union) {
        while (!union.sweepQueue.isEmpty()) {
            if (union.alphabetCursor >= DRILL_ALPHABET.length()) {
                union.sweepQueue.poll();
                union.alphabetCursor = 0;
                continue;
            }
            String target = union.sweepQueue.peek() + DRILL_ALPHABET.charAt(union.alphabetCursor);
            union.alphabetCursor++;
            return target;
        }
        return null;
    }

    private static List<String> unionPool(AutofillUnion union, CaptureValueAction a) {
        List<String> pool = new ArrayList<>(union.names);
        boolean ordered = a.listSelection == CaptureListSelector.Selection.SEQUENTIAL
            || a.listSelection == CaptureListSelector.Selection.FIRST
            || a.listSelection == CaptureListSelector.Selection.LAST
            || a.listSelection == CaptureListSelector.Selection.POSITION;
        if (ordered) pool.sort(String.CASE_INSENSITIVE_ORDER);
        return pool;
    }

    private String autofillQuery(CaptureValueAction a) {
        String query = resolve(a.autofillCommand);
        if (query == null) return "";
        if (!query.startsWith("/")) query = "/" + query;
        if (!query.contains(" ")) query += " ";
        String prefix = a.listFilter == CaptureListSelector.Filter.PREFIX && a.listFilterText != null
            ? resolve(a.listFilterText).trim() : "";
        if (!prefix.isEmpty() && !prefix.contains(" ") && query.endsWith(" ")) {
            int end = query.length();
            while (end > 0 && query.charAt(end - 1) == ' ') end--;
            query = query.substring(0, end) + " " + prefix;
        }
        return query;
    }

    private int sendBook(NbtBookAction book, int index, int total, MultiMacroHost host) {
        List<String> pages = bookPages(book);
        if (pages == null || pages.isEmpty()) return -1;
        String title = book.title == null ? "" : resolve(book.title);
        if (book.appendCount && total > 1 && index > 0) title += " #" + (index + 1);
        int slot = host.writeBook(pages, title, book.sign, book.requireHeldWritableBook, pendingBookHotbarMask);
        if (slot == -2) return -2;
        if (slot < 0 || slot > 8) return -1;
        pendingBookHotbarMask |= 1 << slot;
        return slot;
    }

    private List<String> bookPages(NbtBookAction book) {
        int pageCount = Math.max(1, Math.min(AutismBookPayloadBuilder.MAX_PAGES, book.pages));
        int chars = Math.max(1, Math.min(AutismBookPayloadBuilder.DEFAULT_CHARS_PER_PAGE, book.characters));
        String source = book.dataSource == null ? NbtBookAction.SOURCE_RANDOM : book.dataSource.trim();
        if (NbtBookAction.SOURCE_FILE.equalsIgnoreCase(source)) return null;
        if (!NbtBookAction.SOURCE_PASTED.equalsIgnoreCase(source)
            || book.customComponent == null || book.customComponent.isEmpty()) {
            return AutismBookPayloadBuilder.randomPages(pageCount, chars, book.randomType, new Random());
        }
        String text = resolve(book.customComponent);
        List<String> pages = new java.util.ArrayList<>(pageCount);
        for (int offset = 0; offset < text.length() && pages.size() < pageCount; offset += chars) {
            pages.add(text.substring(offset, Math.min(text.length(), offset + chars)));
        }
        if (pages.isEmpty()) pages.add("");
        return List.copyOf(pages);
    }

    private void finishPendingBook(MultiMacroHost host, boolean completed) {
        NbtBookAction book = pendingBook;
        pendingBook = null;
        pendingBookIndex = 0;
        pendingBookTotal = 0;
        pendingBookHotbarMask = 0;
        pendingBookDelayMs = 0L;
        pendingBookSwapAttempts = 0;
        if (completed && book != null && book.disconnectAfter) host.disconnectBot("NBT book macro");
    }

    private static boolean isPatternMode(CaptureValueAction a) {
        return a.matchMode != null && a.matchMode != MacroCapturePattern.Mode.MATCH;
    }

    private static boolean captureMatches(CaptureValueAction a, String text) {
        if (text == null) return false;
        if (a.pattern == null || a.pattern.isBlank()) return true;
        if (isPatternMode(a)) return MacroCapturePattern.match(a.matchMode, a.pattern, text).isPresent();
        return text.toLowerCase(Locale.ROOT).contains(a.pattern.toLowerCase(Locale.ROOT));
    }

    private static String normalizeCapturedNumber(String v) {
        java.math.BigDecimal n = parseCapturedNumber(v);
        return n == null ? v : formatCapturedNumber(n);
    }

    private static boolean applyNumberModifier(CaptureValueAction a, Map<String, String> values) {
        CaptureValueAction.NumberModifier mod = a.numberModifier == null
            ? CaptureValueAction.NumberModifier.NONE : a.numberModifier;
        if (mod == CaptureValueAction.NumberModifier.NONE || values.isEmpty()) return true;
        if (!Double.isFinite(a.numberModifierAmount)) return false;
        if (mod == CaptureValueAction.NumberModifier.DIVIDE && a.numberModifierAmount == 0.0) return false;
        java.math.BigDecimal operand = java.math.BigDecimal.valueOf(a.numberModifierAmount);
        boolean changed = false;
        for (Map.Entry<String, String> e : values.entrySet()) {
            java.math.BigDecimal n = parseCapturedNumber(e.getValue());
            if (n == null) continue;
            java.math.BigDecimal r = switch (mod) {
                case PLUS -> n.add(operand);
                case MINUS -> n.subtract(operand);
                case MULTIPLY -> n.multiply(operand);
                case DIVIDE -> n.divide(operand, java.math.MathContext.DECIMAL128);
                case NONE -> n;
            };
            e.setValue(formatCapturedNumber(r));
            changed = true;
        }
        return changed;
    }

    private static java.math.BigDecimal parseCapturedNumber(String raw) {
        if (raw == null) return null;
        try {
            return new java.math.BigDecimal(autismclient.util.macro.MacroTemplate.parseCompactNumber(raw));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String formatCapturedNumber(java.math.BigDecimal n) {
        java.math.BigDecimal stripped = n.stripTrailingZeros();
        return stripped.scale() <= 0 ? stripped.toBigInteger().toString() : stripped.toPlainString();
    }

    private XCarryAction resolveXCarry(XCarryAction source) {
        XCarryAction copy = new XCarryAction();
        copy.mode = source.mode;
        copy.transferMode = source.transferMode;
        copy.safeClickDelayTicks = source.safeClickDelayTicks;
        copy.safeClickDelayAfterPickup = source.safeClickDelayAfterPickup;
        copy.safeClickDelayBeforeReturn = source.safeClickDelayBeforeReturn;
        copy.carryCursor = source.carryCursor;
        copy.useCrafting = source.useCrafting;
        copy.useArmor = source.useArmor;
        copy.useOffhand = source.useOffhand;

        List<ItemTarget> configured = source.entryTargets.isEmpty()
            ? source.entries.stream().map(ItemTarget::fromLegacyEntry).toList()
            : source.entryTargets;
        int count = Math.min(XCarryAction.MAX_ENTRIES, configured.size());
        for (int i = 0; i < count; i++) {
            ItemTarget target = configured.get(i);
            if (target == null) continue;
            ItemTarget resolved = target.copy();
            String template = target.template == null || target.template.isBlank()
                ? target.editorText() : target.template;
            if (template != null && template.indexOf('{') >= 0) {
                ItemTarget dynamic = ItemTarget.fromLegacyEntry(resolve(template));
                if (target.hasSlot()) dynamic.slot = target.slot;
                resolved = dynamic;
            }
            if (!resolved.hasSlot() && !resolved.hasIdentity()) continue;
            copy.entryTargets.add(resolved);
            copy.entryDestinations.add(source.destinationFor(i));
            copy.entryAmountModes.add(source.amountModeFor(i));
            copy.entryAmounts.add(source.amountFor(i));
        }
        return copy;
    }

    private autismclient.util.macro.MacroTemplate.Resolution resolveTemplate(String template) {
        if (template == null || template.isEmpty() || template.indexOf('{') < 0) {
            return autismclient.util.macro.MacroTemplate.Resolution.ok(template == null ? "" : template);
        }
        return autismclient.util.macro.MacroTemplate.resolve(template, buildContext(), null);
    }

    private String resolve(String template) {
        return resolveTemplate(template).value();
    }

    private MacroVariableContext buildContext() {
        MacroVariableContext ctx = new MacroVariableContext();
        MultiMacroHost host = activeHost;
        if (host != null) seedBuiltIns(ctx, host);
        for (Map.Entry<String, String> e : vars.entrySet()) ctx.set(e.getKey(), MacroValue.text(e.getValue()));
        return ctx;
    }

    private static void seedBuiltIns(MacroVariableContext ctx, MultiMacroHost host) {
        String username = host.botUsername();
        if (!username.isBlank()) {
            MacroValue u = MacroValue.text(username);
            ctx.set("username", u);
            ctx.set("user", u);
            ctx.set("player", u);
        }
        String uuid = host.botUuid();
        if (!uuid.isBlank()) ctx.set("uuid", MacroValue.text(uuid));
        String server = host.serverAddress();
        if (!server.isBlank()) ctx.set("server", MacroValue.text(server));
        String password = host.macroPassword();
        if (!password.isBlank()) ctx.set("password", MacroValue.text(password));
        String dim = host.dimension();
        if (dim != null && !dim.isBlank()) {
            MacroValue d = MacroValue.text(dim);
            ctx.set("dimension", d);
            ctx.set("dim", d);
        }
        MacroValue slot = MacroValue.slot(Math.max(0, Math.min(8, host.selectedHotbar())));
        ctx.set("selected_slot", slot);
        ctx.set("target_slot", slot);
        if (host.hasPosition()) {
            double x = host.posX(), y = host.posY(), z = host.posZ();
            ctx.set("x", MacroValue.text(String.format(Locale.ROOT, "%.3f", x)));
            ctx.set("y", MacroValue.text(String.format(Locale.ROOT, "%.3f", y)));
            ctx.set("z", MacroValue.text(String.format(Locale.ROOT, "%.3f", z)));
            int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
            ctx.set("bx", MacroValue.text(Integer.toString(bx)));
            ctx.set("by", MacroValue.text(Integer.toString(by)));
            ctx.set("bz", MacroValue.text(Integer.toString(bz)));
            ctx.set("pos", MacroValue.text(bx + " " + by + " " + bz));
            float yaw = host.currentYaw(), pitch = host.currentPitch();
            ctx.set("yaw", MacroValue.text(String.format(Locale.ROOT, "%.2f", yaw)));
            ctx.set("pitch", MacroValue.text(String.format(Locale.ROOT, "%.2f", pitch)));
            ctx.set("rot", MacroValue.text(String.format(Locale.ROOT, "%.2f %.2f", yaw, pitch)));
            ctx.set("facing", MacroValue.text(net.minecraft.core.Direction.fromYRot(yaw).getName()));
        }
    }

    private static String modeWord(AutismPacketClick.Mode mode) {
        if (mode == AutismPacketClick.Mode.RIGHT_CLICK) return "right";
        if (mode == AutismPacketClick.Mode.QUICK_MOVE) return "shift";
        return "left";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
