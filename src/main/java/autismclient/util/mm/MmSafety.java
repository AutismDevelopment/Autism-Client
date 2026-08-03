package autismclient.util.mm;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MmSafety {
    private static final long TIMESTAMP_WINDOW_MS = 120_000;
    private static final long MSGID_TTL_MS = 300_000;
    private static final int BUCKET_CAPACITY = 24;
    private static final double REFILL_PER_SEC = 6.0;

    private static final int MAX_MSGIDS = 8192;
    private static final int MAX_SENDERS = 4096;
    private static final long SENDER_IDLE_MS = 600_000;

    private static final int GLOBAL_CAPACITY = 256;
    private static final double GLOBAL_REFILL_PER_SEC = 128.0;

    private final Map<String, Long> seenMsgIds = new ConcurrentHashMap<>();
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final TokenBucket globalGate = new TokenBucket(System.currentTimeMillis(), GLOBAL_CAPACITY, GLOBAL_REFILL_PER_SEC);

    public final AtomicLong droppedReplay = new AtomicLong();
    public final AtomicLong droppedStale = new AtomicLong();
    private volatile long lastStaleLogMs;
    public final AtomicLong droppedFlood = new AtomicLong();
    public final AtomicLong droppedOversize = new AtomicLong();
    public final AtomicLong droppedAuth = new AtomicLong();

    public static int maxSizeFor(MmMessageType type) {
        if (type == null) return 0;
        return switch (type) {
            case CHAT -> 2 * 1024;
            case COMMAND_OFFER -> 4 * 1024;
            case MACRO_OFFER, PACKET_OFFER -> 32 * 1024;

            case BLOB_OFFER -> 60 * 1024;
            case PRESENCE -> 2 * 1024;
            case LOCATION -> 256;
            case LEAVE -> 64;
            case KICK -> 64;
            case RECEIPT -> 64;
        };
    }

    public boolean withinSizeLimit(MmMessageType type, int payloadLen) {
        boolean ok = payloadLen <= maxSizeFor(type);
        if (!ok) droppedOversize.incrementAndGet();
        return ok;
    }

    public boolean admitPreDecrypt() {
        if (!globalGate.tryConsume(System.currentTimeMillis())) { droppedFlood.incrementAndGet(); return false; }
        return true;
    }

    public boolean accept(MmEnvelope env) {
        long now = System.currentTimeMillis();
        purge(now);

        long serverNow = ServerClock.nowMs();
        if (Math.abs(serverNow - env.timestamp) > TIMESTAMP_WINDOW_MS) {
            droppedStale.incrementAndGet();

            if (now - lastStaleLogMs > 10_000) {
                lastStaleLogMs = now;
                autismclient.AutismClientAddon.LOG.info("[mm-safety] dropped stale frame: sender timestamp {}s {} server time (limit ±120s)",
                    Math.abs(env.timestamp - serverNow) / 1000, env.timestamp > serverNow ? "ahead of" : "behind");
            }
            return false;
        }

        String fp = env.senderFpHex();
        if (!withinSizeLimit(env.type(), env.payload.length)) return false;

        String msgKey = fp + ':' + bytesHex(env.msgId);
        if (seenMsgIds.putIfAbsent(msgKey, now) != null) { droppedReplay.incrementAndGet(); return false; }

        TokenBucket bucket = buckets.computeIfAbsent(fp, k -> new TokenBucket(now, BUCKET_CAPACITY, REFILL_PER_SEC));
        if (!bucket.tryConsume(now)) { droppedFlood.incrementAndGet(); return false; }

        return true;
    }

    public void forgetPeer(String fpHex) {
        buckets.remove(fpHex);
        seenMsgIds.keySet().removeIf(k -> k.startsWith(fpHex + ':'));
    }

    private void purge(long now) {

        seenMsgIds.values().removeIf(t -> now - t > MSGID_TTL_MS);
        if (seenMsgIds.size() > MAX_MSGIDS) evictOldestByValue(seenMsgIds, MAX_MSGIDS);

        if (buckets.size() > MAX_SENDERS) {
            buckets.entrySet().removeIf(e -> now - e.getValue().lastActivityMs() > SENDER_IDLE_MS);
            if (buckets.size() > MAX_SENDERS) evictOldestBuckets(MAX_SENDERS);
        }
    }

    private static void evictOldestByValue(Map<String, Long> map, int cap) {
        int over = map.size() - cap;
        if (over <= 0) return;
        List<String> victims = map.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .limit(over)
            .map(Map.Entry::getKey)
            .toList();
        victims.forEach(map::remove);
    }

    private void evictOldestBuckets(int cap) {
        int over = buckets.size() - cap;
        if (over <= 0) return;
        List<String> victims = buckets.entrySet().stream()
            .sorted(Comparator.comparingLong(e -> e.getValue().lastActivityMs()))
            .limit(over)
            .map(Map.Entry::getKey)
            .toList();
        victims.forEach(buckets::remove);
    }

    private static String bytesHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }

    private static final class TokenBucket {
        private final double capacity;
        private final double refillPerSec;
        private double tokens;
        private long lastRefillMs;
        TokenBucket(long now, double capacity, double refillPerSec) {
            this.capacity = capacity;
            this.refillPerSec = refillPerSec;
            this.tokens = capacity;
            this.lastRefillMs = now;
        }
        long lastActivityMs() { return lastRefillMs; }
        synchronized boolean tryConsume(long now) {
            double elapsed = (now - lastRefillMs) / 1000.0;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed * refillPerSec);
                lastRefillMs = now;
            }
            if (tokens >= 1.0) { tokens -= 1.0; return true; }
            return false;
        }
    }
}
