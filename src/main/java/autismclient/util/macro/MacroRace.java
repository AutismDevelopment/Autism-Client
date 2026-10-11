package autismclient.util.macro;

import autismclient.modules.AutismModule;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismMacro;
import autismclient.util.AutismSharedState;
import autismclient.util.macro.MacroActionResources.SharedClientResource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

final class MacroRace {
    private MacroRace() {
    }
    public static void runPacketGateAction(Minecraft mc, PacketGateAction action) {
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
                MacroPrearm.waitForPacketMatch(wait);
                disablePacketGate(mc, action);
            }
            case UNTIL_GUI -> {
                MacroRunLoop.waitForPredicate(() -> MacroGuiMatcher.matches(mc.gui.screen(), action.untilGuiType, action.untilGuiTitle), Math.max(0, action.durationValue));
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
                MacroRunLoop.waitForPredicate(() -> wait.matches(mc), wait.timeoutMs);
                disablePacketGate(mc, action);
            }
            default -> {}
        }
    }

    private static void disablePacketGate(Minecraft mc, PacketGateAction action) {
        PacketGateManager.disableAndFlushConfigured(
            action.gateId == null || action.gateId.isBlank() ? "auto" : action.gateId,
            MacroExecutor.currentRunId(),
            mc == null ? null : mc.getConnection(),
            action.flushOnDisable);
    }

    public static void runFinallyActions(MacroExecutor.RunState run, Minecraft mc) {
        if (run == null || run.finallyActions.isEmpty()) return;
        ArrayList<MacroAction> actions = new ArrayList<>(run.finallyActions);
        run.finallyActions.clear();
        for (MacroAction action : actions) {
            if (action == null || !action.isEnabled()) continue;
            try {
                MacroDispatcher.executeSingleActionWithWaits(mc, action, AutismModule.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                AutismClientMessaging.sendPrefixed("\u00a7cCleanup failed: " + safeThrowableMessage(t));
            }
        }
    }

    public static void waitForMacroStep(WaitForMacroStepAction action) {
        long startMs = System.currentTimeMillis();
        while (MacroExecutor.isCurrentRunActive()) {
            if (isMacroStepSatisfied(action)) return;
            if (action.timeoutMs > 0 && System.currentTimeMillis() - startMs >= action.timeoutMs) return;
            CompletableFuture<Void> tick = MacroConditionRegistry.waitForNextTick();
            MacroRunLoop.waitForCondition(tick);
        }
    }

    static boolean isMacroStepSatisfied(WaitForMacroStepAction action) {
        String target = action.macroName == null ? "" : action.macroName.trim();
        if (target.isEmpty()) return true;
        synchronized (MacroExecutor.RUN_LOCK) {
            for (MacroExecutor.RunState run : MacroExecutor.ACTIVE_RUNS.values()) {
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

    public static int runRaceGroup(Minecraft mc, AutismModule module, java.util.List<MacroAction> actions, int headerIndex, RaceAction race) throws InterruptedException {
        int bodyCount = race.normalizedBodyCount(actions, headerIndex);

        java.util.List<MacroAction> conditions = new ArrayList<>();
        java.util.List<MacroAction> bodyActions = new ArrayList<>();
        for (int offset = 1; offset <= bodyCount && headerIndex + offset < actions.size(); offset++) {
            MacroAction child = actions.get(headerIndex + offset);
            if (child == null || !child.isEnabled()) continue;
            if (RaceAction.isConditionAction(child)) conditions.add(child);
            else if (RaceAction.isBodyAction(child)) bodyActions.add(child);
        }

        MacroExecutor.setCurrentStatus(conditions.isEmpty()
            ? "Race: dispatching"
            : "Race: waiting for " + (conditions.size() == 1 ? conditions.get(0).getDisplayName() : conditions.size() + " conditions"));

        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
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
            MacroExecutor.setCurrentStatus("Race: timeout");
            return bodyCount;
        }

        MacroExecutor.setCurrentStatus("Race: fired");
        waitForTimedFuture(bodyFuture, 250);
        return bodyCount;
    }

    private static CompletableFuture<?> raceTriggerBarrier(Minecraft mc, java.util.List<MacroAction> conditions) {
        if (conditions == null || conditions.isEmpty()) return CompletableFuture.completedFuture(null);
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();

        java.util.List<CompletableFuture<MacroExecutor.MacroEvent>> baseFutures = new ArrayList<>();
        java.util.List<CompletableFuture<MacroExecutor.MacroEvent>> futures = new ArrayList<>();
        for (MacroAction condition : conditions) {
            MacroPrearm.clearOwnCaptureOutput(condition);
            CompletableFuture<MacroExecutor.MacroEvent> base = timedConditionFuture(mc, condition);
            baseFutures.add(base);
            futures.add(base.thenApply(event -> {
                if (event.packet() != null) MacroPrearm.recordMatchedPacket(condition, event.packet(), event.direction());
                if (!(condition instanceof MacroCaptureOutput output)) return event;
                String name = MacroVariableContext.cleanRootName(output.getSaveAs());
                if (name.isEmpty()) return event;
                MacroValue value = MacroPrearm.conditionOutputValue(condition, mc);
                if (value == null) return event;
                java.util.LinkedHashMap<String, MacroValue> captures = new java.util.LinkedHashMap<>(event.captures());
                captures.put(name, value);
                return new MacroExecutor.MacroEvent(event.packet(), event.direction(), event.nanoTime(), event.chatCapture(), captures);
            }));
        }
        CompletableFuture<Object> first = CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]));
        CompletableFuture<Object> out = new CompletableFuture<>();
        first.whenComplete((value, e) -> {
            if (e != null) {
                out.completeExceptionally(e);
                return;
            }
            if (value instanceof MacroExecutor.MacroEvent event && run != null && !event.captures().isEmpty()) {
                run.variables.setAll(event.captures());
            }
            out.complete(value);
        });
        out.whenComplete((v, e) -> {
            for (CompletableFuture<MacroExecutor.MacroEvent> future : baseFutures) {
                if (!future.isDone()) future.cancel(true);
            }
        });
        return out;
    }

    public static int runReportGroup(Minecraft mc, java.util.List<MacroAction> actions, int headerIndex, ReportAction report) throws InterruptedException {
        int conditionCount = report.normalizedConditionCount(actions, headerIndex);
        if (conditionCount < 2 || headerIndex + 2 >= actions.size()) {
            MacroExecutor.setCurrentStatus(report.getDisplayName());
            report.execute(mc);
            return conditionCount;
        }

        MacroAction startCondition = actions.get(headerIndex + 1);
        MacroAction endCondition = actions.get(headerIndex + 2);
        if (RaceAction.isBodyAction(startCondition) && RaceAction.isConditionAction(endCondition)) {
            int timeoutMs = Math.max(100, report.timeoutMs);
            String label = report.reportLabel == null || report.reportLabel.isBlank() ? "Report" : report.reportLabel.trim();
            MacroExecutor.setCurrentStatus("Report: start " + startCondition.getDisplayName());
            CompletableFuture<MacroExecutor.MacroEvent> startAnchor = startActionAnchorFuture(startCondition);
            MacroDispatcher.executeSingleActionWithWaits(mc, startCondition, null);
            if (!MacroExecutor.isCurrentRunActive()) return conditionCount;
            MacroExecutor.MacroEvent startEvent = waitForAnchorOrNow(startAnchor, 250, startCondition.getDisplayName(), "client action");

            MacroExecutor.setCurrentStatus("Report: waiting end " + endCondition.getDisplayName());
            CompletableFuture<MacroExecutor.MacroEvent> endFuture = timedConditionFuture(mc, endCondition);
            MacroExecutor.MacroEvent endEvent = waitForTimedEvent(endFuture, timeoutMs);
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
        MacroExecutor.setCurrentStatus("Report: waiting start " + startCondition.getDisplayName());
        boolean startFired = waitForMacroCondition(mc, startCondition, timeoutMs);
        if (!startFired) {
            AutismClientMessaging.sendPrefixed("\u00A7eReport \"" + report.reportLabel + "\": timeout waiting for start condition");
            return conditionCount;
        }

        MacroExecutor.MacroEvent startEvent = new MacroExecutor.MacroEvent(null, "condition " + startCondition.getDisplayName(), System.nanoTime(), null);
        long spentMs = (startEvent.nanoTime() - startWaitNanos) / 1_000_000L;
        int remainingMs = (int) Math.max(1, timeoutMs - spentMs);
        MacroExecutor.setCurrentStatus("Report: waiting end " + endCondition.getDisplayName());
        CompletableFuture<MacroExecutor.MacroEvent> endFuture = timedConditionFuture(mc, endCondition);
        MacroExecutor.MacroEvent endEvent = waitForTimedEvent(endFuture, remainingMs);
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

    public static int runMultiGroup(Minecraft mc, AutismModule module, String macroName, java.util.List<MacroAction> actions, int headerIndex, MultiAction multi) throws InterruptedException {

        if (autismclient.util.AutismLiteVariant.enabled()) return 0;
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
                MacroExecutor.setCurrentStatus("Multi: starting batch");
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
        if (!MacroExecutor.isCurrentRunActive()) return stepCount;

        int waitMs = Math.max(0, multi.waitMs);
        if (MultiAction.WAIT_UNTIL_READY.equals(multi.waitMode)) {
            MacroExecutor.setCurrentStatus("Multi: waiting for " + scope.size() + (scope.size() == 1 ? " account" : " accounts"));
            multiWaitUntilReady(manager, scope, waitMs);
        } else if (MultiAction.WAIT_FIXED_DELAY.equals(multi.waitMode) && waitMs > 0) {
            MacroExecutor.setCurrentStatus("Multi: delay " + waitMs + " ms");
            multiSleepCancellable(waitMs);
        }
        if (!MacroExecutor.isCurrentRunActive()) return stepCount;

        AutismMacro subMacro = new AutismMacro();
        subMacro.name = (macroName == null || macroName.isBlank() ? "Macro" : macroName) + " (Multi)";
        for (int offset = 1; offset <= stepCount && headerIndex + offset < actions.size(); offset++) {
            MacroAction child = actions.get(headerIndex + offset);
            if (child == null) continue;
            MacroAction copy = AutismMacro.createActionFromTag(AutismMacro.serializeAction(child));
            if (copy != null) subMacro.actions.add(copy);
        }

        MacroExecutor.setCurrentStatus("Multi: running on " + scope.size() + (scope.size() == 1 ? " account" : " accounts"));
        autismclient.util.multi.MultiManager.BroadcastResult result = manager.runMacroDirect(subMacro, scope);
        AutismClientMessaging.sendPrefixed("\u00A7bMulti: " + result.summary());

        MacroExecutor.setCurrentStatus("Multi: waiting for completion");
        multiWaitForCompletion(manager, scope, subMacro.name);
        if (!MacroExecutor.isCurrentRunActive()) return stepCount;

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
            if (!MacroExecutor.isCurrentRunActive()) return;
            Thread.sleep(Math.min(100L, Math.max(1L, remaining / 1_000_000L)));
        }
    }

    private static void multiWaitUntilReady(autismclient.util.multi.MultiManager manager, java.util.Set<String> scope, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + Math.max(0L, timeoutMs) * 1_000_000L;
        while (MacroExecutor.isCurrentRunActive()) {
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
        while (MacroExecutor.isCurrentRunActive()) {
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
            MacroDispatcher.executeSingleActionWithWaits(mc, trigger, null);
            return true;
        }
        boolean fired = waitForFutureDone(future, timeoutMs);
        if (!fired) future.cancel(true);
        return fired;
    }

    private static void dispatchRaceBody(Minecraft mc, AutismModule module, java.util.List<MacroAction> bodyActions,
                                         MacroExecutor.RunState run, CompletableFuture<Void> bodyFuture) {
        if (bodyActions == null || bodyActions.isEmpty()) {
            bodyFuture.complete(null);
            return;
        }
        Runnable task = () -> {
            MacroExecutor.RunState previous = MacroExecutor.CURRENT_RUN.get();
            if (run != null) MacroExecutor.CURRENT_RUN.set(run);
            EnumSet<SharedClientResource> heldResources = EnumSet.noneOf(SharedClientResource.class);
            try {
                heldResources = MacroExecutor.acquireClientResources(MacroExecutor.resourcesForActions(bodyActions));
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
                MacroExecutor.releaseClientResources(heldResources);
                if (previous != null) MacroExecutor.CURRENT_RUN.set(previous);
                else MacroExecutor.CURRENT_RUN.remove();
            }
        };

        if (bodyActions.stream().allMatch(MacroRace::isRaceDirectPacketAction)) {
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

    private static CompletableFuture<MacroExecutor.MacroEvent> startActionAnchorFuture(MacroAction action) {
        if (action instanceof PacketClickAction) {
            return MacroPacketObserve.awaitTimedSend(packet -> packet instanceof ServerboundContainerClickPacket).future;
        }
        if (action instanceof ItemAction) {
            return MacroPacketObserve.awaitTimedSend(packet -> packet instanceof ServerboundContainerClickPacket).future;
        }
        if (action instanceof CloseGuiAction) {
            return MacroPacketObserve.awaitTimedSend(packet -> packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClosePacket).future;
        }
        if (action instanceof SendChatAction) {
            return MacroPacketObserve.awaitTimedSend(packet -> packet instanceof net.minecraft.network.protocol.game.ServerboundChatPacket
                || packet instanceof net.minecraft.network.protocol.game.ServerboundChatCommandPacket
                || packet instanceof net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket).future;
        }
        if (action instanceof SendPacketAction || action instanceof PayloadAction) {
            return MacroPacketObserve.awaitTimedSend(packet -> true).future;
        }
        return null;
    }

    private static MacroExecutor.MacroEvent waitForAnchorOrNow(CompletableFuture<MacroExecutor.MacroEvent> anchor, int timeoutMs, String label, String fallbackDirection) {
        if (anchor != null) {
            MacroExecutor.MacroEvent event = waitForTimedEvent(anchor, timeoutMs);
            if (event != null) return event;
            anchor.cancel(true);
        }
        return new MacroExecutor.MacroEvent(null, fallbackDirection + " " + (label == null ? "" : label), System.nanoTime(), null);
    }

    private static MacroExecutor.MacroEvent waitForTimedEvent(CompletableFuture<MacroExecutor.MacroEvent> future, long timeoutMs) {
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
            return !future.isCancelled() && MacroExecutor.isCurrentRunActive();
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

    public static String safeThrowableMessage(Throwable throwable) {
        if (throwable == null) return "unknown error";
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static boolean isWorldTransitionPacket(Packet<?> packet) {
        return packet instanceof net.minecraft.network.protocol.game.ClientboundRespawnPacket
            || packet instanceof net.minecraft.network.protocol.game.ClientboundLoginPacket;
    }

    private static CompletableFuture<MacroExecutor.MacroEvent> timedConditionFuture(Minecraft mc, MacroAction trigger) {
        if (trigger == null) {
            return CompletableFuture.completedFuture(new MacroExecutor.MacroEvent(null, "condition", System.nanoTime(), null));
        }
        if (trigger instanceof WaitForChatAction chat) {
            MacroVariableContext context = MacroExecutor.currentVariableContext();
            java.util.concurrent.atomic.AtomicReference<java.util.Map<String, MacroValue>> captures =
                new java.util.concurrent.atomic.AtomicReference<>(java.util.Map.of());
            MacroPacketObserve.TimedOneShotPacketListener listener = MacroPacketObserve.awaitTimedReceive((packet, direction, now) -> {
                MacroExecutor.ChatCapture capture = MacroChatObserve.extractIncomingChat(packet);
                var matched = MacroChatObserve.captureChatAction(chat, capture, context);
                matched.ifPresent(captures::set);
                return matched.isPresent();
            });

            CompletableFuture<MacroExecutor.MacroEvent> out = new CompletableFuture<>();
            listener.future.whenComplete((event, e) -> {
                if (e != null) out.completeExceptionally(e);
                else out.complete(new MacroExecutor.MacroEvent(event.packet(), event.direction(), event.nanoTime(),
                    MacroChatObserve.extractIncomingChat(event.packet()), captures.get()));
            });
            out.whenComplete((v, e) -> listener.cancel());
            return out;
        }
        if (trigger instanceof WaitForPacketAction packetAction) {
            java.util.List<String> targets = packetAction.effectiveList();
            CompletableFuture<MacroExecutor.MacroEvent> matched = new CompletableFuture<>();
            MacroPacketObserve.TimedOneShotPacketListener recv = MacroPacketObserve.awaitTimedReceive((packet, direction, now) -> {
                if (targets.isEmpty()) return true;
                for (String target : targets) {
                    if (MacroPacketWait.matchesPacketTarget(target, packet, "S2C")) return true;
                }
                return false;
            });
            MacroPacketObserve.TimedOneShotPacketListener send = MacroPacketObserve.awaitTimedSend((packet, direction, now) -> {
                if (targets.isEmpty()) return true;
                for (String target : targets) {
                    if (MacroPacketWait.matchesPacketTarget(target, packet, "C2S")) return true;
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
            CompletableFuture<MacroExecutor.MacroEvent> done = new CompletableFuture<>();
            CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForWorldChange(world);
            MacroPacketObserve.TimedOneShotPacketListener worldListener = MacroPacketObserve.awaitTimedReceive((packet, direction, now) -> isWorldTransitionPacket(packet));
            tickFuture.whenComplete((v, e) -> {
                if (e != null) {
                    if (!done.isDone()) done.cancel(true);
                } else {
                    done.complete(new MacroExecutor.MacroEvent(null, "world change", System.nanoTime(), null));
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
            CompletableFuture<MacroExecutor.MacroEvent> done = new CompletableFuture<>();
            MacroPacketObserve.TimedOneShotPacketListener teleListener = MacroPacketObserve.awaitTimedReceive((packet, direction, now) ->
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
                        else done.complete(new MacroExecutor.MacroEvent(event.packet(), event.direction(), System.nanoTime(), event.chatCapture()));
                    });
            });
            done.whenComplete((v, e) -> teleListener.cancel());
            return done;
        }

        CompletableFuture<?> base = raceConditionFuture(mc, trigger);
        if (base == null) {
            CompletableFuture<MacroExecutor.MacroEvent> done = new CompletableFuture<>();
            MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
            mc.execute(() -> MacroExecutor.withRunContext(run, () -> {
                try {
                    MacroDispatcher.executeSingleActionWithWaits(mc, trigger, null);
                    done.complete(new MacroExecutor.MacroEvent(null, trigger.getDisplayName(), System.nanoTime(), null));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    done.cancel(true);
                } catch (Throwable t) {
                    done.completeExceptionally(t);
                }
            }));
            return done;
        }
        CompletableFuture<MacroExecutor.MacroEvent> timed = new CompletableFuture<>();
        base.whenComplete((v, e) -> {
            if (e != null) timed.cancel(true);
            else timed.complete(new MacroExecutor.MacroEvent(null, trigger.getDisplayName(), System.nanoTime(), null));
        });
        timed.whenComplete((v, e) -> {
            if (!base.isDone()) base.cancel(true);
        });
        return timed;
    }

    static CompletableFuture<?> raceConditionFuture(Minecraft mc, MacroAction trigger) {
        if (trigger instanceof WaitMovementAction wm) {
            return raceConditionFuture(mc, wm.resolveSubAction());
        }
        if (trigger instanceof WaitGamemodeChangeAction gm) {
            return MacroConditionRegistry.waitForGamemodeChange(gm);
        }
        if (trigger instanceof DelayAction delay) {
            MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
            return CompletableFuture.runAsync(() -> MacroExecutor.withRunContext(run, () -> {
                try {
                    if (delay.useTicks) {
                        for (int i = 0; i < delay.delayTicks && MacroExecutor.isCurrentRunActive(); i++) {
                            MacroRunLoop.waitForCondition(MacroConditionRegistry.waitForNextTick());
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
            CompletableFuture<MacroExecutor.MacroEvent> done = new CompletableFuture<>();
            MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
            Thread worker = new Thread(() -> MacroExecutor.withRunContext(run, () -> {
                long start = System.currentTimeMillis();
                boolean saw = false;
                java.util.Map<String, MacroValue> observed = java.util.Map.of();
                while (!done.isDone() && MacroExecutor.isCurrentRunActive()) {
                    var captured = gui.captureGui(Minecraft.getInstance().gui.screen());
                    if (captured.isPresent()) {
                        saw = true;
                        observed = captured.get();
                        if (gui.waitMode == WaitForGuiAction.WaitMode.OPEN) {
                            done.complete(new MacroExecutor.MacroEvent(null, "GUI", System.nanoTime(), null, observed));
                            return;
                        }
                    } else if (saw && gui.waitMode == WaitForGuiAction.WaitMode.CLOSE) {
                        done.complete(new MacroExecutor.MacroEvent(null, "GUI close", System.nanoTime(), null, observed));
                        return;
                    }
                    if (gui.timeoutMs > 0 && System.currentTimeMillis() - start >= gui.timeoutMs) {
                        done.cancel(false);
                        return;
                    }
                    java.util.concurrent.locks.LockSupport.parkNanos(MacroExecutor.MACRO_WAIT_POLL_NANOS);
                }
            }), "MacroGuiCaptureWait");
            worker.setDaemon(true);
            worker.start();
            done.whenComplete((v, e) -> worker.interrupt());
            return done;
        }
        if (trigger instanceof WaitForChatAction chat) {
            MacroVariableContext context = MacroExecutor.currentVariableContext();
            MacroExecutor.OneShotPacketListener listener = MacroExecutor.awaitReceive(packet -> {
                MacroExecutor.ChatCapture capture = MacroChatObserve.extractIncomingChat(packet);
                return capture != null && MacroChatObserve.captureChatAction(chat, capture, context).isPresent();
            });
            listener.future.whenComplete((v, e) -> listener.cancel());
            return listener.future;
        }
        if (trigger instanceof WaitForPacketAction packetAction) {
            java.util.List<String> targets = packetAction.effectiveList();
            CompletableFuture<Packet<?>> matched = new CompletableFuture<>();
            MacroExecutor.OneShotPacketListener recv = MacroExecutor.awaitReceive(packet -> {
                if (targets.isEmpty()) return true;
                for (String target : targets) {
                    if (MacroPacketWait.matchesPacketTarget(target, packet, "S2C")) return true;
                }
                return false;
            });
            MacroExecutor.OneShotPacketListener send = MacroExecutor.awaitSend(packet -> {
                if (targets.isEmpty()) return true;
                for (String target : targets) {
                    if (MacroPacketWait.matchesPacketTarget(target, packet, "C2S")) return true;
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
            ItemTarget target = MacroWorldActions.resolveItemTarget(cooldown.itemTarget, cooldown.itemName);
            return MacroConditionRegistry.waitForCooldown(target, cooldown.checkMainInteractionHand);
        }
        if (trigger instanceof WaitPosAction pos) {
            return MacroConditionRegistry.waitForPos(pos.x, pos.y, pos.z, pos.leeway, pos.checkRotation, pos.yaw, pos.pitch, pos.rotLeeway);
        }
        if (trigger instanceof WaitForEntityAction entity) {
            return MacroPrearm.predicateFuture(() -> entity.matchesEntityTarget(Minecraft.getInstance()), entity.timeoutMs);
        }
        if (trigger instanceof WaitForSoundAction sound) {
            return MacroConditionRegistry.waitForSound(sound);
        }
        if (trigger instanceof WaitForSlotChangeAction slot) {
            return MacroConditionRegistry.waitForSlotChange(slot);
        }
        if (trigger instanceof WaitDurabilityAction durability) {
            return MacroPrearm.predicateFuture(() -> MacroDurability.durabilityObservationMatches(Minecraft.getInstance(), durability), durability.timeoutMs);
        }
        if (trigger instanceof WaitFreeSlotsAction freeSlots) {
            return MacroPrearm.predicateFuture(() -> freeSlots.matches(Minecraft.getInstance()), freeSlots.timeoutMs);
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
            MacroExecutor.OneShotPacketListener worldListener = MacroExecutor.awaitReceive(MacroRace::isWorldTransitionPacket);

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

            MacroExecutor.OneShotPacketListener teleListener = MacroExecutor.awaitReceive(packet ->
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
            MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
            return CompletableFuture.runAsync(() -> MacroExecutor.withRunContext(run, () -> {
                try {
                    MacroDispatcher.executeSingleActionWithWaits(mc, trigger, null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        return null;
    }

    static boolean waitForFutureDone(CompletableFuture<?> future, long timeoutMs) {
        Thread t = Thread.currentThread();
        long start = System.nanoTime();
        long timeoutNanos = timeoutMs <= 0 ? Long.MAX_VALUE : timeoutMs * 1_000_000L;
        future.whenComplete((v, e) -> java.util.concurrent.locks.LockSupport.unpark(t));
        while (!future.isDone() && MacroExecutor.isCurrentRunActive()) {
            if (System.nanoTime() - start >= timeoutNanos) break;
            java.util.concurrent.locks.LockSupport.parkNanos(MacroExecutor.MACRO_WAIT_POLL_NANOS);
            if (Thread.interrupted()) break;
        }
        return future.isDone() && !future.isCancelled() && MacroExecutor.isCurrentRunActive();
    }
}
