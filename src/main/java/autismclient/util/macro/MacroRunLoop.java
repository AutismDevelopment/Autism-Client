package autismclient.util.macro;

import autismclient.AutismClientAddon;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismMacro;
import autismclient.util.macro.MacroActionResources.SharedClientResource;
import net.minecraft.client.Minecraft;

import java.util.EnumSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

final class MacroRunLoop {
    private MacroRunLoop() {
    }
    public static void run(MacroExecutor.RunState runState) {
        MacroExecutor.CURRENT_RUN.set(runState);
        try {
            runInternal(runState);
        } catch (Throwable fatal) {

            if (!runState.options.silentLifecycle()) {
                try { AutismClientMessaging.sendPrefixed("\u00a7cMacro crashed: " + fatal.getMessage()); } catch (Throwable ignored) {  }
            }
        } finally {
            MacroLifecycle.finishCurrentRun();
            MacroExecutor.CURRENT_RUN.remove();
        }
    }

    private static void runInternal(MacroExecutor.RunState runState) {
        MacroExecutor.CURRENT_RUN.set(runState);
        MacroVariables.clear();
        AutismMacro macro = runState.macro;
        Minecraft mc = Minecraft.getInstance();
        int loopCount = 0;
        int maxLoops = macro.loop ? (macro.loopCount == -1 ? Integer.MAX_VALUE : macro.loopCount) : 1;

        MacroLifecycle.currentPersistentActions().clear();
        MacroLifecycle.currentBackgroundMoves().clear();
        Thread persistentThread = new Thread(() -> {
            MacroExecutor.CURRENT_RUN.set(runState);
            try {
                while (MacroLifecycle.isCurrentRunActive()) {
                    if (!MacroLifecycle.currentPersistentActions().isEmpty() || !MacroLifecycle.currentBackgroundMoves().isEmpty()) {
                        MacroExecutor.RunState scheduledRun = MacroExecutor.CURRENT_RUN.get();
                        mc.execute(() -> MacroLifecycle.withRunContext(scheduledRun, () -> {
                            ReentrantLock inputLock = MacroExecutor.SHARED_RESOURCE_LOCKS.get(SharedClientResource.INPUT);
                            inputLock.lock();
                            try {

                            for (MacroAction pa : MacroLifecycle.currentPersistentActions()) {
                                try { pa.execute(mc); } catch (Exception ignored) {  }
                            }

                            java.util.Iterator<java.util.Map.Entry<MoveAction, int[]>> it = MacroLifecycle.currentBackgroundMoves().entrySet().iterator();
                            while (it.hasNext()) {
                                java.util.Map.Entry<MoveAction, int[]> entry = it.next();
                                MoveAction bma = entry.getKey();
                                int[] ticks = entry.getValue();
                                if (ticks[0] > 0) {
                                    bma.execute(mc);
                                    ticks[0]--;
                                } else {
                                    MacroLifecycle.releaseMoveIfUnused(scheduledRun, bma, mc);
                                    MacroLifecycle.currentBackgroundMoves().remove(bma);
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
                        if (!MacroLifecycle.isCurrentRunActive()) break;
                    }
                }
            } finally {
                MacroLifecycle.releaseBackgroundMoves(MacroLifecycle.currentBackgroundMoves(), mc, runState);
                MacroLifecycle.currentBackgroundMoves().clear();
                MacroExecutor.CURRENT_RUN.remove();
            }
        }, "Autism-Macro-Persistent-" + runState.id);
        persistentThread.setDaemon(true);
        persistentThread.start();

        try {
            while (MacroLifecycle.isCurrentRunActive() && loopCount < maxLoops) {
                if (macro.actions.isEmpty()) {
                    if (!runState.options.silentLifecycle()) AutismClientMessaging.sendPrefixed("\u00a7cMacro stopped: no actions left.");
                    MacroLifecycle.stopCurrentRun();
                    break;
                }
                MacroLifecycle.setCurrentStatus("Loop " + (loopCount + 1) + (maxLoops == Integer.MAX_VALUE ? "" : "/" + maxLoops));

                AutismModule module = AutismModule.get();
                boolean burstMode = module.usePacketBurstMode();

                MacroExecutor.totalSteps = macro.actions.size();

                runState.branchElseStart = -1;
                runState.branchElseCount = 0;
                runState.jumpBudget = 0;

                runState.lastCompletedStep = 0;
                for (int i = 0; i < macro.actions.size(); i++) {
                    if (!MacroLifecycle.isCurrentRunActive()) break;

                    MacroLifecycle.setCurrentStep(i, macro.actions.size());

                    MacroLifecycle.updateStepBoundOverrides(runState, i);
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
                            int newI = MacroLifecycle.performFlowJump(runState, macro, i, flow, mc);
                            if (newI == Integer.MIN_VALUE) {
                                MacroLifecycle.broadcastStepProgress(runState, i + 1, macro.actions.size(), macro.name);
                                MacroLifecycle.stopCurrentRun();
                                break;
                            }
                            i = newI;
                        }
                        continue;
                    }

                    MacroPrearm.prearmNextActionWait(mc, macro.actions, i);

                    EnumSet<SharedClientResource> heldResources = MacroLifecycle.acquireClientResources(action);
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

                        MacroRender.executeBurstPackets(mc, packetBatch);

                        i = batchEnd - 1;
                        continue;
                    }

                    if (action instanceof DelayAction) {
                        DelayAction da = (DelayAction) action;
                        if (da.useTicks) {

                            for (int t = 0; t < da.delayTicks; t++) {
                                if (!MacroLifecycle.isCurrentRunActive()) break;

                                CompletableFuture<Void> future = MacroConditionRegistry.waitForNextTick();
                                waitForCondition(future);
                            }
                        } else {

                            Thread.sleep(da.delayMs);
                        }
                    }
                    else if (action instanceof CustomMenuAction customMenu) {
                        MacroLifecycle.setCurrentStatus(customMenu.getDisplayName());
                        customMenu.execute(mc);
                    }
                    else if (action instanceof FpsAction fps) {
                        MacroLifecycle.setCurrentStatus(fps.getDisplayName());
                        if (fps.endMode() == FpsAction.EndMode.TIMEOUT) {

                            MacroLifecycle.clearFpsStepOverride(runState);
                            long fpsDurationNanos = fps.durationMillis() * 1_000_000L;
                            long fpsDeadline = System.nanoTime() + fpsDurationNanos;
                            FpsLimitController.apply(runState.id, Math.max(0, fps.maxFps), fpsDurationNanos);
                            if (fps.continueNextActions) {

                                runState.fpsBackgroundDeadlineNanos = fpsDeadline;
                            } else {

                                long fpsRemaining;
                                while (MacroLifecycle.isCurrentRunActive() && (fpsRemaining = fpsDeadline - System.nanoTime()) > 0L) {
                                    Thread.sleep(Math.min(25L, fpsRemaining / 1_000_000L + 1L));
                                }
                                FpsLimitController.clear(runState.id);
                                runState.fpsBackgroundDeadlineNanos = 0L;
                            }
                        } else {

                            int covered = fps.endMode() == FpsAction.EndMode.END
                                    ? macro.actions.size() - 1 - i
                                    : fps.normalizedStepCount();
                            int endIndex = MacroLifecycle.stepOverrideEndIndex(macro, i, covered);

                            MacroLifecycle.armFpsStepOverride(runState, i, endIndex, Math.max(1, fps.maxFps));
                            runState.fpsBackgroundDeadlineNanos = 0L;
                        }

                    }
                    else if (action instanceof SPingAction sping) {
                        MacroLifecycle.setCurrentStatus(sping.getDisplayName());
                        if (sping.endMode() == SPingAction.EndMode.TIMEOUT) {
                            MacroLifecycle.clearPingStepOverride(runState);
                            long spingDurationNanos = sping.durationMillis() * 1_000_000L;
                            long spingDeadline = System.nanoTime() + spingDurationNanos;
                            PingSpoofController.apply(runState.id, Math.max(0, sping.pingDelayMs),
                                sping.realIncoming, sping.realOutgoing, spingDurationNanos);
                            if (sping.continueNextActions) {

                                runState.pingBackgroundDeadlineNanos = spingDeadline;
                            } else {

                                long spingRemaining;
                                while (MacroLifecycle.isCurrentRunActive() && (spingRemaining = spingDeadline - System.nanoTime()) > 0L) {
                                    Thread.sleep(Math.min(25L, spingRemaining / 1_000_000L + 1L));
                                }
                                PingSpoofController.clearMacro(runState.id);
                                runState.pingBackgroundDeadlineNanos = 0L;
                            }
                        } else {
                            int covered = sping.endMode() == SPingAction.EndMode.END
                                    ? macro.actions.size() - 1 - i
                                    : sping.normalizedStepCount();
                            int endIndex = MacroLifecycle.stepOverrideEndIndex(macro, i, covered);
                            MacroLifecycle.armPingStepOverride(runState, i, endIndex,
                                    Math.max(0, sping.pingDelayMs), sping.realIncoming, sping.realOutgoing);
                            runState.pingBackgroundDeadlineNanos = 0L;
                        }

                    }
                    else if (action instanceof WaitForPacketAction packetAction) {
                        packetAction.matchedPacket = null;
                        packetAction.matchedDirection = "";
                        MacroExecutor.RunState packetRun = MacroExecutor.CURRENT_RUN.get();
                        if (packetRun != null) {
                            packetRun.lastMatchedPacket = null;
                            packetRun.lastMatchedPacketDirection = "";
                        }
                        java.util.List<String> targets = packetAction.effectiveList();
                        MacroExecutor.PrearmedWait prearmed = MacroPrearm.consumePrearmedWait(action);
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
                                if (!MacroLifecycle.isCurrentRunActive()) break;
                                MacroPacketWait.awaitPacket(targets.get(targetIndex));
                            }
                        }
                        MacroPrearm.copyLastMatchedPacket(packetAction);
                    }
                    else if (action instanceof WaitPacketMatchAction wpma) {
                        MacroLifecycle.setCurrentStatus(wpma.getDisplayName());
                        if (!MacroPrearm.waitForPrearmed(wpma, wpma.timeoutMs)) MacroPrearm.waitForPacketMatch(wpma);
                    }
                    else if (action instanceof WaitInventoryPredicateAction wipa) {
                        MacroLifecycle.setCurrentStatus(wipa.getDisplayName());
                        if (!MacroPrearm.waitForPrearmed(wipa, wipa.timeoutMs)) waitForPredicate(() -> wipa.matches(mc), wipa.timeoutMs);
                    }
                    else if (action instanceof WaitDurabilityAction wda) {
                        MacroLifecycle.setCurrentStatus(wda.getDisplayName());
                        if (wda.useNext) MacroDurability.waitForDurabilityUseNext(mc, wda);
                        else if (!MacroPrearm.waitForPrearmed(wda, wda.timeoutMs)) waitForPredicate(() -> wda.matches(mc), wda.timeoutMs);
                    }
                    else if (action instanceof WaitFreeSlotsAction wfsa) {
                        MacroLifecycle.setCurrentStatus(wfsa.getDisplayName());
                        if (!MacroPrearm.waitForPrearmed(wfsa, wfsa.timeoutMs)) waitForPredicate(() -> wfsa.matches(mc), wfsa.timeoutMs);
                    }
                    else if (action instanceof WaitEntityTargetAction weta) {
                        MacroLifecycle.setCurrentStatus(weta.getDisplayName());
                        if (!MacroPrearm.waitForPrearmed(weta, weta.timeoutMs)) waitForPredicate(() -> weta.matches(mc), weta.timeoutMs);
                    }
                    else if (action instanceof WaitGuiTypeAction wgta) {
                        MacroLifecycle.setCurrentStatus(wgta.getDisplayName());
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
                        MacroLifecycle.setCurrentStatus(wh.waitingStatusText());
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
                        MacroLifecycle.setCurrentStatus(wba.getDisplayName());
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
                            MacroLifecycle.setCurrentStatus("Waiting for GUI close: " + wga.guiTitle);
                            waitForPredicate(guiClosePredicate(wga, mc), wga.timeoutMs);
                            MacroPrearm.commitStagedCapture(wga);
                        } else {
                            MacroLifecycle.setCurrentStatus("Waiting for GUI: " + wga.guiTitle);
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
                        ItemTarget cooldownTarget = MacroWorldActions.resolveItemTarget(wca.itemTarget, wca.itemName).resolveTemplate(mc);
                        if (cooldownTarget == null) continue;
                        String cooldownLabel = MacroWorldActions.describeItemTarget(cooldownTarget);
                        MacroLifecycle.setCurrentStatus("Waiting for cooldown: " +
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
                        MacroLifecycle.setCurrentStatus("Waiting for Pos: " + String.format("%.0f, %.0f, %.0f", wpa.x, wpa.y, wpa.z));
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
                            for (int t = 0; t < ja.durationTicks && MacroLifecycle.isCurrentRunActive(); t++) {
                                CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                                waitForCondition(tickFuture);
                            }
                        }
                        mc.execute(() -> ja.release(mc));
                    }
                    else if (action instanceof SneakAction) {
                        SneakAction sna = (SneakAction) action;
                        if (sna.persistent && sna.sneak) {
                            MacroLifecycle.currentPersistentActions().removeIf(p -> p instanceof SneakAction);
                            MacroLifecycle.currentPersistentActions().add(sna);
                        } else if (!sna.sneak) {
                            MacroLifecycle.currentPersistentActions().removeIf(p -> p instanceof SneakAction);
                        }
                        if (sna.persistent || !sna.sneak) MacroClientThread.postOnClientThread(mc, () -> MacroLifecycle.applyAggregateSneakState(mc), sna.isTickAligned());
                        else MacroClientThread.postOnClientThread(mc, () -> sna.execute(mc), sna.isTickAligned());
                    }
                    else if (action instanceof SprintAction) {
                        SprintAction spa = (SprintAction) action;
                        if (spa.persistent && spa.sprint) {
                            MacroLifecycle.currentPersistentActions().removeIf(p -> p instanceof SprintAction);
                            MacroLifecycle.currentPersistentActions().add(spa);
                        } else if (!spa.sprint) {
                            MacroLifecycle.currentPersistentActions().removeIf(p -> p instanceof SprintAction);
                        }
                        if (spa.persistent || !spa.sprint) MacroClientThread.postOnClientThread(mc, () -> MacroLifecycle.applyAggregateSprintState(mc), spa.isTickAligned());
                        else MacroClientThread.postOnClientThread(mc, () -> spa.execute(mc), spa.isTickAligned());
                    }
                    else if (action instanceof DisconnectAction) {
                        DisconnectAction da2 = (DisconnectAction) action;

                        MacroLifecycle.broadcastStepProgress(runState, i + 1, macro.actions.size(), macro.name);
                        if (da2.mode == DisconnectAction.DisconnectMode.DISCONNECT) {
                            if (da2.delayMs > 0) Thread.sleep(da2.delayMs);
                            mc.execute(() -> da2.execute(mc));
                        } else if (da2.mode == DisconnectAction.DisconnectMode.AUTO_DISCONNECT) {

                            MacroLifecycle.setCurrentStatus("Auto Disconnect: waiting for " + da2.trigger.name() + "...");
                            da2.execute(mc);
                            MacroLifecycle.setCurrentStatus("Auto Disconnect: executed");
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
                        MacroLifecycle.stopCurrentRun();
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
                            if (!MacroLifecycle.isCurrentRunActive()) break;
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
                        MacroWorldActions.runGoToAction(mc, ga);
                    }

                    else if (action instanceof MoveAction) {
                        MoveAction ma = (MoveAction) action;
                        if (ma.nonBlocking) {

                            mc.execute(() -> ma.execute(mc));
                            MacroLifecycle.currentBackgroundMoves().put(ma, new int[]{ma.durationTicks - 1});

                        } else {

                            for (int t = 0; t < ma.durationTicks && MacroLifecycle.isCurrentRunActive(); t++) {
                                mc.execute(() -> ma.execute(mc));
                                CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                                waitForCondition(tickFuture);
                            }
                            MacroExecutor.RunState movementRun = MacroExecutor.CURRENT_RUN.get();
                            mc.execute(() -> MacroLifecycle.releaseMoveIfUnused(movementRun, ma, mc));
                        }
                    }
                    else if (action instanceof LookAtBlockAction) {
                        LookAtBlockAction la = (LookAtBlockAction) action;
                        LookAtBlockAction.RotationTarget rotationTarget = MacroRender.resolveLookAtTargetOnClient(mc, la);
                        if (rotationTarget == null) {
                            continue;
                        }
                        if (la.smooth) {
                            MacroRender.startSmoothRotation(rotationTarget.yaw(), rotationTarget.pitch(), la.getRotationStep());
                            if (la.waitForCompletion) {
                                MacroRender.waitForSmoothRotationCompletion();
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

                        for (int rep = 0; rep < ra.repeatCount && MacroLifecycle.isCurrentRunActive(); rep++) {
                            MacroLifecycle.setCurrentStatus("Repeat " + (rep + 1) + "/" + ra.repeatCount);
                            for (int j = startIdx; j < endIdx && MacroLifecycle.isCurrentRunActive(); j++) {
                                MacroAction subAction = macro.actions.get(j);
                                if (!subAction.isEnabled()) continue;

                                MacroExecutor.REPEAT_PACKET_CONTEXT.set(new MacroExecutor.RepeatPacketContext(macro, j + 1, endIdx));
                                MacroExecutor.REPEAT_PACKET_SKIP.set(0);
                                try {
                                    MacroDispatcher.executeSingleActionWithWaits(mc, subAction, module);
                                    j += Math.max(0, MacroExecutor.REPEAT_PACKET_SKIP.get());
                                } finally {
                                    MacroExecutor.REPEAT_PACKET_SKIP.set(0);
                                    MacroExecutor.REPEAT_PACKET_CONTEXT.remove();
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
                        MacroLifecycle.setCurrentStatus("Waiting for chat: " + wca.pattern);
                        if (!MacroPrearm.waitForPrearmed(wca, wca.timeoutMs)) MacroPacketWait.awaitChat(wca);
                    }
                    else if (action instanceof WaitForEntityAction) {
                        WaitForEntityAction wea = (WaitForEntityAction) action;
                        MacroLifecycle.setCurrentStatus(wea.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(wea, 0)) {
                                waitForPredicate(() -> wea.matchesEntityTarget(mc), wea.timeoutMs);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForSoundAction) {
                        WaitForSoundAction wsa = (WaitForSoundAction) action;
                        String sndDesc = wsa.soundIds.isEmpty() ? "any" : wsa.soundIds.get(0);
                        MacroLifecycle.setCurrentStatus("Waiting for sound: " + sndDesc);
                        try {
                            if (!MacroPrearm.waitForPrearmed(wsa, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForSound(wsa);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof MineAction) {
                        MineAction mna = (MineAction) action;
                        MacroWorldActions.runMineAction(mc, mna);
                    }
                    else if (action instanceof InstaBreakAction) {
                        MacroBreak.runInstaBreakAction(mc, (InstaBreakAction) action);
                    }
                    else if (action instanceof BreakAction breakAction) {
                        MacroExecutor.InlineNextActions nextActions = MacroExecutor.InlineNextActions.empty();
                        if (breakAction.interact && breakAction.runNextSteps) {
                            nextActions = MacroLifecycle.collectInlineNextActions(macro.actions, i);
                            breakAction.setNextActions(nextActions.actions());
                        }
                        MacroBreak.runBreakAction(mc, breakAction);
                        if (breakAction.didRunNextActions() && nextActions.lastIndex() > i) i = nextActions.lastIndex();
                    }

                    else if (action instanceof WaitForSlotChangeAction) {
                        WaitForSlotChangeAction wsca = (WaitForSlotChangeAction) action;
                        MacroLifecycle.setCurrentStatus(wsca.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(wsca, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForSlotChange(wsca);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForWorldChangeAction world) {
                        MacroLifecycle.setCurrentStatus(world.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(world, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForWorldChange(world);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForPositionDeltaAction wpda) {
                        MacroLifecycle.setCurrentStatus(wpda.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(wpda, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForPositionDelta(wpda.distance, wpda.horizontalOnly);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForTeleportAction teleport) {
                        MacroLifecycle.setCurrentStatus(teleport.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(teleport, 0)) {
                                CompletableFuture<?> future = MacroRace.raceConditionFuture(mc, teleport);
                                MacroRace.waitForFutureDone(future, 0);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitGamemodeChangeAction gm) {
                        MacroLifecycle.setCurrentStatus(gm.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(gm, gm.timeoutMs)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForGamemodeChange(gm);
                                MacroRace.waitForFutureDone(future, gm.timeoutMs);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitMovementAction wm) {
                        MacroLifecycle.setCurrentStatus(wm.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(wm, 0)) {
                                CompletableFuture<?> future = MacroRace.raceConditionFuture(mc, wm.resolveSubAction());
                                MacroRace.waitForFutureDone(future, 0);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForLanStepAction) {
                        WaitForLanStepAction wla = (WaitForLanStepAction) action;
                        MacroLifecycle.setCurrentStatus(wla.getDisplayName());
                        try {
                            if (!MacroPrearm.waitForPrearmed(wla, 0)) {
                                CompletableFuture<Void> future = MacroConditionRegistry.waitForLanStep(wla);
                                waitForCondition(future);
                            }
                        } catch (CancellationException e) {  }
                    }
                    else if (action instanceof WaitForMacroStepAction) {
                        WaitForMacroStepAction wma = (WaitForMacroStepAction) action;
                        MacroLifecycle.setCurrentStatus(wma.getDisplayName());
                        if (!MacroPrearm.waitForPrearmed(wma, wma.timeoutMs)) MacroRace.waitForMacroStep(wma);
                    }
                    else if (action instanceof StopMacroAction) {
                        MacroLifecycle.setCurrentStatus("Stopping Macro");
                        StopMacroAction stopAction = (StopMacroAction) action;
                        if (stopAction.target != StopMacroAction.StopTarget.SELF) {
                            stopAction.execute(mc);
                            break;
                        }

                        MacroLifecycle.broadcastStepProgress(runState, i + 1, macro.actions.size(), macro.name);
                        MacroLifecycle.stopCurrentRun();
                        break;
                    }

                    else if (action instanceof TickSyncAction) {
                        TickSyncAction tsa = (TickSyncAction) action;

                        if (mc.level == null) continue;

                        java.util.List<net.minecraft.network.protocol.Packet<?>> preGenerated =
                            MacroRender.preGeneratePackets(macro, i + 1, tsa.preGenCount);

                        long targetTick = mc.level.getGameTime() + tsa.tickOffset;
                        MacroLifecycle.setCurrentStatus("Tick Sync -> " + targetTick + " (" + preGenerated.size() + " pkts)");

                        while (MacroLifecycle.isCurrentRunActive() && mc.level.getGameTime() < targetTick) {
                            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
                        }

                        if (MacroLifecycle.isCurrentRunActive()) {
                            MacroRender.executePreGeneratedBurst(mc, preGenerated);
                            i += MacroRender.countPreGeneratedActions(macro, i + 1, preGenerated.size());
                            AutismClientMessaging.sendPrefixed("Tick sync: " + preGenerated.size() + " pkts sent");
                        }
                    }
                    else if (action instanceof RevisionSyncAction) {
                        RevisionSyncAction rsa = (RevisionSyncAction) action;

                        if (mc.player == null || mc.player.containerMenu == null) continue;

                        java.util.List<net.minecraft.network.protocol.Packet<?>> preGenerated =
                            MacroRender.preGeneratePackets(macro, i + 1, rsa.preGenCount);

                        int baseRevision = mc.player.containerMenu.getStateId();
                        int targetRevision = baseRevision + rsa.revisionOffset;
                        MacroLifecycle.setCurrentStatus("Rev Sync -> " + targetRevision + " (" + preGenerated.size() + " pkts)");

                        while (MacroLifecycle.isCurrentRunActive()) {
                            if (mc.player == null || mc.player.containerMenu == null) break;
                            if (mc.player.containerMenu.getStateId() >= targetRevision) break;
                            java.util.concurrent.locks.LockSupport.parkNanos(500_000L);
                        }

                        if (MacroLifecycle.isCurrentRunActive() && mc.player != null && mc.player.containerMenu != null) {
                            MacroRender.executePreGeneratedBurst(mc, preGenerated);
                            i += MacroRender.countPreGeneratedActions(macro, i + 1, preGenerated.size());
                            AutismClientMessaging.sendPrefixed("Rev sync: " + preGenerated.size() + " pkts sent");
                        }
                    }
                    else if (action instanceof ServerTickSyncAction) {
                        ServerTickSyncAction stsa = (ServerTickSyncAction) action;

                        if (mc.getConnection() == null) continue;

                        java.util.List<net.minecraft.network.protocol.Packet<?>> preGenerated =
                            MacroRender.preGeneratePackets(macro, i + 1, stsa.preGenCount);

                        int sampleWaitMs = 0;
                        while (MacroLifecycle.isCurrentRunActive() && !ServerTickTracker.isReady() && sampleWaitMs < stsa.maxWaitMs) {

                            float progress = ServerTickTracker.getWarmupProgress();
                            MacroLifecycle.setCurrentStatus(String.format("ServerSync warmup %.0f%% (%d samples, %dms)",
                                progress * 100, ServerTickTracker.getSampleCount(), ServerTickTracker.getTrackingTimeMs()));
                            Thread.sleep(50);
                            sampleWaitMs += 50;
                        }

                        if (!MacroLifecycle.isCurrentRunActive()) continue;

                        long optimalTime = ServerTickTracker.getOptimalSendTime(stsa.bufferMs, stsa.ignorePing);
                        long msUntil = (optimalTime - System.nanoTime()) / 1_000_000L;
                        int ping = ServerTickTracker.getPingMs();
                        String pingStr = stsa.ignorePing ? " (no ping)" : " ping:" + ping + "ms";
                        MacroLifecycle.setCurrentStatus("ServerSync in " + Math.max(0, msUntil) + "ms" + pingStr + " (" + preGenerated.size() + " pkts)");

                        long startWait = System.nanoTime();
                        long maxWaitNanos = stsa.maxWaitMs * 1_000_000L;

                        while (MacroLifecycle.isCurrentRunActive()) {
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

                        if (MacroLifecycle.isCurrentRunActive()) {
                            MacroRender.executePreGeneratedBurst(mc, preGenerated);
                            i += MacroRender.countPreGeneratedActions(macro, i + 1, preGenerated.size());
                            long actualDelay = (System.nanoTime() - optimalTime) / 1_000_000L;
                            AutismClientMessaging.sendPrefixed("ServerSync: " + preGenerated.size() + " pkts (+" + actualDelay + "ms)");
                        }
                    }
                    else if (action instanceof RotateAction) {
                        RotateAction ra = (RotateAction) action;
                        if (ra.smooth) {
                            MacroRender.startSmoothRotation(ra.yaw, ra.pitch, ra.getRotationStep());
                            if (ra.waitForCompletion) {
                                MacroRender.waitForSmoothRotationCompletion();
                            }
                        } else {
                            mc.execute(() -> action.execute(mc));
                        }
                    }
                    else if (action instanceof PacedTpAction pacedTp) {
                        runPacedTpAction(mc, pacedTp);
                    }
                    else if (action instanceof LookAtBlockAction la) {
                        LookAtBlockAction.RotationTarget rotationTarget = MacroRender.resolveLookAtTargetOnClient(mc, la);
                        if (rotationTarget == null) {
                            continue;
                        }
                        if (la.smooth) {
                            MacroRender.startSmoothRotation(rotationTarget.yaw(), rotationTarget.pitch(), la.getRotationStep());
                            if (la.waitForCompletion) {
                                MacroRender.waitForSmoothRotationCompletion();
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
                                MacroWorldActions.waitForItemActionTargets(mc, ia);
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
                        MacroWorldActions.runCraftAction(mc, craftAction);
                    }
                    else if (action instanceof SendPacketAction) {

                        if (module != null && module.useInstantExecutionMode()) {

                            try {
                                action.execute(mc);
                            } catch (Exception e) {
                                AutismClientMessaging.sendPrefixed("\u00a7cError in SendPacket: " + e.getMessage());
                            }
                        } else {
                            MacroClientThread.runOnClientThread(mc, () -> {
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
                        MacroLifecycle.setCurrentStatus((sia.mode == autismclient.util.macro.StoreItemAction.Mode.LOOT ? "Looting" : "Storing")
                            + (sia.persistent ? " \u221e" : ""));
                        if (sia.persistent) {

                            while (MacroLifecycle.isCurrentRunActive()) {
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
                        MacroWorldActions.runPayAction(mc, (PayAction) action);
                    }
                    else if (action instanceof XCarryAction xsa) {
                        if (xsa.mode == XCarryAction.Mode.PUT_IN
                                && (xsa.transferMode == XCarryAction.TransferMode.CLICK
                                    || xsa.transferMode == XCarryAction.TransferMode.SAFE_CLICK)) {
                            if (xsa.transferMode == XCarryAction.TransferMode.SAFE_CLICK) {
                                MacroLifecycle.setCurrentStatus("XCarry Safe");
                                MacroXCarry.runXCarrySafeClickPutIn(mc, xsa);
                            } else {
                                MacroLifecycle.setCurrentStatus("XCarry Click");
                                MacroXCarry.runXCarryClickPutIn(mc, xsa);
                            }
                        } else {
                            MacroLifecycle.setCurrentStatus(switch (xsa.mode) {
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
                    }
                    else if (action instanceof InventoryAuditAction auditAction
                            && (auditAction.mode == InventoryAuditAction.Mode.DUPE
                                || auditAction.mode == InventoryAuditAction.Mode.DUPE_SPAM)) {
                        MacroLifecycle.setCurrentStatus("Dupe: " + auditAction.dupeVector.name() + " " + auditAction.openMode.name());
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
                        MacroLifecycle.setCurrentStatus("Bundle Dupe V2");
                        bundleV2.execute(mc);
                    }
                    else if (action instanceof RollbackAction rollback) {
                        MacroLifecycle.setCurrentStatus("Rollback");
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

                        MacroWorldActions.runPlaceAction(mc, placeInteract);
                    }
                    else if (action instanceof PlaceAction place && place.waitForItem) {

                        ItemTarget placeTarget = place.resolvedItemTarget().resolveTemplate(mc);
                        if (placeTarget == null) {
                            postGuiFuture = null;
                        } else {
                        if (placeTarget.hasIdentity() || placeTarget.hasSlot()) {
                            MacroLifecycle.setCurrentStatus("Waiting for " + MacroWorldActions.describeItemTarget(placeTarget));
                            try {
                                MacroWorldActions.waitForItemActionEntry(mc, placeTarget);
                            } catch (java.util.concurrent.CancellationException e) {  }
                        }
                        if (MacroLifecycle.isCurrentRunActive()) {
                            final PlaceAction fPlace = place;
                            MacroClientThread.postOnClientThread(mc, () -> {
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
                            MacroExecutor.RunState scheduledRun = MacroExecutor.CURRENT_RUN.get();
                            mc.execute(() -> MacroLifecycle.withRunContext(scheduledRun, () -> {
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

                    if (postGuiFuture != null && MacroLifecycle.isCurrentRunActive()) {
                        try {
                            waitForCondition(postGuiFuture);
                        } catch (java.util.concurrent.CancellationException e) {  }
                    }

                    if (MacroLifecycle.isCurrentRunActive()) {
                        MacroLifecycle.markStepCompleted(i + 1);
                        MacroLifecycle.broadcastStepProgress(runState, i + 1, macro.actions.size(), macro.name);
                    }

                    if (MacroLifecycle.isCurrentRunActive() && !MacroActionRunner.isInstantInventoryAction(action)) {
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
                        MacroLifecycle.releaseClientResources(heldResources);
                    }
                }

                if (macro.actions.isEmpty()) {
                    if (!runState.options.silentLifecycle()) AutismClientMessaging.sendPrefixed("\u00a7cMacro stopped: no actions left.");
                    MacroLifecycle.stopCurrentRun();
                    break;
                }

                if (MacroLifecycle.isCurrentRunActive()) {
                    PacketGateManager.clearOwnerAndFlushConfigured(runState.id, mc == null ? null : mc.getConnection());
                }

                if (macro.loop && loopCount < maxLoops - 1 && MacroLifecycle.isCurrentRunActive()) {
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

            MacroLifecycle.awaitBackgroundTimedOverrides(runState);

            if (MacroLifecycle.isCurrentRunActive()) {
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
            MacroLifecycle.finishCurrentRun();
            MacroRender.cancelRotation(runState);
            if (!MacroExecutor.isRunning.get()) MacroConditionRegistry.cancelAll();

            MacroLifecycle.broadcastStepProgress(runState, -1, 0, "");
        }
    }

    public static void waitForCondition(CompletableFuture<Void> future) {

        Thread t = Thread.currentThread();
        future.whenComplete((v, e) -> java.util.concurrent.locks.LockSupport.unpark(t));
        while (!future.isDone() && MacroLifecycle.isCurrentRunActive()) {

            java.util.concurrent.locks.LockSupport.parkNanos(MacroExecutor.MACRO_WAIT_POLL_NANOS);
            if (Thread.interrupted()) break;
        }
        if (!MacroLifecycle.isCurrentRunActive()) {
            future.cancel(true);
        }
    }

    private static void runPacedTpAction(Minecraft mc, PacedTpAction action) throws InterruptedException {
        AtomicReference<autismclient.util.multi.PacketTeleportController.MacroHandle> handleRef = new AtomicReference<>();
        MacroClientThread.runOnClientThread(mc, () -> handleRef.set(
            autismclient.util.multi.PacketTeleportController.startMacro(action)));
        autismclient.util.multi.PacketTeleportController.MacroHandle handle = handleRef.get();
        if (handle == null) return;
        MacroLifecycle.setCurrentStatus("TP " + action.commandArguments());
        try {
            while (MacroLifecycle.isCurrentRunActive() && !handle.completion().isDone()) MacroExecutor.waitOneTick();
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
        if (!MacroLifecycle.isCurrentRunActive()) return;
        CompletableFuture<Void> next = new CompletableFuture<>();
        MAIN_DRAIN_BARRIER.set(next);
        try {
            mc.execute(() -> next.complete(null));
        } catch (Throwable ignored) {
            next.complete(null);
        }
    }

    public static void waitForPredicate(java.util.function.BooleanSupplier predicate, int timeoutMs) {
        long start = System.currentTimeMillis();
        while (MacroLifecycle.isCurrentRunActive()) {
            try {
                if (predicate.getAsBoolean()) return;
            } catch (Throwable ignored) {  }
            if (timeoutMs > 0 && System.currentTimeMillis() - start >= timeoutMs) return;
            CompletableFuture<Void> tick = MacroConditionRegistry.waitForNextTick();
            waitForCondition(tick);
        }
    }

    public static java.util.function.BooleanSupplier guiClosePredicate(WaitForGuiAction action, Minecraft mc) {
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

    public static java.util.function.BooleanSupplier guiClosePredicate(WaitGuiTypeAction action, Minecraft mc) {
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
}
