package autismclient.util.multi;

final class MultiTimerBudget {

    static final long TICK_NANOS = 50_000_000L;

    static final long DRIFT_NANOS = 120_000_000L;

    private long balanceNanos;

    MultiTimerBudget() {
        reset(System.nanoTime());
    }

    synchronized void reset(long nowNanos) {
        balanceNanos = nowNanos - DRIFT_NANOS;
    }

    synchronized boolean reserve(long nowNanos) {
        if (balanceNanos < nowNanos - DRIFT_NANOS) balanceNanos = nowNanos - DRIFT_NANOS;
        if (balanceNanos > nowNanos) return false;
        balanceNanos += TICK_NANOS;
        return true;
    }

    synchronized int available(long nowNanos) {
        long floored = Math.max(balanceNanos, nowNanos - DRIFT_NANOS);
        if (floored > nowNanos) return 0;
        return (int) ((nowNanos - floored) / TICK_NANOS) + 1;
    }

    synchronized long drainMillis(long nowNanos, int packets) {
        if (packets <= 0) return 0L;
        long floored = Math.max(balanceNanos, nowNanos - DRIFT_NANOS);
        long finishAt = floored + (long) (packets - 1) * TICK_NANOS;
        return Math.max(0L, (finishAt - nowNanos) / 1_000_000L);
    }
}
