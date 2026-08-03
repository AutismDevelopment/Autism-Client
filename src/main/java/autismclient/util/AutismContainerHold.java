package autismclient.util;

import autismclient.modules.PackHideState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class AutismContainerHold {

    private static final long IDLE_TTL_MS = 60_000L;

    private static final Map<Integer, AtomicInteger> HOLDS = new ConcurrentHashMap<>();
    private static final Map<Integer, ServerboundContainerClosePacket> PENDING_CLOSES = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_TOUCHED_MS = new ConcurrentHashMap<>();

    private static volatile Consumer<AutismPacketClick.Target> pendingCapture = null;

    private AutismContainerHold() {}

    public static void hold(int containerId) {
        if (containerId < 0) return;
        HOLDS.computeIfAbsent(containerId, id -> new AtomicInteger(0)).incrementAndGet();
        LAST_TOUCHED_MS.put(containerId, System.currentTimeMillis());
    }

    public static boolean isHeld(int containerId) {
        AtomicInteger counter = HOLDS.get(containerId);
        return counter != null && counter.get() > 0;
    }

    public static void capturePendingClose(int containerId, ServerboundContainerClosePacket pkt) {
        if (pkt == null) return;
        PENDING_CLOSES.put(containerId, pkt);
        LAST_TOUCHED_MS.put(containerId, System.currentTimeMillis());
    }

    public static void release(int containerId, ClientPacketListener conn) {
        AtomicInteger counter = HOLDS.get(containerId);
        if (counter == null) {
            flushPendingClose(containerId, conn);
            return;
        }
        if (counter.decrementAndGet() <= 0) {
            HOLDS.remove(containerId);
            flushPendingClose(containerId, conn);
            LAST_TOUCHED_MS.remove(containerId);
        } else {
            LAST_TOUCHED_MS.put(containerId, System.currentTimeMillis());
        }
    }

    private static void flushPendingClose(int containerId, ClientPacketListener conn) {
        ServerboundContainerClosePacket pending = PENDING_CLOSES.remove(containerId);
        if (PackHideState.isHardLocked()) return;
        if (pending != null && conn != null) {
            try {
                conn.send(pending);
            } catch (Throwable ignored) {  }
        }
    }

    public static void clearAll() {
        HOLDS.clear();
        PENDING_CLOSES.clear();
        LAST_TOUCHED_MS.clear();
    }

    public static void releaseAllAndFlush(ClientPacketListener conn) {
        HOLDS.clear();
        LAST_TOUCHED_MS.clear();
        if (conn == null || PackHideState.isHardLocked()) {
            PENDING_CLOSES.clear();
            return;
        }
        for (Map.Entry<Integer, ServerboundContainerClosePacket> entry : PENDING_CLOSES.entrySet()) {
            try {
                conn.send(entry.getValue());
            } catch (Throwable ignored) {  }
        }
        PENDING_CLOSES.clear();
    }

    public static void onContainerOpened(int newContainerId) {
        if (HOLDS.remove(newContainerId) != null) {
            PENDING_CLOSES.remove(newContainerId);
            LAST_TOUCHED_MS.remove(newContainerId);
        }
    }

    public static void tickExpiry() {
        if (LAST_TOUCHED_MS.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<Integer> expired = new ArrayList<>();
        for (Iterator<Map.Entry<Integer, Long>> it = LAST_TOUCHED_MS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> e = it.next();
            if (now - e.getValue() >= IDLE_TTL_MS) {
                expired.add(e.getKey());
            }
        }
        if (expired.isEmpty()) return;
        ClientPacketListener conn = null;
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            conn = mc == null ? null : mc.getConnection();
        } catch (Throwable ignored) {  }
        for (Integer id : expired) {
            HOLDS.remove(id);
            LAST_TOUCHED_MS.remove(id);
            flushPendingClose(id, conn);
        }
    }

    public static boolean hasExpiryWork() {
        return !LAST_TOUCHED_MS.isEmpty();
    }

    public static void setPendingCapture(Consumer<AutismPacketClick.Target> consumer) {
        pendingCapture = consumer;
    }

    public static boolean hasPendingCapture() {
        return pendingCapture != null;
    }

    public static boolean deliverCapture(AutismPacketClick.Target target) {
        Consumer<AutismPacketClick.Target> consumer = pendingCapture;
        if (consumer == null || target == null) return false;
        pendingCapture = null;
        try {
            consumer.accept(target);
        } catch (Throwable ignored) {  }
        return true;
    }

    public static void clearPendingCapture() {
        pendingCapture = null;
    }
}
