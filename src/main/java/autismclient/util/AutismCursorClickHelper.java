package autismclient.util;

import autismclient.modules.PackHideState;
import autismclient.util.macro.ItemTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AutismCursorClickHelper {
    private static int originContainerId = -1;
    private static int originSlotId = -1;
    private static ItemStack originStack = ItemStack.EMPTY;

    private AutismCursorClickHelper() {
    }

    public static boolean click(Minecraft mc) {
        return click(mc, null, 1);
    }

    public static boolean click(Minecraft mc, ItemTarget carriedGuard) {
        return click(mc, carriedGuard, 1);
    }

    public static boolean click(Minecraft mc, ItemTarget carriedGuard, int times) {
        if (PackHideState.isHardLocked()) return false;
        if (mc == null) return false;
        int clickCount = Math.max(1, times);
        if (mc.isSameThread()) {
            return clickNow(mc, carriedGuard, clickCount);
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean clicked = new AtomicBoolean(false);
        mc.execute(() -> {
            try {
                clicked.set(clickNow(mc, carriedGuard, clickCount));
            } finally {
                latch.countDown();
            }
        });

        try {
            if (!latch.await(2, TimeUnit.SECONDS)) return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return clicked.get();
    }

    private static boolean clickNow(Minecraft mc, ItemTarget carriedGuard, int times) {
        if (PackHideState.isHardLocked()) return false;
        if (mc == null || mc.player == null || mc.gameMode == null || mc.getConnection() == null) return false;
        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler == null) return false;

        boolean clickedAny = false;
        for (int i = 0; i < times; i++) {
            ItemStack carried = handler.getCarried();
            if (carried == null || carried.isEmpty()) break;
            if (!matchesCarriedGuard(carriedGuard, carried)) break;

            int triggerSlot = findOriginTriggerSlot(mc, handler, carried);
            if (triggerSlot < 0) break;

            try {
                mc.gameMode.handleContainerInput(handler.containerId, triggerSlot, 0, ContainerInput.PICKUP_ALL, mc.player);
                refreshOriginStack(handler);
                clickedAny = true;
            } catch (RuntimeException ignored) {
                break;
            }
        }
        return clickedAny;
    }

    public static void recordAfterContainerClick(
            Minecraft mc,
            AbstractContainerMenu handler,
            int slotId,
            int button,
            ContainerInput input,
            ItemStack beforeCarried,
            ItemStack beforeSlot
    ) {
        if (mc == null || mc.player == null || handler == null || input == null) return;
        ItemStack afterCarried = handler.getCarried();
        if (afterCarried == null || afterCarried.isEmpty()) {
            clearOrigin();
            return;
        }

        if (input == ContainerInput.PICKUP
                && slotId >= 0
                && slotId < handler.slots.size()
                && (button == 0 || button == 1)
                && (beforeCarried == null || beforeCarried.isEmpty())
                && beforeSlot != null
                && !beforeSlot.isEmpty()
                && ItemStack.isSameItemSameComponents(beforeSlot, afterCarried)) {
            originContainerId = handler.containerId;
            originSlotId = slotId;
            originStack = afterCarried.copy();
            return;
        }

        if (originContainerId == handler.containerId
                && originSlotId >= 0
                && ItemStack.isSameItemSameComponents(originStack, afterCarried)) {
            originStack = afterCarried.copy();
        } else {
            clearOrigin();
        }
    }

    private static int findOriginTriggerSlot(Minecraft mc, AbstractContainerMenu handler, ItemStack carried) {
        if (originContainerId != handler.containerId || originSlotId < 0 || originSlotId >= handler.slots.size()) return -1;
        if (originStack.isEmpty() || !ItemStack.isSameItemSameComponents(originStack, carried)) return -1;
        Slot slot = handler.slots.get(originSlotId);
        return isSafePickupAllTrigger(mc, slot, carried) ? originSlotId : -1;
    }

    private static boolean isSafePickupAllTrigger(Minecraft mc, Slot slot, ItemStack carried) {
        if (mc == null || mc.player == null || slot == null || !slot.isActive()) return false;
        if (!slot.hasItem()) return true;
        return !slot.mayPickup(mc.player) && !ItemStack.isSameItemSameComponents(slot.getItem(), carried);
    }

    private static void refreshOriginStack(AbstractContainerMenu handler) {
        if (handler == null || originContainerId != handler.containerId) return;
        ItemStack carried = handler.getCarried();
        if (carried == null || carried.isEmpty()) clearOrigin();
        else originStack = carried.copy();
    }

    private static void clearOrigin() {
        originContainerId = -1;
        originSlotId = -1;
        originStack = ItemStack.EMPTY;
    }

    private static boolean matchesCarriedGuard(ItemTarget target, ItemStack carried) {
        if (target == null || !target.hasIdentity()) return true;
        ItemTarget identityOnly = target.copy();
        identityOnly.slot = -1;
        return identityOnly.matches(carried, -1);
    }
}
