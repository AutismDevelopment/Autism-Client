package autismclient.modules;

import autismclient.AutismClientAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class AutismBlinkManager {

    private static final Minecraft MC = Minecraft.getInstance();

    private static volatile boolean blinkIncoming;
    private static volatile boolean blinkOutgoing;
    private static volatile boolean autoResetEnabled;
    private static volatile int autoResetTicks = 100;

    private static volatile boolean holdMovement = true;
    private static volatile boolean holdActions = true;

    private static volatile boolean hasServerPos;
    private static volatile boolean showPosition = true;
    private static volatile double serverX, serverY, serverZ;
    private static volatile float serverYaw, serverPitch, serverHeadYaw, serverBodyYaw;

    private static AutismBlinkFakePlayer clone;
    private static boolean cloneDirty;

    private static final Queue<Packet<?>> INCOMING = new ConcurrentLinkedQueue<>();
    private static final Queue<Packet<?>> OUTGOING = new ConcurrentLinkedQueue<>();

    private static final Set<Packet<?>> PASS_THROUGH =
        Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final AtomicInteger PASS_THROUGH_COUNT = new AtomicInteger();
    private static volatile boolean flushIncomingNow;
    private static int heldTicks;

    private AutismBlinkManager() {}

    public static void setDirections(boolean incoming, boolean outgoing) {
        blinkIncoming = incoming;
        blinkOutgoing = outgoing;
    }

    public static void setAutoReset(boolean enabled, int ticks) {
        autoResetEnabled = enabled;
        autoResetTicks = Math.max(1, ticks);
    }

    public static void setScope(boolean movement, boolean actions) {
        holdMovement = movement;
        holdActions = actions;
    }

    public static void setShowPosition(boolean show) {
        showPosition = show;
    }

    public static boolean isActive() {
        return blinkIncoming || blinkOutgoing;
    }

    public static int held() {
        return INCOMING.size() + OUTGOING.size();
    }

    public static int ticksUntilReset() {
        if (!autoResetEnabled || held() <= 0) return -1;
        return Math.max(0, autoResetTicks - heldTicks);
    }

    public static void captureServerPos() {
        LocalPlayer player = MC.player;
        if (player == null) { hasServerPos = false; return; }
        serverX = player.getX();
        serverY = player.getY();
        serverZ = player.getZ();
        serverYaw = player.getYRot();
        serverPitch = player.getXRot();
        serverHeadYaw = player.yHeadRot;
        serverBodyYaw = player.yBodyRot;
        hasServerPos = true;
        cloneDirty = true;
    }

    private static void updateClone() {
        boolean want = blinkOutgoing && holdMovement && showPosition && hasServerPos
            && MC.player != null && MC.level != null;
        if (!want) { despawnClone(); return; }
        if (clone == null || clone.isRemoved() || clone.level() != MC.level) {
            spawnClone();
            return;
        }
        if (cloneDirty) {
            positionClone(clone);
            cloneDirty = false;
        }
    }

    private static void spawnClone() {
        despawnClone();
        LocalPlayer player = MC.player;
        ClientLevel level = MC.level;
        if (player == null || level == null) return;
        try {
            AutismBlinkFakePlayer fake = new AutismBlinkFakePlayer(level, player);
            positionClone(fake);
            level.addEntity(fake);
            clone = fake;
            cloneDirty = false;
        } catch (Throwable t) {
            clone = null;
            AutismClientAddon.LOG.warn("[Autism] Blink clone spawn failed", t);
        }
    }

    private static void positionClone(AutismBlinkFakePlayer fake) {
        fake.snapTo(serverX, serverY, serverZ, serverYaw, serverPitch);
        fake.freezeHeadRotation(serverHeadYaw, serverBodyYaw);
    }

    private static void despawnClone() {
        if (clone != null) {
            try { clone.discard(); } catch (Throwable ignored) {  }
            clone = null;
        }
    }

    public static void disableAndFlush() {
        blinkIncoming = false;
        blinkOutgoing = false;
        flushAll();
        heldTicks = 0;
        hasServerPos = false;
    }

    public static void clear() {
        INCOMING.clear();
        OUTGOING.clear();
        PASS_THROUGH.clear();
        PASS_THROUGH_COUNT.set(0);
        flushIncomingNow = false;
        heldTicks = 0;
        hasServerPos = false;
    }

    public static boolean interceptInbound(Packet<?> packet) {
        if (!blinkIncoming || MC.player == null) return false;
        if (isIncomingNeverHold(packet)) return false;
        if (isIncomingFlushTrigger(packet)) {
            INCOMING.add(packet);
            flushIncomingNow = true;
            return true;
        }
        INCOMING.add(packet);
        return true;
    }

    public static boolean interceptOutbound(Packet<?> packet) {
        if (consumePassThrough(packet)) return false;
        if (!blinkOutgoing || MC.player == null) return false;
        if (isOutgoingNeverHold(packet)) return false;
        if (!isInOutboundScope(packet)) return false;
        OUTGOING.add(packet);
        return true;
    }

    private static boolean isInOutboundScope(Packet<?> packet) {
        if (holdMovement && packet instanceof ServerboundMovePlayerPacket) return true;
        if (holdActions && isActionPacket(packet)) return true;
        return false;
    }

    private static boolean isActionPacket(Packet<?> packet) {
        return packet instanceof ServerboundPlayerActionPacket
            || packet instanceof ServerboundUseItemOnPacket
            || packet instanceof ServerboundUseItemPacket
            || packet instanceof ServerboundInteractPacket
            || packet instanceof ServerboundSwingPacket;
    }

    public static void tick() {
        if (!blinkIncoming && !blinkOutgoing && INCOMING.isEmpty() && OUTGOING.isEmpty()
                && PASS_THROUGH_COUNT.get() == 0 && !flushIncomingNow && clone == null) {
            heldTicks = 0;
            return;
        }
        if (MC.getConnection() == null) {
            if (held() > 0 || PASS_THROUGH_COUNT.get() > 0) clear();
            despawnClone();
            return;
        }
        if (flushIncomingNow) {
            flushIncomingNow = false;
            flushIncoming();
        }
        if (held() > 0) {
            heldTicks++;
            if (autoResetEnabled && heldTicks >= autoResetTicks) {
                flushAll();
                heldTicks = 0;

                captureServerPos();
            }
        } else {
            heldTicks = 0;
        }
        updateClone();
    }

    public static void flushAll() {
        flushIncoming();
        flushOutgoing();
    }

    public static void flushIncoming() {
        ClientPacketListener connection = MC.getConnection();
        Packet<?> packet;
        while ((packet = INCOMING.poll()) != null) {
            if (connection != null) deliverIncoming(connection, packet);
        }
    }

    public static void flushOutgoing() {
        ClientPacketListener connection = MC.getConnection();
        Packet<?> packet;
        while ((packet = OUTGOING.poll()) != null) {
            if (connection == null) continue;
            addPassThrough(packet);
            try {
                connection.send(packet);
            } catch (Throwable t) {
                consumePassThrough(packet);
                logRedispatchError("outgoing", packet, t);
            }
        }
    }

    private static void addPassThrough(Packet<?> packet) {
        if (packet != null && PASS_THROUGH.add(packet)) PASS_THROUGH_COUNT.incrementAndGet();
    }

    private static boolean consumePassThrough(Packet<?> packet) {
        if (PASS_THROUGH_COUNT.get() == 0 || packet == null) return false;
        if (!PASS_THROUGH.remove(packet)) return false;
        PASS_THROUGH_COUNT.updateAndGet(count -> Math.max(0, count - 1));
        return true;
    }

    private static void deliverIncoming(ClientPacketListener connection, Packet<?> packet) {
        try {
            @SuppressWarnings("unchecked")
            Packet<ClientGamePacketListener> gamePacket = (Packet<ClientGamePacketListener>) (Packet<?>) packet;
            gamePacket.handle(connection);
        } catch (RunningOnDifferentThreadException ignored) {

        } catch (Throwable t) {
            logRedispatchError("incoming", packet, t);
        }
    }

    private static volatile long lastErrorLogMs;
    private static final long ERROR_LOG_INTERVAL_MS = 5_000L;

    private static void logRedispatchError(String direction, Packet<?> packet, Throwable t) {
        if (t instanceof RunningOnDifferentThreadException
                || t.getCause() instanceof RunningOnDifferentThreadException) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastErrorLogMs < ERROR_LOG_INTERVAL_MS) return;
        lastErrorLogMs = now;
        AutismClientAddon.LOG.warn("[Autism] Blink re-dispatch ({}) failed for {}",
            direction, packet.getClass().getSimpleName(), t);
    }

    private static boolean isIncomingNeverHold(Packet<?> packet) {

        return packet instanceof ClientboundKeepAlivePacket
            || packet instanceof ClientboundPingPacket
            || packet instanceof ClientboundSystemChatPacket
            || packet instanceof ClientboundDisguisedChatPacket;
    }

    private static boolean isIncomingFlushTrigger(Packet<?> packet) {
        return packet instanceof ClientboundPlayerPositionPacket
            || packet instanceof ClientboundRespawnPacket
            || packet instanceof ClientboundLoginPacket
            || packet instanceof ClientboundDisconnectPacket
            || (packet instanceof ClientboundSetHealthPacket health && health.getHealth() <= 0.0F);
    }

    private static boolean isOutgoingNeverHold(Packet<?> packet) {
        return packet instanceof ServerboundKeepAlivePacket
            || packet instanceof ServerboundPongPacket
            || packet instanceof ServerboundChatPacket
            || packet instanceof ServerboundChatCommandPacket
            || packet instanceof ServerboundResourcePackPacket;
    }
}
