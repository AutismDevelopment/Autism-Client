package autismclient.util.macro;

import autismclient.util.AutismClientMessaging;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class MacroXCarry {
    private MacroXCarry() {
    }
    public static void runXCarryClickPutIn(Minecraft mc, XCarryAction xsa) throws InterruptedException {
        if (mc == null || mc.player == null || mc.gameMode == null) return;

        if (xsa.requiresCursorStorage()) {
            MacroExecutor.setCurrentStatus("XCarry Cursor");
            MacroClientThread.runOnClientThread(mc, () -> xsa.execute(mc));
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

        MacroClientThread.runOnClientThread(mc, () -> {
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
            MacroClientThread.runOnClientThread(mc, () -> xsa.execute(mc));
            return;
        }

        if (usingContainer.get()) {
            for (int slotId : containerSlotIdsRef.get()) {
                if (!MacroExecutor.isCurrentRunActive()) return;
                MacroExecutor.setCurrentStatus("XCarry Click: collect");
                MacroClientThread.runOnClientThread(mc, () -> {
                    if (mc.player == null || mc.gameMode == null) return;
                    net.minecraft.world.inventory.AbstractContainerMenu handler = containerHandlerRef.get();
                    if (handler == null || mc.player.containerMenu != handler) return;
                    if (slotId < 0 || slotId >= handler.slots.size()) return;
                    if (handler.slots.get(slotId).getItem().isEmpty()) return;
                    mc.gameMode.handleContainerInput(handler.containerId, slotId, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player);
                });
                MacroExecutor.waitOneTick();
            }

            MacroClientThread.runOnClientThread(mc, () -> {
                if (mc.player == null || mc.getConnection() == null) return;
                net.minecraft.world.inventory.AbstractContainerMenu handler = containerHandlerRef.get();
                if (handler == null) return;
                mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(handler.containerId));
            });
            MacroExecutor.waitOneTick();

            MacroClientThread.runOnClientThread(mc, () -> {
                if (mc.player != null) {
                    mc.player.containerMenu = mc.player.inventoryMenu;
                }
            });
            MacroExecutor.waitOneTick();
        }

        MacroClientThread.runOnClientThread(mc, xsa::preparePutInStorageGuard);

        int xCarryAttempts = 0;
        while (MacroExecutor.isCurrentRunActive() && xCarryAttempts++ < 512) {
            XCarryAction.PutInMove move = MacroClientThread.callOnClientThread(mc, () -> {
                if (mc.player == null) return null;
                return xsa.findNextPutInMove(mc.player.inventoryMenu);
            }, null);
            if (move == null) break;

            MacroExecutor.setCurrentStatus("XCarry Click -> " + move.targetSlotId + " x" + move.count);
            int targetBefore = MacroClientThread.callOnClientThread(mc, () -> {
                if (mc.player == null) return 0;
                net.minecraft.world.inventory.InventoryMenu handler = mc.player.inventoryMenu;
                if (move.targetSlotId < 0 || move.targetSlotId >= handler.slots.size()) return 0;
                return handler.slots.get(move.targetSlotId).getItem().getCount();
            }, 0);

            boolean moved = MacroClientThread.callOnClientThread(mc, () -> {
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

            MacroExecutor.waitOneTick();
        }

        MacroClientThread.runOnClientThread(mc, () -> {
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
            MacroClientThread.runOnClientThread(mc, () -> {
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
            MacroExecutor.waitOneTick();
        }
        } finally {
            sharedState.setXCarryArmorBypass(prevBypass);
        }
    }

    public static void runXCarrySafeClickPutIn(Minecraft mc, XCarryAction xsa) throws InterruptedException {
        if (mc == null || mc.player == null || mc.gameMode == null) return;

        if (xsa.requiresCursorStorage()) {
            MacroExecutor.setCurrentStatus("XCarry Safe: Cursor");
            MacroClientThread.runOnClientThread(mc, () -> xsa.execute(mc));
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

        MacroClientThread.runOnClientThread(mc, () -> {
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
            MacroClientThread.runOnClientThread(mc, () -> xsa.execute(mc));
            return;
        }

        if (usingContainer.get()) {
            for (int slotId : containerSlotIdsRef.get()) {
                if (!MacroExecutor.isCurrentRunActive()) return;
                MacroExecutor.setCurrentStatus("XCarry Safe: collect");
                sendContainerClickThenTick(mc, containerHandlerRef.get(), slotId, 0, ContainerInput.QUICK_MOVE);
            }

            MacroClientThread.runOnClientThread(mc, () -> {
                if (mc.player == null || mc.getConnection() == null) return;
                net.minecraft.world.inventory.AbstractContainerMenu handler = containerHandlerRef.get();
                if (handler == null) return;
                mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(handler.containerId));
            });
            MacroExecutor.waitOneTick();

            MacroClientThread.runOnClientThread(mc, () -> {
                if (mc.player != null) {
                    mc.player.containerMenu = mc.player.inventoryMenu;
                }
            });
            MacroExecutor.waitOneTick();
        }

        MacroClientThread.runOnClientThread(mc, xsa::preparePutInStorageGuard);

        int safeDelayTicks = XCarryAction.clampSafeClickDelayTicks(xsa.safeClickDelayTicks);
        boolean delayAfterPickup = xsa.safeClickDelayAfterPickup;
        boolean delayBeforeReturn = xsa.safeClickDelayBeforeReturn;
        int attempts = 0;
        while (MacroExecutor.isCurrentRunActive() && attempts++ < 512) {
            List<XCarryAction.PutInMove> group = MacroClientThread.callOnClientThread(mc, () -> nextXCarrySafeGroup(mc, xsa), List.of());
            if (group.isEmpty()) break;

            XCarryAction.PutInMove first = group.get(0);
            int total = 0;
            for (XCarryAction.PutInMove move : group) total += Math.max(0, move.count());
            MacroExecutor.setCurrentStatus("XCarry Safe -> " + group.size() + " slots x" + total);

            if (!executeXCarrySafeGroup(mc, group, safeDelayTicks, delayAfterPickup, delayBeforeReturn)) {
                rollbackXCarryCursorSafe(mc, first.sourceSlotId(), safeDelayTicks, delayBeforeReturn);
                break;
            }
        }

        MacroClientThread.runOnClientThread(mc, () -> {
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
            MacroClientThread.runOnClientThread(mc, () -> {
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
            MacroExecutor.waitOneTick();
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
        SafeGroupSnapshot snapshot = MacroClientThread.callOnClientThread(mc, () -> snapshotXCarrySafeGroup(mc, group), SafeGroupSnapshot.invalid());
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
        if (!Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc, () -> {
            InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
            return handler != null
                    && handler.getCarried().isEmpty()
                    && xcarrySafeSnapshotStillMatches(handler, group, snapshot);
        }, Boolean.FALSE))) {
            return SafeGroupSnapshot.invalid();
        }

        SafeQuickCraftPlan quickCraftPlan = MacroClientThread.callOnClientThread(mc, () -> {
            InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
            return createXCarryQuickCraftPlan(handler, group, snapshot);
        }, SafeQuickCraftPlan.invalid());

        SafeGroupSnapshot expected = snapshot;
        if (quickCraftPlan.valid) {
            MacroExecutor.setCurrentStatus("XCarry Safe drag -> " + group.size() + " slots x" + quickCraftPlan.placeCount);
            int start = net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(0, quickCraftPlan.quickCraftType);
            int add = net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(1, quickCraftPlan.quickCraftType);
            int end = net.minecraft.world.inventory.AbstractContainerMenu.getQuickcraftMask(2, quickCraftPlan.quickCraftType);
            int nextDragIndex = 0;
            if (delayAfterPickup) {
                if (!sendXCarrySafeInventoryClick(mc, first.sourceSlotId(), 0, ContainerInput.PICKUP)) {
                    return SafeGroupSnapshot.invalid();
                }
                waitXCarrySafeAfterPickupDelay(delayTicks, true);
                if (!Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc, () -> {
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

        boolean hasLeftover = Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc, () -> {
            InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
            return handler != null && !handler.getCarried().isEmpty();
        }, Boolean.FALSE));
        if (hasLeftover) {
            if (delayBeforeReturn) {
                waitXCarrySafeDelayTicks(delayTicks);
            }
            int returnSlot = MacroClientThread.callOnClientThread(mc, () -> findXCarrySafeReturnSlot(mc, first.sourceSlotId()), -1);
            if (returnSlot < 0) return SafeGroupSnapshot.invalid();
            if (!sendXCarrySafeInventoryClick(mc, returnSlot, 0, ContainerInput.PICKUP)) return SafeGroupSnapshot.invalid();
        }

        boolean cursorEmpty = Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc, () -> {
            InventoryMenu handler = mc.player == null ? null : mc.player.inventoryMenu;
            return handler != null && handler.getCarried().isEmpty();
        }, Boolean.FALSE));
        return cursorEmpty ? expected : SafeGroupSnapshot.invalid();
    }

    private static boolean placeXCarrySafeDirectCount(Minecraft mc, int targetSlotId, int count, int delayTicks) throws InterruptedException {
        if (mc == null || count <= 0) return false;
        int remaining = count;
        while (remaining > 0 && MacroExecutor.isCurrentRunActive()) {
            int carriedCount = MacroClientThread.callOnClientThread(mc, () -> {
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
        return Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc, () -> {
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
        for (int i = 0; i < ticks && MacroExecutor.isCurrentRunActive(); i++) {
            MacroExecutor.waitOneTick();
        }
    }

    private static void waitXCarrySafeAfterPickupDelay(int delayTicks, boolean enabled) {
        if (enabled) {
            waitXCarrySafeDelayTicks(delayTicks);
        }
    }

    private static boolean sendInventoryClickThenTick(Minecraft mc, int slotId, int button, ContainerInput input) throws InterruptedException {
        if (mc == null || mc.player == null || mc.gameMode == null) return false;
        boolean clicked = MacroClientThread.callOnClientThread(mc, () -> {
            if (mc.player == null || mc.gameMode == null) return false;
            InventoryMenu handler = mc.player.inventoryMenu;
            mc.player.containerMenu = handler;
            if (slotId < 0 || slotId >= handler.slots.size()) return false;
            mc.gameMode.handleContainerInput(handler.containerId, slotId, button, input, mc.player);
            return true;
        }, Boolean.FALSE);
        if (!clicked) return false;
        MacroExecutor.waitOneTick();
        return true;
    }

    private static boolean sendContainerClickThenTick(Minecraft mc, net.minecraft.world.inventory.AbstractContainerMenu handler,
                                                      int slotId, int button, ContainerInput input) throws InterruptedException {
        if (mc == null || handler == null || input == null) return false;
        boolean clicked = MacroClientThread.callOnClientThread(mc, () -> {
            if (mc.player == null || mc.gameMode == null || mc.player.containerMenu != handler) return false;
            if (slotId < 0 || slotId >= handler.slots.size()) return false;
            if (handler.slots.get(slotId).getItem().isEmpty()) return false;
            mc.gameMode.handleContainerInput(handler.containerId, slotId, button, input, mc.player);
            return true;
        }, Boolean.FALSE);
        if (!clicked) return false;
        MacroExecutor.waitOneTick();
        return true;
    }

    private static boolean waitForXCarrySafeLocal(Minecraft mc, int ticks, java.util.function.BooleanSupplier accepted) throws InterruptedException {
        int maxTicks = Math.max(1, ticks);
        for (int i = 0; i < maxTicks && MacroExecutor.isCurrentRunActive(); i++) {
            if (Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc, () -> accepted != null && accepted.getAsBoolean(), Boolean.FALSE))) return true;
            MacroExecutor.waitOneTick();
        }
        return Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc, () -> accepted != null && accepted.getAsBoolean(), Boolean.FALSE));
    }

    private static void waitForXCarrySafeSettle(Minecraft mc, long startRevision, int minTicks, long maxMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(50L, maxMs));
        int ticks = Math.max(1, minTicks);
        for (int i = 0; i < ticks && MacroExecutor.isCurrentRunActive(); i++) MacroExecutor.waitOneTick();
        while (MacroExecutor.isCurrentRunActive() && System.nanoTime() < deadline) {
            long revision = MacroConditionRegistry.inventorySyncRevision();
            if (revision != startRevision) {
                MacroExecutor.waitOneTick();
                return;
            }
            MacroExecutor.waitOneTick();
        }
    }

    private static boolean waitForXCarrySafeFinalState(Minecraft mc, SafeGroupSnapshot snapshot, long startRevision) throws InterruptedException {
        waitForXCarrySafeSettle(mc, startRevision, 2, 250L);
        if (Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc, () -> xcarrySafeFinalMatches(mc, snapshot), Boolean.FALSE))) return true;
        waitForXCarrySafeSettle(mc, MacroConditionRegistry.inventorySyncRevision(), 3, 350L);
        return Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc, () -> xcarrySafeFinalMatches(mc, snapshot), Boolean.FALSE));
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
        if (MacroClientThread.callOnClientThread(mc, () -> mc.player != null && mc.player.inventoryMenu.getCarried().isEmpty(), Boolean.FALSE)) {
            return true;
        }

        int returnSlot = MacroClientThread.callOnClientThread(mc, () -> findXCarrySafeReturnSlot(mc, preferredSlotId), -1);
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
        if (MacroClientThread.callOnClientThread(mc, () -> mc.player != null && mc.player.inventoryMenu.getCarried().isEmpty(), Boolean.FALSE)) return;
        int returnSlot = MacroClientThread.callOnClientThread(mc, () -> findXCarrySafeReturnSlot(mc, sourceSlotId), -1);
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
        MacroClientThread.runOnClientThread(mc, () -> {
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
        while (MacroExecutor.isCurrentRunActive()) {
            if (Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc, condition, Boolean.FALSE))) {
                return true;
            }
            if (System.nanoTime() >= deadline) break;
            MacroExecutor.waitOneTick();
        }
        return Boolean.TRUE.equals(MacroClientThread.callOnClientThread(mc, condition, Boolean.FALSE));
    }
}
