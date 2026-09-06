package autismclient.modules;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillAuraAimModelTest {
    private static final double HALF_WIDTH = 0.3D;

    private static AABB playerBox() {
        return new AABB(-HALF_WIDTH, 0.0, -HALF_WIDTH, HALF_WIDTH, 1.8, HALF_WIDTH);
    }

    private static Vec3 eyesAt(double distance) {
        return new Vec3(distance, 0.9, 0.0);
    }

    @Test
    void missOffsetLandsInsideIntaveRingAcrossSeeds() {
        AABB box = playerBox();
        Vec3 center = box.getCenter();
        for (double distance : new double[] {2.0D, 3.0D, 4.5D}) {
            Vec3 eyes = eyesAt(distance);

            double innerAngle = Math.toDegrees(Math.atan((HALF_WIDTH + 0.05D) / distance));
            double outerAngle = Math.toDegrees(Math.atan((HALF_WIDTH + 0.25D) / distance));
            for (long seed = 0; seed < 500; seed++) {
                Vec3 point = KillAuraModule.missAimPoint(box, eyes, new Random(seed));
                double lateral = Math.abs(point.z - center.z);
                double depth = point.x - center.x;
                assertTrue(lateral >= HALF_WIDTH + KillAuraModule.MISS_LATERAL_MIN - 1.0E-12,
                    "lateral " + lateral + " below halfWidth + 0.10 at " + distance);
                assertTrue(lateral <= HALF_WIDTH + KillAuraModule.MISS_LATERAL_MAX + 1.0E-12,
                    "lateral " + lateral + " above halfWidth + 0.20 at " + distance);
                assertTrue(Math.abs(depth) <= KillAuraModule.MISS_DEPTH_JITTER + 1.0E-12,
                    "depth jitter " + depth + " broke the perpendicular bias");

                double angle = Math.toDegrees(Math.atan2(lateral, distance + depth));
                assertTrue(angle > innerAngle,
                    "miss angle " + angle + " reaches the +0.05 attack-required box at " + distance);
                assertTrue(angle < outerAngle,
                    "miss angle " + angle + " leaves the +0.25 pre-attack box at " + distance);

                assertTrue(box.inflate(0.25D).contains(point), "miss point outside the +0.25 box");
                assertFalse(box.inflate(0.05D).contains(point), "miss point inside the +0.05 box");
                assertNotEquals(center.y, point.y, "vertical miss offset must never be exactly 0.0");
            }
        }
    }

    @Test
    void bandBoundsFollowTheDistanceSpec() {
        for (double distance : new double[] {2.0D, 2.5D, 3.0D, 3.5D}) {
            double hwa = KillAuraModule.AimPointTracker.horizontalHalfWidthAngle(HALF_WIDTH, distance);
            double bandLo = KillAuraModule.AimPointTracker.aimBandLow(hwa);
            double bandHi = KillAuraModule.AimPointTracker.aimBandHigh(hwa);
            assertEquals(Math.min(2.2D, 0.55D * hwa), bandLo, 1.0E-12);
            assertEquals(Math.max(bandLo + 3.6D, 0.85D * hwa), bandHi, 1.0E-12);
            assertTrue(bandLo <= bandHi);
        }
    }

    @Test
    void bandMagnitudesKeepAbsoluteYawErrorStddevAboveOne() {

        for (double distance : new double[] {2.0D, 2.5D, 3.0D}) {
            double hwa = KillAuraModule.AimPointTracker.horizontalHalfWidthAngle(HALF_WIDTH, distance);
            Random random = new Random(0x5EED + (long) (distance * 100.0D));
            double[] samples = new double[500];
            double sum = 0.0D;
            for (int i = 0; i < samples.length; i++) {
                samples[i] = KillAuraModule.AimPointTracker.rollBandMagnitude(random, hwa);
                sum += samples[i];
            }
            double mean = sum / samples.length;
            double squared = 0.0D;
            for (double sample : samples) squared += (sample - mean) * (sample - mean);
            double stddev = Math.sqrt(squared / samples.length);
            assertTrue(stddev >= 1.0D,
                "yaw |error| stddev " + stddev + " below 1.0 at distance " + distance);
        }
    }

    private static double signedError(Vec3 point, Vec3 center, double distance) {

        double lateral = point.z - center.z;
        double depth = point.x - center.x;
        return Math.copySign(Math.toDegrees(Math.atan2(Math.abs(lateral), distance + depth)), lateral);
    }

    @Test
    void sideChangesAndExcursionsStayInsideTheirWindows() {
        double distance = 2.5D;
        AABB box = playerBox();
        Vec3 eyes = eyesAt(distance);
        Vec3 center = box.getCenter();
        double hwa = KillAuraModule.AimPointTracker.horizontalHalfWidthAngle(HALF_WIDTH, distance);
        double bandLo = KillAuraModule.AimPointTracker.aimBandLow(hwa);
        double bandHi = KillAuraModule.AimPointTracker.aimBandHigh(hwa);

        int ticks = 600;
        double[] angle = new double[ticks];
        boolean[] positive = new boolean[ticks];
        KillAuraModule.AimPointTracker tracker =
            new KillAuraModule.AimPointTracker(new Random(0xBEEF));
        double previousPitchOffset = Double.NaN;
        for (int tick = 0; tick < ticks; tick++) {

            Vec3 point = tick == 0
                ? tracker.begin(42, box, eyes, false, true)
                : tracker.advance(42, box, eyes, false, true);
            double signed = signedError(point, center, distance);
            angle[tick] = Math.abs(signed);
            positive[tick] = signed > 0.0D;
            assertTrue(angle[tick] > 0.0D, "horizontal error must never be exactly 0.0");
            assertTrue(angle[tick] >= bandLo - 1.0E-9,
                "error " + angle[tick] + " dipped under the checkFollow floor " + bandLo);
            assertNotEquals(center.y, point.y, "pitch offset must never sit at exactly 0.0");
            double pitchOffset = Math.toDegrees(Math.atan2(point.y - center.y, distance));
            if (!Double.isNaN(previousPitchOffset)) {
                assertTrue(Math.abs(pitchOffset - previousPitchOffset) <= 0.5D + 1.0E-9,
                    "pitch offset moved " + Math.abs(pitchOffset - previousPitchOffset)
                        + " degrees in one tick");
            }
            previousPitchOffset = pitchOffset;
        }

        int previousChange = -1;
        int changes = 0;
        for (int tick = 1; tick < ticks; tick++) {
            if (positive[tick] == positive[tick - 1]) continue;
            if (previousChange >= 0) {
                int gap = tick - previousChange;
                assertTrue(gap >= 2 && gap <= 12, "side change gap " + gap + " outside [2, 12]");
            }
            previousChange = tick;
            changes++;
        }
        assertTrue(changes > ticks / 12, "the error side never changed");

        for (int tick = 1; tick < ticks; tick++) {
            if (positive[tick] == positive[tick - 1]) continue;
            assertEquals(bandLo, angle[tick], 1.0E-6,
                "side change did not bounce off the band floor");
            assertEquals(bandLo, angle[tick - 1], 1.0E-6,
                "side change did not bounce off the band floor");
        }

        List<Integer> starts = new ArrayList<>();
        for (int tick = 0; tick < ticks; tick++) {
            if (angle[tick] > bandHi + 1.0E-9) {
                if (tick == 0 || angle[tick - 1] <= bandHi + 1.0E-9) starts.add(tick);
            }
        }
        assertTrue(starts.size() >= 10, "excursions did not fire regularly");
        for (int start : starts) {
            assertTrue(angle[start] >= 13.0D - 1.0E-9 && angle[start] <= 16.0D + 1.0E-9,
                "excursion magnitude " + angle[start] + " outside [13, 16]");
        }
        assertTrue(starts.get(0) >= 30 && starts.get(0) <= 38,
            "first excursion after acquisition at rotation " + starts.get(0));
        for (int i = 1; i < starts.size(); i++) {
            int gap = starts.get(i) - starts.get(i - 1);
            assertTrue(gap >= 30 && gap <= 38, "excursion gap " + gap + " outside [30, 38]");
        }
    }

    @Test
    void noModelStepInsideTheAttackWindowReachesIntavesSamplingThreshold() {
        AABB box = playerBox();

        for (double distance : new double[] {1.5D, 2.0D, 2.5D, 3.0D, 4.0D, 5.0D}) {
            Vec3 eyes = eyesAt(distance);
            Vec3 center = box.getCenter();
            for (long seed = 0; seed < 60; seed++) {
                KillAuraModule.AimPointTracker tracker =
                    new KillAuraModule.AimPointTracker(new Random(seed));
                double previous = signedError(tracker.begin(42, box, eyes, true, true), center, distance);
                for (int tick = 1; tick < 400; tick++) {
                    double signed = signedError(
                        tracker.advance(42, box, eyes, true, true), center, distance);

                    assertTrue(Math.abs(signed - previous) < KillAuraModule.AccuracyGovernor.SAMPLE_YAW_SPEED,
                        "model step " + Math.abs(signed - previous) + " degrees at distance "
                            + distance + " (seed " + seed + ", tick " + tick + ") reaches the"
                            + " attack-accuracy sampling threshold");
                    previous = signed;
                }
            }
        }
    }

    @Test
    void excursionsStillReachTheirPeakWhenEveryTickIsInsideTheAttackWindow() {
        double distance = 3.0D;
        AABB box = playerBox();
        Vec3 eyes = eyesAt(distance);
        Vec3 center = box.getCenter();

        int ticks = 600;
        double[] angle = new double[ticks];
        KillAuraModule.AimPointTracker tracker =
            new KillAuraModule.AimPointTracker(new Random(0xBEEF));
        for (int tick = 0; tick < ticks; tick++) {
            Vec3 point = tick == 0
                ? tracker.begin(42, box, eyes, true, true)
                : tracker.advance(42, box, eyes, true, true);
            angle[tick] = Math.abs(signedError(point, center, distance));
        }

        int peaks = 0;
        for (int tick = 0; tick < ticks; tick++) {
            if (angle[tick] < 12.5D) continue;

            assertTrue(angle[tick] <= 16.0D + 1.0E-9,
                "excursion peak " + angle[tick] + " above the rolled maximum");
            if (tick == 0 || angle[tick - 1] < 12.5D) peaks++;
        }
        assertTrue(peaks >= 8,
            "only " + peaks + " excursions reached their peak in " + ticks + " in-window ticks");
    }

    @Test
    void emittedErrorStreamKeepsItsStddevInsideTheAttackWindow() {
        AABB box = playerBox();

        for (double distance : new double[] {2.0D, 2.5D, 3.0D}) {
            Vec3 eyes = eyesAt(distance);
            Vec3 center = box.getCenter();
            KillAuraModule.AimPointTracker tracker =
                new KillAuraModule.AimPointTracker(new Random(0x5EED));
            int ticks = 700;
            double[] error = new double[ticks];
            for (int tick = 0; tick < ticks; tick++) {
                Vec3 point = tick == 0
                    ? tracker.begin(42, box, eyes, true, true)
                    : tracker.advance(42, box, eyes, true, true);
                error[tick] = Math.abs(signedError(point, center, distance));
            }

            int windows = 0;
            int reached = 0;
            for (int start = 0; start + 7 <= ticks; start += 7) {
                double sum = 0.0D;
                for (int i = start; i < start + 7; i++) sum += error[i];
                double mean = sum / 7.0D;
                double squared = 0.0D;
                for (int i = start; i < start + 7; i++) squared += (error[i] - mean) * (error[i] - mean);
                windows++;
                if (Math.sqrt(squared / 7.0D) >= 1.0D) reached++;
            }
            assertTrue(reached * 3 >= windows,
                "only " + reached + " of " + windows + " 7-sample windows reached stddev 1.0 at "
                    + distance + " blocks");
        }
    }

    @Test
    void governorThrottlesOnlyOnceTheWindowCannotFinishUnderTheThreshold() {
        KillAuraModule.AccuracyGovernor governor = new KillAuraModule.AccuracyGovernor();
        assertFalse(governor.speedAtRisk(), "an empty window must not restrain anything");

        governor.onAttackSent();
        governor.onOutgoingRotation(0.0f, 0.0f, 0.0f);
        float yaw = 0.0f;
        for (int i = 0; i < 20; i++) {
            yaw += 6.5f;
            governor.onAttackSent();
            governor.onOutgoingRotation(yaw, 0.0f, yaw);
            assertFalse(governor.speedAtRisk(),
                "a window of 6.5 degree samples must not restrain anything");
        }

        KillAuraModule.AccuracyGovernor hot = new KillAuraModule.AccuracyGovernor();
        hot.onAttackSent();
        hot.onOutgoingRotation(0.0f, 0.0f, 0.0f);
        yaw = 0.0f;
        int samples = 0;
        double sum = 0.0D;
        while (!hot.speedAtRisk()) {
            yaw += KillAuraModule.WINDOW_MAX_YAW_STEP;
            hot.onAttackSent();
            hot.onOutgoingRotation(yaw, 0.0f, yaw);
            sum += KillAuraModule.WINDOW_MAX_YAW_STEP;
            samples++;
            assertTrue(samples < KillAuraModule.AccuracyGovernor.WINDOW_SAMPLES,
                "the governor never engaged inside the window");
        }

        double worstMean = (sum
            + (KillAuraModule.AccuracyGovernor.WINDOW_SAMPLES - samples)
              * KillAuraModule.AccuracyGovernor.THROTTLED_SAMPLE)
            / KillAuraModule.AccuracyGovernor.WINDOW_SAMPLES;
        assertTrue(worstMean < 10.0D,
            "worst-case window mean " + worstMean + " still reaches the flag threshold");
    }

    @Test
    void governorIgnoresPacketsOutsideTheSixtyMillisecondWindow() {
        KillAuraModule.AccuracyGovernor governor = new KillAuraModule.AccuracyGovernor();
        governor.onOutgoingRotation(0.0f, 0.0f, 0.0f);

        float yaw = 0.0f;
        for (int i = 0; i < 40; i++) {
            yaw += 40.0f;
            governor.onOutgoingRotation(yaw, 0.0f, yaw + 30.0f);
        }
        assertFalse(governor.speedAtRisk(), "packets with no recent attack must not buffer");
        assertFalse(governor.errorAtRisk(), "packets with no recent attack must not buffer");
    }

    @Test
    void missStateNeverAllowsTwoMissesInARow() {
        KillAuraModule.MissState state = new KillAuraModule.MissState();
        assertTrue(state.mayRoll());

        state.begin(7, new Vec3(1.0, 2.0, 3.0));
        assertFalse(state.mayRoll());
        assertFalse(state.isFireTick());
        state.advance();
        assertTrue(state.isFireTick());
        assertFalse(state.mayRoll());
        state.advance();
        assertFalse(state.isPending());
        assertFalse(state.mayRoll());

        state.onAttackFired();
        assertTrue(state.mayRoll());

        state.begin(8, new Vec3(3.0, 2.0, 1.0));
        state.clear();
        assertFalse(state.isPending());
        assertTrue(state.mayRoll());
    }
}
