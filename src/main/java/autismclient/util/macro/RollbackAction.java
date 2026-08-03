package autismclient.util.macro;

import autismclient.modules.PackHideState;
import autismclient.util.AutismClientMessaging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class RollbackAction implements MacroAction {
    public enum Scope { ALL_CONTAINER, CAPTURED_SLOTS, SLOT_RANGE }

    private static final int TASK_TIMEOUT_SECONDS = 60;

    private boolean enabled = true;
    public Scope scope = Scope.ALL_CONTAINER;
    public ArrayList<String> slots = new ArrayList<>();
    public int startSlot = 0;
    public int endSlot = 26;

    @Override
    public void execute(Minecraft mc) {
        if (mc == null || mc.isSameThread()) {
            AutismClientMessaging.sendPrefixed("\u00a7cRollback must run from the macro executor.");
            return;
        }
        try {
            executeRollback(mc);
        } catch (InterruptedException e) {
            AutismClientMessaging.sendPrefixed("\u00a7eRollback canceled safely.");
        } catch (Throwable t) {
            AutismClientMessaging.sendPrefixed("\u00a7cRollback failed: " + conciseMessage(t));
        }
    }

    public void executeRollback(Minecraft mc) throws Exception {
        if (mc == null || mc.isSameThread()) {
            throw new IllegalStateException("rollback cannot execute on the render thread");
        }
        if (PackHideState.isHardLocked()) return;

        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            AutismClientMessaging.sendPrefixed("\u00a7cRollback requires a singleplayer world or the LAN host.");
            return;
        }

        TransferPlan plan = onClient(mc, () -> createPlan(mc));
        if (plan == null) return;

        requireRunning();
        onServer(server, () -> duplicateAuthoritatively(server, plan));
    }

    private TransferPlan createPlan(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null || mc.player.containerMenu == null) {
            AutismClientMessaging.sendPrefixed("\u00a7cRollback requires an open container.");
            return null;
        }

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == mc.player.inventoryMenu || menu.containerId == 0 || !menu.getCarried().isEmpty()) {
            AutismClientMessaging.sendPrefixed("\u00a7cOpen a container and keep the cursor empty first.");
            return null;
        }

        List<Integer> requested = requestedSlots(menu.slots.size());
        List<SlotSnapshot> selected = new ArrayList<>();
        IdentityHashMap<Container, Set<Integer>> capturedContainerSlots = new IdentityHashMap<>();
        for (int slotId : requested) {
            if (slotId < 0 || slotId >= menu.slots.size()) continue;
            Slot slot = menu.slots.get(slotId);
            if (slot.container == mc.player.getInventory() || !slot.hasItem() || !slot.mayPickup(mc.player)
                    || !slot.mayPlace(slot.getItem())) continue;
            Set<Integer> indexes = capturedContainerSlots.computeIfAbsent(slot.container, ignored -> new LinkedHashSet<>());
            if (!indexes.add(slot.getContainerSlot())) continue;
            selected.add(new SlotSnapshot(slotId, slot.getItem().copy()));
        }

        if (selected.isEmpty()) {
            AutismClientMessaging.sendPrefixed("\u00a7cRollback found no transferable container items.");
            return null;
        }

        return new TransferPlan(menu.containerId, selected, mc.player.getUUID());
    }

    private List<Integer> requestedSlots(int menuSize) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        switch (scope == null ? Scope.ALL_CONTAINER : scope) {
            case ALL_CONTAINER -> {
                for (int i = 0; i < menuSize; i++) result.add(i);
            }
            case CAPTURED_SLOTS -> {
                for (String raw : slots) {
                    try {
                        result.add(Integer.parseInt(raw.trim()));
                    } catch (RuntimeException ignored) {  }
                }
            }
            case SLOT_RANGE -> {
                int first = Math.max(0, Math.min(startSlot, endSlot));
                int last = Math.min(menuSize - 1, Math.max(startSlot, endSlot));
                for (int i = first; i <= last; i++) result.add(i);
            }
        }
        return List.copyOf(result);
    }

    private static boolean duplicateAuthoritatively(IntegratedServer server, TransferPlan plan) {
        ServerPlayer player = server.getPlayerList().getPlayer(plan.playerId);
        if (player == null || player.containerMenu == null || player.containerMenu.containerId != plan.containerId
                || !player.containerMenu.getCarried().isEmpty()) {
            throw new IllegalStateException("the container is no longer open");
        }

        AbstractContainerMenu menu = player.containerMenu;
        for (SlotSnapshot expected : plan.slots) {
            if (expected.slotId >= menu.slots.size()) throw new IllegalStateException("container layout changed");
            Slot source = menu.slots.get(expected.slotId);
            if (source.container == player.getInventory() || !ItemStack.matches(source.getItem(), expected.stack)) {
                throw new IllegalStateException("container contents changed before duplication");
            }
        }

        Inventory inventory = player.getInventory();
        List<ItemStack> simulated = new ArrayList<>(Inventory.INVENTORY_SIZE);
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) simulated.add(inventory.getItem(slot).copy());
        for (SlotSnapshot source : plan.slots) {
            if (!insertStack(simulated, source.stack.copy(), inventory.getMaxStackSize())) {
                throw new IllegalStateException("not enough inventory space for all selected items");
            }
        }

        for (int slot = 0; slot < simulated.size(); slot++) {
            ItemStack target = simulated.get(slot);
            if (!ItemStack.matches(inventory.getItem(slot), target)) {
                target.setPopTime(5);
                inventory.setItem(slot, target);
                player.connection.send(inventory.createInventoryUpdatePacket(slot));
            }
        }
        inventory.setChanged();
        menu.broadcastFullState();

        if (!inventoryMatches(inventory, simulated) || !sourceMatches(menu, plan)) {
            throw new IllegalStateException("authoritative duplicate verification failed");
        }
        return true;
    }

    private static boolean sourceMatches(AbstractContainerMenu menu, TransferPlan plan) {
        for (SlotSnapshot expected : plan.slots) {
            if (expected.slotId >= menu.slots.size()
                    || !ItemStack.matches(menu.slots.get(expected.slotId).getItem(), expected.stack)) return false;
        }
        return true;
    }

    private static boolean inventoryMatches(Inventory inventory, List<ItemStack> expected) {
        if (expected.size() != Inventory.INVENTORY_SIZE) return false;
        for (int slot = 0; slot < expected.size(); slot++) {
            if (!ItemStack.matches(inventory.getItem(slot), expected.get(slot))) return false;
        }
        return true;
    }

    private static boolean insertStack(List<ItemStack> inventory, ItemStack incoming, int inventoryMaxStackSize) {
        if (incoming.isEmpty()) return true;
        for (ItemStack target : inventory) {
            if (incoming.isEmpty()) return true;
            if (target.isEmpty() || !ItemStack.isSameItemSameComponents(target, incoming)) continue;
            int capacity = Math.min(inventoryMaxStackSize, target.getMaxStackSize()) - target.getCount();
            if (capacity <= 0) continue;
            int moved = Math.min(capacity, incoming.getCount());
            target.grow(moved);
            incoming.shrink(moved);
        }
        for (int slot = 0; slot < inventory.size() && !incoming.isEmpty(); slot++) {
            if (!inventory.get(slot).isEmpty()) continue;
            int moved = Math.min(incoming.getCount(), Math.min(inventoryMaxStackSize, incoming.getMaxStackSize()));
            inventory.set(slot, incoming.copyWithCount(moved));
            incoming.shrink(moved);
        }
        return incoming.isEmpty();
    }

    private static void requireRunning() throws InterruptedException {
        if (PackHideState.isHardLocked() || !MacroExecutor.isCurrentActionRunActive() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("rollback canceled");
        }
    }

    private static <T> T onClient(Minecraft mc, Supplier<T> task) throws Exception {
        if (mc.isSameThread()) return task.get();
        CompletableFuture<T> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static <T> T onServer(IntegratedServer server, Supplier<T> task) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static String conciseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putBoolean("enabled", enabled);
        tag.putString("scope", scope.name());
        tag.put("slots", MacroStringList.toTag(slots));
        tag.putInt("startSlot", startSlot);
        tag.putInt("endSlot", endSlot);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        enabled = tag.getBooleanOr("enabled", true);
        scope = MacroStringList.enumValue(Scope.class, tag.getStringOr("scope", "ALL_CONTAINER"), Scope.ALL_CONTAINER);
        slots = MacroStringList.fromTag(tag.getList("slots").orElse(new net.minecraft.nbt.ListTag()));
        startSlot = tag.getIntOr("startSlot", 0);
        endSlot = tag.getIntOr("endSlot", 26);
    }

    @Override public MacroActionType getType() { return MacroActionType.ROLLBACK; }
    @Override public String getDisplayName() { return "Rollback " + scope.name().replace('_', ' '); }
    @Override public String getIcon() { return "RB"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }

    private record SlotSnapshot(int slotId, ItemStack stack) {}

    private record TransferPlan(
            int containerId,
            List<SlotSnapshot> slots,
            UUID playerId
    ) {}
}
