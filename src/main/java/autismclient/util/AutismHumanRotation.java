package autismclient.util;

import java.util.Objects;
import java.util.Random;

public final class AutismHumanRotation {

    private static final double FALLBACK_GCD = 0.15D;

    private static final double REROLL_GOAL_DEGREES = 4.0D;

    private static final double PEAK_MIN = 0.70D;
    private static final double PEAK_SPAN = 0.30D;

    private static final double EASE_MIN = 0.55D;
    private static final double EASE_SPAN = 0.35D;

    private static final double ACCEL_MIN = 8.0D;
    private static final double ACCEL_SPAN = 10.0D;

    private static final double TAIL_CUT_DEGREES = 3.0D;

    private static final double OFFSET_MIN = 0.75D;
    private static final double OFFSET_SPAN = 1.25D;

    private static final double ARRIVE_DEGREES = 0.5D;

    private static final double IDENTICAL_DELTA_MIN = 1.25D;

    private static final double JITTER_PROBABILITY = 0.20D;

    private static final double DITHER_OTHER_MIN = 2.75D;
    private static final double DITHER_PROBABILITY = 0.5D;

    private static final double PITCH_LIMIT = 89.9D;

    public enum MotionProfile {
        STANDARD(PEAK_MIN, PEAK_SPAN, EASE_MIN, EASE_SPAN, ACCEL_MIN, ACCEL_SPAN),

        TELLY_FLICK(0.97D, 0.03D, 0.97D, 0.03D, 60.0D, 8.0D),

        TELLY_AIR_FLICK(0.95D, 0.05D, 0.985D, 0.015D, 68.0D, 8.0D),

        SURROUND_FAST_2(0.80D, 0.20D, 0.65D, 0.30D, 11.0D, 7.0D),
        SURROUND_FAST_3(0.90D, 0.10D, 0.75D, 0.24D, 14.0D, 4.0D),
        SURROUND_FAST_4(0.95D, 0.05D, 0.91D, 0.08D, 31.5D, 9.0D),
        SURROUND_FAST_5(0.97D, 0.03D, 0.97D, 0.02D, 62.0D, 18.0D),

        BED_SHELL(0.88D, 0.12D, 0.982D, 0.016D, 46.0D, 12.0D);

        private final double peakMin;
        private final double peakSpan;
        private final double easeMin;
        private final double easeSpan;
        private final double accelMin;
        private final double accelSpan;

        MotionProfile(double peakMin, double peakSpan, double easeMin, double easeSpan,
                      double accelMin, double accelSpan) {
            this.peakMin = peakMin;
            this.peakSpan = peakSpan;
            this.easeMin = easeMin;
            this.easeSpan = easeSpan;
            this.accelMin = accelMin;
            this.accelSpan = accelSpan;
        }
    }

    private AutismHumanRotation() {
    }

    public static final class Stream {
        private final Random random;
        private boolean initialized;
        private double yawAcc;
        private double pitchAcc;

        private double lastStepYaw;
        private double lastStepPitch;

        private int lastKYaw;
        private int lastKPitch;

        private Rolls active;
        private Rolls pending;

        private double lastGoalYaw;
        private double lastGoalPitch;

        public Stream() {
            this(new Random());
        }

        public Stream(Random random) {
            this.random = Objects.requireNonNull(random, "random");
        }
    }

    public static final class Step {
        private final Stream owner;
        private final int kYaw;
        private final int kPitch;
        private final double gcd;

        private Step(Stream owner, int kYaw, int kPitch, double gcd) {
            this.owner = owner;
            this.kYaw = kYaw;
            this.kPitch = kPitch;
            this.gcd = gcd;
        }

        public AutismRotationUtil.Rotation preview() {
            return new AutismRotationUtil.Rotation(
                (float) (owner.yawAcc + kYaw * gcd),
                (float) clampPitch(owner.pitchAcc + kPitch * gcd));
        }
    }

    private static final class Rolls {
        MotionProfile profile;
        double peakYaw;
        double peakPitch;
        double ease;
        double accel;
        double offYaw;
        double offPitch;
        double goalYaw;
        double goalPitch;
    }

