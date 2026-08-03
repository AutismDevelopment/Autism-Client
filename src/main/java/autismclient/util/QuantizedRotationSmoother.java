package autismclient.util;

public final class QuantizedRotationSmoother {
    private static final double EPSILON = 1.0E-8;
    private static final double MIN_ACCELERATION = 0.65;

    private double yawVelocity;
    private double pitchVelocity;
    private double yawNoise;
    private double pitchNoise;
    private double curvePhase;
    private long previousYawCounts;
    private long previousPitchCounts;
    private long randomState = 0x6A09E667F3BCC909L;
    private boolean hasPreviousStep;
    private boolean repeatPolarity;

    public record Step(long yawCounts, long pitchCounts) {
        public AutismRotationUtil.Rotation asDelta(double degreesPerCount) {
            if (!Double.isFinite(degreesPerCount) || degreesPerCount <= 0.0) {
                return new AutismRotationUtil.Rotation(0.0f, 0.0f);
            }
            return new AutismRotationUtil.Rotation(
                (float) (yawCounts * degreesPerCount),
                (float) (pitchCounts * degreesPerCount)
            );
        }
    }

    public void reset() {
        reset(0x6A09E667F3BCC909L);
    }

    public void reset(long seed) {
        randomState = mixSeed(seed);
        halt();
        curvePhase = unitRandom() * Math.PI * 2.0;
    }

    public void halt() {
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        yawNoise = 0.0;
        pitchNoise = 0.0;
        previousYawCounts = 0L;
        previousPitchCounts = 0L;
        hasPreviousStep = false;
        repeatPolarity = false;
    }

    public Step step(
        float yawErrorDegrees,
        float pitchErrorDegrees,
        double degreesPerCount,
        float horizontalSpeed,
        float verticalSpeed,
        float directionChange,
        float midpoint,
        boolean allowYaw,
        boolean allowPitch
    ) {
        return stepWithLimits(
            yawErrorDegrees,
            pitchErrorDegrees,
            degreesPerCount,
            speedLimit(horizontalSpeed),
            speedLimit(verticalSpeed),
            1.0,
            directionChange,
            midpoint,
            allowYaw,
            allowPitch
        );
    }

    public Step stepFast(
        float yawErrorDegrees,
        float pitchErrorDegrees,
        double degreesPerCount,
        float horizontalDegreesPerTick,
        float verticalDegreesPerTick,
        float directionChange,
        float midpoint,
        boolean allowYaw,
        boolean allowPitch
    ) {
        if (!Double.isFinite(degreesPerCount) || degreesPerCount <= EPSILON) return zeroStep();
        return stepWithLimits(
            yawErrorDegrees,
            pitchErrorDegrees,
            degreesPerCount,
            degreeSpeedLimit(horizontalDegreesPerTick, degreesPerCount),
            degreeSpeedLimit(verticalDegreesPerTick, degreesPerCount),
            1.30,
            directionChange,
            midpoint,
            allowYaw,
            allowPitch
        );
    }

    private Step stepWithLimits(
        float yawErrorDegrees,
        float pitchErrorDegrees,
        double degreesPerCount,
        double horizontalLimit,
        double verticalLimit,
        double responseScale,
        float directionChange,
        float midpoint,
        boolean allowYaw,
        boolean allowPitch
    ) {
        if (!Double.isFinite(degreesPerCount) || degreesPerCount <= EPSILON) return zeroStep();

        long yawError = allowYaw ? Math.round(yawErrorDegrees / degreesPerCount) : 0L;
        long pitchError = allowPitch ? Math.round(pitchErrorDegrees / degreesPerCount) : 0L;
        if (!allowYaw) yawVelocity = 0.0;
        if (!allowPitch) pitchVelocity = 0.0;

        double direction = clamp(directionChange, 0.0, 1.0);
        double curvePoint = clamp(midpoint, 0.0, 1.0);

        yawVelocity = advanceVelocity(yawVelocity, yawError, horizontalLimit, direction, curvePoint, responseScale);
        pitchVelocity = advanceVelocity(pitchVelocity, pitchError, verticalLimit, direction, curvePoint, responseScale);

        yawNoise = yawNoise * 0.72 + centeredRandom() * 0.28;
        pitchNoise = pitchNoise * 0.67 + centeredRandom() * 0.33;
        curvePhase += 0.51 + unitRandom() * 0.23;

        double distance = Math.hypot(yawError, pitchError);
        double yawCandidate = yawVelocity;
        double pitchCandidate = pitchVelocity;
        if (allowYaw && allowPitch && distance >= 7.0) {
            double curvature = Math.sin(curvePhase)
                * Math.min(1.15, 0.32 + Math.sqrt(distance) * 0.045)
                * (0.55 + direction * 0.45);
            yawCandidate += (-pitchError / distance) * curvature;
            pitchCandidate += (yawError / distance) * curvature;
        }

        double noiseScale = distance >= 5.0 ? 0.72 + direction * 0.28 : 0.0;
        yawCandidate += yawNoise * noiseScale;
        pitchCandidate += pitchNoise * noiseScale * 0.83;

        long yawCounts = boundedCounts(yawCandidate, yawError);
        long pitchCounts = boundedCounts(pitchCandidate, pitchError);
        Step varied = varyRepeatedStep(yawCounts, pitchCounts, yawError, pitchError);
        previousYawCounts = varied.yawCounts;
        previousPitchCounts = varied.pitchCounts;
        hasPreviousStep = true;
        return varied;
    }

