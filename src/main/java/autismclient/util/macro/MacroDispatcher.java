package autismclient.util.macro;

import autismclient.modules.AutismModule;
import autismclient.util.AutismClientMessaging;
import net.minecraft.client.Minecraft;
import autismclient.util.macro.MacroActionResources.SharedClientResource;

import java.util.EnumSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class MacroDispatcher {
    private MacroDispatcher() {
    }
    static void executeSingleActionWithWaits(Minecraft mc, MacroAction action, AutismModule module) throws InterruptedException {
        if (!MacroDynamicBindings.apply(action, mc)) return;
        if (!MacroActionRunner.dynamicTextInputsAvailable(action, mc)) return;
        MacroPrearm.clearOwnCaptureOutput(action);
        EnumSet<SharedClientResource> heldResources = MacroLifecycle.acquireClientResources(action);
        try {

        if (!MacroActionRunner.waitForGuiBeforeAction(mc, action)) return;
        MacroActionRunner.beginRaycastAim(mc, action);
        java.util.concurrent.CompletableFuture<Void> postGuiFuture =
                (action instanceof ItemAction || action instanceof DropAction || MacroActionRunner.isTargetWaitInteractionAction(action)) ? null : MacroActionRunner.createPostGuiFuture(mc, action);

        if (action instanceof DelayAction) {
            DelayAction da = (DelayAction) action;
            if (da.useTicks) {
                for (int t = 0; t < da.delayTicks && MacroLifecycle.isCurrentRunActive(); t++) {
                    CompletableFuture<Void> tf = MacroConditionRegistry.waitForNextTick();
                    MacroRunLoop.waitForCondition(tf);
                }
            } else {
                Thread.sleep(da.delayMs);
            }
        } else if (action instanceof CustomMenuAction customMenu) {
            MacroLifecycle.setCurrentStatus(customMenu.getDisplayName());
            customMenu.execute(mc);
        } else if (action instanceof WaitForPacketAction packetAction) {
            packetAction.matchedPacket = null;
            packetAction.matchedDirection = "";
            MacroExecutor.RunState packetRun = MacroExecutor.CURRENT_RUN.get();
            if (packetRun != null) {
                packetRun.lastMatchedPacket = null;
                packetRun.lastMatchedPacketDirection = "";
            }
            java.util.List<String> targets = packetAction.effectiveList();
            if (targets.isEmpty()) {
                MacroPacketWait.awaitPacket("");
            } else {
                for (String target : targets) {
                    if (!MacroLifecycle.isCurrentRunActive()) break;
                    MacroPacketWait.awaitPacket(target);
                }
            }
            MacroPrearm.copyLastMatchedPacket(packetAction);
        } else if (action instanceof WaitPacketMatchAction wpma) {
            MacroLifecycle.setCurrentStatus(wpma.getDisplayName());
            MacroPrearm.waitForPacketMatch(wpma);
        } else if (action instanceof WaitInventoryPredicateAction wipa) {
            MacroLifecycle.setCurrentStatus(wipa.getDisplayName());
            MacroRunLoop.waitForPredicate(() -> wipa.matches(mc), wipa.timeoutMs);
        } else if (action instanceof WaitDurabilityAction wda) {
            MacroLifecycle.setCurrentStatus(wda.getDisplayName());
            if (wda.useNext) MacroDurability.waitForDurabilityUseNext(mc, wda);
            else MacroRunLoop.waitForPredicate(() -> wda.matches(mc), wda.timeoutMs);
        } else if (action instanceof WaitFreeSlotsAction wfsa) {
            MacroLifecycle.setCurrentStatus(wfsa.getDisplayName());
            MacroRunLoop.waitForPredicate(() -> wfsa.matches(mc), wfsa.timeoutMs);
        } else if (action instanceof WaitEntityTargetAction weta) {
            MacroLifecycle.setCurrentStatus(weta.getDisplayName());
            MacroRunLoop.waitForPredicate(() -> weta.matches(mc), weta.timeoutMs);
        } else if (action instanceof WaitGuiTypeAction wgta) {
            MacroLifecycle.setCurrentStatus(wgta.getDisplayName());
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
            MacroLifecycle.setCurrentStatus(wh.waitingStatusText());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForHealth(wh.healthThreshold, wh.below);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForBlockAction) {
            WaitForBlockAction wba = (WaitForBlockAction) action;
            MacroLifecycle.setCurrentStatus(wba.getDisplayName());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForBlock(wba);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForGuiAction) {
            WaitForGuiAction wga = (WaitForGuiAction) action;
            MacroPrearm.clearCaptureOutputs(wga);
            if (wga.waitMode == WaitForGuiAction.WaitMode.CLOSE) {
                MacroLifecycle.setCurrentStatus("Waiting for GUI close: " + wga.guiTitle);
                MacroRunLoop.waitForPredicate(MacroRunLoop.guiClosePredicate(wga, mc), wga.timeoutMs);
            } else {
                MacroLifecycle.setCurrentStatus("Waiting for GUI: " + wga.guiTitle);
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
            MacroLifecycle.setCurrentStatus("Waiting for cooldown: " +
                (!cooldownLabel.isEmpty() ? cooldownLabel : (wca.checkMainInteractionHand ? "Main Hand" : "Off Hand")));
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForCooldown(cooldownTarget, wca.checkMainInteractionHand);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitPosAction) {
            WaitPosAction wpa = (WaitPosAction) action;
            MacroLifecycle.setCurrentStatus("Waiting for Pos: " + String.format("%.0f, %.0f, %.0f", wpa.x, wpa.y, wpa.z));
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForPos(
                    wpa.x, wpa.y, wpa.z, wpa.leeway, wpa.checkRotation, wpa.yaw, wpa.pitch, wpa.rotLeeway);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof CaptureValueAction captureValue) {
            MacroCaptureAutofill.runCaptureValueAction(mc, captureValue);
        } else if (action instanceof WaitForChatAction) {
            WaitForChatAction wca = (WaitForChatAction) action;
            MacroLifecycle.setCurrentStatus("Waiting for chat: " + wca.pattern);
            MacroPacketWait.awaitChat(wca);
        } else if (action instanceof WaitForEntityAction) {
            WaitForEntityAction wea = (WaitForEntityAction) action;
            MacroLifecycle.setCurrentStatus(wea.getDisplayName());
            try {
                MacroRunLoop.waitForPredicate(() -> wea.matchesEntityTarget(mc), wea.timeoutMs);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForSoundAction) {
            WaitForSoundAction wsa = (WaitForSoundAction) action;
            String sndDesc = wsa.soundIds.isEmpty() ? "any" : wsa.soundIds.get(0);
            MacroLifecycle.setCurrentStatus("Waiting for sound: " + sndDesc);
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForSound(wsa);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForSlotChangeAction) {
            WaitForSlotChangeAction wsca = (WaitForSlotChangeAction) action;
            MacroLifecycle.setCurrentStatus(wsca.getDisplayName());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForSlotChange(wsca);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForWorldChangeAction world) {
            MacroLifecycle.setCurrentStatus(world.getDisplayName());
            try {
                CompletableFuture<?> future = MacroRace.raceConditionFuture(mc, world);
                MacroRace.waitForFutureDone(future, 0);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForPositionDeltaAction wpda) {
            MacroLifecycle.setCurrentStatus(wpda.getDisplayName());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForPositionDelta(wpda.distance, wpda.horizontalOnly);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForTeleportAction teleport) {
            MacroLifecycle.setCurrentStatus(teleport.getDisplayName());
            try {
                CompletableFuture<?> future = MacroRace.raceConditionFuture(mc, teleport);
                MacroRace.waitForFutureDone(future, 0);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitGamemodeChangeAction gm) {
            MacroLifecycle.setCurrentStatus(gm.getDisplayName());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForGamemodeChange(gm);
                MacroRace.waitForFutureDone(future, gm.timeoutMs);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitMovementAction wm) {
            MacroLifecycle.setCurrentStatus(wm.getDisplayName());
            try {
                CompletableFuture<?> future = MacroRace.raceConditionFuture(mc, wm.resolveSubAction());
                MacroRace.waitForFutureDone(future, 0);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForLanStepAction) {
            WaitForLanStepAction wla = (WaitForLanStepAction) action;
            MacroLifecycle.setCurrentStatus(wla.getDisplayName());
            try {
                CompletableFuture<Void> future = MacroConditionRegistry.waitForLanStep(wla);
                MacroRunLoop.waitForCondition(future);
            } catch (CancellationException e) {  }
        } else if (action instanceof WaitForMacroStepAction) {
            WaitForMacroStepAction wma = (WaitForMacroStepAction) action;
            MacroLifecycle.setCurrentStatus(wma.getDisplayName());
            MacroRace.waitForMacroStep(wma);
        } else if (action instanceof GoToAction) {
            GoToAction ga = (GoToAction) action;
            MacroWorldActions.runGoToAction(mc, ga);
        } else if (action instanceof MoveAction) {
            MoveAction ma = (MoveAction) action;
            if (ma.nonBlocking) {
                mc.execute(() -> ma.execute(mc));
                MacroLifecycle.currentBackgroundMoves().put(ma, new int[]{ma.durationTicks - 1});
            } else {
                for (int t = 0; t < ma.durationTicks && MacroLifecycle.isCurrentRunActive(); t++) {
                    mc.execute(() -> ma.execute(mc));
                    CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                    MacroRunLoop.waitForCondition(tickFuture);
                }
                MacroExecutor.RunState movementRun = MacroExecutor.CURRENT_RUN.get();
                mc.execute(() -> MacroLifecycle.releaseMoveIfUnused(movementRun, ma, mc));
            }
        } else if (action instanceof LookAtBlockAction) {
            LookAtBlockAction la = (LookAtBlockAction) action;
            LookAtBlockAction.RotationTarget rotationTarget = MacroRender.resolveLookAtTargetOnClient(mc, la);
            if (rotationTarget == null) {
                return;
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
        } else if (action instanceof RotateAction) {
            RotateAction ra = (RotateAction) action;
            if (ra.smooth) {
                MacroRender.startSmoothRotation(ra.yaw, ra.pitch, ra.getRotationStep());
                if (ra.waitForCompletion) {
                    MacroRender.waitForSmoothRotationCompletion();
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
                for (int t = 0; t < ja.durationTicks && MacroLifecycle.isCurrentRunActive(); t++) {
                    CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                    MacroRunLoop.waitForCondition(tickFuture);
                }
            }
            mc.execute(() -> ja.release(mc));
        } else if (action instanceof SneakAction) {
            SneakAction sna = (SneakAction) action;
            if (sna.persistent && sna.sneak) {
                MacroLifecycle.currentPersistentActions().removeIf(p -> p instanceof SneakAction);
                MacroLifecycle.currentPersistentActions().add(sna);
            } else if (!sna.sneak) {
                MacroLifecycle.currentPersistentActions().removeIf(p -> p instanceof SneakAction);
            }
            if (sna.persistent || !sna.sneak) MacroClientThread.postOnClientThread(mc, () -> MacroLifecycle.applyAggregateSneakState(mc), sna.isTickAligned());
            else MacroClientThread.postOnClientThread(mc, () -> sna.execute(mc), sna.isTickAligned());
        } else if (action instanceof SprintAction) {
            SprintAction spa = (SprintAction) action;
            if (spa.persistent && spa.sprint) {
                MacroLifecycle.currentPersistentActions().removeIf(p -> p instanceof SprintAction);
                MacroLifecycle.currentPersistentActions().add(spa);
            } else if (!spa.sprint) {
                MacroLifecycle.currentPersistentActions().removeIf(p -> p instanceof SprintAction);
            }
            if (spa.persistent || !spa.sprint) MacroClientThread.postOnClientThread(mc, () -> MacroLifecycle.applyAggregateSprintState(mc), spa.isTickAligned());
            else MacroClientThread.postOnClientThread(mc, () -> spa.execute(mc), spa.isTickAligned());
        } else if (action instanceof NbtBookAction nba) {
            int totalBooks = Math.max(1, nba.bookCount);
            long delayMs = Math.max(1, nba.delayTicks) * 50L;
            boolean signedAny = false;
            for (int b = 0; b < totalBooks; b++) {
                if (!MacroLifecycle.isCurrentRunActive()) break;
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
        } else if (action instanceof CraftAction) {
            MacroWorldActions.runCraftAction(mc, (CraftAction) action);
        } else if (action instanceof autismclient.util.macro.StoreItemAction) {
            autismclient.util.macro.StoreItemAction sia = (autismclient.util.macro.StoreItemAction) action;
            MacroLifecycle.setCurrentStatus((sia.mode == autismclient.util.macro.StoreItemAction.Mode.LOOT ? "Looting" : "Storing")
                + (sia.persistent ? " \u221e" : ""));
            if (sia.persistent) {
                while (MacroLifecycle.isCurrentRunActive()) {
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
            MacroLifecycle.setCurrentStatus("Dupe: " + auditAction.dupeVector.name() + " " + auditAction.openMode.name());
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
            MacroLifecycle.setCurrentStatus("Tick Sync -> " + targetTick + " (" + preGenerated.size() + " pkts)");
            while (MacroLifecycle.isCurrentRunActive() && mc.level.getGameTime() < targetTick) {
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
            }
            if (MacroLifecycle.isCurrentRunActive()) {
                MacroRender.executePreGeneratedBurst(mc, preGenerated);
                MacroExecutor.RepeatPacketContext context = MacroExecutor.REPEAT_PACKET_CONTEXT.get();
                if (context != null) MacroExecutor.REPEAT_PACKET_SKIP.set(MacroRender.countPreGeneratedActions(context.macro(), context.startIdx(), preGenerated.size(), context.endIdx()));
                AutismClientMessaging.sendPrefixed("Tick sync: " + preGenerated.size() + " pkts sent");
            }
        } else if (action instanceof RevisionSyncAction rsa) {
            if (mc.player == null || mc.player.containerMenu == null) return;
            java.util.List<net.minecraft.network.protocol.Packet<?>> preGenerated =
                MacroActionRunner.preGeneratePacketsForRepeat(rsa.preGenCount, mc);
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
                MacroExecutor.RepeatPacketContext context = MacroExecutor.REPEAT_PACKET_CONTEXT.get();
                if (context != null) MacroExecutor.REPEAT_PACKET_SKIP.set(MacroRender.countPreGeneratedActions(context.macro(), context.startIdx(), preGenerated.size(), context.endIdx()));
                AutismClientMessaging.sendPrefixed("Rev sync: " + preGenerated.size() + " pkts sent");
            }
        } else if (action instanceof ServerTickSyncAction stsa) {
            if (mc.getConnection() == null) return;
            java.util.List<net.minecraft.network.protocol.Packet<?>> preGenerated =
                MacroActionRunner.preGeneratePacketsForRepeat(stsa.preGenCount, mc);
            int sampleWaitMs = 0;
            while (MacroLifecycle.isCurrentRunActive() && !ServerTickTracker.isReady() && sampleWaitMs < stsa.maxWaitMs) {
                float progress = ServerTickTracker.getWarmupProgress();
                MacroLifecycle.setCurrentStatus(String.format("ServerSync warmup %.0f%% (%d samples, %dms)",
                    progress * 100, ServerTickTracker.getSampleCount(), ServerTickTracker.getTrackingTimeMs()));
                Thread.sleep(50);
                sampleWaitMs += 50;
            }
            if (!MacroLifecycle.isCurrentRunActive()) return;
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
                MacroExecutor.RepeatPacketContext context = MacroExecutor.REPEAT_PACKET_CONTEXT.get();
                if (context != null) MacroExecutor.REPEAT_PACKET_SKIP.set(MacroRender.countPreGeneratedActions(context.macro(), context.startIdx(), preGenerated.size(), context.endIdx()));
                long actualDelay = (System.nanoTime() - optimalTime) / 1_000_000L;
                AutismClientMessaging.sendPrefixed("ServerSync: " + preGenerated.size() + " pkts (+" + actualDelay + "ms)");
            }
        } else if (action instanceof BundleDupeV2Action bundleV2) {
            MacroLifecycle.setCurrentStatus("Bundle Dupe V2");
            bundleV2.execute(mc);
        } else if (action instanceof RollbackAction rollback) {
            MacroLifecycle.setCurrentStatus("Rollback");
            try {
                rollback.executeRollback(mc);
            } catch (InterruptedException e) {
                AutismClientMessaging.sendPrefixed("\u00a7eRollback canceled safely.");
            } catch (Exception e) {
                AutismClientMessaging.sendPrefixed("\u00a7cRollback failed: " + MacroRace.safeThrowableMessage(e));
            }
        } else if (action instanceof StopMacroAction) {
            StopMacroAction stopAction = (StopMacroAction) action;
            if (stopAction.target == StopMacroAction.StopTarget.SELF) MacroLifecycle.stopCurrentRun();
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
                MacroExecutor.RunState scheduledRun = MacroExecutor.CURRENT_RUN.get();
                mc.execute(() -> MacroLifecycle.withRunContext(scheduledRun, () -> {
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

        if (postGuiFuture != null && MacroLifecycle.isCurrentRunActive()) {
            try {
                MacroRunLoop.waitForCondition(postGuiFuture);
            } catch (java.util.concurrent.CancellationException e) {  }
        }

        if (MacroLifecycle.isCurrentRunActive() && !MacroActionRunner.isInstantInventoryAction(action) && module != null && module.useInstantExecutionMode()) {
            int delayUs = module.getActionDelayUs();
            if (delayUs > 0) java.util.concurrent.locks.LockSupport.parkNanos(delayUs * 1000L);
            else Thread.onSpinWait();
        }
        } finally {
            MacroRaycastAim.release();
            MacroLifecycle.releaseClientResources(heldResources);
        }
    }
}
