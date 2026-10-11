package autismclient.util.macro;

import autismclient.AutismClientAddon;
import autismclient.modules.PackHideState;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismCompatManager;
import autismclient.util.AutismCraftingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class MacroWorldActions {
    private MacroWorldActions() {
    }
    public static void runPlaceAction(Minecraft mc, PlaceAction action) throws InterruptedException {
        if (mc == null || mc.player == null || mc.level == null) return;

        ItemTarget target = action.resolvedItemTarget().resolveTemplate(mc);
        if (target == null) return;
        if (action.waitForItem && target != null && (target.hasIdentity() || target.hasSlot())) {
            MacroLifecycle.setCurrentStatus("Waiting for " + describeItemTarget(target));
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

            MacroClientThread.runOnClientThread(mc, () -> MacroBreak.sendBlockInteract(mc, placePos, face), action.isTickAligned());
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            MacroActionRunner.runMacroActionOnClientThread(mc, action, "Place");
        } else if (t == InteractTiming.WITH) {

            MacroClientThread.runOnClientThread(mc, () -> {
                try { action.execute(mc); } catch (Exception e) { AutismClientAddon.LOG.debug("[Autism] Macro WITH-interact action failed", e); }
                MacroBreak.sendBlockInteract(mc, placePos, face);
            }, action.isTickAligned());
        } else if (t == InteractTiming.AFTER_PLUS) {
            MacroActionRunner.runMacroActionOnClientThread(mc, action, "Place");
            MacroExecutor.waitOneTick();
            MacroClientThread.runOnClientThread(mc, () -> MacroBreak.sendBlockInteract(mc, placePos, face), action.isTickAligned());
        } else {

            MacroActionRunner.runMacroActionOnClientThread(mc, action, "Place");
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            MacroClientThread.runOnClientThread(mc, () -> MacroBreak.sendBlockInteract(mc, placePos, face), action.isTickAligned());
        }
    }

    private static void waitForItemInSpecificHandlerSlot(Minecraft mc, int slotId, ItemTarget target) throws InterruptedException {
        while (MacroLifecycle.isCurrentRunActive()) {
            if (Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc, () -> handlerSlotMatchesItem(mc, slotId, target), Boolean.FALSE))) return;
            MacroExecutor.waitOneTick();
        }
    }

    private static void waitForAnyItemInSpecificHandlerSlot(Minecraft mc, int slotId) throws InterruptedException {
        while (MacroLifecycle.isCurrentRunActive()) {
            if (Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc, () -> handlerSlotHasAnyItem(mc, slotId), Boolean.FALSE))) return;
            MacroExecutor.waitOneTick();
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

    public static void waitForItemActionTargets(Minecraft mc, ItemAction ia) throws InterruptedException {
        if (ia == null) return;

        List<ItemTarget> targets = resolveItemTargets(mc, ia.itemTargets, ia.itemNames);
        if (!targets.isEmpty()) {
            for (ItemTarget target : targets) {
                waitForItemActionEntry(mc, target);
            }
            return;
        }

        if (ia.useSlot && ia.targetSlot >= 0) {
            MacroLifecycle.setCurrentStatus("Waiting for slot " + ia.targetSlot);
            waitForAnyItemInSpecificHandlerSlot(mc, ia.targetSlot);
        }
    }

    public static void waitForItemActionEntry(Minecraft mc, ItemTarget target) throws InterruptedException {
        ItemTarget resolvedTarget = target == null ? null : target.resolveTemplate(mc);
        if (resolvedTarget == null) return;
        String itemLabel = describeItemTarget(resolvedTarget);
        int slot = resolvedTarget.hasSlot() ? resolvedTarget.slot : -1;

        if (slot >= 0 && resolvedTarget.hasIdentity()) {
            MacroLifecycle.setCurrentStatus("Waiting for " + itemLabel);
            waitForItemInSpecificHandlerSlot(mc, slot, resolvedTarget);
            return;
        }

        if (slot >= 0) {
            MacroLifecycle.setCurrentStatus("Waiting for slot " + slot);
            waitForAnyItemInSpecificHandlerSlot(mc, slot);
            return;
        }

        if (resolvedTarget.hasIdentity()) {
            MacroLifecycle.setCurrentStatus("Waiting for item in GUI: " + itemLabel);
            CompletableFuture<Void> waitFuture = MacroConditionRegistry.waitForItemInHandler(resolvedTarget);
            MacroRunLoop.waitForCondition(waitFuture);
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

    public static String describeItemTarget(ItemTarget target) {
        if (target == null) return "";
        String summary = target.summaryText();
        if (summary != null && !summary.isBlank()) return summary;
        String legacy = target.toLegacyEntry();
        return legacy == null ? "" : legacy;
    }

    public static void runCraftAction(Minecraft mc, CraftAction craftAction) throws InterruptedException {
        if (craftAction == null || !craftAction.hasEntries()) {
            MacroLifecycle.setCurrentStatus("Crafting");
            AutismClientMessaging.sendPrefixed("\u00a7cNo craft recipes selected.");
            return;
        }

        for (int index = 0; index < craftAction.entries.size(); index++) {
            CraftAction.CraftEntry entry = craftAction.entries.get(index);
            if (entry == null || !entry.hasRecipe()) continue;

            MacroLifecycle.setCurrentStatus(entry.useMaxAmount
                ? "Crafting " + entry.resultName + " [Max] (" + (index + 1) + "/" + craftAction.entries.size() + ")"
                : "Crafting " + entry.resultName + " x" + Math.max(1, entry.amount) + " (" + (index + 1) + "/" + craftAction.entries.size() + ")");

            AutismCraftingHelper.CraftableRecipeOption option = AutismCraftingHelper.findCraftableRecipe(mc, entry.recipeKey);
            if (option == null) option = AutismCraftingHelper.findCraftableRecipe(mc, entry.recipeId);
            if (option == null) {
                MacroLifecycle.setCurrentStatus("Recipe not found");
                AutismClientMessaging.sendPrefixed("\u00a7cRecipe not found: " + (entry.resultName == null || entry.resultName.isBlank() ? "unknown" : entry.resultName));
                return;
            }

            int desiredAmount = AutismCraftingHelper.getEffectiveRequestedOutput(option, entry.amount, entry.useMaxAmount);
            if (desiredAmount <= 0) {
                MacroLifecycle.setCurrentStatus("No space or materials");
                AutismClientMessaging.sendPrefixed("\u00a7cNo space or materials for " + entry.resultName + ".");
                return;
            }

            AutismCraftingHelper.CraftExecutionResult result = AutismCraftingHelper.executeCraftImmediately(
                mc,
                entry.recipeKey,
                entry.recipeId,
                desiredAmount
            );

            MacroLifecycle.setCurrentStatus(result.message);
            if (!result.success) {
                AutismClientMessaging.sendPrefixed("\u00a7c" + result.message);
                return;
            }
            if (MacroLifecycle.isCurrentRunActive()) {
                CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                MacroRunLoop.waitForCondition(tickFuture);
            }
        }
    }

    public static void runGoToAction(Minecraft mc, GoToAction goToAction) throws InterruptedException {
        MacroLifecycle.setCurrentStatus(String.format("Going to %.0f,%.0f,%.0f", goToAction.x, goToAction.y, goToAction.z));

        if (!AutismCompatManager.isBaritoneAvailable()) {
            AutismClientMessaging.sendPrefixed("Baritone not installed; skipping goto action.");
            return;
        }

        int goalX = (int) Math.floor(goToAction.x);
        int goalY = (int) Math.floor(goToAction.y);
        int goalZ = (int) Math.floor(goToAction.z);
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();

        if (!runBaritoneStart(mc, () -> AutismCompatManager.startBaritoneGoTo(mc, goalX, goalY, goalZ))) {
            AutismClientMessaging.sendPrefixed("Failed to start Baritone goto.");
            return;
        }

        try {
            if (!goToAction.waitForArrival) {
                MacroLifecycle.setCurrentStatus("Goto started");
                return;
            }

            long startMs = System.currentTimeMillis();
            boolean sawBaritoneActivity = false;

            while (MacroLifecycle.isCurrentRunActive()) {
                boolean goalActive = AutismCompatManager.isBaritoneGoalActive();
                boolean baritoneBusy = AutismCompatManager.isBaritoneBusy();
                if (goalActive || baritoneBusy) sawBaritoneActivity = true;

                if (sawBaritoneActivity && !goalActive && !baritoneBusy) {
                    MacroLifecycle.setCurrentStatus("Goto finished");
                    return;
                }

                if (!sawBaritoneActivity && System.currentTimeMillis() - startMs >= 1500 && !goalActive && !baritoneBusy) {
                    MacroLifecycle.setCurrentStatus("Goto finished");
                    return;
                }

                CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                MacroRunLoop.waitForCondition(tickFuture);
            }
        } finally {
            if (!MacroLifecycle.isCurrentRunActive()) requestOwnedBaritoneStop(run, mc);
            else releaseBaritoneOwner(run);
        }
    }

    public static void runMineAction(Minecraft mc, MineAction mineAction) throws InterruptedException {
        String mineDesc = mineAction.targetBlocks.isEmpty() ? "none" : mineAction.targetBlocks.get(0);
        MacroLifecycle.setCurrentStatus("Mining: " + mineDesc);

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

        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
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

            while (MacroLifecycle.isCurrentRunActive()) {
                boolean mineActive = AutismCompatManager.isBaritoneMineActive();
                boolean baritoneBusy = AutismCompatManager.isBaritoneBusy();
                if (mineActive || baritoneBusy) sawBaritoneActivity = true;

                String stopReason = null;
                int minedDelta = 0;

                if (mineTracker != null) {
                    mineTracker.poll(mc);
                    minedDelta = mineTracker.destroyedCount();
                    MacroLifecycle.setCurrentStatus("Mining: " + mineDesc + " (" + minedDelta + "/" + mineAction.minedCountTarget + ")");
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
                    MacroLifecycle.setCurrentStatus(stopReason);
                    requestOwnedBaritoneStop(run, mc);
                    waitForBaritoneIdle();
                    return;
                }

                if (sawBaritoneActivity && !mineActive && !baritoneBusy) {
                    MacroLifecycle.setCurrentStatus("Mine finished");
                    return;
                }

                if (!sawBaritoneActivity && System.currentTimeMillis() - mineStartMs >= 1500 && !mineActive && !baritoneBusy) {
                    MacroLifecycle.setCurrentStatus("Mine finished");
                    return;
                }

                CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
                MacroRunLoop.waitForCondition(tickFuture);
            }
        } finally {
            if (run != null && run.activeMineTracker == mineTracker) run.activeMineTracker = null;
            if (!MacroLifecycle.isCurrentRunActive()) requestOwnedBaritoneStop(run, mc);
            else releaseBaritoneOwner(run);
        }
    }

    private static boolean runBaritoneStart(Minecraft mc, java.util.function.BooleanSupplier starter) throws InterruptedException {
        return runBaritoneStart(mc, starter, false);
    }

    private static boolean runBaritoneStart(Minecraft mc, java.util.function.BooleanSupplier starter,
                                            boolean tickAligned) throws InterruptedException {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run != null) MacroExecutor.BARITONE_OWNER.set(run);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean started = new AtomicBoolean(false);

        Runnable task = () -> {
            try {
                started.set(starter.getAsBoolean());
            } finally {
                latch.countDown();
            }
        };
        if (tickAligned && !mc.isSameThread()) MacroClientThread.TICK_ALIGNED.add(task);
        else mc.execute(task);

        latch.await(2, TimeUnit.SECONDS);
        if (!started.get()) releaseBaritoneOwner(run);
        return started.get();
    }

    private static void requestBaritoneStop(Minecraft mc) {
        if (mc == null || !AutismCompatManager.isBaritoneAvailable()) return;
        mc.execute(() -> AutismCompatManager.stopBaritone(mc));
    }

    public static void requestOwnedBaritoneStop(MacroExecutor.RunState run, Minecraft mc) {
        if (run == null || !MacroExecutor.BARITONE_OWNER.compareAndSet(run, null)) return;
        requestBaritoneStop(mc);
    }

    private static void releaseBaritoneOwner(MacroExecutor.RunState run) {
        if (run != null) MacroExecutor.BARITONE_OWNER.compareAndSet(run, null);
    }

    private static void waitForBaritoneIdle() {
        long waitStartMs = System.currentTimeMillis();
        while (MacroLifecycle.isCurrentRunActive() && AutismCompatManager.isBaritoneBusy() && System.currentTimeMillis() - waitStartMs < 3000L) {
            CompletableFuture<Void> tickFuture = MacroConditionRegistry.waitForNextTick();
            MacroRunLoop.waitForCondition(tickFuture);
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

    public static final class MineProgressTracker {
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

    public static void runPayAction(Minecraft mc, PayAction payAction) throws InterruptedException {
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
            if (!MacroLifecycle.isCurrentRunActive()) break;
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

            MacroLifecycle.setCurrentStatus("Paying " + player + " " + amountLabel + " (" + (sent + 1) + "/" + targets.size() + ")");

            MacroClientThread.runOnClientThread(mc, () -> {
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
                MacroLifecycle.setCurrentStatus("Confirming " + player);
                PayAction.runConfirmMacro(payAction.confirmMacro, () -> !MacroLifecycle.isCurrentRunActive());
            }
        }
    }
}
