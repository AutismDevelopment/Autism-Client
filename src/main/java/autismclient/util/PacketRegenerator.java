package autismclient.util;

import autismclient.AutismClientAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PacketRegenerator {

    private static final Map<Class<? extends Packet<?>>, PacketHandler<?>> handlers = new HashMap<>();

    static {
        registerAllHandlers();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Packet<?>> T regenerate(T original) {
        if (original == null) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return original;

        PacketHandler<T> handler = (PacketHandler<T>) handlers.get(original.getClass());
        if (handler != null) {
            try {
                T result = handler.regenerate(original, mc);

                return result;
            } catch (Exception e) {
                AutismClientAddon.LOG.error("[Autism] Failed to regenerate {}: {}",
                    original.getClass().getSimpleName(), e.getMessage());
                return original;
            }
        }

        return original;
    }

    private static void registerAllHandlers() {

        register(ServerboundPlayerActionPacket.class, (packet, mc) -> {
            return new ServerboundPlayerActionPacket(
                packet.getAction(),
                packet.getPos(),
                packet.getDirection(),
                0
            );
        });

        register(ServerboundUseItemOnPacket.class, (packet, mc) -> {
            BlockPos targetBlock = packet.getHitResult().getBlockPos();
            Direction face = packet.getHitResult().getDirection();

            Vec3 eyePos = mc.player.getEyePosition();
            Vec3 hitPos = new Vec3(
                targetBlock.getX() + 0.5 + face.getStepX() * 0.5,
                targetBlock.getY() + 0.5 + face.getStepY() * 0.5,
                targetBlock.getZ() + 0.5 + face.getStepZ() * 0.5
            );

            BlockHitResult newHit = new BlockHitResult(
                hitPos, face, targetBlock, false
            );

            return new ServerboundUseItemOnPacket(
                packet.getHand(),
                newHit,
                0
            );
        });

        register(ServerboundContainerClickPacket.class, (packet, mc) -> {
            AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler == null) return packet;

            int currentSyncId = handler.containerId;
            int currentRevision = handler.getStateId();

            try {

                int slot = getFieldInt(packet, "slot", short.class, 0);
                int button = getFieldInt(packet, "button", byte.class, 0);
                ContainerInput action = getFieldEnum(packet, ContainerInput.class);

                it.unimi.dsi.fastutil.ints.Int2ObjectMap<net.minecraft.network.HashedStack> modifiedStacks =
                    new it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap<>();

                return new ServerboundContainerClickPacket(
                    currentSyncId,
                    currentRevision,
                    (short) slot,
                    (byte) button,
                    action,
                    modifiedStacks,
                    net.minecraft.network.HashedStack.EMPTY
                );
            } catch (Exception e) {
                AutismClientAddon.LOG.error("[Autism] ClickSlot regeneration failed: {}", e.getMessage());
                return packet;
            }
        });

        register(ServerboundContainerButtonClickPacket.class, (packet, mc) -> {
            AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler == null) return packet;

            int buttonId = getFieldInt(packet, "buttonId", int.class, 1);
            return new ServerboundContainerButtonClickPacket(handler.containerId, buttonId);
        });

        register(ServerboundUseItemPacket.class, (packet, mc) -> {
            return new ServerboundUseItemPacket(
                packet.getHand(),
                0,
                mc.player.getYRot(),
                mc.player.getXRot()
            );
        });

        register(ServerboundMovePlayerPacket.PosRot.class, (packet, mc) -> {
            return new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                mc.player.getYRot(),
                mc.player.getXRot(),
                mc.player.onGround(),
                mc.player.horizontalCollision
            );
        });

        register(ServerboundMovePlayerPacket.Pos.class, (packet, mc) -> {
            return new ServerboundMovePlayerPacket.Pos(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                mc.player.onGround(),
                mc.player.horizontalCollision
            );
        });

        register(ServerboundMovePlayerPacket.Rot.class, (packet, mc) -> {
            return new ServerboundMovePlayerPacket.Rot(
                mc.player.getYRot(),
                mc.player.getXRot(),
                mc.player.onGround(),
                mc.player.horizontalCollision
            );
        });

        register(ServerboundMovePlayerPacket.StatusOnly.class, (packet, mc) -> {
            return new ServerboundMovePlayerPacket.StatusOnly(
                mc.player.onGround(),
                mc.player.horizontalCollision
            );
        });

        register(ServerboundInteractPacket.class, (packet, mc) -> {

            int originalEntityId = packet.entityId();
            Entity originalEntity = mc.level != null ? mc.level.getEntity(originalEntityId) : null;

            if (originalEntity == null && mc.level != null) {

                Vec3 playerPos = mc.player.getEyePosition().subtract(0, mc.player.getEyeHeight(), 0);
                AABB searchAABB = new AABB(playerPos.subtract(5, 5, 5), playerPos.add(5, 5, 5));
                List<Entity> nearby = new ArrayList<>();
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity != null && entity != mc.player && searchAABB.contains(entity.position())) {
                        nearby.add(entity);
                    }
                }

                if (!nearby.isEmpty()) {

                    originalEntity = nearby.stream()
                        .min((a, b) -> Double.compare(a.distanceToSqr(playerPos), b.distanceToSqr(playerPos)))
                        .orElse(null);
                }
            }

            if (originalEntity != null) {

                return new ServerboundInteractPacket(originalEntity.getId(), packet.hand(), packet.location(), packet.usingSecondaryAction());
            }

            return packet;
        });

        register(ServerboundSetCarriedItemPacket.class, (packet, mc) -> {

            return packet;
        });

        register(ServerboundContainerClosePacket.class, (packet, mc) -> {
            AbstractContainerMenu handler = mc.player.containerMenu;
            if (handler == null) return packet;
            return new ServerboundContainerClosePacket(handler.containerId);
        });

        register(ServerboundChatPacket.class, (packet, mc) -> {

            return packet;
        });

        register(ServerboundCustomPayloadPacket.class, (packet, mc) -> {
            CustomPacketPayload payload = packet.payload();
            if (payload == null) return null;

            String channel = AutismPayloadSupport.payloadChannel(payload);
            byte[] rawBytes = AutismPayloadSupport.extractPayloadBytes(payload);

            if (channel == null || channel.isBlank()) {
                AutismClientAddon.LOG.warn("[Autism] Cannot regenerate ServerboundCustomPayloadPacket - channel is blank");
                return null;
            }

            return AutismPayloadSupport.createC2SPacket(channel, rawBytes);
        });
    }

    private static <T extends Packet<?>> void register(Class<T> clazz, PacketHandler<T> handler) {
        handlers.put(clazz, handler);
    }

    @FunctionalInterface
    public interface PacketHandler<T extends Packet<?>> {
        T regenerate(T original, Minecraft mc);
    }

    private static int getFieldInt(Object obj, String hint, Class<?> type, int index) {
        try {
            Field field = findField(obj.getClass(), type, index);
            if (type == byte.class) return field.getByte(obj);
            if (type == short.class) return field.getShort(obj);
            return field.getInt(obj);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void setFieldInt(Object obj, String hint, Class<?> type, int index, int value) throws Exception {
        Field field = findField(obj.getClass(), type, index);
        field.setInt(obj, value);
    }

    private static String getFieldString(Object obj, String hint, int index) {
        try {
            Field field = findField(obj.getClass(), String.class, index);
            return (String) field.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E getFieldEnum(Object obj, Class<E> enumClass) {
        try {
            Field field = findField(obj.getClass(), enumClass, 0);
            return (E) field.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static Field findField(Class<?> clazz, Class<?> type, int index) throws NoSuchFieldException {
        List<Field> matches = new ArrayList<>();
        Class<?> current = clazz;

        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (f.getType().equals(type)) {
                    matches.add(f);
                }
            }
            current = current.getSuperclass();
        }

        if (index >= 0 && index < matches.size()) {
            Field f = matches.get(index);
            f.setAccessible(true);
            return f;
        }

        throw new NoSuchFieldException("Field of type " + type.getName() + " at index " + index + " not found");
    }
}
