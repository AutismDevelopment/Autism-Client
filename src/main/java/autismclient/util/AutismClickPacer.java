package autismclient.util;

import java.util.Random;
import java.util.function.LongSupplier;

public final class AutismClickPacer {

    private static final double UNARMED_CPS = 12.0;

    private static final long PHASE_MIN_NANOS = 2_500_000_000L;
    private static final long PHASE_SPAN_NANOS = 3_000_000_000L;

    private static final double MAX_TICK_CPS = 20.0;
    private static final double TICK_SECONDS = 0.05;

    private static final int MIN_HESITATION_TICKS = 1;
    private static final int HESITATION_TICK_SPAN = 3;
    private static final double MEAN_HESITATION_TICKS = MIN_HESITATION_TICKS + (HESITATION_TICK_SPAN - 1) / 2.0;

    private static final double MAX_BANKED_CLICKS = 2.0;

    private static final int WINDOW_GAPS = 49;
    private static final long WINDOW_NANOS = 4_000_000_000L;

    private final Random rng;
    private final LongSupplier clock;

    private double budget;
    private double scheduledCps;
    private long lastNanos;

    private long phaseEndNanos;
    private double phaseCps;
    private double phaseRoughness;
    private double phaseHesitationChance;
    private int hesitationTicks;

    private final long[] recentClicks = new long[WINDOW_GAPS];
    private int recentIndex;
    private int recentCount;

    public AutismClickPacer() {
        this(new Random(), System::nanoTime);
    }

    AutismClickPacer(Random rng, LongSupplier clock) {
        this.rng = rng;
        this.clock = clock;
    }

    public void reset() {
        budget = 0.0;
        scheduledCps = 0.0;
        lastNanos = 0L;
        phaseEndNanos = 0L;
        phaseCps = 0.0;
        phaseRoughness = 0.0;
        phaseHesitationChance = 0.0;
        hesitationTicks = 0;

    }

    public boolean shouldClick(double minCps, double maxCps) {
        double high = Math.max(0.5, Math.max(minCps, maxCps));
        double low = Math.max(0.5, Math.min(minCps, maxCps));

        long now = clock.getAsLong();
        long elapsed;
        if (lastNanos == 0L) {
            hesitationTicks = 0;
            rollPhase(now, low, high);
            scheduledCps = rollClickCps();
            budget = 1.0;
            elapsed = 0L;
        } else {
            elapsed = Math.max(0L, Math.min(200_000_000L, now - lastNanos));
        }
        lastNanos = now;

        if (now >= phaseEndNanos) {
            rollPhase(now, low, high);
        }

        if (hesitationTicks > 0) {
            hesitationTicks--;
            return false;
        }

        budget = Math.min(MAX_BANKED_CLICKS, budget + elapsed * scheduledCps / 1_000_000_000.0);
        if (budget < 1.0) return false;
        if (high <= UNARMED_CPS && wouldFillWindow(now)) {
            return false;
        }
        recordClick(now);
        budget -= 1.0;
        scheduledCps = rollClickCps();
        if (phaseHesitationChance > 0.0 && rng.nextDouble() < phaseHesitationChance) {
            hesitationTicks = MIN_HESITATION_TICKS + rng.nextInt(HESITATION_TICK_SPAN);
        }
        return true;
    }

    private void rollPhase(long now, double low, double high) {
        phaseEndNanos = now + PHASE_MIN_NANOS + (long) (rng.nextDouble() * PHASE_SPAN_NANOS);
        boolean ragged = phaseRoughness < 0.3 ? rng.nextDouble() < 0.8 : rng.nextDouble() < 0.2;
        phaseRoughness = ragged ? 0.4 + rng.nextDouble() * 0.6 : rng.nextDouble() * 0.15;

        double placeInBand = 0.05 + 0.9 * phaseRoughness + rng.nextGaussian() * 0.18;
        double target = low + (high - low) * Math.max(0.0, Math.min(1.0, placeInBand));

        double wanted = ragged ? 0.02 + rng.nextDouble() * 0.12 : 0.0;

        double affordablePause = 1.0 / target - 1.0 / MAX_TICK_CPS;
        double affordableChance = affordablePause / (MEAN_HESITATION_TICKS * TICK_SECONDS);
        phaseHesitationChance = Math.max(0.0, Math.min(wanted, affordableChance));

        double spentOnPauses = phaseHesitationChance * MEAN_HESITATION_TICKS * TICK_SECONDS;
        double perClick = 1.0 / target - spentOnPauses;
        phaseCps = perClick > 0 ? Math.min(MAX_TICK_CPS, 1.0 / perClick) : MAX_TICK_CPS;
    }

    private boolean wouldFillWindow(long now) {
        if (recentCount < WINDOW_GAPS) return false;
        return now - recentClicks[recentIndex] < WINDOW_NANOS;
    }

    private void recordClick(long now) {
        recentClicks[recentIndex] = now;
        recentIndex = (recentIndex + 1) % WINDOW_GAPS;
        if (recentCount < WINDOW_GAPS) recentCount++;
    }

    private double rollClickCps() {

        double room = Math.min(0.3, Math.max(0.0, 1.0 - phaseCps / MAX_TICK_CPS));
        double wobble = rng.nextGaussian() * (0.01 + 0.12 * phaseRoughness);
        double period = (1.0 / phaseCps) * (1.0 + Math.max(-room, Math.min(room, wobble)));
        if (period <= 0) return Math.min(MAX_TICK_CPS, phaseCps);
        return Math.max(0.5, Math.min(MAX_TICK_CPS, 1.0 / period));
    }
}
