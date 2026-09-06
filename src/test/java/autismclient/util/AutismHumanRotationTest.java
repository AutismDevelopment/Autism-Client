package autismclient.util;

import autismclient.util.AutismRotationUtil.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutismHumanRotationTest {
    private static final double GCD = 0.15D;
    private static final float YAW_CAP = 58.5F;
    private static final float PITCH_CAP = 8.0F;

    private static final double ARRIVE_SETTLE_BAND = 0.5D + GCD + 1.0E-3D;

    @Test
    void theEmittedYawNeverWrapsAcrossTheSeam() {

        AutismHumanRotation.Stream s = seededStream(11L, 170.0F, 0.0F);

        float previous = 170.0F;
        float lastDelta = 0.0F;

        for (int i = 1; i <= 60; i++) {
            Rotation goal = new Rotation(170.0F + i * 5.0F, 0.0F);
            Rotation emitted = AutismHumanRotation.step(s, goal, YAW_CAP, PITCH_CAP, GCD);
            float delta = emitted.yaw() - previous;

            assertTrue(Math.abs(delta) <= 320.0D,
                "step " + i + " emitted a " + delta + " degree jump: that is AimModulo360");

            assertTrue(Math.abs(delta) <= YAW_CAP + GCD,
                "step " + i + " exceeded the turn cap at " + delta);

            previous = emitted.yaw();
            lastDelta = delta;
        }

        assertTrue(previous > 180.0F,
            "the accumulator wrapped instead of running free, ended at " + previous);
        assertTrue(Math.abs(lastDelta) < 30.0F, "and it should be tracking calmly by the end");
    }

    private static AutismHumanRotation.Stream seededStream(long seed, float yaw, float pitch) {
        AutismHumanRotation.Stream s = new AutismHumanRotation.Stream(new Random(seed));
        AutismHumanRotation.seed(s, new Rotation(yaw, pitch));
        return s;
    }

    private static Rotation randomGoal(Random random) {
        return new Rotation(random.nextFloat() * 360.0F - 180.0F, random.nextFloat() * 160.0F - 80.0F);
    }

    private static float wrapF(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    private static double wrapD(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) wrapped -= 360.0D;
        if (wrapped < -180.0D) wrapped += 360.0D;
        return wrapped;
    }

    private static float yawDelta(Rotation next, Rotation prev) {
        return wrapF(next.yaw() - prev.yaw());
    }

    private static double gridError(double value, double anchor, boolean wrapped) {
        double diff = wrapped ? wrapD(value - anchor) : value - anchor;
        return Math.abs(diff - Math.round(diff / GCD) * GCD);
    }

    private static double distanceToMultipleOf45(double yaw) {
        double wrapped = wrapD(yaw);
        return Math.abs(wrapped - 45.0D * Math.round(wrapped / 45.0D));
    }

    @Test
    void uninitializedStreamsFollowTheSeedFirstContract() {
        AutismHumanRotation.Stream s = new AutismHumanRotation.Stream(new Random(1L));
        assertFalse(AutismHumanRotation.isInitialized(s));
        assertNull(AutismHumanRotation.current(s));

        Rotation goal = new Rotation(50.0F, 20.0F);

        assertThrows(IllegalStateException.class,
            () -> AutismHumanRotation.compute(s, goal, YAW_CAP, PITCH_CAP, GCD));

        Rotation fallback = AutismHumanRotation.step(s, goal, YAW_CAP, PITCH_CAP, GCD);
        assertEquals(50.0F, fallback.yaw(), 1.0E-4F);
        assertEquals(20.0F, fallback.pitch(), 1.0E-4F);
        assertTrue(AutismHumanRotation.isInitialized(s));
        assertEquals(new Rotation(50.0F, 20.0F), AutismHumanRotation.current(s));

        AutismHumanRotation.clear(s);
        assertFalse(AutismHumanRotation.isInitialized(s));
        assertNull(AutismHumanRotation.current(s));

        Rotation reseeded = AutismHumanRotation.step(s, goal, YAW_CAP, PITCH_CAP, GCD);
        assertEquals(50.0F, reseeded.yaw(), 1.0E-4F);
    }

    @Test
    void applyRejectsClearedAndForeignStreams() {
        Rotation goal = new Rotation(30.0F, 10.0F);
        AutismHumanRotation.Stream a = seededStream(5L, 0.0F, 0.0F);
        AutismHumanRotation.Stream b = seededStream(9L, 0.0F, 0.0F);

        AutismHumanRotation.Step foreign = AutismHumanRotation.compute(b, goal, YAW_CAP, PITCH_CAP, GCD);
        assertThrows(IllegalArgumentException.class, () -> AutismHumanRotation.apply(a, foreign));

        AutismHumanRotation.Step stale = AutismHumanRotation.compute(a, goal, YAW_CAP, PITCH_CAP, GCD);
        AutismHumanRotation.clear(a);
        assertThrows(IllegalStateException.class, () -> AutismHumanRotation.apply(a, stale));
    }

    @Test
    void computeLeavesCurrentUntouchedAndApplyMatchesStep() {
        AutismHumanRotation.Stream viaStep = seededStream(42L, 12.3F, -4.5F);
        AutismHumanRotation.Stream viaCompute = seededStream(42L, 12.3F, -4.5F);
        Rotation goal = new Rotation(-100.0F, 30.0F);

        Rotation expected = AutismHumanRotation.step(viaStep, goal, YAW_CAP, PITCH_CAP, GCD);

        Rotation before = AutismHumanRotation.current(viaCompute);
        AutismHumanRotation.Step step = AutismHumanRotation.compute(viaCompute, goal, YAW_CAP, PITCH_CAP, GCD);
        assertEquals(before, AutismHumanRotation.current(viaCompute),
            "compute() must not advance the stream");
        Rotation actual = AutismHumanRotation.apply(viaCompute, step);
        assertEquals(expected, actual,
            "apply(compute(...)) must equal step(...) on identically seeded streams");
    }

    @Test
    void nonPositiveGcdFallsBackToDefaultGrid() {
        AutismHumanRotation.Stream s = seededStream(3L, 0.0F, 0.0F);
        Rotation goal = new Rotation(30.0F, 10.0F);
        for (int tick = 0; tick < 30; tick++) {
            Rotation cur = AutismHumanRotation.step(s, goal, YAW_CAP, YAW_CAP, 0.0D);
            assertTrue(gridError(cur.yaw(), 0.0D, true) <= 0.01D,
                "gcd <= 0 must be treated as 0.15, yaw off-grid");
            assertTrue(gridError(cur.pitch(), 0.0D, false) <= 0.01D,
                "gcd <= 0 must be treated as 0.15, pitch off-grid");
        }
    }

    @Test
    void noIdenticalConsecutiveDeltasAboveAimCThreshold() {

        long[] seeds = {1L, 7L, 20240801L};
        for (long seed : seeds) {
            Random jumps = new Random(seed * 31L + 5L);
            AutismHumanRotation.Stream s = seededStream(seed, 10.0F, 5.0F);
            Rotation goal = new Rotation(0.0F, 0.0F);
            int nextJump = 0;
            Rotation prev = AutismHumanRotation.current(s);
            float prevDeltaYaw = 0.0F;
            float prevDeltaPitch = 0.0F;
            for (int tick = 0; tick < 2000; tick++) {
                if (tick >= nextJump) {
                    goal = randomGoal(jumps);
                    nextJump = tick + 5 + jumps.nextInt(25);
                }
                Rotation cur = AutismHumanRotation.step(s, goal, YAW_CAP, PITCH_CAP, GCD);
                float deltaYaw = yawDelta(cur, prev);
                float deltaPitch = cur.pitch() - prev.pitch();
                if (Math.abs(deltaYaw) > 1.25F && Math.abs(prevDeltaYaw) > 1.25F) {
                    assertTrue(deltaYaw != prevDeltaYaw,
                        () -> "seed " + seed + ": two identical yaw deltas " + deltaYaw + " > 1.25°");
                }
                if (Math.abs(deltaPitch) > 1.25F && Math.abs(prevDeltaPitch) > 1.25F) {
                    assertTrue(deltaPitch != prevDeltaPitch,
                        () -> "seed " + seed + ": two identical pitch deltas " + deltaPitch + " > 1.25°");
                }
                prevDeltaYaw = deltaYaw;
                prevDeltaPitch = deltaPitch;
                prev = cur;
            }
        }
    }

    @Test
    void appliedDeltasNeverExceedAxisCaps() {

        float[][] caps = {{58.5F, 8.0F}, {58.5F, 58.5F}, {2.0F, 1.0F}};
        for (float[] cap : caps) {
            for (long seed = 1L; seed <= 12L; seed++) {
                Random jumps = new Random(seed * 953L);
                AutismHumanRotation.Stream s = seededStream(seed * 37L, 3.0F, -2.0F);
                Rotation goal = new Rotation(0.0F, 0.0F);
                int nextJump = 0;
                Rotation prev = AutismHumanRotation.current(s);
                for (int tick = 0; tick < 400; tick++) {
                    if (tick >= nextJump) {
                        goal = randomGoal(jumps);
                        nextJump = tick + 5 + jumps.nextInt(25);
                    }
                    Rotation cur = AutismHumanRotation.step(s, goal, cap[0], cap[1], GCD);
                    float deltaYaw = Math.abs(yawDelta(cur, prev));
                    float deltaPitch = Math.abs(cur.pitch() - prev.pitch());
                    assertTrue(deltaYaw <= cap[0] + 1.0E-4F,
                        () -> "yaw delta " + deltaYaw + " exceeded cap " + cap[0] + " mid-stream");
                    assertTrue(deltaPitch <= cap[1] + 1.0E-4F,
                        () -> "pitch delta " + deltaPitch + " exceeded cap " + cap[1] + " mid-stream");
                    prev = cur;
                }
            }
        }
    }

    @Test
    void emittedRotationsStayOnTheSensitivityGrid() {

        float seedYaw = 37.2F;
        float seedPitch = -12.6F;
        double anchorYaw = wrapD((double) seedYaw);
        double anchorPitch = (double) seedPitch;
        for (long seed = 1L; seed <= 6L; seed++) {
            Random jumps = new Random(seed * 17L);
            AutismHumanRotation.Stream s = seededStream(seed * 611L, seedYaw, seedPitch);
            Rotation goal = new Rotation(0.0F, 0.0F);
            int nextJump = 0;
            for (int tick = 0; tick < 500; tick++) {
                if (tick >= nextJump) {
                    goal = randomGoal(jumps);
                    nextJump = tick + 7 + jumps.nextInt(20);
                }
                Rotation cur = AutismHumanRotation.step(s, goal, YAW_CAP, PITCH_CAP, GCD);
                assertTrue(gridError(cur.yaw(), anchorYaw, true) <= 0.01D,
                    () -> "yaw " + cur.yaw() + " off the seed-anchored grid");
                assertTrue(gridError(cur.pitch(), anchorPitch, false) <= 0.01D,
                    () -> "pitch " + cur.pitch() + " off the seed-anchored grid");
            }
        }
    }

    @Test
    void firstStepFromStandstillIsRampBounded() {

        for (long seed = 0L; seed < 200L; seed++) {
            Random random = new Random(seed * 71L + 1L);
            AutismHumanRotation.Stream s = seededStream(seed, 0.0F, 0.0F);
            Rotation goal = randomGoal(random);
            Rotation cur = AutismHumanRotation.step(s, goal, YAW_CAP, YAW_CAP, GCD);
            assertTrue(Math.abs(yawDelta(cur, new Rotation(0.0F, 0.0F))) <= 18.0F + 1.0E-4F,
                () -> "first yaw step from standstill exceeded 18°");
            assertTrue(Math.abs(cur.pitch()) <= 18.0F + 1.0E-4F,
                () -> "first pitch step from standstill exceeded 18°");
        }
    }

    @Test
    void noLargeStepIsFlankedByQuietSteps() {

        for (long seed = 1L; seed <= 8L; seed++) {
            Random jumps = new Random(seed * 39L);
            AutismHumanRotation.Stream s = seededStream(seed * 277L, 0.0F, 0.0F);
            Rotation goal = new Rotation(0.0F, 0.0F);
            int nextJump = 0;
            Rotation prev = AutismHumanRotation.current(s);
            float[] yawHist = {0.0F, 0.0F};
            float[] pitchHist = {0.0F, 0.0F};
            for (int tick = 0; tick < 1500; tick++) {
                if (tick >= nextJump) {
                    goal = randomGoal(jumps);
                    nextJump = tick + 5 + jumps.nextInt(25);
                }
                Rotation cur = AutismHumanRotation.step(s, goal, YAW_CAP, YAW_CAP, GCD);
                float deltaYaw = Math.abs(yawDelta(cur, prev));
                float deltaPitch = Math.abs(cur.pitch() - prev.pitch());
                assertFalse(yawHist[1] > 40.0F && yawHist[0] < 9.0F && deltaYaw < 9.0F,
                    () -> "quiet→snap→quiet yaw pattern: " + yawHist[0] + "/" + yawHist[1] + "/" + deltaYaw);
                assertFalse(pitchHist[1] > 40.0F && pitchHist[0] < 9.0F && deltaPitch < 9.0F,
                    () -> "quiet→snap→quiet pitch pattern");
                yawHist[0] = yawHist[1];
                yawHist[1] = deltaYaw;
                pitchHist[0] = pitchHist[1];
                pitchHist[1] = deltaPitch;
                prev = cur;
            }
        }
    }

    @Test
    void turnsConvergeWithinFirmBounds() {

        int[] distances = {45, 90, 135};
        int[] bounds = {7, 9, 10};
        for (int i = 0; i < distances.length; i++) {
            final int distance = distances[i];
            final int bound = bounds[i];
            for (int dir = 1; dir >= -1; dir -= 2) {
                for (long seed = 0L; seed < 30L; seed++) {
                    AutismHumanRotation.Stream s = seededStream(seed * 131L + i * 17L + dir, 0.0F, 0.0F);
                    Rotation goal = new Rotation((float) (distance * dir), 0.0F);
                    int arrival = -1;
                    for (int tick = 1; tick <= 60; tick++) {
                        Rotation cur = AutismHumanRotation.step(s, goal, YAW_CAP, PITCH_CAP, GCD);
                        double[] eff = AutismHumanRotation.effectiveGoalForTesting(s);
                        double remYaw = Math.abs(wrapD(cur.yaw() - eff[0]));
                        double remPitch = Math.abs(cur.pitch() - eff[1]);
                        if (arrival < 0 && remYaw <= 0.5D && remPitch <= 0.5D) {
                            arrival = tick;
                        }
                        if (arrival > 0) {
                            assertTrue(remYaw <= 0.5D + GCD + 1.0E-3D,
                                () -> "yaw drifted off the effective goal after converging");
                            assertTrue(remPitch <= 0.5D + GCD + 1.0E-3D,
                                () -> "pitch drifted off the effective goal after converging");
                        }
                    }
                    final int arrived = arrival;
                    assertTrue(arrived > 0, () -> distance + "° turn never converged");
                    assertTrue(arrived <= bound,
                        () -> distance + "° turn took " + arrived + " ticks, bound is " + bound);
                }
            }
        }
    }

    @Test
    void finalYawStaysOffTheFortyFiveDegreeGrid() {

        int[] multiples = {-180, -135, -90, -45, 0, 45, 90, 135};
        double[] turns = {65.0D, 90.0D, 120.0D, 170.0D};
        for (long seed = 0L; seed < 40L; seed++) {
            int multiple = multiples[(int) (seed % multiples.length)];
            double turn = turns[(int) ((seed / multiples.length) % turns.length)];
            int dir = seed % 2L == 0L ? 1 : -1;
            float seedYaw = (float) wrapD(multiple - dir * turn);
            Rotation goal = new Rotation((float) multiple, 0.0F);

            AutismHumanRotation.Stream s = seededStream(seed * 7919L + 13L, seedYaw, 0.0F);
            int arrival = -1;
            for (int tick = 1; tick <= 60; tick++) {
                Rotation cur = AutismHumanRotation.step(s, goal, YAW_CAP, PITCH_CAP, GCD);
                double[] eff = AutismHumanRotation.effectiveGoalForTesting(s);
                double remYaw = Math.abs(wrapD(cur.yaw() - eff[0]));
                if (arrival < 0 && remYaw <= 0.5D) {
                    arrival = tick;
                }
                if (arrival > 0 && tick <= arrival + 30) {
                    assertTrue(distanceToMultipleOf45(cur.yaw()) > 0.08D,
                        () -> "final yaw " + cur.yaw() + " landed within 0.08° of a 45° multiple");
                }
            }
            assertTrue(arrival > 0, "stream must converge for the landing check to mean anything");
        }
    }

    @Test
    void pitchStaysInsideLimitsAtExtremeGoals() {

        for (long seed = 0L; seed < 10L; seed++) {
            Random random = new Random(seed * 101L + 7L);
            AutismHumanRotation.Stream s = seededStream(seed * 59L, 0.0F, 0.0F);
            Rotation goal = new Rotation(0.0F, 90.0F);
            for (int tick = 0; tick < 300; tick++) {
                if (tick % 20 == 0) {
                    goal = new Rotation(random.nextFloat() * 360.0F - 180.0F,
                        random.nextBoolean() ? 90.0F : -90.0F);
                }
                Rotation cur = AutismHumanRotation.step(s, goal, YAW_CAP, YAW_CAP, GCD);
                assertTrue(Math.abs(cur.pitch()) <= 89.9F + 1.0E-4F,
                    () -> "pitch left ±89.9: " + cur.pitch());
                assertTrue(gridError(cur.pitch(), 0.0D, false) <= 0.01D,
                    () -> "pitch fell off the grid near the limit: " + cur.pitch());
            }
        }
    }

    @Test
    void offsetFreeStepsConvergeOntoTheRequestedGoal() {

        for (long seed = 0L; seed < 20L; seed++) {
            AutismHumanRotation.Stream s = seededStream(seed * 431L + 3L, 100.0F, -30.0F);
            Rotation goal = new Rotation(-45.0F, 45.0F);
            double bestYaw = Double.MAX_VALUE;
            double bestPitch = Double.MAX_VALUE;
            for (int tick = 0; tick < 40; tick++) {
                Rotation cur = AutismHumanRotation.step(s, goal, YAW_CAP, PITCH_CAP, GCD, false);
                bestYaw = Math.min(bestYaw, Math.abs(wrapD(cur.yaw() - goal.yaw())));
                bestPitch = Math.min(bestPitch, Math.abs(cur.pitch() - goal.pitch()));
            }
            final double landedYaw = bestYaw;
            final double landedPitch = bestPitch;
            assertTrue(landedYaw <= GCD + 1.0E-3D,
                () -> "offset-free yaw never landed within one quantum of the requested goal: " + landedYaw);
            assertTrue(landedPitch <= GCD + 1.0E-3D,
                () -> "offset-free pitch never landed within one quantum of the requested goal: " + landedPitch);
            for (int tick = 0; tick < 20; tick++) {
                Rotation held = AutismHumanRotation.step(s, goal, YAW_CAP, PITCH_CAP, GCD, false);
                assertTrue(Math.abs(wrapD(held.yaw() - goal.yaw())) <= ARRIVE_SETTLE_BAND,
                    "offset-free stream wandered off the requested goal's settle band");
                assertTrue(Math.abs(held.pitch() - goal.pitch()) <= ARRIVE_SETTLE_BAND,
                    "offset-free stream wandered off the requested goal's settle band");
            }
        }
    }

    @Test
    void offsetFreeLandingSkipsThePerTurnGoalOffset() {

        for (long seed = 0L; seed < 20L; seed++) {
            Rotation goal = new Rotation(30.0F, 10.0F);
            AutismHumanRotation.Stream withOffset = seededStream(seed * 877L + 11L, -120.0F, -40.0F);
            AutismHumanRotation.Stream withoutOffset = seededStream(seed * 877L + 11L, -120.0F, -40.0F);
            double bestDefault = Double.MAX_VALUE;
            double bestExact = Double.MAX_VALUE;
            double[] effective = null;
            for (int tick = 0; tick < 40; tick++) {
                Rotation off = AutismHumanRotation.step(withOffset, goal, YAW_CAP, YAW_CAP, GCD);
                Rotation exact = AutismHumanRotation.step(withoutOffset, goal, YAW_CAP, YAW_CAP, GCD, false);
                effective = AutismHumanRotation.effectiveGoalForTesting(withOffset);
                bestDefault = Math.min(bestDefault, Math.abs(wrapD(off.yaw() - effective[0])));
                bestExact = Math.min(bestExact, Math.abs(wrapD(exact.yaw() - goal.yaw())));
            }
            final double defaultLanding = bestDefault;
            final double offsetApplied = Math.abs(wrapD(effective[0] - goal.yaw()));
            assertTrue(defaultLanding <= GCD + 1.0E-3D,
                () -> "default overload never converged onto its effective goal: " + defaultLanding);
            assertTrue(offsetApplied >= 0.75D,
                () -> "default overload's effective goal should sit >= 0.75° off the requested goal");
            assertTrue(bestExact <= GCD + 1.0E-3D,
                "offset-free overload must land on the requested goal itself");
        }
    }

    @Test
    void tellyAirFlickReachesPlacementWindowWithinTwoUpdates() {

        double[] sensitivityGrids = {0.0096D, 0.15D, 0.6144D};
        float cap = 130.0F;
        for (double gcd : sensitivityGrids) {
            for (int direction : new int[]{-1, 1}) {
                for (long seed = 0L; seed < 256L; seed++) {
                    AutismHumanRotation.Stream stream = seededStream(
                        seed * 104729L + direction * 31L, 0.0F, 0.0F);
                    Rotation goal = new Rotation(179.0F * direction, 70.0F * direction);
                    Rotation previous = AutismHumanRotation.current(stream);
                    Rotation current = previous;
                    for (int tick = 0; tick < 2; tick++) {
                        current = AutismHumanRotation.step(
                            stream, goal, cap, cap, gcd, false,
                            AutismHumanRotation.MotionProfile.TELLY_AIR_FLICK);
                        float yawSpeed = Math.abs(yawDelta(current, previous));
                        float pitchSpeed = Math.abs(current.pitch() - previous.pitch());
                        assertTrue(yawSpeed <= cap + 1.0E-4F,
                            "Telly yaw exceeded its production cap");
                        assertTrue(pitchSpeed <= cap + 1.0E-4F,
                            "Telly pitch exceeded its production cap");
                        if (tick == 0) {
                            assertTrue(Math.abs(yawDelta(current, goal)) > 95.0F,
                                "the airborne Telly profile must remain a multi-update flick");
                        }
                        previous = current;
                    }

                    double yawRemaining = Math.abs(wrapD(goal.yaw() - current.yaw()));
                    double pitchRemaining = Math.abs(goal.pitch() - current.pitch());
                    assertTrue(yawRemaining <= 4.5D,
                        "two-update Telly yaw missed its placement window: " + yawRemaining);
                    assertTrue(pitchRemaining <= 4.5D,
                        "two-update Telly pitch missed its placement window: " + pitchRemaining);

                    Rotation returnGoal = new Rotation(0.0F, 0.0F);
                    for (int tick = 0; tick < 2; tick++) {
                        Rotation returned = AutismHumanRotation.step(
                            stream, returnGoal, cap, cap, gcd, false,
                            AutismHumanRotation.MotionProfile.TELLY_AIR_FLICK);
                        float yawSpeed = Math.abs(yawDelta(returned, current));
                        float pitchSpeed = Math.abs(returned.pitch() - current.pitch());
                        assertTrue(yawSpeed <= cap + 1.0E-4F,
                            "Telly return yaw exceeded its production cap");
                        assertTrue(pitchSpeed <= cap + 1.0E-4F,
                            "Telly return pitch exceeded its production cap");
                        current = returned;
                    }
                    assertTrue(Math.abs(wrapD(current.yaw())) <= 2.0D,
                        "two-update Telly return missed the course yaw: " + current.yaw());
                    assertTrue(Math.abs(current.pitch()) <= 2.0D,
                        "two-update Telly return missed the course pitch: " + current.pitch());
                }
            }
        }
    }

    @Test
    void noStepOvershootsEffectiveGoalBeyondOneQuantum() {

        for (long seed = 0L; seed < 15L; seed++) {
            Random jumps = new Random(seed * 101L);
            AutismHumanRotation.Stream s = seededStream(seed * 881L, 5.0F, -3.0F);
            Rotation goal = new Rotation(0.0F, 0.0F);
            int nextJump = 0;
            Rotation prev = AutismHumanRotation.current(s);
            for (int tick = 0; tick < 600; tick++) {
                if (tick >= nextJump) {
                    goal = randomGoal(jumps);
                    nextJump = tick + 5 + jumps.nextInt(25);
                }
                Rotation cur = AutismHumanRotation.step(s, goal, YAW_CAP, PITCH_CAP, GCD);
                double[] eff = AutismHumanRotation.effectiveGoalForTesting(s);

                double remYawBefore = wrapD(eff[0] - prev.yaw());
                double travelYaw = wrapD(cur.yaw() - prev.yaw());
                if (Math.abs(remYawBefore) > 1.0E-6D
                    && Math.signum(travelYaw) == Math.signum(remYawBefore)) {
                    double beyond = Math.abs(travelYaw) - Math.abs(remYawBefore);
                    assertTrue(beyond <= GCD + 1.0E-3D,
                        () -> "yaw overshot the effective goal by " + beyond + "°");
                }

                double remPitchBefore = eff[1] - prev.pitch();
                double travelPitch = cur.pitch() - prev.pitch();
                if (Math.abs(remPitchBefore) > 1.0E-6D
                    && Math.signum(travelPitch) == Math.signum(remPitchBefore)) {
                    double beyond = Math.abs(travelPitch) - Math.abs(remPitchBefore);
                    assertTrue(beyond <= GCD + 1.0E-3D,
                        () -> "pitch overshot the effective goal by " + beyond + "°");
                }
                prev = cur;
            }
        }
    }
}
