package autismclient.util.macro;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FpsLimitController {

    private static final long LEGACY_OWNER = 0L;
    private static final ConcurrentHashMap<Long, Override> OVERRIDES = new ConcurrentHashMap<>();

    private static volatile int publishedLimit = -1;
    private static volatile long publishedNextExpiry = Long.MAX_VALUE;

    private record Override(int limit, long expiryNanos, boolean indefinite) {
        boolean active(long now) {
            return indefinite || now - expiryNanos < 0L;
        }
    }

    private FpsLimitController() {}

    public static void apply(int fpsLimit, long durationNanos) {
        apply(LEGACY_OWNER, fpsLimit, durationNanos);
    }

    public static synchronized void apply(long ownerRunId, int fpsLimit, long durationNanos) {
        if (durationNanos <= 0L) {
            clear(ownerRunId);
            return;
        }
        OVERRIDES.put(ownerRunId, new Override(Math.max(0, fpsLimit), System.nanoTime() + durationNanos, false));
        republish(System.nanoTime());
    }

    public static void applyUntilCleared(int fpsLimit) {
        applyUntilCleared(LEGACY_OWNER, fpsLimit);
    }

    public static synchronized void applyUntilCleared(long ownerRunId, int fpsLimit) {
        OVERRIDES.put(ownerRunId, new Override(Math.max(0, fpsLimit), 0L, true));
        republish(System.nanoTime());
    }

    public static void clear() {
        clear(LEGACY_OWNER);
    }

    public static synchronized void clear(long ownerRunId) {
        OVERRIDES.remove(ownerRunId);
        republish(System.nanoTime());
    }

    public static synchronized void clearAll() {
        OVERRIDES.clear();
        publishedLimit = -1;
        publishedNextExpiry = Long.MAX_VALUE;
    }

    public static boolean isActive() {
        return activeLimit() >= 0;
    }

    public static int limit() {
        int active = activeLimit();
        return Math.max(0, active);
    }

    public static int activeLimit() {
        int limit = publishedLimit;
        if (limit < 0) return -1;
        long expiry = publishedNextExpiry;
        if (expiry != Long.MAX_VALUE && System.nanoTime() - expiry >= 0L) {
            return refreshExpired();
        }
        return limit;
    }

    public static boolean shouldFreeze() {
        return activeLimit() == 0;
    }

    public static long remainingMillis() {
        if (activeLimit() < 0) return 0L;
        long now = System.nanoTime();
        long longest = 0L;
        for (Override override : OVERRIDES.values()) {
            if (override.indefinite) return Long.MAX_VALUE;
            longest = Math.max(longest, override.expiryNanos - now);
        }
        return longest > 0L ? longest / 1_000_000L : 0L;
    }

    private static synchronized int refreshExpired() {
        long now = System.nanoTime();
        if (publishedLimit < 0 || publishedNextExpiry == Long.MAX_VALUE || now - publishedNextExpiry < 0L) {
            return publishedLimit;
        }
        republish(now);
        return publishedLimit;
    }

    private static void republish(long now) {
        int effective = Integer.MAX_VALUE;
        long nextExpiry = Long.MAX_VALUE;
        for (Map.Entry<Long, Override> entry : OVERRIDES.entrySet()) {
            Override override = entry.getValue();
            if (!override.active(now)) {
                OVERRIDES.remove(entry.getKey(), override);
                continue;
            }
            effective = Math.min(effective, override.limit);
            if (!override.indefinite) nextExpiry = Math.min(nextExpiry, override.expiryNanos);
        }
        publishedLimit = effective == Integer.MAX_VALUE ? -1 : effective;
        publishedNextExpiry = publishedLimit < 0 ? Long.MAX_VALUE : nextExpiry;
    }
}