    private Step zeroStep() {
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        previousYawCounts = 0L;
        previousPitchCounts = 0L;
        hasPreviousStep = false;
        return new Step(0L, 0L);
    }

    private double advanceVelocity(double velocity, long error, double speedLimit,
                                   double directionChange, double midpoint, double responseScale) {
        if (error == 0L) return approach(velocity, 0.0, Math.max(MIN_ACCELERATION, speedLimit * 0.28));

        double acceleration = Math.max(
            MIN_ACCELERATION,
            speedLimit * (0.14 + (1.0 - midpoint) * 0.10 + directionChange * 0.06)
        ) * Math.max(1.0, responseScale);
        double brakingAcceleration = acceleration * (0.72 + midpoint * 0.75);
        double brakingSpeed = Math.sqrt(2.0 * brakingAcceleration * Math.abs((double) error));
        double desired = Math.copySign(Math.min(speedLimit, brakingSpeed), error);
        return approach(velocity, desired, acceleration);
    }

    private Step varyRepeatedStep(long yaw, long pitch, long yawError, long pitchError) {
        if (!hasPreviousStep || (yaw == 0L && pitch == 0L)
            || yaw != previousYawCounts || pitch != previousPitchCounts) {
            return new Step(yaw, pitch);
        }

        boolean preferYaw = Math.abs(yawError) - Math.abs(yaw) >= Math.abs(pitchError) - Math.abs(pitch);
        long variedYaw = yaw;
        long variedPitch = pitch;
        if (preferYaw && canVary(yaw, yawError)) variedYaw = varyCount(yaw, yawError);
        else if (canVary(pitch, pitchError)) variedPitch = varyCount(pitch, pitchError);
        else if (canVary(yaw, yawError)) variedYaw = varyCount(yaw, yawError);
        repeatPolarity = !repeatPolarity;
        return new Step(variedYaw, variedPitch);
    }

    private boolean canVary(long count, long error) {
        return count != 0L && Math.abs(error) > Math.abs(count) + 1L;
    }

    private long varyCount(long count, long error) {
        long direction = Long.signum(error);
        long candidate = count + (repeatPolarity ? direction : -direction);
        if (candidate == count || Long.signum(candidate) != direction || Math.abs(candidate) > Math.abs(error)) {
            candidate = count + direction;
        }
        return candidate;
    }

    private static long boundedCounts(double candidate, long error) {
        if (error == 0L || !Double.isFinite(candidate)) return 0L;
        long rounded = Math.round(candidate);
        long direction = Long.signum(error);
        if (rounded != 0L && Long.signum(rounded) != direction) return 0L;
        long magnitude = Math.min(Math.abs(rounded), Math.abs(error));
        if (magnitude == 0L && Math.abs(candidate) >= 0.35) magnitude = 1L;
        return direction * magnitude;
    }

    private static double speedLimit(float configuredSpeed) {
        return 2.0 + 68.0 * clamp(configuredSpeed, 0.01, 1.0);
    }

    private static double degreeSpeedLimit(float degreesPerTick, double degreesPerCount) {
        double degrees = Double.isFinite(degreesPerTick) ? Math.max(0.01, degreesPerTick) : 0.01;
        return Math.max(2.0, degrees / degreesPerCount);
    }

    private static double approach(double current, double target, double amount) {
        if (current < target) return Math.min(target, current + amount);
        return Math.max(target, current - amount);
    }

    private double centeredRandom() {
        return unitRandom() * 2.0 - 1.0;
    }

    private double unitRandom() {
        long x = randomState;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        randomState = x;
        return (x >>> 11) * 0x1.0p-53;
    }

    private static long mixSeed(long seed) {
        long value = seed == 0L ? 0x9E3779B97F4A7C15L : seed;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value == 0L ? 0xD1B54A32D192ED03L : value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
