package autismclient.util.multi;

import autismclient.util.AutismConfig;

import java.util.regex.Pattern;

public final class MultiMacroDelay {
    public static final int MIN_MS = 0;
    public static final int MAX_MS = 10_000;

    public static final int STEP_MS = 100;

    public static final int TYPED_MAX_LENGTH = 4;

    private static final Pattern TYPED_SECONDS = Pattern.compile("\\d{0,2}(\\.\\d?)?");

    private MultiMacroDelay() {
    }

    public static int currentMs() {
        return clamp(AutismConfig.getGlobal().multiMacroStartDelayMs);
    }

    public static void setMs(int ms) {
        AutismConfig.getGlobal().multiMacroStartDelayMs = clamp(ms);
    }

    public static void persist() {
        AutismConfig.getGlobal().save();
    }

    public static int clamp(int ms) {
        int bounded = Math.max(MIN_MS, Math.min(MAX_MS, ms));
        return Math.round(bounded / (float) STEP_MS) * STEP_MS;
    }

    public static double ratio(int ms) {
        return clamp(ms) / (double) MAX_MS;
    }

    public static int fromMouse(double mouseX, int trackX, int trackWidth) {
        if (trackWidth <= 0) return MIN_MS;
        double ratio = Math.max(0.0D, Math.min(1.0D, (mouseX - trackX) / trackWidth));
        return clamp((int) Math.round(ratio * MAX_MS));
    }

    public static int nudge(int ms, int direction) {
        return clamp(clamp(ms) + Integer.signum(direction) * STEP_MS);
    }

    public static void nudgeAndPersist(int direction) {
        if (direction == 0) return;
        setMs(nudge(currentMs(), direction));
        persist();
    }

    public static boolean typable(String text) {
        return text != null && TYPED_SECONDS.matcher(text).matches();
    }

    public static String editText(int ms) {
        int value = clamp(ms);
        return value % 1000 == 0 ? Integer.toString(value / 1000) : (value / 1000) + "." + (value % 1000) / 100;
    }

    public static int fromTyped(String typed, int fallback) {
        double seconds;
        try {
            seconds = Double.parseDouble(typed == null ? "" : typed);
        } catch (NumberFormatException e) {
            return clamp(fallback);
        }
        if (Double.isNaN(seconds)) return clamp(fallback);

        double bounded = Math.max(MIN_MS / 1000.0D, Math.min(MAX_MS / 1000.0D, seconds));
        return clamp((int) Math.round(bounded * 1000.0D));
    }

    public static String valueText(int ms) {
        int value = clamp(ms);
        if (value <= 0) return "Off";
        return value % 1000 == 0 ? (value / 1000) + "s" : (value / 1000) + "." + (value % 1000) / 100 + "s";
    }

    public static String countdownText(long remainingMs) {
        long tenths = Math.max(0L, (remainingMs + 99L) / 100L);
        return (tenths / 10) + "." + (tenths % 10) + "s";
    }

    public static long startAt(long launchedAt, int index, int gapMs) {
        return launchedAt + (long) Math.max(0, index) * Math.max(0, gapMs);
    }
}
