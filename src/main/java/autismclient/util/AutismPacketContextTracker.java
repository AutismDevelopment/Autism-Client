package autismclient.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AutismPacketContextTracker {
    private static final Minecraft MC = Minecraft.getInstance();
    public static final Capture EMPTY_CAPTURE = new Capture(Snapshot.EMPTY, Snapshot.EMPTY, List.of(), false);

    private boolean contextStarted;
    private int activeContainerId = -1;
    private int activeContainerStateId = -1;
    private String activeScreenType = "";
    private String activeScreenTitle = "";
    private int slotCount = -1;
    private int containerSlotCount = -1;
    private String cursorItem = "unknown";
    private int selectedHotbarSlot = -1;
    private Position lastClientPosition;
    private Position lastServerPosition;
    private int lastTeleportId = -1;
    private String dimension = "";
    private Integer chunkCenterX;
    private Integer chunkCenterZ;
    private Integer chunkRadius;
    private Integer simulationDistance;
    private String protocolState = "unknown";
    private final Map<Integer, String> containerSlots = new LinkedHashMap<>();
    private final Map<Integer, String> playerInventorySlots = new LinkedHashMap<>();

    public synchronized void reset() {
        contextStarted = false;
        activeContainerId = -1;
        activeContainerStateId = -1;
        activeScreenType = "";
        activeScreenTitle = "";
        slotCount = -1;
        containerSlotCount = -1;
        cursorItem = "unknown";
        selectedHotbarSlot = -1;
        lastClientPosition = null;
        lastServerPosition = null;
        lastTeleportId = -1;
        dimension = "";
        chunkCenterX = null;
        chunkCenterZ = null;
        chunkRadius = null;
        simulationDistance = null;
        protocolState = "unknown";
        containerSlots.clear();
        playerInventorySlots.clear();
    }

    public synchronized Capture capture(Packet<?> packet, String direction) {
        if (!isRelevant(packet)) return EMPTY_CAPTURE;
        seedFromClientIfUseful();
        Snapshot before = snapshot();
        List<String> changes = new ArrayList<>();
        apply(packet, direction == null ? "" : direction.toUpperCase(Locale.ROOT), changes);
        Snapshot after = snapshot();
        contextStarted = true;
        return new Capture(before, after, List.copyOf(changes), true);
    }

    public static boolean isRelevant(Packet<?> packet) {
        if (packet == null) return false;
        return packet instanceof ClientboundOpenScreenPacket
            || packet instanceof ClientboundContainerClosePacket
            || packet instanceof ClientboundContainerSetContentPacket
            || packet instanceof ClientboundContainerSetSlotPacket
            || packet instanceof ClientboundContainerSetDataPacket
            || packet instanceof ClientboundSetCursorItemPacket
            || packet instanceof ClientboundSetPlayerInventoryPacket
            || packet instanceof ClientboundSetHeldSlotPacket
            || packet instanceof ServerboundSetCarriedItemPacket
            || packet instanceof ServerboundContainerClickPacket
            || packet instanceof ServerboundContainerClosePacket
            || packet instanceof ServerboundMovePlayerPacket
            || packet instanceof ClientboundPlayerPositionPacket
            || packet instanceof ServerboundAcceptTeleportationPacket
            || packet instanceof ClientboundLoginPacket
            || packet instanceof ClientboundRespawnPacket
            || packet instanceof ClientboundTransferPacket
            || packet instanceof ClientboundStartConfigurationPacket
            || packet instanceof ClientboundFinishConfigurationPacket
            || packet instanceof ClientboundSetChunkCacheCenterPacket
            || packet instanceof ClientboundSetChunkCacheRadiusPacket
            || packet instanceof ClientboundSetSimulationDistancePacket
            || packet instanceof ClientboundForgetLevelChunkPacket
            || packet instanceof ClientboundLevelChunkWithLightPacket
            || packet instanceof ClientboundGameEventPacket
            || packet instanceof ClientboundResourcePackPushPacket
            || packet instanceof ClientboundResourcePackPopPacket
            || packet instanceof ClientboundDisconnectPacket;
    }

    private void apply(Packet<?> packet, String direction, List<String> changes) {
        if (packet instanceof ClientboundLoginPacket login) {
            clearScreenState();
            protocolState = "play";
            dimension = String.valueOf(login.commonPlayerSpawnInfo().dimension().identifier());
            chunkRadius = login.chunkRadius();
            simulationDistance = login.simulationDistance();
            changes.add("Entered world " + dimension + " as entity #" + login.playerId());
            return;
        }
        if (packet instanceof ClientboundRespawnPacket respawn) {
            clearScreenState();
            protocolState = "play";
            dimension = String.valueOf(respawn.commonPlayerSpawnInfo().dimension().identifier());
            changes.add("Respawned / switched world to " + dimension);
            return;
        }
        if (packet instanceof ClientboundTransferPacket transfer) {
            clearScreenState();
            protocolState = "transfer " + transfer.host() + ":" + transfer.port();
            changes.add("Server transfer to " + transfer.host() + ":" + transfer.port());
            return;
        }
        if (packet instanceof ClientboundStartConfigurationPacket) {
            protocolState = "configuration";
            changes.add("Server moved connection into configuration phase");
            return;
        }
        if (packet instanceof ClientboundFinishConfigurationPacket) {
            protocolState = "configuration finished";
            changes.add("Configuration phase finished");
            return;
        }
        if (packet instanceof ClientboundDisconnectPacket) {
            changes.add("Disconnected; tracked packet context cleared");
            reset();
            return;
        }

        if (packet instanceof ClientboundOpenScreenPacket open) {
            activeContainerId = open.getContainerId();
            activeContainerStateId = -1;
            activeScreenType = safeString(open.getType());
            activeScreenTitle = open.getTitle() == null ? "" : open.getTitle().getString();
            slotCount = -1;
            containerSlotCount = -1;
            containerSlots.clear();
            changes.add("Opened container #" + activeContainerId + " " + quote(activeScreenTitle));
            return;
        }
        if (packet instanceof ClientboundContainerSetContentPacket content) {
            activeContainerId = content.containerId();
            activeContainerStateId = content.stateId();
            slotCount = content.items().size();
            containerSlotCount = estimateContainerSlotCount(activeContainerId, slotCount);
            containerSlots.clear();
            for (int i = 0; i < content.items().size(); i++) {
                containerSlots.put(i, summarizeItem(content.items().get(i)));
            }
            cursorItem = summarizeItem(content.carriedItem());
            changes.add("Synced " + slotCount + " slots (" + nonEmpty(content.items()) + " non-empty)");
            return;
        }
        if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            activeContainerStateId = slot.getStateId();
            String previous = slotItem(slot.getSlot());
            String next = summarizeItem(slot.getItem());
            if (slot.getContainerId() == -1) {
                cursorItem = next;
                changes.add("Cursor changed " + previous + " -> " + next);
            } else if (slot.getContainerId() == -2) {
                playerInventorySlots.put(slot.getSlot(), next);
                changes.add("Player inventory slot " + slot.getSlot() + " changed " + previous + " -> " + next);
            } else {
                if (activeContainerId < 0) activeContainerId = slot.getContainerId();
                containerSlots.put(slot.getSlot(), next);
                changes.add("Slot " + slot.getSlot() + " changed " + previous + " -> " + next);
            }
            return;
        }
        if (packet instanceof ClientboundSetCursorItemPacket cursor) {
            String previous = cursorItem;
            cursorItem = summarizeItem(cursor.contents());
            changes.add("Cursor changed " + previous + " -> " + cursorItem);
            return;
        }
        if (packet instanceof ClientboundSetPlayerInventoryPacket inventory) {
            String previous = playerInventorySlots.getOrDefault(inventory.slot(), "unknown");
            String next = summarizeItem(inventory.contents());
            playerInventorySlots.put(inventory.slot(), next);
            changes.add("Player inventory slot " + inventory.slot() + " changed " + previous + " -> " + next);
            return;
        }
        if (packet instanceof ClientboundSetHeldSlotPacket held) {
            int previous = selectedHotbarSlot;
            selectedHotbarSlot = held.slot();
            changes.add("Held hotbar slot changed " + previous + " -> " + selectedHotbarSlot);
            return;
        }
        if (packet instanceof ServerboundSetCarriedItemPacket carried) {
            int previous = selectedHotbarSlot;
            selectedHotbarSlot = carried.getSlot();
            changes.add("Client selected hotbar slot " + previous + " -> " + selectedHotbarSlot);
            return;
        }
        if (packet instanceof ServerboundContainerClickPacket click) {
            activeContainerId = click.containerId();
            activeContainerStateId = click.stateId();
            applyChangedSlots(click.changedSlots());
            cursorItem = summarizeHashedStack(click.carriedItem());
            changes.add("Client " + describeInput(click.containerInput()) + " on slot " + click.slotNum()
                + " changed " + click.changedSlots().size() + " slots");
            return;
        }
        if (packet instanceof ServerboundContainerClosePacket || packet instanceof ClientboundContainerClosePacket) {
            changes.add("Closed container #" + activeContainerId);
            clearScreenState();
            return;
        }

        if (packet instanceof ServerboundMovePlayerPacket move) {
            Position before = lastClientPosition == null ? currentPlayerPosition() : lastClientPosition;
            double x = move.getX(before == null ? 0.0D : before.x());
            double y = move.getY(before == null ? 0.0D : before.y());
            double z = move.getZ(before == null ? 0.0D : before.z());
            float yaw = move.getYRot(before == null ? 0.0F : before.yaw());
            float pitch = move.getXRot(before == null ? 0.0F : before.pitch());
            lastClientPosition = new Position(x, y, z, yaw, pitch, move.hasPosition(), move.hasRotation());
            if (before != null) changes.add("Client move delta " + formatDistance(before.distanceTo(lastClientPosition)) + " blocks");
            return;
        }
        if (packet instanceof ClientboundPlayerPositionPacket position) {
            Position base = lastServerPosition == null ? currentPlayerPosition() : lastServerPosition;
            Position next = computeServerPosition(base, position.change(), position.relatives());
            lastServerPosition = next;
            lastTeleportId = position.id();
            changes.add("Server correction/teleport #" + lastTeleportId + " -> " + formatPosition(next));
            return;
        }
        if (packet instanceof ServerboundAcceptTeleportationPacket ack) {
            changes.add("Client acknowledged teleport #" + ack.getId()
                + (ack.getId() == lastTeleportId ? " (matches pending)" : " (tracked pending " + lastTeleportId + ")"));
            if (ack.getId() == lastTeleportId) lastTeleportId = -1;
            return;
        }

        if (packet instanceof ClientboundSetChunkCacheCenterPacket center) {
            chunkCenterX = center.getX();
            chunkCenterZ = center.getZ();
            changes.add("Chunk cache center -> " + chunkCenterX + ", " + chunkCenterZ);
            return;
        }
        if (packet instanceof ClientboundSetChunkCacheRadiusPacket radius) {
            chunkRadius = radius.getRadius();
            changes.add("Chunk cache radius -> " + chunkRadius);
            return;
        }
        if (packet instanceof ClientboundSetSimulationDistancePacket sim) {
            simulationDistance = sim.simulationDistance();
            changes.add("Simulation distance -> " + simulationDistance);
            return;
        }
        if (packet instanceof ClientboundForgetLevelChunkPacket forget) {
            changes.add("Unload chunk " + forget.pos().x() + ", " + forget.pos().z());
            return;
        }
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            changes.add("Load chunk " + chunk.getX() + ", " + chunk.getZ() + " with light");
            return;
        }
        if (packet instanceof ClientboundGameEventPacket gameEvent) {
            changes.add("Game event " + gameEvent.getEvent() + " value=" + gameEvent.getParam());
            return;
        }
        if (packet instanceof ClientboundResourcePackPushPacket pack) {
            changes.add("Resource pack push " + pack.id() + " required=" + pack.required());
            return;
        }
        if (packet instanceof ClientboundResourcePackPopPacket pack) {
            changes.add("Resource pack pop " + pack.id().map(Object::toString).orElse("latest"));
        }
    }

    private void seedFromClientIfUseful() {
        if (lastClientPosition == null) lastClientPosition = currentPlayerPosition();
        if (lastServerPosition == null) lastServerPosition = currentPlayerPosition();
        if (selectedHotbarSlot < 0 && MC != null && MC.player != null) {
            try {
                selectedHotbarSlot = MC.player.getInventory().getSelectedSlot();
            } catch (Throwable ignored) {  }
        }
    }

    private Snapshot snapshot() {
        return new Snapshot(
            contextStarted,
            activeContainerId,
            activeContainerStateId,
            activeScreenType,
            activeScreenTitle,
            slotCount,
            containerSlotCount,
            cursorItem,
            selectedHotbarSlot,
            lastClientPosition,
            lastServerPosition,
            lastTeleportId,
            dimension,
            chunkCenterX,
            chunkCenterZ,
            chunkRadius,
            simulationDistance,
            protocolState,
            Collections.unmodifiableMap(new LinkedHashMap<>(containerSlots)),
            Collections.unmodifiableMap(new LinkedHashMap<>(playerInventorySlots))
        );
    }

    private void clearScreenState() {
        activeContainerId = -1;
        activeContainerStateId = -1;
        activeScreenType = "";
        activeScreenTitle = "";
        slotCount = -1;
        containerSlotCount = -1;
        cursorItem = "unknown";
        containerSlots.clear();
    }

    private void applyChangedSlots(Int2ObjectMap<HashedStack> changedSlots) {
        if (changedSlots == null) return;
        for (Int2ObjectMap.Entry<HashedStack> entry : changedSlots.int2ObjectEntrySet()) {
            containerSlots.put(entry.getIntKey(), summarizeHashedStack(entry.getValue()));
        }
    }

    private String slotItem(int slot) {
        if (slot < 0) return "outside";
        String value = containerSlots.get(slot);
        if (value != null) return value;
        return playerInventorySlots.getOrDefault(slot, "unknown");
    }

    private static int estimateContainerSlotCount(int containerId, int totalSlots) {
        if (totalSlots < 0) return -1;
        if (containerId == 0) return Math.max(0, totalSlots - 36);
        return totalSlots >= 36 ? Math.max(0, totalSlots - 36) : -1;
    }

    private static int nonEmpty(List<ItemStack> stacks) {
        int count = 0;
        if (stacks != null) {
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) count++;
            }
        }
        return count;
    }

    public static String summarizeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        StringBuilder out = new StringBuilder();
        out.append(stack.getCount()).append('x').append(' ').append(id);
        try {
            String display = stack.getHoverName() == null ? "" : stack.getHoverName().getString();
            String vanilla = stack.getItem().getName(stack).getString();
            if (!display.isBlank() && !Objects.equals(display, vanilla)) {
                out.append(" [").append(shorten(display, 48)).append(']');
            }
        } catch (Throwable ignored) {  }
        try {
            if (stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                int damage = stack.getDamageValue();
                out.append(" dur=").append(Math.max(0, max - damage)).append('/').append(max);
            }
        } catch (Throwable ignored) {  }
        return out.toString();
    }

    public static String summarizeHashedStack(HashedStack stack) {
        if (stack == null || stack == HashedStack.EMPTY) return "empty";
        if (stack instanceof HashedStack.ActualItem actual) {
            String id = actual.item().unwrapKey()
                .map(key -> key.identifier().toString())
                .orElseGet(() -> safeString(actual.item()));
            return actual.count() + "x " + id + componentHint(actual.components());
        }
        return safeString(stack);
    }

    private static String componentHint(Object components) {
        String value = safeString(components);
        if (value == null || value.isBlank() || "HashedPatchMap[hashedPatches={}]".equals(value)) return "";
        return " components";
    }

    private static Position computeServerPosition(Position base, PositionMoveRotation change, Set<Relative> relatives) {
        Vec3 pos = change.position();
        Vec3 delta = change.deltaMovement();
        float yaw = change.yRot();
        float pitch = change.xRot();
        if (base != null && relatives != null) {
            if (relatives.contains(Relative.X)) pos = new Vec3(base.x() + pos.x, pos.y, pos.z);
            if (relatives.contains(Relative.Y)) pos = new Vec3(pos.x, base.y() + pos.y, pos.z);
            if (relatives.contains(Relative.Z)) pos = new Vec3(pos.x, pos.y, base.z() + pos.z);
            if (relatives.contains(Relative.Y_ROT)) yaw += base.yaw();
            if (relatives.contains(Relative.X_ROT)) pitch += base.pitch();
        }
        return new Position(pos.x, pos.y, pos.z, yaw, pitch, true, true);
    }

    private static Position currentPlayerPosition() {
        if (MC == null || MC.player == null) return null;
        try {
            return new Position(MC.player.getX(), MC.player.getY(), MC.player.getZ(), MC.player.getYRot(), MC.player.getXRot(), true, true);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String describeInput(ContainerInput input) {
        if (input == null) return "clicked";
        return switch (input) {
            case PICKUP -> "picked up / placed";
            case QUICK_MOVE -> "quick-moved";
            case SWAP -> "hotbar-swapped";
            case CLONE -> "cloned";
            case THROW -> "threw";
            case QUICK_CRAFT -> "drag-crafted";
            case PICKUP_ALL -> "picked up all matching";
        };
    }

    private static String formatDistance(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    public static String formatPosition(Position position) {
        if (position == null) return "unknown";
        return String.format(Locale.ROOT, "x=%.3f, y=%.3f, z=%.3f, yaw=%.2f, pitch=%.2f",
            position.x(), position.y(), position.z(), position.yaw(), position.pitch());
    }

    public static String slotArea(Snapshot snapshot, int slot) {
        if (slot < 0) return "outside / carried item area";
        if (snapshot == null || snapshot.slotCount() <= 0 || snapshot.containerSlotCount() < 0) {
            return "handler slot " + slot;
        }
        int containerSlots = snapshot.containerSlotCount();
        if (slot < containerSlots) return "container slot " + slot;
        int playerRelative = slot - containerSlots;
        if (playerRelative >= 0 && playerRelative < 27) return "player inventory slot " + (playerRelative + 9);
        if (playerRelative >= 27 && playerRelative < 36) return "hotbar slot " + (playerRelative - 27);
        return "handler slot " + slot;
    }

    private static String quote(String value) {
        if (value == null || value.isBlank()) return "\"\"";
        return "\"" + shorten(value, 72) + "\"";
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String safeString(Object value) {
        if (value == null) return "";
        try {
            return String.valueOf(value);
        } catch (Throwable ignored) {
            return value.getClass().getSimpleName();
        }
    }

    public record Capture(Snapshot before, Snapshot after, List<String> changes, boolean relevant) {
        public Capture {
            changes = changes == null ? List.of() : List.copyOf(changes);
            before = before == null ? Snapshot.EMPTY : before;
            after = after == null ? Snapshot.EMPTY : after;
        }
    }

    public record Snapshot(
        boolean known,
        int containerId,
        int containerStateId,
        String screenType,
        String screenTitle,
        int slotCount,
        int containerSlotCount,
        String cursorItem,
        int selectedHotbarSlot,
        Position clientPosition,
        Position serverPosition,
        int lastTeleportId,
        String dimension,
        Integer chunkCenterX,
        Integer chunkCenterZ,
        Integer chunkRadius,
        Integer simulationDistance,
        String protocolState,
        Map<Integer, String> containerSlots,
        Map<Integer, String> playerInventorySlots
    ) {
        public static final Snapshot EMPTY = new Snapshot(false, -1, -1, "", "", -1, -1, "unknown", -1,
            null, null, -1, "", null, null, null, null, "unknown", Map.of(), Map.of());

        public Snapshot {
            screenType = screenType == null ? "" : screenType;
            screenTitle = screenTitle == null ? "" : screenTitle;
            cursorItem = cursorItem == null ? "unknown" : cursorItem;
            dimension = dimension == null ? "" : dimension;
            protocolState = protocolState == null ? "unknown" : protocolState;
            containerSlots = containerSlots == null ? Map.of() : Map.copyOf(containerSlots);
            playerInventorySlots = playerInventorySlots == null ? Map.of() : Map.copyOf(playerInventorySlots);
        }

        public String slotItem(int slot) {
            if (slot < 0) return "outside";
            String value = containerSlots.get(slot);
            if (value != null) return value;
            return playerInventorySlots.getOrDefault(slot, "unknown");
        }
    }

    public record Position(double x, double y, double z, float yaw, float pitch, boolean hasPosition, boolean hasRotation) {
        public double distanceTo(Position other) {
            if (other == null) return 0.0D;
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }
}