    public static double settleBandDegrees(double gcd) {
        return ARRIVE_DEGREES + (gcd > 0.0D ? gcd : FALLBACK_GCD);
    }

    public static boolean isInitialized(Stream s) {
        return s != null && s.initialized;
    }

    public static AutismRotationUtil.Rotation current(Stream s) {
        return isInitialized(s)
            ? new AutismRotationUtil.Rotation((float) s.yawAcc, (float) s.pitchAcc)
            : null;
    }

    public static void seed(Stream s, AutismRotationUtil.Rotation from) {
        Objects.requireNonNull(s, "stream");
        Objects.requireNonNull(from, "from");

        s.yawAcc = from.yaw();

        s.pitchAcc = from.pitch();
        s.lastStepYaw = 0.0D;
        s.lastStepPitch = 0.0D;
        s.lastKYaw = 0;
        s.lastKPitch = 0;
        s.active = null;
        s.pending = null;
        s.lastGoalYaw = from.yaw();
        s.lastGoalPitch = from.pitch();
        s.initialized = true;
    }

    public static void clear(Stream s) {
        Objects.requireNonNull(s, "stream");
        s.initialized = false;
        s.yawAcc = 0.0D;
        s.pitchAcc = 0.0D;
        s.lastStepYaw = 0.0D;
        s.lastStepPitch = 0.0D;
        s.lastKYaw = 0;
        s.lastKPitch = 0;
        s.active = null;
        s.pending = null;
        s.lastGoalYaw = 0.0D;
        s.lastGoalPitch = 0.0D;
    }

    public static Step compute(Stream s, AutismRotationUtil.Rotation goal, float maxYawStep, float maxPitchStep, double gcd) {
        return compute(s, goal, maxYawStep, maxPitchStep, gcd, true);
    }

    public static Step compute(Stream s, AutismRotationUtil.Rotation goal, float maxYawStep, float maxPitchStep, double gcd,
                               boolean applyGoalOffset) {
        return compute(s, goal, maxYawStep, maxPitchStep, gcd, applyGoalOffset, MotionProfile.STANDARD);
    }

    public static Step compute(Stream s, AutismRotationUtil.Rotation goal, float maxYawStep, float maxPitchStep, double gcd,
                               boolean applyGoalOffset, MotionProfile profile) {
        Objects.requireNonNull(s, "stream");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(profile, "profile");
        if (!s.initialized) {
            throw new IllegalStateException("stream is not seeded; seed() from the current server rotation first");
        }
        double g = gcd > 0.0D ? gcd : FALLBACK_GCD;
        Rolls rolls;
        if (rollsStale(s, goal, profile)) {
            s.pending = roll(s.random, goal, profile);
            rolls = s.pending;
        } else {
            s.pending = null;
            rolls = s.active;
        }
        s.lastGoalYaw = goal.yaw();
        s.lastGoalPitch = goal.pitch();

        double effectiveYaw = goal.yaw() + (applyGoalOffset ? rolls.offYaw : 0.0D);

        double effectivePitch = clampPitch(goal.pitch() + (applyGoalOffset ? rolls.offPitch : 0.0D));
        Axis yawAxis = new Axis(wrapDegrees(effectiveYaw - s.yawAcc), maxYawStep, g,
            s.lastStepYaw, rolls.accel, Double.NaN);
        Axis pitchAxis = new Axis(effectivePitch - s.pitchAcc, maxPitchStep, g,
            s.lastStepPitch, rolls.accel, s.pitchAcc);

        int kYaw = quantize(yawAxis, s.lastKYaw, rolls.peakYaw, rolls.ease, s.random);
        int kPitch = quantize(pitchAxis, s.lastKPitch, rolls.peakPitch, rolls.ease, s.random);

        if (kYaw == 0 && Math.abs(kPitch * g) > DITHER_OTHER_MIN
            && yawAxis.converged() && s.random.nextDouble() < DITHER_PROBABILITY) {
            kYaw = dither(yawAxis, s.random);
        }
        if (kPitch == 0 && Math.abs(kYaw * g) > DITHER_OTHER_MIN
            && pitchAxis.converged() && s.random.nextDouble() < DITHER_PROBABILITY) {
            kPitch = dither(pitchAxis, s.random);
        }
        return new Step(s, kYaw, kPitch, g);
    }

