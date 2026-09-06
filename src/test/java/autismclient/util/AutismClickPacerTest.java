package autismclient.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AutismClickPacerTest {

    private static final long TICK_NANOS = 50_000_000L;
    private static final int TICKS_PER_RUN = 20 * 60 * 12;

    private static final class IntaveStability {
        private final Deque<Long> places = new ArrayDeque<>();
        private final Deque<Long> deviations = new ArrayDeque<>();
        private double vl;
        private double peakVl;
        private int violations;
        private long started;
        private long lastPlace = Long.MIN_VALUE;

        void place(long atMs) {
            long gap = lastPlace == Long.MIN_VALUE ? 0 : atMs - lastPlace;
            lastPlace = atMs;
            if (gap > 4000) {
                places.clear();
                return;
            }
            if (places.isEmpty()) started = atMs;
            places.add(gap);

            if (places.size() >= 50) {
                deviations.add((long) standardDeviation(places));
                places.clear();
            }
            if (deviations.size() >= 5) {
                double std = standardDeviation(deviations);
                long length = atMs - started;
                if (std < 10 && length < 4000) {
                    vl += 1;
                    if (vl > 2) violations++;
                } else if (vl > 0) {
                    vl -= 0.2;
                    vl *= 0.98;
                }
                peakVl = Math.max(peakVl, vl);
                deviations.clear();
            }
        }

        private static double standardDeviation(Collection<? extends Number> values) {
            double sum = 0;
            for (Number v : values) sum += v.doubleValue();
            double mean = sum / values.size();
            double squares = 0;
            for (Number v : values) squares += (v.doubleValue() - mean) * (v.doubleValue() - mean);
            return Math.sqrt(squares / values.size());
        }
    }

    private static List<Long> clickTimes(double minCps, double maxCps, long seed) {
        long[] nanos = {1_000_000_000L};
        AutismClickPacer pacer = new AutismClickPacer(new Random(seed), () -> nanos[0]);
        List<Long> clicks = new ArrayList<>();
        for (int tick = 0; tick < TICKS_PER_RUN; tick++) {
            if (pacer.shouldClick(minCps, maxCps)) clicks.add(nanos[0] / 1_000_000L);
            nanos[0] += TICK_NANOS;
        }
        return clicks;
    }

    private static double rateOf(List<Long> clicks) {
        return clicks.size() / (TICKS_PER_RUN * (TICK_NANOS / 1_000_000_000.0));
    }

    private static IntaveStability judge(double minCps, double maxCps, long seed) {
        IntaveStability check = new IntaveStability();
        for (long at : clickTimes(minCps, maxCps, seed)) check.place(at);
        return check;
    }

    private static List<double[]> allBands() {
        List<double[]> bands = new ArrayList<>();
        for (double min = 5.0; min <= 20.0; min += 0.5) {
            for (double max = min + 0.5; max <= 20.0; max += 0.5) bands.add(new double[]{min, max});
        }
        return bands;
    }

    @Test
    void everyBandIsRespectedInBothDirections() {

        for (double[] band : allBands()) {
            for (long seed = 1; seed <= 2; seed++) {
                List<Long> clicks = clickTimes(band[0], band[1], seed);
                double rate = rateOf(clicks);
                String where = band[0] + "-" + band[1] + " seed " + seed;

                assertTrue(rate <= band[1] * 1.005, "band " + where + " exceeded its maximum at " + rate);
                assertTrue(rate >= band[0] * 0.995, "band " + where + " fell under its minimum at " + rate);

                long longestAllowed = Math.round(1000.0 / (band[0] * 0.9)) + 200;
                for (int i = 1; i < clicks.size(); i++) {
                    long gap = clicks.get(i) - clicks.get(i - 1);
                    assertTrue(gap <= longestAllowed, "band " + where + " paused for " + gap + " ms");
                }
            }
        }
    }

    @Test
    void anyBandUnderTheArmingRateCannotBeFlagged() {

        for (double[] band : allBands()) {
            if (band[1] > 12.0) continue;
            for (long seed = 1; seed <= 3; seed++) {
                IntaveStability check = judge(band[0], band[1], seed);
                assertTrue(check.violations == 0, "flagged at band " + band[0] + "-" + band[1]
                    + " seed " + seed + " (peak vl " + check.peakVl + ")");
            }
        }
    }

    @Test
    void shippedDefaultsCannotEvenArmTheCheck() {

        for (double[] band : new double[][]{{8, 12}, {6, 11}}) {
            for (long seed = 1; seed <= 4; seed++) {
                List<Long> clicks = clickTimes(band[0], band[1], seed);
                for (int i = 49; i < clicks.size(); i++) {
                    long window = clicks.get(i) - clicks.get(i - 49);
                    assertTrue(window >= 4000,
                        "50 places inside " + window + " ms at band " + band[0] + "-" + band[1] + " seed " + seed);
                }
            }
        }
    }

    @Test
    void aResetFiresTheNextClickPromptly() {

        long[] nanos = {1_000_000_000L};
        AutismClickPacer pacer = new AutismClickPacer(new Random(3L), () -> nanos[0]);
        assertTrue(pacer.shouldClick(8, 12), "a fresh pacer must arm on its first tick");
        for (int tick = 0; tick < 40; tick++) {
            nanos[0] += TICK_NANOS;
            pacer.shouldClick(8, 12);
        }
        pacer.reset();
        assertTrue(pacer.shouldClick(8, 12), "a reset pacer must arm on its first tick");
    }
}
