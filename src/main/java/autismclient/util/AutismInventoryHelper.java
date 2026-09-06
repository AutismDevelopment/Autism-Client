package autismclient.util;

import autismclient.mixin.accessor.AutismMultiPlayerGameModeAccessor;
import autismclient.modules.PackHideState;
import autismclient.util.macro.ItemTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;

public final class AutismInventoryHelper {
    public static final int PLAYER_VISIBLE_SLOT_COUNT = 41;
    public static final int FIRST_GUI_SLOT = 100;

    private AutismInventoryHelper() {
    }

    public static void selectHotbarSlot(Minecraft mc, int slot) {
        if (PackHideState.isHardLocked()) return;
        selectHotbarSlotInternal(mc, slot);
    }

    public static void restoreHotbarSlot(Minecraft mc, int slot) {
        selectHotbarSlotInternal(mc, slot);
    }

    private static void selectHotbarSlotInternal(Minecraft mc, int slot) {
        if (mc == null || mc.player == null || mc.gameMode == null || mc.getConnection() == null) return;

        int clampedSlot = Math.max(0, Math.min(8, slot));
        if (mc.player.getInventory().getSelectedSlot() == clampedSlot) return;

        mc.player.getInventory().setSelectedSlot(clampedSlot);
        ((AutismMultiPlayerGameModeAccessor) mc.gameMode).autism$ensureHasSentCarriedItem();
    }

    public static boolean swapInventoryWithHotbar(Minecraft mc, int inventorySlot, int hotbarSlot) {
        if (PackHideState.isHardLocked()) return false;
        return swapInventoryWithHotbarInternal(mc, inventorySlot, hotbarSlot);
    }

    public static boolean restoreInventoryHotbarSwap(Minecraft mc, int inventorySlot, int hotbarSlot) {
        return swapInventoryWithHotbarInternal(mc, inventorySlot, hotbarSlot);
    }

    private static boolean swapInventoryWithHotbarInternal(Minecraft mc, int inventorySlot, int hotbarSlot) {
        if (mc == null || mc.player == null || mc.gameMode == null) return false;
        if (inventorySlot < 9 || inventorySlot >= 36 || hotbarSlot < 0 || hotbarSlot >= 9) return false;
        if (!mc.player.containerMenu.getCarried().isEmpty()) return false;
        int sourceHandlerSlot = toHandlerSlot(mc, inventorySlot);
        if (sourceHandlerSlot < 0) return false;
        mc.gameMode.handleContainerInput(
            mc.player.containerMenu.containerId,
            sourceHandlerSlot,
            hotbarSlot,
            ContainerInput.SWAP,
            mc.player
        );
        return true;
    }

    public static boolean swapInventorySlots(Minecraft mc, int fromSlot, int toSlot) {
        if (PackHideState.isHardLocked()) return false;
        if (mc == null || mc.player == null || mc.gameMode == null) return false;
        if (fromSlot == toSlot) return true;
        if (fromSlot < 0 || fromSlot >= PLAYER_VISIBLE_SLOT_COUNT || toSlot < 0 || toSlot >= PLAYER_VISIBLE_SLOT_COUNT) return false;
        if (!mc.player.containerMenu.getCarried().isEmpty()) return false;

        int fromScreenSlot = toHandlerSlot(mc, fromSlot);
        int toScreenSlot = toHandlerSlot(mc, toSlot);
        if (fromScreenSlot < 0 || toScreenSlot < 0) return false;

        return swapHandlerSlots(mc, fromScreenSlot, toScreenSlot);
    }

    public static int findInventorySlotByName(Minecraft mc, String itemName) {
        return findInventorySlot(mc, ItemTarget.fromLegacyEntry(itemName));
    }

    public static int findInventorySlot(Minecraft mc, ItemTarget target) {
        if (mc == null || mc.player == null || target == null || !target.hasIdentity()) return -1;

        int bestSlot = -1;
        int bestScore = -1;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            int score = target.score(stack, slot);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestScore >= 0 ? bestSlot : -1;
    }

    public static int findUserVisibleSlotByName(Minecraft mc, String itemName) {
        return findUserVisibleSlot(mc, ItemTarget.fromLegacyEntry(itemName));
    }

