package autismclient.util;

import autismclient.gui.screen.AutismOverlayHostScreen;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.multi.MultiPilot;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class AutismBlockNbtCapture {
    static final String FORMAT = "autism:block_data/v1";
    private static final long CONTAINER_TIMEOUT_NANOS = 1_500_000_000L;

    private static boolean capturedThisPress;
    private static PendingContainer pendingContainer;

    private AutismBlockNbtCapture() {}

    public static boolean hasTickWork() {
        return capturedThisPress || pendingContainer != null;
    }

    public static void tick(Minecraft mc) {
        if (mc == null || mc.options == null || !mc.options.keyUse.isDown() || !modifiersDown(mc)) {
            capturedThisPress = false;
        }
        PendingContainer pending = pendingContainer;
        if (pending == null || mc == null) return;

        if (pending.pov()) {
            MultiPilot.BlockInspectionMenu menu = MultiPilot.blockInspectionMenuAfter(pending.initialMenuGeneration());
            if (menu != null) {
                pendingContainer = null;
                MultiPilot.finishBlockInspectionMenu();
                CompoundTag complete = attachContainerContents(mc, pending.payload(), menu.items(), menu.title(), menu.typeId());
                show(mc, complete, menu.items(), "Server-synchronized contents.");
                return;
            }
        } else if (mc.player != null) {
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (menu != null && menu != mc.player.inventoryMenu && menu.containerId != pending.initialContainerId()) {
                pendingContainer = null;
                List<ItemStack> items = containerItems(menu, mc.player.getInventory());
                String title = mc.gui.screen() == null ? "" : mc.gui.screen().getTitle().getString();
                CompoundTag complete = attachContainerContents(mc, pending.payload(), items, title,
                    String.valueOf(BuiltInRegistries.MENU.getKey(menu.getType())));
                mc.player.closeContainer();
                show(mc, complete, items, "Server-synchronized contents.");
                return;
            }
        }

        if (System.nanoTime() - pending.startedAtNanos() >= CONTAINER_TIMEOUT_NANOS
            || pending.pov() != MultiPilot.isActive()) {
            pendingContainer = null;
            show(mc, pending.payload(), List.of(), "Contents were not provided.");
        }
    }

    public static boolean handleStartUse(Minecraft mc) {
        if (!inspectEnabled()) return false;
        if (!modifiersDown(mc) || !(mc.hitResult instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) return false;
        if (capturedThisPress) return true;
        capturedThisPress = true;

        if (mc.level == null) {
            AutismNotifications.error("Block data unavailable.");
            return true;
        }

        BlockPos pos = hit.getBlockPos().immutable();
        BlockState state = mc.level.getBlockState(pos);
        BlockEntity blockEntity = mc.level.getBlockEntity(pos);
        CompoundTag localEntity = null;
        try {
            if (blockEntity != null) localEntity = blockEntity.saveWithFullMetadata(mc.level.registryAccess());
            Snapshot snapshot = new Snapshot(
                mc.level.dimension().identifier().toString(), pos, BlockStateParser.serialize(state),
                encodeState(state));
            CompoundTag localPayload = payload(snapshot, localEntity, "client");

            if (blockEntity != null && !MultiPilot.isActive() && mc.player != null && mc.player.connection != null
                && mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                show(mc, localPayload, List.of(), "Requesting server data.");
                CompoundTag localCopy = localEntity == null ? null : localEntity.copy();
                mc.player.connection.getDebugQueryHandler().queryBlockEntityTag(pos, serverEntity -> {
                    if (serverEntity == null) return;
                    CompoundTag authoritative = mergeAuthoritative(localCopy, serverEntity, pos);
                    CompoundTag serverPayload = payload(snapshot, authoritative, "server");
                    show(mc, serverPayload, List.of(), "Authoritative server data.");
                });
                return true;
            }

            if (hasContainerMenu(mc, pos, state, blockEntity)) {
                boolean pov = MultiPilot.isActive();
                long generation = pov ? MultiPilot.blockInspectionMenuGeneration() : -1L;
                int containerId = !pov && mc.player != null && mc.player.containerMenu != null
                    ? mc.player.containerMenu.containerId : -1;
                pendingContainer = new PendingContainer(localPayload, pov, generation, containerId, System.nanoTime());
                AutismNotifications.show("Reading block contents...", AutismColors.packetCyan());
                return false;
            }

            show(mc, localPayload, List.of(), blockEntity == null ? "No block entity data." : "Client-synchronized data.");
        } catch (Throwable error) {
            AutismNotifications.error("Block inspection failed.");
        }
        return true;
    }

    static boolean shortcutDown(boolean control, boolean shift) {
        return control && shift;
    }

    private static boolean inspectEnabled() {
        Module module = ModuleRegistry.get("better-tooltips");
        return module != null && module.isEnabled()
            && Boolean.parseBoolean(module.value("block-nbt-inspect"));
    }

    static CompoundTag payload(Snapshot snapshot, CompoundTag blockEntity, String source) {
        CompoundTag root = new CompoundTag();
        root.putString("format", FORMAT);
        root.putString("source", source == null ? "client" : source);
        root.putString("dimension", snapshot.dimension());
        root.putIntArray("position", new int[]{snapshot.pos().getX(), snapshot.pos().getY(), snapshot.pos().getZ()});
        root.putString("block_state", snapshot.serializedState());
        root.put("state", snapshot.stateTag().copy());
        if (blockEntity != null) root.put("block_entity", blockEntity.copy());
        return root;
    }

    static CompoundTag mergeAuthoritative(CompoundTag local, CompoundTag server, BlockPos pos) {
        CompoundTag merged = local == null ? new CompoundTag() : local.copy();
        if (server != null) merged.merge(server);
        merged.putInt("x", pos.getX());
        merged.putInt("y", pos.getY());
        merged.putInt("z", pos.getZ());
        return merged;
    }

    private static CompoundTag encodeState(BlockState state) {
        CompoundTag encoded = new CompoundTag();
        String id = state.typeHolder().unwrapKey()
            .map(key -> key.identifier().toString())
            .orElseGet(() -> BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        encoded.putString("Name", id);
        if (!state.isSingletonState()) {
            CompoundTag properties = new CompoundTag();
            state.getValues().forEach(value ->
                properties.putString(value.property().getName(), value.valueName()));
            if (!properties.isEmpty()) encoded.put("Properties", properties);
        }
        return encoded;
    }

    private static boolean hasContainerMenu(Minecraft mc, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (blockEntity instanceof Container) return true;
        try {
            return state.getMenuProvider(mc.level, pos) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static List<ItemStack> containerItems(AbstractContainerMenu menu, Container playerInventory) {
        if (menu == null) return List.of();
        int count = Math.max(0, menu.slots.size() - 36);
        List<ItemStack> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Slot slot = menu.slots.get(i);
            if (slot != null && slot.container == playerInventory) break;
            ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
            items.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return List.copyOf(items);
    }

    static CompoundTag attachContainerContents(Minecraft mc, CompoundTag source, List<ItemStack> items,
                                               String title, String menuType) {
        ListTag encodedItems = new ListTag();
        if (mc != null && mc.level != null && items != null) {
            for (int slot = 0; slot < items.size(); slot++) {
                ItemStack stack = items.get(slot);
                if (stack == null || stack.isEmpty()) continue;
                try {
                    Tag encoded = ItemStack.CODEC.encodeStart(
                        mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack).result().orElse(null);
                    if (encoded instanceof CompoundTag item) {
                        CompoundTag withSlot = item.copy();
                        withSlot.putByte("Slot", (byte) slot);
                        encodedItems.add(withSlot);
                    }
                } catch (Throwable ignored) {

                }
            }
        }
        return attachEncodedContents(source, encodedItems, items == null ? 0 : items.size(), title, menuType);
    }

    static CompoundTag attachEncodedContents(CompoundTag source, ListTag encodedItems, int slotCount,
                                              String title, String menuType) {
        CompoundTag root = source == null ? new CompoundTag() : source.copy();
        CompoundTag entity = root.getCompound("block_entity").orElse(new CompoundTag());
        entity.put("Items", encodedItems == null ? new ListTag() : encodedItems.copy());
        root.put("block_entity", entity);
        root.putString("source", "container");
        root.putBoolean("contents_available", true);
        root.putInt("container_slots", Math.max(0, slotCount));
        if (title != null && !title.isBlank()) root.putString("container_title", title);
        if (menuType != null && !menuType.isBlank() && !"null".equals(menuType)) root.putString("container_type", menuType);
        return root;
    }

    private static void show(Minecraft mc, CompoundTag root, List<ItemStack> items, String note) {
        if (mc == null) return;
        Runnable open = () -> {
            CompoundTag displayRoot = root == null ? new CompoundTag() : root.copy();
            List<ItemStack> displayItems = items == null ? List.of() : items;
            CompoundTag entity = displayRoot.getCompound("block_entity").orElse(null);
            if (displayItems.isEmpty() && entity != null && entity.contains("Items")) {
                displayItems = decodeContainerItems(mc, entity);
                displayRoot.putBoolean("contents_available", true);
                if (!displayRoot.contains("container_slots")) {
                    displayRoot.putInt("container_slots", displayItems.size());
                }
            }
            AutismBlockNbtInspector.BlockInspection inspection = AutismBlockNbtInspector.inspect(displayRoot, displayItems, note);
            if (!AutismItemNbtInspectOverlay.openGlobal(inspection)) {
                AutismNotifications.error("NBT overlay unavailable.");
                return;
            }
            AutismItemNbtInspectOverlay overlay = AutismItemNbtInspectOverlay.getSharedOverlay(mc.font);
            if (!(mc.gui.screen() instanceof AutismOverlayHostScreen host && host.hostsOverlay(overlay))) {
                mc.gui.setScreen(new AutismOverlayHostScreen(overlay));
            }
        };
        if (mc.isSameThread()) open.run();
        else mc.execute(open);
    }

    private static List<ItemStack> decodeContainerItems(Minecraft mc, CompoundTag entity) {
        if (mc == null || mc.level == null || entity == null) return List.of();
        List<ItemStack> decoded = new ArrayList<>();
        for (Tag value : entity.getListOrEmpty("Items")) {
            if (!(value instanceof CompoundTag item)) continue;
            int slot = Byte.toUnsignedInt(item.getByteOr("Slot", (byte) 0));
            if (slot > 255) continue;
            ItemStack stack = ItemStack.CODEC.parse(
                mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), item).result().orElse(ItemStack.EMPTY);
            while (decoded.size() <= slot) decoded.add(ItemStack.EMPTY);
            decoded.set(slot, stack.copy());
        }
        return List.copyOf(decoded);
    }

    private static boolean modifiersDown(Minecraft mc) {
        if (mc == null || mc.getWindow() == null) return false;
        long window = mc.getWindow().handle();
        boolean control = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        return shortcutDown(control, shift);
    }

    record Snapshot(String dimension, BlockPos pos, String serializedState, CompoundTag stateTag) {
        Snapshot {
            dimension = dimension == null ? "" : dimension;
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
            serializedState = serializedState == null ? "minecraft:air" : serializedState;
            stateTag = stateTag == null ? new CompoundTag() : stateTag.copy();
        }
    }

    private record PendingContainer(CompoundTag payload, boolean pov, long initialMenuGeneration,
                                    int initialContainerId, long startedAtNanos) {
        private PendingContainer {
            payload = payload == null ? new CompoundTag() : payload.copy();
        }
    }
}
