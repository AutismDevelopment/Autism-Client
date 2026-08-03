package autismclient.util.macro;

import autismclient.modules.PackHideState;
import autismclient.util.AutismPacketNamer;
import autismclient.util.AutismPacketRegistry;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.Packet;

public final class PacketGateManager {
    public enum Result { PASS, CANCEL, DELAY }

    public static final class Gate {
        public final long ownerRunId;
        public final String id;
        public final PacketGateAction.GateMode mode;
        public final ArrayList<String> packets;
        public final boolean flushOnDisable;

        final String[] normalizedPackets;
        final boolean matchesAll;

        Gate(long ownerRunId, String id, PacketGateAction.GateMode mode, ArrayList<String> packets, boolean flushOnDisable) {
            this.ownerRunId = ownerRunId;
            this.id = id;
            this.mode = mode;
            this.packets = packets;
            this.flushOnDisable = flushOnDisable;
            boolean all = packets.isEmpty();
            String[] normalized = new String[packets.size()];
            for (int i = 0; i < packets.size(); i++) {
                String pattern = packets.get(i);
                if (pattern == null || pattern.isBlank()) {
                    all = true;
                    normalized[i] = "";
                } else {
                    normalized[i] = normalize(pattern);
                }
            }
            this.matchesAll = all;
            this.normalizedPackets = normalized;
        }
    }

    private static final ConcurrentHashMap<String, Gate> GATES = new ConcurrentHashMap<>();
    private static volatile String lastOpenedGateKey = null;

    private PacketGateManager() {}

    public static void install(PacketGateAction action) {
        install(action, 0L);
    }

    public static void install(PacketGateAction action, long ownerRunId) {
        if (PackHideState.isHardLocked()) return;
        installOwned(action, ownerRunId);
    }

    static void installOwned(PacketGateAction action, long ownerRunId) {
        if (action == null) return;
        ownerRunId = Math.max(0L, ownerRunId);
        String id = action.gateId == null || action.gateId.isBlank() ? "auto" : action.gateId.trim();
        if (action.mode == PacketGateAction.GateMode.DISABLE_GATE) {
            disable(id, ownerRunId);
            return;
        }
        String key = gateKey(ownerRunId, id);
        GATES.put(key, new Gate(ownerRunId, id, action.mode, action.effectivePackets(), action.flushOnDisable));
        lastOpenedGateKey = key;
    }

    public static void disable(String id) {
        if (id == null || id.isBlank() || id.equalsIgnoreCase("all")) {
            clearAll();
            return;
        }
        String target = id.trim();
        GATES.entrySet().removeIf(entry -> entry.getValue().id.equalsIgnoreCase(target));
        refreshLastOpenedGate();
    }

    public static void disable(String id, long ownerRunId) {
        if (ownerRunId <= 0L) {
            disable(id);
            return;
        }
        if (id == null || id.isBlank() || id.equalsIgnoreCase("all")) {
            GATES.entrySet().removeIf(entry -> entry.getValue().ownerRunId == ownerRunId);
        } else {
            GATES.remove(gateKey(ownerRunId, id));
        }
        refreshLastOpenedGate();
    }

    public static void disableAndFlushConfigured(String id, net.minecraft.client.multiplayer.ClientPacketListener connection, boolean flushRequested) {
        disableAndFlushConfigured(id, 0L, connection, flushRequested);
    }

    public static void disableAndFlushConfigured(String id, long ownerRunId,
                                                  net.minecraft.client.multiplayer.ClientPacketListener connection,
                                                  boolean flushRequested) {
        if (PackHideState.isHardLocked()) {
            disable(id, ownerRunId);
            return;
        }
        if (ownerRunId <= 0L && (id == null || id.isBlank() || id.equalsIgnoreCase("all"))) {
            clearAllAndFlushConfigured(connection);
            return;
        }
        ArrayList<Gate> removed = new ArrayList<>();
        if (id == null || id.isBlank() || id.equalsIgnoreCase("all")) {
            GATES.entrySet().removeIf(entry -> {
                if (entry.getValue().ownerRunId != ownerRunId) return false;
                removed.add(entry.getValue());
                return true;
            });
        } else if (ownerRunId > 0L) {
            Gate gate = GATES.remove(gateKey(ownerRunId, id));
            if (gate != null) removed.add(gate);
        } else {
            String target = id.trim();
            GATES.entrySet().removeIf(entry -> {
                if (!entry.getValue().id.equalsIgnoreCase(target)) return false;
                removed.add(entry.getValue());
                return true;
            });
        }
        refreshLastOpenedGate();
        boolean shouldFlush = flushRequested || removed.stream().anyMatch(gate -> gate.flushOnDisable);
        if (shouldFlush && !hasActiveDelayGate()) {
            autismclient.util.AutismSharedState.get().flushDelayedPackets(connection);
        }
    }