    public static int findUserVisibleSlot(Minecraft mc, ItemTarget target) {
        if (mc == null || mc.player == null || target == null || !target.hasIdentity()) return -1;

        int bestSlot = -1;
        int bestScore = -1;
        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler != null) {
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot = handler.slots.get(i);
                if (slot == null) continue;
                int visibleSlot = toUserVisibleSlot(mc, i);
                int score = target.score(slot.getItem(), visibleSlot);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = visibleSlot;
                }
            }
        }

        if (bestScore >= 0) return bestSlot;
        return findInventorySlot(mc, target);
    }

    public static int selectHotbarItemByName(Minecraft mc, String itemName, int preferredHotbarSlot) {
        return selectHotbarItem(mc, ItemTarget.fromLegacyEntry(itemName), preferredHotbarSlot);
    }

    public static int selectHotbarItem(Minecraft mc, ItemTarget target, int preferredHotbarSlot) {
        if (mc == null || mc.player == null || target == null || !target.hasIdentity()) return -1;

        int bestHotbarSlot = -1;
        int bestHotbarScore = -1;
        for (int slot = 0; slot < 9; slot++) {
            int score = target.score(mc.player.getInventory().getItem(slot), slot);
            if (score > bestHotbarScore) {
                bestHotbarScore = score;
                bestHotbarSlot = slot;
            }
        }
        if (bestHotbarScore >= 0) {
            selectHotbarSlot(mc, bestHotbarSlot);
            return bestHotbarSlot;
        }

        int bestInventorySlot = -1;
        int bestInventoryScore = -1;
        for (int slot = 9; slot < 36; slot++) {
            int score = target.score(mc.player.getInventory().getItem(slot), slot);
            if (score > bestInventoryScore) {
                bestInventoryScore = score;
                bestInventorySlot = slot;
            }
        }
        if (bestInventoryScore < 0) return -1;

        int targetHotbarSlot = Math.max(0, Math.min(8, preferredHotbarSlot));
        if (!swapInventorySlots(mc, bestInventorySlot, targetHotbarSlot)) return -1;
        selectHotbarSlot(mc, targetHotbarSlot);
        return targetHotbarSlot;
    }

    public static int toUserVisibleSlot(Minecraft mc, int handlerSlotId) {
        if (mc == null || mc.player == null || mc.player.containerMenu == null) return handlerSlotId;
        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handlerSlotId < 0 || handlerSlotId >= handler.slots.size()) return handlerSlotId;

        Slot slot = handler.slots.get(handlerSlotId);
        int playerSlot = getInventorySlot(mc, slot);
        if (playerSlot >= 0) {
            return playerSlot;
        }

        int extraOrdinal = 0;
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot currentSlot = handler.slots.get(i);
            if (currentSlot == null || isInventorySlot(mc, currentSlot)) continue;
            if (i == handlerSlotId) return FIRST_GUI_SLOT + extraOrdinal;
            extraOrdinal++;
        }

        return handlerSlotId;
    }

    public static int toUserVisibleSlot(Minecraft mc, Slot slot) {
        int handlerSlotId = toMenuSlotId(mc, slot);
        return handlerSlotId >= 0 ? toUserVisibleSlot(mc, handlerSlotId) : -1;
    }

    public static int toHandlerSlot(Minecraft mc, int userVisibleSlot) {
        if (mc == null || mc.player == null || mc.player.containerMenu == null) return -1;
        AbstractContainerMenu handler = mc.player.containerMenu;

        if (userVisibleSlot >= 0 && userVisibleSlot < mc.player.getInventory().getContainerSize()) {
            for (int i = 0; i < handler.slots.size(); i++) {
                if (getInventorySlot(mc, handler.slots.get(i)) == userVisibleSlot) return i;
            }
        }

        if (userVisibleSlot >= FIRST_GUI_SLOT) {
            int extraOrdinal = userVisibleSlot - FIRST_GUI_SLOT;
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot = handler.slots.get(i);
                if (slot == null || isInventorySlot(mc, slot)) continue;
                if (extraOrdinal == 0) return i;
                extraOrdinal--;
            }
        }

        return -1;
    }

    public static int resolveConfiguredHandlerSlot(Minecraft mc, int configuredSlot) {
        return toHandlerSlot(mc, configuredSlot);
    }

    public static boolean isInventorySlot(Minecraft mc, Slot slot) {
        return getInventorySlot(mc, slot) >= 0;
    }

    public static int toMenuSlotId(Minecraft mc, Slot targetSlot) {
        if (mc == null || mc.player == null || mc.player.containerMenu == null || targetSlot == null) return -1;
        AbstractContainerMenu handler = mc.player.containerMenu;
        for (int i = 0; i < handler.slots.size(); i++) {
            if (handler.slots.get(i) == targetSlot) return i;
        }
        return -1;
    }

    public static boolean swapHandlerSlots(Minecraft mc, int fromScreenSlot, int toScreenSlot) {
        if (PackHideState.isHardLocked()) return false;
        if (mc == null || mc.player == null || mc.gameMode == null) return false;
        if (fromScreenSlot == toScreenSlot) return true;
        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler == null) return false;
        if (fromScreenSlot < 0 || fromScreenSlot >= handler.slots.size()) return false;
        if (toScreenSlot < 0 || toScreenSlot >= handler.slots.size()) return false;
        if (!handler.getCarried().isEmpty()) return false;

        int syncId = handler.containerId;
        mc.gameMode.handleContainerInput(syncId, fromScreenSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(syncId, toScreenSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(syncId, fromScreenSlot, 0, ContainerInput.PICKUP, mc.player);
        return true;
    }

    private static int getInventorySlot(Minecraft mc, Slot slot) {
        if (mc == null || mc.player == null || slot == null || slot.container != mc.player.getInventory()) return -1;
        int index = slot.getContainerSlot();
        return index >= 0 && index < mc.player.getInventory().getContainerSize() ? index : -1;
    }
}
