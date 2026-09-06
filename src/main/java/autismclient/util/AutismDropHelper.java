package autismclient.util;

import autismclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class AutismDropHelper {
    private AutismDropHelper() {
    }

    public static int dropFromHandlerSlot(Minecraft mc, int handlerSlotId, int count) {
        if (PackHideState.isHardLocked()) return 0;
        if (mc == null || mc.player == null || mc.gameMode == null) return 0;
        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler == null || handlerSlotId < 0 || handlerSlotId >= handler.slots.size()) return 0;

        ItemStack stack = handler.slots.get(handlerSlotId).getItem();
        if (stack.isEmpty()) return 0;

        int syncId = handler.containerId;
        if (count <= 0 || count >= stack.getCount()) {
            mc.gameMode.handleContainerInput(syncId, handlerSlotId, 1, ContainerInput.THROW, mc.player);
            return 1;
        }

        int sent = 0;
        int drops = Math.min(count, stack.getCount());
        for (int i = 0; i < drops; i++) {
            mc.gameMode.handleContainerInput(syncId, handlerSlotId, 0, ContainerInput.THROW, mc.player);
            sent++;
        }
        return sent;
    }

    public static int dropFromInventorySlot(Minecraft mc, int inventorySlotId, int count) {
        if (PackHideState.isHardLocked()) return 0;
        if (mc == null || mc.player == null) return 0;
        if (inventorySlotId < 0 || inventorySlotId >= 36) return 0;
        int handlerSlotId = AutismInventoryHelper.toHandlerSlot(mc, inventorySlotId);
        return dropFromHandlerSlot(mc, handlerSlotId, count);
    }
}
