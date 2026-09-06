package autismclient.api.module;

import java.util.Random;

public record ValueRange(double min, double max) {
    public ValueRange {
        if (max < min) {
            double swap = min;
            min = max;
            max = swap;
        }
    }

    public ValueRange(int min, int max) {
        this((double) min, (double) max);
    }

    public ValueRange clamp(double lower, double upper) {
        double high = Math.max(lower, upper);
        return new ValueRange(
            Math.max(lower, Math.min(high, min)),
            Math.max(lower, Math.min(high, max)));
    }

    public ValueRange withMinSeparation(double separation, double lower, double upper, boolean anchorMin) {
        double high = Math.max(lower, upper);
        if (separation <= 0 || high - lower < separation) return clamp(lower, high);
        ValueRange bounded = clamp(lower, high);
        if (bounded.max - bounded.min >= separation) return bounded;
        if (anchorMin) {
            double pushed = bounded.min + separation;
            return pushed <= high
                ? new ValueRange(bounded.min, pushed)
                : new ValueRange(high - separation, high);
        }
        double pushed = bounded.max - separation;
        return pushed >= lower
            ? new ValueRange(pushed, bounded.max)
            : new ValueRange(lower, lower + separation);
    }

    public double random(Random random) {
        if (random == null || max <= min) return min;
        return min + random.nextDouble() * (max - min);
    }

    @Override
    public String toString() {
        return number(min) + "," + number(max);
    }

    public String format(double step) {
        boolean whole = step >= 1.0 && step == Math.rint(step);
        return whole
            ? Math.round(min) + " - " + Math.round(max)
            : number(min) + " - " + number(max);
    }

    private static String number(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) return Long.toString((long) value);
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }

    public static ValueRange parse(String raw, ValueRange fallback) {
        if (raw == null) return fallback;
        int comma = raw.indexOf(',');
        if (comma < 0) return fallback;
        try {
            return new ValueRange(
                Double.parseDouble(raw.substring(0, comma).trim()),
                Double.parseDouble(raw.substring(comma + 1).trim()));
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