    public static AutismRotationUtil.Rotation apply(Stream s, Step step) {
        Objects.requireNonNull(s, "stream");
        Objects.requireNonNull(step, "step");
        if (!s.initialized) {
            throw new IllegalStateException("stream is not seeded; seed() first");
        }
        if (step.owner != s) {
            throw new IllegalArgumentException("step was computed on a different stream");
        }
        if (s.pending != null) {
            s.active = s.pending;
            s.pending = null;
        }

        s.yawAcc = s.yawAcc + step.kYaw * step.gcd;
        s.pitchAcc = clampPitch(s.pitchAcc + step.kPitch * step.gcd);
        s.lastStepYaw = Math.abs(step.kYaw * step.gcd);
        s.lastStepPitch = Math.abs(step.kPitch * step.gcd);
        s.lastKYaw = step.kYaw;
        s.lastKPitch = step.kPitch;
        return current(s);
    }

    public static AutismRotationUtil.Rotation step(Stream s, AutismRotationUtil.Rotation goal, float maxYawStep, float maxPitchStep, double gcd) {
        return step(s, goal, maxYawStep, maxPitchStep, gcd, true);
    }

    public static AutismRotationUtil.Rotation step(Stream s, AutismRotationUtil.Rotation goal, float maxYawStep, float maxPitchStep, double gcd,
                                                   boolean applyGoalOffset) {
        return step(s, goal, maxYawStep, maxPitchStep, gcd, applyGoalOffset, MotionProfile.STANDARD);
    }

    public static AutismRotationUtil.Rotation step(Stream s, AutismRotationUtil.Rotation goal,
                                                   float maxYawStep, float maxPitchStep, double gcd,
                                                   boolean applyGoalOffset, MotionProfile profile) {
        Objects.requireNonNull(s, "stream");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(profile, "profile");
        if (!s.initialized) {
            seed(s, goal);
            return current(s);
        }
        return apply(s, compute(s, goal, maxYawStep, maxPitchStep, gcd, applyGoalOffset, profile));
    }

    static double[] effectiveGoalForTesting(Stream s) {
        if (!isInitialized(s) || s.active == null) return null;
        return new double[] {
            s.lastGoalYaw + s.active.offYaw,
            clampPitch(s.lastGoalPitch + s.active.offPitch)
        };
    }

    private static Rolls roll(Random random, AutismRotationUtil.Rotation goal, MotionProfile profile) {
        Rolls rolls = new Rolls();
        rolls.profile = profile;
        rolls.peakYaw = profile.peakMin + profile.peakSpan * random.nextDouble();
        rolls.peakPitch = profile.peakMin + profile.peakSpan * random.nextDouble();
        rolls.ease = profile.easeMin + profile.easeSpan * random.nextDouble();
        rolls.accel = profile.accelMin + profile.accelSpan * random.nextDouble();
        rolls.offYaw = rollOffset(random);
        rolls.offPitch = rollOffset(random);
        rolls.goalYaw = goal.yaw();
        rolls.goalPitch = goal.pitch();
        return rolls;
    }

    private static double rollOffset(Random random) {
        double magnitude = OFFSET_MIN + OFFSET_SPAN * random.nextDouble();
        return random.nextBoolean() ? magnitude : -magnitude;
    }

    private static boolean rollsStale(Stream s, AutismRotationUtil.Rotation goal, MotionProfile profile) {
        if (s.active == null) return true;
        if (s.active.profile != profile) return true;
        double dYaw = wrapDegrees(goal.yaw() - s.active.goalYaw);
        double dPitch = goal.pitch() - s.active.goalPitch;
        return Math.max(Math.abs(dYaw), Math.abs(dPitch)) > REROLL_GOAL_DEGREES;
    }

