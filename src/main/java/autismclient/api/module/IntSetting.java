package autismclient.api.module;

public final class IntSetting extends Setting<Integer, IntSetting> {
    public IntSetting(String name, String title, int defaultValue, int min, int max, int step) {
        super(Kind.INTEGER, name, title, clampStatic(defaultValue, min, Math.max(min, max)));
        setRange(min, max);
        setSliderRange(min, max);
        setStep(Math.max(1, step));
    }

    public IntSetting sliderRange(double min, double max) {
        setSliderRange(min, max);
        return this;
    }

    @Override
    protected Integer decode(String raw) {
        if (raw == null) return defaultValueTyped();
        try {
            return (int) clampStatic((long) Double.parseDouble(raw.trim()), (long) min(), (long) max());
        } catch (Exception e) {
            return defaultValueTyped();
        }
    }

    @Override
    protected String encode(Integer value) {
        int v = value == null ? defaultValueTyped() : value;
        return Integer.toString((int) clampStatic(v, (long) min(), (long) max()));
    }

    @Override
    protected Integer sanitizeTyped(Integer value) {
        int v = value == null ? defaultValueTyped() : value;
        return (int) clampStatic(v, (long) min(), (long) max());
    }

    private static int clampStatic(int value, int min, int max) {
        return (int) clampStatic((long) value, (long) min, (long) max);
    }

    private static long clampStatic(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
