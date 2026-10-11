package autismclient.util.macro;

import autismclient.modules.PackHideState;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismGuiActions;
import autismclient.util.AutismInventoryClickHelper;
import autismclient.util.AutismSharedState;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Locale;

final class MacroActionRunner {
    private MacroActionRunner() {
    }
    public static void beginRaycastAim(Minecraft mc, MacroAction action) {
        if (!(action instanceof RaycastAim aim) || !aim.isRaycast()) return;
        autismclient.util.AutismRotationUtil.Rotation rotation;
        try {
            rotation = MacroClientThread.callOnClientThread(mc, () -> MacroRaycastAim.aimFor(mc, aim.raycastTarget(mc)), null);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        if (rotation == null) return;
        MacroRaycastAim.hold(rotation);
        MacroExecutor.waitOneTick();
    }

    static boolean dynamicTextInputsAvailable(MacroAction action, Minecraft mc) {
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

    public static java.util.List<net.minecraft.network.protocol.Packet<?>> preGeneratePacketsForRepeat(
            int count, Minecraft mc) {
        MacroExecutor.RepeatPacketContext context = MacroExecutor.REPEAT_PACKET_CONTEXT.get();
        if (context == null || context.macro() == null) return new java.util.ArrayList<>();
        return MacroRender.preGeneratePackets(context.macro(), context.startIdx(), count, context.endIdx());
    }

    public static boolean isTargetWaitInteractionAction(MacroAction action) {
        return action instanceof OpenContainerAction || action instanceof InteractEntityAction;
    }

    public static CompletableFuture<Void> runTargetWaitInteractionAction(Minecraft mc, MacroAction action) throws InterruptedException {
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

        MacroExecutor.setCurrentStatus("Waiting target: " + action.getDisplayName());
        while (MacroExecutor.isCurrentRunActive()) {
            if (!canRunTargetInteractionNow(mc, action)) {
                java.util.concurrent.locks.LockSupport.parkNanos(MacroExecutor.MACRO_WAIT_POLL_NANOS);
                if (Thread.interrupted()) throw new InterruptedException();
                continue;
            }

            CompletableFuture<Void> postGuiFuture = createPostGuiFuture(mc, action);
            if (runTargetInteractionAttempt(mc, action)) {
                return postGuiFuture;
            }
            if (postGuiFuture != null) postGuiFuture.cancel(true);
            java.util.concurrent.locks.LockSupport.parkNanos(MacroExecutor.MACRO_WAIT_POLL_NANOS);
            if (Thread.interrupted()) throw new InterruptedException();
        }
        return null;
    }

    private static boolean canRunTargetInteractionNow(Minecraft mc, MacroAction action) throws InterruptedException {
        return MacroClientThread.callOnClientThread(mc, () -> {
            if (action instanceof OpenContainerAction open) return open.canExecuteNow(mc);
            if (action instanceof InteractEntityAction interact) return interact.canExecuteNow(mc);
            return true;
        }, Boolean.FALSE);
    }

    private static boolean runTargetInteractionAttempt(Minecraft mc, MacroAction action) throws InterruptedException {
        return MacroClientThread.callOnClientThread(mc, () -> {
            try {
                if (action instanceof OpenContainerAction open) return open.tryExecute(mc);
                if (action instanceof InteractEntityAction interact) return interact.tryExecute(mc);
                action.execute(mc);
                return true;
            } catch (Exception e) {
                AutismClientMessaging.sendPrefixed("\u00a7cError in " + action.getDisplayName() + ": " + e.getMessage());
                return true;
            }
        }, Boolean.FALSE, usesTickAlignedDispatch(action));
    }

    public static CompletableFuture<Void> createPostGuiFuture(Minecraft mc, MacroAction action) {
        if (Boolean.TRUE.equals(MacroExecutor.SUPPRESS_POST_GUI_AFTER.get())) return null;
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

    public static boolean waitForGuiBeforeAction(Minecraft mc, MacroAction action) throws InterruptedException {
        if (!(action instanceof WaitsForGui wfg)) return true;
        return waitForGuiBeforeAction(mc, wfg);
    }

    public static boolean waitForGuiBeforeAction(Minecraft mc, WaitsForGui action) throws InterruptedException {
        if (mc == null || action == null || !action.isWaitForGuiBefore()) return true;
        MacroTemplate.Resolution guiName = MacroVariables.resolve(action.getWaitGuiName(), mc);
        if (!guiName.success()) return false;
        if (isWaitGuiPresent(mc, guiName.value())) {
            waitForCurrentGuiReady(mc);
            return true;
        }
        CompletableFuture<Void> future = MacroConditionRegistry.waitForGui(guiName.value());
        MacroRunLoop.waitForCondition(future);
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
        while (MacroExecutor.isCurrentRunActive()) {
            Boolean ready = MacroClientThread.callOnClientThread(mc, () -> {
                if (mc.player == null || mc.gui.screen() == null) return false;
                if (mc.player.containerMenu != null && mc.player.containerMenu != mc.player.inventoryMenu) {
                    return mc.player.containerMenu.getStateId() > 0;
                }
                return true;
            }, Boolean.FALSE);
            if (Boolean.TRUE.equals(ready)) return;
            if (System.nanoTime() >= deadline) return;
            java.util.concurrent.locks.LockSupport.parkNanos(MacroExecutor.MACRO_WAIT_POLL_NANOS);
            if (Thread.interrupted()) return;
        }
    }

    public static void runMacroActionOnClientThread(Minecraft mc, MacroAction action, String label) throws InterruptedException {
        Runnable body = () -> {
            try {
                action.execute(mc);
            } catch (Throwable t) {
                recordAddonActionError(action, "Action threw " + t.getClass().getSimpleName());
                AutismClientMessaging.sendPrefixed("\u00a7cError in " + label + ": "
                        + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
            }
        };
        if (usesTickAlignedDispatch(action)) MacroClientThread.runOnClientTick(mc, body);
        else MacroClientThread.runOnClientThread(mc, body);
    }

    public static boolean usesTickAlignedDispatch(MacroAction action) {
        return action instanceof PacketOrdered ordered && ordered.isTickAligned();
    }

    public static void runItemActionPaced(Minecraft mc, ItemAction ia) throws InterruptedException {
        MacroExecutor.setCurrentStatus(ia.getDisplayName());
        java.util.List<Runnable> plan = MacroClientThread.callOnClientThread(mc, () -> ia.buildClickPlan(mc), java.util.Collections.<Runnable>emptyList());
        if (plan == null || plan.isEmpty()) return;
        long delayMs = Math.max(0, ia.clickDelayMs);
        MacroExecutor.RunState scheduledRun = MacroExecutor.CURRENT_RUN.get();
        boolean first = true;
        for (Runnable op : plan) {
            if (!MacroExecutor.isCurrentRunActive()) break;
            if (!first && delayMs > 0) Thread.sleep(delayMs);
            first = false;
            mc.execute(() -> MacroExecutor.withRunContext(scheduledRun, () -> {
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

    public static void runContextMacroAction(autismclient.api.macro.ContextMacroAction action) {
        if (PackHideState.isHardLocked()) return;
        if (action == null) return;
        try {
            action.run(new ApiExecutionContext(MacroExecutor.CURRENT_RUN.get()));
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
        private final MacroExecutor.RunState run;

        private ApiExecutionContext(MacroExecutor.RunState run) {
            this.run = run;
        }

        @Override public Minecraft mc() { return Minecraft.getInstance(); }

        @Override public void runOnClientThread(Runnable r) {
            if (r == null) return;

            Runnable safe = () -> MacroExecutor.withRunContext(run, () -> {
                try { r.run(); }
                catch (Throwable t) { AutismClientMessaging.sendPrefixed("\u00a7cAddon action (client task) error: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage())); }
            });
            try { MacroClientThread.runOnClientThread(Minecraft.getInstance(), safe); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        @Override public <T> T callOnClientThread(java.util.function.Supplier<T> s) {
            if (s == null) return null;
            java.util.function.Supplier<T> safe = () -> {
                AtomicReference<T> value = new AtomicReference<>();
                MacroExecutor.withRunContext(run, () -> {
                    try { value.set(s.get()); }
                    catch (Throwable t) { AutismClientMessaging.sendPrefixed("\u00a7cAddon action (client task) error: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage())); }
                });
                return value.get();
            };
            try { return MacroClientThread.callOnClientThread(Minecraft.getInstance(), safe, null); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
        }

        @Override public void waitTicks(int ticks) {
            MacroExecutor.withRunContext(run, () -> {
                for (int i = 0; i < ticks && MacroExecutor.isCurrentRunActive(); i++) {
                    MacroRunLoop.waitForCondition(MacroConditionRegistry.waitForNextTick());
                }
            });
        }

        @Override public void awaitCondition(CompletableFuture<Void> future) {
            if (future != null) MacroExecutor.withRunContext(run, () -> MacroRunLoop.waitForCondition(future));
        }

        @Override public void awaitCondition(autismclient.api.macro.AddonCondition condition) {
            if (condition != null) MacroExecutor.withRunContext(run, () -> MacroRunLoop.waitForCondition(MacroConditionRegistry.await(condition)));
        }

        @Override public boolean isActive() { return run != null && run.running.get() && !PackHideState.isHardLocked(); }

        @Override public void setStatus(String status) { MacroExecutor.withRunContext(run, () -> MacroExecutor.setCurrentStatus(status)); }

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

    public static void runUseItemPhaseAction(Minecraft mc, UseItemPhaseAction action) throws InterruptedException {
        int times = action.repeatTimes();
        for (int i = 0; i < times && MacroExecutor.isCurrentRunActive(); i++) {

            MacroClientThread.runOnClientThread(mc, () -> {
                try { action.sendUsePacket(mc); }
                catch (Exception e) { AutismClientMessaging.sendPrefixed("\u00a7cError in Use phase: " + e.getMessage()); }
            });

            final boolean[] gateInstalled = { false };
            if (action.shouldGate()) {
                MacroClientThread.runOnClientThread(mc, () -> {
                    try { gateInstalled[0] = action.installGate(mc); }
                    catch (Exception e) { AutismClientMessaging.sendPrefixed("\u00a7cError in Use gate: " + e.getMessage()); }
                });
            }
            try {

                for (int t = 0; t < action.holdTicks && MacroExecutor.isCurrentRunActive(); t++) {
                    CompletableFuture<Void> tf = MacroConditionRegistry.waitForNextTick();
                    MacroRunLoop.waitForCondition(tf);
                }
            } finally {
                if (gateInstalled[0]) {
                    final boolean wasOnThread = mc.isSameThread();
                    if (wasOnThread) {
                        action.removeGate();
                    } else {
                        MacroClientThread.runOnClientThread(mc, action::removeGate);
                    }
                }
            }
            if (!MacroExecutor.isCurrentRunActive()) break;

            MacroClientThread.runOnClientThread(mc, () -> {
                try { action.finishRelease(mc); }
                catch (Exception e) { AutismClientMessaging.sendPrefixed("\u00a7cError in Use release: " + e.getMessage()); }
            });
        }
    }

    public static void runClickActionBurst(Minecraft mc, ClickAction action) throws InterruptedException {
        int times = Math.max(1, action.clickCount);

        MacroClientThread.runOnClientThread(mc, () -> {
            for (int c = 0; c < times && MacroExecutor.isCurrentRunActive(); c++) {
                try {
                    action.execute(mc);
                } catch (Exception e) {
                    AutismClientMessaging.sendPrefixed("\u00a7cError in Click: " + e.getMessage());
                    break;
                }
            }
        }, action.isTickAligned());
    }

    public static boolean isInstantInventoryAction(MacroAction action) {
        if (action instanceof ItemAction
                || action instanceof DropAction
                || action instanceof SwapSlotsAction
                || action instanceof ClickAction
                || action instanceof XCarryAction) {
            return true;
        }
        return action instanceof StoreItemAction storeItemAction && !storeItemAction.persistent;
    }

    public static void runStoreItemAction(Minecraft mc, StoreItemAction action) throws InterruptedException {
        if (action == null) return;
        int delayTicks = StoreItemAction.clampDelayTicks(action.delayTicks);
        if (delayTicks <= 0) {
            MacroClientThread.runOnClientThread(mc, () -> action.doTransfer(mc));
            return;
        }

        List<Integer> slots = MacroClientThread.callOnClientThread(mc, () -> action.collectTransferSlots(mc), List.of());
        for (int i = 0; i < slots.size() && MacroExecutor.isCurrentRunActive(); i++) {
            AutismInventoryClickHelper.click(mc, slots.get(i), 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
            if (i + 1 < slots.size()) waitTicks(delayTicks);
        }

        if (action.closeAfter && !action.persistent) {
            MacroClientThread.runOnClientThread(mc, () -> AutismGuiActions.closeCurrentScreen(mc, action.closeSendPkt, false));
        }
    }

    private static void waitTicks(int ticks) {
        for (int i = 0; i < ticks && MacroExecutor.isCurrentRunActive(); i++) {
            CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
            MacroRunLoop.waitForCondition(tickFuture);
        }
    }

    public static void runUseItemAction(Minecraft mc, UseItemAction action) throws InterruptedException {
        if (action == null) return;

        boolean sneaking = action.sneak;
        try {
            if (action.useMode == UseItemAction.UseMode.CUSTOM_HOLD) {
                MacroClientThread.runOnClientThread(mc, () -> {
                    try {
                        if (sneaking) autismclient.util.AutismMacroSneak.forceSneak(mc, true);
                        action.execute(mc);
                    }
                    catch (Exception e) { AutismClientMessaging.sendPrefixed("\u00a7cError in UseItem: " + e.getMessage()); }
                }, action.isTickAligned());
                if (action.holdTicks > 0) {
                    waitTicks(action.holdTicks);
                    if (!MacroExecutor.isCurrentRunActive()) return;
                    MacroClientThread.runOnClientThread(mc, () -> {
                        try { action.sendRelease(mc); }
                        catch (Exception e) { AutismClientMessaging.sendPrefixed("\u00a7cError in UseItem release: " + e.getMessage()); }
                    }, action.isTickAligned());
                    if (action.waitForFinish) waitForUseItemFinish(mc, action.holdTicks + 8);
                }
                return;
            }

            int times = Math.max(1, action.useCount);
            for (int i = 0; i < times && MacroExecutor.isCurrentRunActive(); i++) {
                MacroClientThread.runOnClientThread(mc, () -> {
                    try {
                        if (sneaking) autismclient.util.AutismMacroSneak.forceSneak(mc, true);
                        action.execute(mc);
                    }
                    catch (Exception e) { AutismClientMessaging.sendPrefixed("\u00a7cError in UseItem: " + e.getMessage()); }
                }, action.isTickAligned());
                if (action.waitForFinish) waitForUseItemFinish(mc, 0);
                if (i + 1 < times && MacroExecutor.isCurrentRunActive()) MacroExecutor.waitOneTick();
            }
        } finally {
            if (sneaking) mc.execute(() -> autismclient.util.AutismMacroSneak.forceSneak(mc, false));
        }
    }

    public static void runContainerClickSequenceAction(Minecraft mc, ContainerClickSequenceAction action) throws InterruptedException {
        if (action == null) return;
        List<Integer> slots = action.resolvedSlots();
        int repeats = Math.max(1, action.repeatCount);
        int delay = Math.max(0, action.delayTicks);
        int total = repeats * slots.size();
        int sent = 0;
        for (int repeat = 0; repeat < repeats && MacroExecutor.isCurrentRunActive(); repeat++) {
            for (int slot : slots) {
                if (!MacroExecutor.isCurrentRunActive()) return;
                MacroClientThread.runOnClientThread(mc, () -> action.executeClick(mc, slot));
                sent++;
                if (delay > 0 && sent < total) waitTicks(delay);
            }
        }
    }

    public static void runPacketBurstAction(Minecraft mc, PacketBurstAction action) {
        if (action == null || mc == null || mc.getConnection() == null) return;
        if (action.flushBefore) AutismSharedState.get().flushDelayedPackets(mc.getConnection());
        int total = action.effectiveCount();
        int delay = Math.max(0, action.delayTicks);
        for (int i = 0; i < total && MacroExecutor.isCurrentRunActive(); i++) {
            action.sendOne(mc);
            if (delay > 0 && i + 1 < total) waitTicks(delay);
        }
    }

    private static void waitForUseItemFinish(Minecraft mc, int fallbackTicks) throws InterruptedException {
        if (mc == null || !MacroExecutor.isCurrentRunActive()) return;

        if (!Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc,
                () -> mc.player != null && mc.player.isUsingItem(),
                Boolean.FALSE))) {
            return;
        }

        int remaining = MacroClientThread.callOnClientThread(mc,
                () -> mc.player == null ? 0 : Math.max(0, mc.player.getUseItemRemainingTicks()),
                0);
        int maxTicks = Math.max(8, remaining > 0 ? remaining + 8 : fallbackTicks + 8);
        maxTicks = Math.min(maxTicks, 72000);
        for (int i = 0; i < maxTicks && MacroExecutor.isCurrentRunActive(); i++) {
            if (!Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc,
                    () -> mc.player != null && mc.player.isUsingItem(),
                    Boolean.FALSE))) {
                return;
            }
            CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
            MacroRunLoop.waitForCondition(tickFuture);
        }
    }
}
