package autismclient.api.module;

public final class DoubleSetting extends Setting<Double, DoubleSetting> {
    public DoubleSetting(String name, String title, double defaultValue, double min, double max, double step) {
        super(Kind.DOUBLE, name, title, clamp(defaultValue, min, Math.max(min, max)));
        setRange(min, max);
        setSliderRange(min, max);
        setStep(step <= 0 ? 0.1 : step);
    }

    public DoubleSetting sliderRange(double min, double max) {
        setSliderRange(min, max);
        return this;
    }

    @Override
    protected Double decode(String raw) {
        if (raw == null) return defaultValueTyped();
        try {
            return clamp(Double.parseDouble(raw.trim()), min(), max());
        } catch (Exception e) {
            return defaultValueTyped();
        }
    }

    @Override
    protected String encode(Double value) {
        double v = value == null ? defaultValueTyped() : value;
        return Double.toString(clamp(v, min(), max()));
    }

    @Override
    protected Double sanitizeTyped(Double value) {
        double v = value == null ? defaultValueTyped() : value;
        return clamp(v, min(), max());
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