    public static void disableCurrentGate() {
        if (PackHideState.isHardLocked()) {
            clearAll();
            return;
        }
        String key = lastOpenedGateKey;
        if (key != null) {
            Gate gate = GATES.get(key);
            if (gate != null) {
                disableAndFlushConfigured(gate.id, gate.ownerRunId,
                    net.minecraft.client.Minecraft.getInstance().getConnection(), true);
            }
        }
    }

    public static void clearAll() {
        GATES.clear();
        lastOpenedGateKey = null;
    }

    public static void clearAllAndFlushConfigured(net.minecraft.client.multiplayer.ClientPacketListener connection) {
        if (PackHideState.isHardLocked()) {
            clearAll();
            return;
        }
        boolean flush = GATES.values().stream().anyMatch(g -> g.mode == PacketGateAction.GateMode.DELAY && g.flushOnDisable);
        clearAll();
        if (flush) autismclient.util.AutismSharedState.get().flushDelayedPackets(connection);
    }

    public static void clearOwnerAndFlushConfigured(long ownerRunId,
                                                     net.minecraft.client.multiplayer.ClientPacketListener connection) {
        if (ownerRunId <= 0L) return;
        disableAndFlushConfigured("all", ownerRunId, connection, false);
    }

    private static boolean hasActiveDelayGate() {
        return GATES.values().stream().anyMatch(g -> g.mode == PacketGateAction.GateMode.DELAY);
    }

    private static String newestGateKeyOrNull() {
        String newest = null;
        for (String key : GATES.keySet()) newest = key;
        return newest;
    }

    private static void refreshLastOpenedGate() {
        String current = lastOpenedGateKey;
        if (current == null || !GATES.containsKey(current)) lastOpenedGateKey = newestGateKeyOrNull();
    }

    private static String gateKey(long ownerRunId, String id) {
        String normalized = id == null || id.isBlank() ? "auto" : id.trim().toLowerCase(Locale.ROOT);
        return ownerRunId + "\u0000" + normalized;
    }

    static int activeGateCountForOwner(long ownerRunId) {
        int count = 0;
        for (Gate gate : GATES.values()) {
            if (gate.ownerRunId == ownerRunId) count++;
        }
        return count;
    }

    public static Result handle(Packet<?> packet, String direction) {
        if (PackHideState.isHardLocked()) return Result.PASS;
        if (packet == null || GATES.isEmpty()) return Result.PASS;
        Result result = Result.PASS;
        for (Gate gate : GATES.values()) {
            boolean packetMatches = gate.matchesAll || anyNormalizedMatch(gate.normalizedPackets, packet, direction);
            switch (gate.mode) {
                case CANCEL -> { if (packetMatches) return Result.CANCEL; }
                case DELAY -> { if (packetMatches) result = Result.DELAY; }
                case ALLOW_ONLY -> { if (!packetMatches) return Result.CANCEL; }
                case DISABLE_GATE -> {}
            }
        }
        return result;
    }

    private static boolean anyNormalizedMatch(String[] normalizedPatterns, Packet<?> packet, String direction) {
        for (int i = 0; i < normalizedPatterns.length; i++) {
            if (matchesNormalized(normalizedPatterns[i], packet, direction)) return true;
        }
        return false;
    }

    public static boolean hasActiveGates() {
        if (PackHideState.isHardLocked()) return false;
        return !GATES.isEmpty();
    }

    public static boolean matchesPacket(String expected, Packet<?> packet, String direction) {
        if (expected == null || expected.isBlank()) return true;
        return matchesNormalized(normalize(expected), packet, direction);
    }

    private static boolean matchesNormalized(String want, Packet<?> packet, String direction) {
        if (want.isEmpty()) return false;
        if (contains(want, normalize(AutismPacketNamer.getFriendlyName(packet, direction)))) return true;
        if (contains(want, normalize(AutismPacketNamer.getFriendlyName(packet)))) return true;
        if (contains(want, normalize(packet.getClass().getSimpleName()))) return true;
        @SuppressWarnings("unchecked")
        String registry = normalize(AutismPacketRegistry.getName((Class<? extends Packet<?>>) packet.getClass()));
        return contains(want, registry);
    }

    private static boolean contains(String a, String b) {
        return !a.isEmpty() && !b.isEmpty() && (a.equals(b) || a.endsWith(b) || b.endsWith(a));
    }

    private static String normalize(String value) {
        return AutismPacketNamer.normalizePacketKey(value);
    }
}
