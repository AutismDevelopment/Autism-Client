package autismclient.util.mm.relay;

import autismclient.AutismClientAddon;
import autismclient.util.mm.crypto.MmCrypto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class RelayManager {
    private static final byte MAGIC0 = (byte) 0xA1;
    private static final byte MAGIC1 = (byte) 0x6D;
    private static final byte FRAME_VERSION = 1;
    private static final int HEADER = 2 + 1 + 16 + 2 + 2;
    private static final int MAX_CHUNK_PAYLOAD = 2400;
    public static final int MAX_ENVELOPE = 64 * 1024;
    private static final int MAX_CHUNKS = MAX_ENVELOPE / MAX_CHUNK_PAYLOAD + 2;
    private static final int MAX_PENDING_GROUPS = 256;
    private static final long GROUP_TTL_MS = 60_000;
    private static final long DELIVERED_TTL_MS = 180_000;

    private final List<Relay> relays = new ArrayList<>();
    private final Map<String, Consumer<byte[]>> topicConsumers = new ConcurrentHashMap<>();
    private final Map<String, PendingGroup> pending = new ConcurrentHashMap<>();
    private final Map<String, Long> deliveredGroups = new ConcurrentHashMap<>();
    private volatile long lastWarnMs;

    public RelayManager(List<Relay> relays) {
        this.relays.addAll(relays);
    }

    public List<RelayStatus> statuses() {
        List<RelayStatus> out = new ArrayList<>();
        for (Relay r : relays) out.add(r.status());
        return out;
    }

    public void subscribe(String topic, Consumer<byte[]> onEnvelope) {
        topicConsumers.put(topic, onEnvelope);
        for (Relay r : relays) r.subscribe(topic, frame -> onFrame(topic, frame));
    }

    public void unsubscribe(String topic) {
        topicConsumers.remove(topic);
        for (Relay r : relays) r.unsubscribe(topic);
        pending.values().removeIf(g -> g.topic.equals(topic));
    }

    public void subscribeRaw(String topic, Consumer<byte[]> onMessage) {
        for (Relay r : relays) r.subscribe(topic, payload -> {
            try {
                onMessage.accept(payload);
            } catch (Throwable t) {
                warnThrottled("Matchmaking: sys-event handler threw on a message", t);
            }
        });
    }

    public void unsubscribeRaw(String topic) {
        for (Relay r : relays) r.unsubscribe(topic);
    }

    public boolean publish(String topic, byte[] envelope) {
        return publish(topic, envelope, false);
    }

    public boolean publish(String topic, byte[] envelope, boolean durable) {
        if (envelope.length > MAX_ENVELOPE) return false;
        byte[] groupId = MmCrypto.randomBytes(16);
        int total = Math.max(1, (envelope.length + MAX_CHUNK_PAYLOAD - 1) / MAX_CHUNK_PAYLOAD);
        for (int index = 0; index < total; index++) {
            int off = index * MAX_CHUNK_PAYLOAD;
            int len = Math.min(MAX_CHUNK_PAYLOAD, envelope.length - off);
            byte[] frame = new byte[HEADER + len];
            frame[0] = MAGIC0; frame[1] = MAGIC1; frame[2] = FRAME_VERSION;
            System.arraycopy(groupId, 0, frame, 3, 16);
            putU16(frame, 19, total);
            putU16(frame, 21, index);
            System.arraycopy(envelope, off, frame, HEADER, len);
            for (Relay r : relays) r.publish(topic, frame, durable);
        }
        return true;
    }

    public void reconnectAll() {
        for (Relay r : relays) {
            try { r.reconnect(); } catch (Throwable ignored) {  }
        }
    }

    public void closeAll() {
        for (Relay r : relays) {
            try { r.close(); } catch (Throwable ignored) {  }
        }
        topicConsumers.clear();
        pending.clear();
        deliveredGroups.clear();
    }

    private void onFrame(String topic, byte[] frame) {

        try {
            if (frame.length < HEADER) return;
            if (frame[0] != MAGIC0 || frame[1] != MAGIC1 || frame[2] != FRAME_VERSION) return;
            int total = getU16(frame, 19);
            int index = getU16(frame, 21);
            if (total < 1 || total > MAX_CHUNKS || index < 0 || index >= total) return;

            String gid = MmCrypto.hex(java.util.Arrays.copyOfRange(frame, 3, 19));
            long now = System.currentTimeMillis();
            purge(now);
            if (deliveredGroups.containsKey(gid)) return;

            byte[] payload = java.util.Arrays.copyOfRange(frame, HEADER, frame.length);
            if (total == 1) {
                deliveredGroups.put(gid, now);
                deliver(topic, payload);
                return;
            }

            if (pending.size() >= MAX_PENDING_GROUPS && !pending.containsKey(gid)) return;
            PendingGroup group = pending.computeIfAbsent(gid, k -> new PendingGroup(topic, total, now));
            byte[] complete = group.add(index, payload);
            if (complete != null) {
                pending.remove(gid);
                deliveredGroups.put(gid, now);
                deliver(topic, complete);
            }
        } catch (Throwable t) {
            warnThrottled("Matchmaking: dropped a bad inbound frame", t);
        }
    }

    private void deliver(String topic, byte[] envelope) {
        Consumer<byte[]> consumer = topicConsumers.get(topic);
        if (consumer == null) return;
        try {
            consumer.accept(envelope);
        } catch (Throwable t) {

            warnThrottled("Matchmaking: inbound handler threw on a frame", t);
        }
    }

    private void warnThrottled(String msg, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastWarnMs < 1000) return;
        lastWarnMs = now;
        AutismClientAddon.LOG.warn(msg, t);
    }

    private void purge(long now) {
        pending.values().removeIf(g -> now - g.createdMs > GROUP_TTL_MS);
        deliveredGroups.values().removeIf(t -> now - t > DELIVERED_TTL_MS);
    }

    private static final class PendingGroup {
        final String topic;
        final int total;
        final long createdMs;
        final byte[][] chunks;
        int received;

        PendingGroup(String topic, int total, long createdMs) {
            this.topic = topic;
            this.total = total;
            this.createdMs = createdMs;
            this.chunks = new byte[total][];
        }

        synchronized byte[] add(int index, byte[] payload) {
            if (chunks[index] != null) return null;
            chunks[index] = payload;
            if (++received < total) return null;
            int size = 0;
            for (byte[] c : chunks) size += c.length;
            byte[] out = new byte[size];
            int off = 0;
            for (byte[] c : chunks) { System.arraycopy(c, 0, out, off, c.length); off += c.length; }
            return out;
        }
    }

    private static void putU16(byte[] b, int off, int v) {
        b[off] = (byte) ((v >>> 8) & 0xFF);
        b[off + 1] = (byte) (v & 0xFF);
    }

    private static int getU16(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }
}
