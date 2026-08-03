package autismclient.util;

import java.util.Random;

public final class AutismClickPacer {
    private final Random rng = new Random();
    private double budget;
    private double scheduledCps;
    private double tempoDrift;
    private long lastNanos;

    public void reset() {
        budget = 0.0;
        scheduledCps = 0.0;
        tempoDrift = 0.0;
        lastNanos = 0L;
    }

    public boolean shouldClick(int cap) {
        int c = Math.max(1, cap);
        long now = System.nanoTime();
        long elapsed;
        if (lastNanos == 0L) {
            tempoDrift = 0.0;
            scheduledCps = rollHumanCps(c);
            budget = 1.0;
            elapsed = 0L;
        } else {
            elapsed = Math.max(0L, Math.min(200_000_000L, now - lastNanos));
        }
        lastNanos = now;
        budget = Math.min(1.6, budget + elapsed * scheduledCps / 1_000_000_000.0);
        if (budget < 1.0) return false;
        budget -= 1.0;
        scheduledCps = rollHumanCps(c);
        return true;
    }

    private double rollHumanCps(int cap) {
        tempoDrift += (rng.nextDouble() - 0.5) * 0.25;
        tempoDrift = Math.max(-2.0, Math.min(0.0, tempoDrift));
        double jitter = Math.abs(rng.nextGaussian()) * 0.55;
        double cps = cap + tempoDrift - jitter;
        double floor = Math.max(1.0, cap * 0.6);
        return Math.max(floor, Math.min(cap, cps));
    }
}
