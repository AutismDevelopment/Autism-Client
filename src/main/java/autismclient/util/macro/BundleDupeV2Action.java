package autismclient.util.macro;

import autismclient.modules.PackHideState;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;

public class BundleDupeV2Action implements MacroAction {
    public int delayAfterPickingUpMs = 30;
    public int delayAfterPuttingBackMs = 20;
    public int bundlePacketCount = 20;
    public int dropDelayMs = 50;
    public int hotbarSlot = 0;
    public int bundleIndex = -1;
    public int maxCycles = 0;
    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {
        if (PackHideState.isHardLocked()) return;
        if (!isReady(mc)) return;
        int cycles = 0;
        while (MacroExecutor.isCurrentActionRunActive() && !Thread.currentThread().isInterrupted()) {
            if (PackHideState.isHardLocked()) return;
            if (!hasConnection(mc)) return;
            int handlerSlot = handlerSlot();
            sendClick(mc, handlerSlot, 1);
            sendBundleSelectBurst(mc, handlerSlot);
            if (!sleep(delayAfterPickingUpMs)) return;

            sendClick(mc, handlerSlot, 0);
            if (!sleep(delayAfterPuttingBackMs)) return;

            ItemStack firstStack = slotStack(mc);
            if (firstStack.isEmpty() || firstStack.is(Items.BUNDLE)) {
                cycles++;
                if (maxCycles > 0 && cycles >= maxCycles) return;
                continue;
            }

            sendClick(mc, handlerSlot, 1);
            if (!sleep(dropDelayMs)) return;
            sendClick(mc, -999, 0);
            if (!sleep(dropDelayMs)) return;

            cycles++;
            if (maxCycles > 0 && cycles >= maxCycles) return;
        }
    }

    private boolean isReady(Minecraft mc) {
        if (!hasConnection(mc) || !(mc.player.containerMenu instanceof InventoryMenu)) return false;
        ItemStack stack = slotStack(mc);
        if (stack.isEmpty() || !stack.is(Items.BUNDLE)) return false;
        BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
        return contents != null && contents.size() == 1;
    }

    private boolean hasConnection(Minecraft mc) {
        return mc != null && mc.player != null && mc.getConnection() != null;
    }

    private ItemStack slotStack(Minecraft mc) {
        if (mc == null || mc.player == null) return ItemStack.EMPTY;
        int slot = Math.max(0, Math.min(8, hotbarSlot));
        return mc.player.getInventory().getItem(slot);
    }

    private int handlerSlot() {
        return 36 + Math.max(0, Math.min(8, hotbarSlot));
    }

    private void sendBundleSelectBurst(Minecraft mc, int handlerSlot) {
        int count = Math.max(1, Math.min(10_000, bundlePacketCount));
        for (int i = 0; i < count && MacroExecutor.isCurrentActionRunActive(); i++) {
            mc.getConnection().send(new ServerboundSelectBundleItemPacket(handlerSlot, bundleIndex));
        }
    }

    private void sendClick(Minecraft mc, int slot, int button) {
        if (!hasConnection(mc) || mc.player.containerMenu == null) return;
        mc.getConnection().send(new ServerboundContainerClickPacket(
            mc.player.containerMenu.containerId,
            mc.player.containerMenu.getStateId(),
            (short) slot,
            (byte) button,
            ContainerInput.PICKUP,
            new Int2ObjectOpenHashMap<>(),
            HashedStack.EMPTY
        ));
    }

    private boolean sleep(int ms) {
        int remaining = Math.max(0, ms);
        long deadline = System.currentTimeMillis() + remaining;
        while (MacroExecutor.isCurrentActionRunActive() && !Thread.currentThread().isInterrupted()) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0L) return true;
            try {
                Thread.sleep(Math.min(10L, left));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putInt("delayAfterPickingUpMs", Math.max(0, delayAfterPickingUpMs));
        tag.putInt("delayAfterPuttingBackMs", Math.max(0, delayAfterPuttingBackMs));
        tag.putInt("bundlePacketCount", Math.max(1, bundlePacketCount));
        tag.putInt("dropDelayMs", Math.max(0, dropDelayMs));
        tag.putInt("hotbarSlot", Math.max(0, Math.min(8, hotbarSlot)));
        tag.putInt("bundleIndex", bundleIndex);
        tag.putInt("maxCycles", Math.max(0, maxCycles));
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        delayAfterPickingUpMs = Math.max(0, tag.getIntOr("delayAfterPickingUpMs", 30));
        delayAfterPuttingBackMs = Math.max(0, tag.getIntOr("delayAfterPuttingBackMs", 20));
        bundlePacketCount = Math.max(1, tag.getIntOr("bundlePacketCount", 20));
        dropDelayMs = Math.max(0, tag.getIntOr("dropDelayMs", 50));
        hotbarSlot = Math.max(0, Math.min(8, tag.getIntOr("hotbarSlot", 0)));
        bundleIndex = tag.getIntOr("bundleIndex", -1);
        maxCycles = Math.max(0, tag.getIntOr("maxCycles", 0));
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
    }

    @Override public MacroActionType getType() { return MacroActionType.BUNDLE_DUPE_V2; }
    @Override public String getDisplayName() { return "Bundle Dupe V2" + (maxCycles > 0 ? " x" + maxCycles : " loop"); }
    @Override public String getIcon() { return "BD2"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
