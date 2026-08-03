package autismclient.util;

public final class SmoothedMouseAccumulator {
    private static final double EPSILON = 1.0E-9;

    private double remainingX;
    private double remainingY;
    private double carryX;
    private double carryY;
    private double remainingSeconds;
    private long previousX;
    private long previousY;
    private boolean varyX;
    private boolean varyY = true;

    public void replace(double x, double y, double durationSeconds) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(durationSeconds)) return;

        carryX = compatibleCarry(carryX, remainingX, x);
        carryY = compatibleCarry(carryY, remainingY, y);
        remainingX = x;
        remainingY = y;
        remainingSeconds = Math.max(0.001, durationSeconds);
    }

    public RawMouseAccumulator.Counts consume(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds <= 0.0 || remainingSeconds <= 0.0) {
            return new RawMouseAccumulator.Counts(0, 0);
        }

        double fraction = elapsedSeconds + EPSILON >= remainingSeconds
            ? 1.0
            : Math.min(1.0, elapsedSeconds / remainingSeconds);
        double sliceX = remainingX * fraction;
        double sliceY = remainingY * fraction;
        remainingX -= sliceX;
        remainingY -= sliceY;
        remainingSeconds = Math.max(0.0, remainingSeconds - elapsedSeconds);
        if (remainingSeconds == 0.0) {
            remainingX = 0.0;
            remainingY = 0.0;
        }

        double totalX = carryX + sliceX;
        double totalY = carryY + sliceY;
        long wholeX = wholeCounts(totalX);
        long wholeY = wholeCounts(totalY);
        if (remainingSeconds > EPSILON) {
            wholeX = varyRepeated(wholeX, previousX, remainingX, varyX);
            wholeY = varyRepeated(wholeY, previousY, remainingY, varyY);
            if (wholeX == previousX && wholeX != 0L) varyX = !varyX;
            if (wholeY == previousY && wholeY != 0L) varyY = !varyY;
        }
        carryX = totalX - wholeX;
        carryY = totalY - wholeY;
        previousX = wholeX;
        previousY = wholeY;
        return new RawMouseAccumulator.Counts(wholeX, wholeY);
    }

    public boolean hasPending() {
        return remainingSeconds > 0.0D;
    }

    public void clear() {
        remainingX = 0.0;
        remainingY = 0.0;
        carryX = 0.0;
        carryY = 0.0;
        remainingSeconds = 0.0;
        previousX = 0L;
        previousY = 0L;
        varyX = false;
        varyY = true;
    }

    private static double compatibleCarry(double carry, double previous, double next) {
        if (Math.abs(next) < EPSILON) return 0.0;
        double direction = Math.abs(previous) >= EPSILON ? previous : carry;
        return Math.abs(direction) >= EPSILON && Math.signum(direction) != Math.signum(next) ? 0.0 : carry;
    }

    private static long wholeCounts(double value) {
        if (!Double.isFinite(value) || Math.abs(value) < 1.0) return 0L;
        double nearest = Math.rint(value);
        if (Math.abs(value - nearest) < 1.0E-7) value = nearest;
        return value > 0.0 ? (long) Math.floor(value) : (long) Math.ceil(value);
    }

    private static long varyRepeated(long value, long previous, double future, boolean increase) {
        if (value == 0L || value != previous || Math.abs(future) < 1.0) return value;
        long direction = Long.signum(value);
        long varied = value + (increase ? direction : -direction);
        return Long.signum(varied) == direction ? varied : value;
    }
}
