package autismclient.util;

import autismclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AutismInventoryClickHelper {
    private AutismInventoryClickHelper() {
    }

    public static boolean click(Minecraft mc, int handlerSlotId, int button, ContainerInput input) {
        if (PackHideState.isHardLocked()) return false;
        if (mc == null) return false;
        if (mc.isSameThread()) {
            return clickNow(mc, handlerSlotId, button, input);
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean clicked = new AtomicBoolean(false);
        mc.execute(() -> {
            try {
                clicked.set(clickNow(mc, handlerSlotId, button, input));
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

    private static boolean clickNow(Minecraft mc, int handlerSlotId, int button, ContainerInput input) {
        if (PackHideState.isHardLocked()) return false;
        if (mc == null || mc.player == null || mc.gameMode == null || mc.getConnection() == null || input == null) return false;
        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler == null || handlerSlotId < 0 || handlerSlotId >= handler.slots.size()) return false;
        ItemStack beforeCarried = handler.getCarried().copy();
        ItemStack beforeSlot = handler.slots.get(handlerSlotId).getItem().copy();

        try {
            mc.gameMode.handleContainerInput(handler.containerId, handlerSlotId, button, input, mc.player);
            AutismCursorClickHelper.recordAfterContainerClick(mc, handler, handlerSlotId, button, input, beforeCarried, beforeSlot);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