    private static int quantize(Axis axis, int lastK, double peak, double ease, Random random) {
        double desired = axis.absRemaining <= TAIL_CUT_DEGREES
            ? axis.absRemaining
            : Math.min(Math.min(axis.absRemaining * ease, axis.cap * peak), axis.rampLimit);
        int k = axis.direction * (int) Math.round(desired / axis.gcd);

        if (!axis.converged() && (k == 0 || Integer.signum(k) != axis.direction)) {
            k = axis.direction;
        }
        while (k != 0 && !axis.valid(k)) {
            k -= Integer.signum(k);
        }

        if (k == lastK && Math.abs(k * axis.gcd) > IDENTICAL_DELTA_MIN) {
            int first = random.nextBoolean() ? 1 : -1;
            if (axis.valid(k + first)) {
                k += first;
            } else if (axis.valid(k - first)) {
                k -= first;
            }
        }

        if (random.nextDouble() < JITTER_PROBABILITY) {
            int first = random.nextBoolean() ? 1 : -1;
            if (axis.valid(k + first) && keepsAimC(k + first, lastK, axis.gcd)) {
                k += first;
            } else if (axis.valid(k - first) && keepsAimC(k - first, lastK, axis.gcd)) {
                k -= first;
            }
        }

        if (k != 0 && onAimBMultiple(Math.abs(k * axis.gcd))) {
            int first = random.nextBoolean() ? 1 : -1;
            if (axis.valid(k + first) && keepsAimC(k + first, lastK, axis.gcd)
                && !onAimBMultiple(Math.abs((k + first) * axis.gcd))) {
                k += first;
            } else if (axis.valid(k - first) && keepsAimC(k - first, lastK, axis.gcd)
                && !onAimBMultiple(Math.abs((k - first) * axis.gcd))) {
                k -= first;
            }
        }
        return k;
    }

    private static boolean keepsAimC(int k, int lastK, double gcd) {
        return k != lastK || Math.abs(k * gcd) <= IDENTICAL_DELTA_MIN;
    }

    private static int dither(Axis axis, Random random) {
        int sign = random.nextBoolean() ? 1 : -1;
        if (axis.valid(sign)) return sign;
        if (axis.valid(-sign)) return -sign;
        return 0;
    }

    private static boolean onAimBMultiple(double absDelta) {
        return Math.abs(absDelta - 0.1D * Math.round(absDelta / 0.1D)) <= 1.0E-6D
            || Math.abs(absDelta - 0.25D * Math.round(absDelta / 0.25D)) <= 1.0E-6D;
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) wrapped -= 360.0D;
        if (wrapped < -180.0D) wrapped += 360.0D;
        return wrapped;
    }

    private static double clampPitch(double pitch) {
        return Math.max(-PITCH_LIMIT, Math.min(PITCH_LIMIT, pitch));
    }

    private static final class Axis {
        final double absRemaining;
        final int direction;
        final double cap;
        final double gcd;
        final double rampLimit;

        final double pitchAcc;

        final boolean pitchPinned;

        Axis(double remaining, float cap, double gcd, double lastStep, double accel, double pitchAcc) {
            this.absRemaining = Math.abs(remaining);
            this.direction = remaining > 0.0D ? 1 : (remaining < 0.0D ? -1 : 0);
            this.cap = cap;
            this.gcd = gcd;
            this.rampLimit = lastStep + accel;
            this.pitchAcc = pitchAcc;
            this.pitchPinned = !Double.isNaN(pitchAcc) && this.direction != 0
                && Math.abs(pitchAcc) <= PITCH_LIMIT + 1.0E-9D
                && Math.abs(pitchAcc + this.direction * gcd) > PITCH_LIMIT + 1.0E-9D;
        }

        boolean converged() {
            return absRemaining <= ARRIVE_DEGREES || pitchPinned;
        }

        boolean valid(int k) {
            double delta = Math.abs(k * gcd);
            if (delta > cap) return false;
            if (delta > absRemaining + gcd + 1.0E-9D) return false;
            if (delta > rampLimit + 1.0E-9D) return false;
            if (!converged() && (k == 0 || Integer.signum(k) != direction)) return false;
            if (!Double.isNaN(pitchAcc) && Math.abs(pitchAcc + k * gcd) > PITCH_LIMIT + 1.0E-9D) return false;
            return true;
        }
    }
}
