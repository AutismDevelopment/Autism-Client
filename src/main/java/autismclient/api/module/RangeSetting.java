package autismclient.api.module;

public final class RangeSetting extends Setting<ValueRange, RangeSetting> {
    private double minSeparation;

    public RangeSetting(String name, String title, ValueRange defaultValue, double min, double max, double step) {
        super(Kind.STRING, name, title,
            (defaultValue == null ? new ValueRange(min, max) : defaultValue).clamp(min, Math.max(min, max)));
        setRange(min, max);
        setSliderRange(min, max);
        setStep(step);
        displayMode(DisplayMode.RANGE_SLIDER);
    }

    public RangeSetting(String name, String title, double defaultMin, double defaultMax,
                        double min, double max, double step) {
        this(name, title, new ValueRange(defaultMin, defaultMax), min, max, step);
    }

    public RangeSetting minSeparation(double separation) {
        this.minSeparation = Math.max(0.0, separation);
        return this;
    }

    public double minSeparation() {
        return minSeparation;
    }

    @Override
    protected ValueRange decode(String raw) {
        return sanitizeTyped(ValueRange.parse(raw, defaultValueTyped()));
    }

    @Override
    protected String encode(ValueRange value) {
        return sanitizeTyped(value).toString();
    }

    @Override
    protected ValueRange sanitizeTyped(ValueRange value) {
        ValueRange range = value == null ? defaultValueTyped() : value;
        return range.withMinSeparation(minSeparation, min(), max(), false);
    }
}
