package autismclient.util.macro;

import autismclient.mixin.accessor.AutismMultiPlayerGameModeAccessor;
import autismclient.modules.AutismModule;
import autismclient.util.AutismAutoTool;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismInstaBreakRenderer;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismSharedState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class MacroBreak {
    private MacroBreak() {
    }
    public static void runInstaBreakAction(Minecraft mc, InstaBreakAction action) throws InterruptedException {
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
            while (MacroExecutor.isCurrentRunActive() && isActionStillInCurrentMacro(action)) {

                if (action.sneak) {
                    long sneakTick = AutismSharedState.get().getClientTickCounter();
                    if (sneakTick != lastSneakTick) {
                        lastSneakTick = sneakTick;
                        final String sneakMode = action.sneakMode;
                        MacroExecutor.runOnClientThread(mc, () -> holdMacroSneak(mc, sneakMode), action.isTickAligned());
                    }
                }

                boolean isAir = isInstaBreakAir(mc, target);
                if (wasSolid && isAir) {
                    completed++;
                    MacroExecutor.setCurrentStatus("InstaBreak " + completed + "/" + (targetTimes == 0 ? "\u221e" : targetTimes));
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
                                MacroExecutor.runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction), action.isTickAligned());
                                if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
                                sendInstaBreakAttempt(mc, target, direction, action.isTickAligned());
                            } else if (t == InteractTiming.AFTER_PLUS) {
                                sendInstaBreakAttempt(mc, target, direction, action.isTickAligned());
                                MacroExecutor.waitOneTick();
                                MacroExecutor.runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction), action.isTickAligned());
                            } else {
                                sendInstaBreakAttempt(mc, target, direction, action.isTickAligned());
                                if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
                                MacroExecutor.runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction), action.isTickAligned());
                            }
                        } else {
                            sendInstaBreakAttempt(mc, target, direction, action.isTickAligned());
                        }
                    }
                }

                MacroExecutor.setCurrentStatus("InstaBreak " + target.getX() + "," + target.getY() + "," + target.getZ()
                    + " " + completed + "/" + (targetTimes == 0 ? "\u221e" : targetTimes));

                Thread.sleep(1);
            }
        } finally {
            if (action.sneak) {
                final String sneakMode = action.sneakMode;
                try {
                    MacroExecutor.runOnClientThread(mc, () -> releaseMacroSneak(mc, sneakMode));
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
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        if (run == null || run.macro == null || run.macro.actions == null) return true;
        return run.macro.actions.contains(action);
    }

    private static boolean ensureInstaBreakPickaxe(Minecraft mc, InstaBreakAction action) throws InterruptedException {

        return MacroExecutor.callOnClientThread(mc, () -> {
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
        MacroExecutor.runOnClientThread(mc, () -> {
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

    public static void runBreakAction(Minecraft mc, BreakAction action) throws InterruptedException {
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
            while (MacroExecutor.isCurrentRunActive() && isActionStillInCurrentMacro(action)) {
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
                        : MacroExecutor.callOnClientThread(mc, () -> startBreakOnClient(mc, action, target, direction, started), -1, action.isTickAligned());
                } else if (action.interact && action.interactTiming == InteractTiming.CUSTOM) {
                    result = useNextSteps
                        ? breakCustomTickWithNextSteps(mc, action, target, direction, nextStepsFired, missingNextStepsWarned)
                        : breakCustomTick(mc, action, target, direction);
                } else {
                    result = useNextSteps
                        ? breakContinueWithNextSteps(mc, action, target, direction, nextStepsFired, missingNextStepsWarned)
                        : MacroExecutor.callOnClientThread(mc, () -> breakContinueOnClient(mc, action, target, direction), -1, action.isTickAligned());
                }

                if (result < 0) {
                    return;
                } else if (result == 1) {
                    boolean completedStartedBreak = started[0];
                    if (useNextSteps && completedStartedBreak) {
                        runBreakNextStepsAfterCompletion(mc, action, nextStepsFired, missingNextStepsWarned);
                    } else if (action.interact && action.interactTiming == InteractTiming.AFTER_PLUS) {
                        MacroExecutor.waitOneTick();
                        MacroExecutor.runOnClientThread(mc, () -> sendBlockInteract(mc, target, direction), action.isTickAligned());
                    }
                    completed++;
                    MacroExecutor.setCurrentStatus("Break " + completed + "/" + (targetTimes == 0 ? "\u221e" : targetTimes));
                    if (targetTimes > 0 && completed >= targetTimes) return;
                    started[0] = false;
                    nextStepsFired[0] = false;
                    waitForBreakSolid(mc, target);
                } else {
                    MacroExecutor.setCurrentStatus("Break " + target.getX() + "," + target.getY() + "," + target.getZ()
                        + " " + completed + "/" + (targetTimes == 0 ? "\u221e" : targetTimes));
                }
            }
        } finally {
            final boolean wasMining = started[0];
            MacroExecutor.runOnClientThread(mc, () -> {
                if (wasMining && mc.gameMode != null && mc.gameMode.isDestroying()) mc.gameMode.stopDestroyBlock();
                if (action.sneak) releaseMacroSneak(mc, action.sneakMode);
            }, action.isTickAligned());
            AutismInstaBreakRenderer.clearTarget(target);
        }
    }

    private static int startBreakWithNextSteps(Minecraft mc, BreakAction action, BlockPos pos, Direction dir,
                                               boolean[] started, boolean[] nextStepsFired,
                                               boolean[] missingNextStepsWarned) throws InterruptedException {
        MacroExecutor.BreakStartPlan plan = MacroExecutor.callOnClientThread(mc, () -> prepareBreakStartOnClient(mc, action, pos), new MacroExecutor.BreakStartPlan(-1, false, false));
        if (plan.result() != 0) return plan.result();

        if (plan.instant() && plan.interactFirst()) {
            runBreakNextStepsOnce(mc, action, nextStepsFired, missingNextStepsWarned);
            if (!MacroExecutor.isCurrentRunActive()) return -1;
        }

        int result = MacroExecutor.callOnClientThread(mc, () -> startPreparedBreakOnClient(mc, action, pos, dir, started), -1, action.isTickAligned());
        return result;
    }

    private static MacroExecutor.BreakStartPlan prepareBreakStartOnClient(Minecraft mc, BreakAction action, BlockPos pos) {
        if (mc.level == null || mc.gameMode == null || mc.player == null || mc.getConnection() == null) {
            return new MacroExecutor.BreakStartPlan(-1, false, false);
        }
        if (mc.level.isOutsideBuildHeight(pos)) return new MacroExecutor.BreakStartPlan(1, false, false);
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return new MacroExecutor.BreakStartPlan(1, false, false);

        if (action.sneak) holdMacroSneak(mc, action.sneakMode);
        if (action.autoTool) {
            AutismAutoTool.equipBestTool(mc, state, action.considerInventory, true);
            state = mc.level.getBlockState(pos);
            if (state.isAir()) return new MacroExecutor.BreakStartPlan(1, false, false);
        }

        float startDelta = state.getDestroyProgress(mc.player, mc.player.level(), pos);
        boolean instant = startDelta >= 1.0f;
        boolean interactFirst = action.interact && (action.interactTiming == InteractTiming.BEFORE
            || action.interactTiming == InteractTiming.WITH
            || (action.interactTiming == InteractTiming.CUSTOM && action.interactCustomMs < 0));
        return new MacroExecutor.BreakStartPlan(0, instant, interactFirst);
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
        MacroExecutor.BreakContinuePlan plan = MacroExecutor.callOnClientThread(mc, () -> inspectBreakContinueOnClient(mc, action, pos), new MacroExecutor.BreakContinuePlan(-1, false, false));
        if (plan.result() != 0) return plan.result();

        InteractTiming timing = action.interactTiming;
        if (timing == InteractTiming.BEFORE && plan.breaksNextTick()) {
            runBreakNextStepsOnce(mc, action, nextStepsFired, missingNextStepsWarned);
            if (!MacroExecutor.isCurrentRunActive()) return -1;
        }
        if (timing == InteractTiming.WITH && plan.breaksThisTick()) {
            runBreakNextStepsOnce(mc, action, nextStepsFired, missingNextStepsWarned);
            if (!MacroExecutor.isCurrentRunActive()) return -1;
        }

        int result = MacroExecutor.callOnClientThread(mc, () -> continueBreakOnClient(mc, action, pos, dir), -1, action.isTickAligned());
        return result;
    }

    private static MacroExecutor.BreakContinuePlan inspectBreakContinueOnClient(Minecraft mc, BreakAction action, BlockPos pos) {
        if (mc.level == null || mc.gameMode == null || mc.player == null || mc.getConnection() == null) {
            return new MacroExecutor.BreakContinuePlan(-1, false, false);
        }
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return new MacroExecutor.BreakContinuePlan(1, false, false);
        if (action.sneak) holdMacroSneak(mc, action.sneakMode);

        boolean breaksThisTick = willBreakThisTick(mc, pos, state);
        boolean breaksNextTick = !breaksThisTick && willBreakNextTick(mc, pos, state);
        return new MacroExecutor.BreakContinuePlan(0, breaksThisTick, breaksNextTick);
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
        boolean willBreak = Boolean.TRUE.equals(MacroExecutor.callOnClientThread(mc, () -> {
            if (mc.level == null || mc.player == null) return Boolean.FALSE;
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) return Boolean.FALSE;
            return willBreakThisTick(mc, pos, state);
        }, Boolean.FALSE));

        if (!willBreak) {
            return MacroExecutor.callOnClientThread(mc, () -> continueBreakOnClient(mc, action, pos, dir), -1, action.isTickAligned());
        }

        long ns = Math.abs((long) action.interactCustomMs) * 1_000_000L;
        int broke;
        if (action.interactCustomMs < 0) {
            runBreakNextStepsOnce(mc, action, nextStepsFired, missingNextStepsWarned);
            if (!MacroExecutor.isCurrentRunActive()) return -1;
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            broke = MacroExecutor.callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1, action.isTickAligned());
        } else {
            broke = MacroExecutor.callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1, action.isTickAligned());
        }
        return broke;
    }

    private static int breakCustomTick(Minecraft mc, BreakAction action, BlockPos pos, Direction dir) throws InterruptedException {
        boolean willBreak = Boolean.TRUE.equals(MacroExecutor.callOnClientThread(mc, () -> {
            if (mc.level == null || mc.player == null) return Boolean.FALSE;
            BlockState s = mc.level.getBlockState(pos);
            if (s.isAir()) return Boolean.FALSE;
            return willBreakThisTick(mc, pos, s);
        }, Boolean.FALSE));

        if (!willBreak) {

            return MacroExecutor.callOnClientThread(mc, () -> {
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

            MacroExecutor.runOnClientThread(mc, () -> sendBlockInteract(mc, pos, dir), action.isTickAligned());
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            broke = MacroExecutor.callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1, action.isTickAligned());
        } else {

            broke = MacroExecutor.callOnClientThread(mc, () -> breakAndDetectOnClient(mc, pos, dir), -1, action.isTickAligned());
            if (ns > 0) java.util.concurrent.locks.LockSupport.parkNanos(ns);
            MacroExecutor.runOnClientThread(mc, () -> sendBlockInteract(mc, pos, dir), action.isTickAligned());
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
        MacroExecutor.setCurrentStatus("Break: next " + names);
        AutismModule module = AutismModule.get();
        boolean previousSuppressPostGui = Boolean.TRUE.equals(MacroExecutor.SUPPRESS_POST_GUI_AFTER.get());
        try {
            MacroExecutor.SUPPRESS_POST_GUI_AFTER.set(true);
            for (MacroAction next : actions) {
                if (next == null || !next.isEnabled() || !MacroExecutor.isCurrentRunActive()) continue;
                try {
                    MacroExecutor.executeSingleActionWithWaits(mc, next, module);
                } catch (InterruptedException e) {
                    throw e;
                } catch (Throwable t) {
                    AutismClientMessaging.sendPrefixed("\u00a7cBreak next step failed (" + next.getDisplayName() + "): " + MacroRace.safeThrowableMessage(t));
                }
            }
        } finally {
            MacroExecutor.SUPPRESS_POST_GUI_AFTER.set(previousSuppressPostGui);
        }
    }

    private static void runBreakNextStepsAfterCompletion(Minecraft mc, BreakAction action, boolean[] fired,
                                                         boolean[] missingWarned) throws InterruptedException {
        if (!action.interact || !action.runNextSteps || action.interactTiming == null) return;
        switch (action.interactTiming) {
            case AFTER -> runBreakNextStepsOnce(mc, action, fired, missingWarned);
            case AFTER_PLUS -> {
                MacroExecutor.waitOneTick();
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

    public static void sendBlockInteract(Minecraft mc, BlockPos pos, Direction dir) {
        if (mc.gameMode == null || mc.player == null) return;
        Vec3 hit = Vec3.atCenterOf(pos).add(dir.getStepX() * 0.5, dir.getStepY() * 0.5, dir.getStepZ() * 0.5);
        BlockHitResult blockHit = new BlockHitResult(hit, dir, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
    }

    private static void waitForBreakSolid(Minecraft mc, BlockPos pos) throws InterruptedException {
        while (MacroExecutor.isCurrentRunActive()) {
            if (!isInstaBreakAir(mc, pos)) return;
            Thread.sleep(1);
        }
    }

    private static void waitBreakClientTicks(int ticks) throws InterruptedException {
        long start = AutismSharedState.get().getClientTickCounter();
        while (MacroExecutor.isCurrentRunActive() && (AutismSharedState.get().getClientTickCounter() - start) < ticks) {
            Thread.sleep(1);
        }
    }

    private static void holdMacroSneak(Minecraft mc, String mode) {
        autismclient.util.AutismMacroSneak.hold(mc, mode);
    }

    private static void releaseMacroSneak(Minecraft mc, String mode) {
        autismclient.util.AutismMacroSneak.release(mc, mode);
        if (!autismclient.util.AutismMacroSneak.isPacket(mode)) MacroExecutor.applyAggregateSneakState(mc);
    }
}
