package autismclient.api.module;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public abstract class Setting<T, S extends Setting<T, S>> {
    private final Kind kind;
    private final String name;
    private final T def;
    private String title;
    private String description = "";
    private String group = "General";
    private String unit = "";
    private DisplayMode displayMode = DisplayMode.DEFAULT;
    private Availability availability;
    private BooleanSupplier visible;
    private Function<String, String> formatter;
    private Runnable action;
    private String linkedActionId = "";
    private double min;
    private double max;
    private double sliderMin;
    private double sliderMax;
    private double step = 1.0;
    private List<String> choices = List.of();

    private SettingOwner owner;
    private transient boolean cached;
    private transient String cachedRaw;
    private transient T cachedValue;

    protected Setting(Kind kind, String name, String title, T def) {
        this.kind = kind == null ? Kind.STRING : kind;
        this.name = name == null ? "" : name;
        this.title = title == null || title.isBlank() ? this.name : title;
        this.def = def;
    }

    @SuppressWarnings("unchecked")
    protected final S self() {
        return (S) this;
    }

    protected abstract T decode(String raw);

    protected abstract String encode(T value);

    protected T sanitizeTyped(T value) {
        return value == null ? def : value;
    }

    public final void attach(SettingOwner owner) {
        this.owner = owner;
        this.cached = false;
    }

    private String stored() {
        if (owner == null) return defaultValue();
        String raw = owner.settingValue(name);
        return raw == null ? defaultValue() : raw;
    }

    public final T get() {
        String raw = stored();
        if (cached && Objects.equals(raw, cachedRaw)) return cachedValue;
        T value = decode(raw);
        cachedRaw = raw;
        cachedValue = value;
        cached = true;
        return value;
    }

    public final void set(T value) {
        if (owner != null) owner.putSettingValue(name, encode(sanitizeTyped(value)));
    }

    public final T defaultValueTyped() {
        return def;
    }

    public final S description(String description) {
        this.description = description == null ? "" : description;
        return self();
    }

    public final S group(String group) {
        this.group = group == null || group.isBlank() ? "General" : group;
        return self();
    }

    public final S unit(String unit) {
        this.unit = unit == null ? "" : unit;
        return self();
    }

    public final S visibleWhen(BooleanSupplier visible) {
        this.visible = visible;
        return self();
    }

    public final S formatter(Function<String, String> formatter) {
        this.formatter = formatter;
        return self();
    }

    public final S displayMode(DisplayMode displayMode) {
        this.displayMode = displayMode == null ? DisplayMode.DEFAULT : displayMode;
        return self();
    }

    public final S readonlySummary() {
        return displayMode(DisplayMode.READONLY_SUMMARY);
    }

    public final S numericTextField() {
        return displayMode(DisplayMode.NUMERIC_TEXT_FIELD);
    }

    public final S playerNameList() {
        return displayMode(DisplayMode.PLAYER_NAME_LIST);
    }

    public final S rankTagList() {
        return displayMode(DisplayMode.RANK_TAG_LIST);
    }

    public final S macroPicker() {
        return displayMode(DisplayMode.MACRO_PICKER);
    }

    public final S conditionalMacroPicker() {
        return displayMode(DisplayMode.CONDITIONAL_MACRO_PICKER);
    }

    public final S filePicker(String linkedActionId) {
        this.linkedActionId = linkedActionId == null ? "" : linkedActionId;
        return displayMode(DisplayMode.FILE_PICKER);
    }

    public final S linkedActionId(String linkedActionId) {
        this.linkedActionId = linkedActionId == null ? "" : linkedActionId;
        return self();
    }

    public final S availableOffline() {
        this.availability = Availability.ALWAYS;
        return self();
    }

    public final S requiresWorld() {
        this.availability = Availability.IN_WORLD;
        return self();
    }

    public final S requiresContainer() {
        this.availability = Availability.IN_CONTAINER;
        return self();
    }

    public final S build() {
        return self();
    }

    protected final void setRange(double min, double max) {
        this.min = min;
        this.max = Math.max(min, max);
    }

    protected final void setSliderRange(double min, double max) {
        this.sliderMin = min;
        this.sliderMax = Math.max(min, max);
    }

    protected final void setStep(double step) {
        this.step = step <= 0 ? 1.0 : step;
    }

    protected final void setChoices(List<String> choices) {
        this.choices = choices == null ? List.of() : List.copyOf(choices);
    }

    protected final void setAction(Runnable action) {
        this.action = action;
    }

    public final String id() {
        return name;
    }

    public final String label() {
        return title;
    }

    public final String description() {
        return description;
    }

    public final String group() {
        return group;
    }

    public boolean isListSelection() {
        return false;
    }

    public final boolean isListModeSelector() {
        List<String> options = choices();
        if (options.size() < 2) return false;
        boolean whitelist = false;
        boolean blacklist = false;
        for (String option : options) {
            if ("whitelist".equalsIgnoreCase(option)) whitelist = true;
            else if ("blacklist".equalsIgnoreCase(option)) blacklist = true;
        }
        return whitelist && blacklist;
    }

    public final Kind kind() {
        return kind;
    }

    public final DisplayMode displayMode() {
        return displayMode;
    }

    public final Availability availability() {
        return availability == null
            ? (kind == Kind.ACTION ? Availability.IN_WORLD : Availability.ALWAYS)
            : availability;
    }

    public final String unit() {
        return unit;
    }

    public final List<String> choices() {
        return choices;
    }

    public final double min() {
        return min;
    }

    public final double max() {
        return max;
    }

    public final double sliderMin() {
        return sliderMin;
    }

    public final double sliderMax() {
        return sliderMax;
    }

    public final double step() {
        return step;
    }

    public final Runnable action() {
        return action;
    }

    public final String linkedActionId() {
        return linkedActionId;
    }

    public final String defaultValue() {
        return encode(def);
    }

    public final String serialize() {
        return stored();
    }

    public final String displayString() {
        return format(serialize());
    }

    public final String format(String value) {
        if (formatter == null) return value == null ? "" : value;
        try {
            return formatter.apply(value);
        } catch (Throwable t) {
            return value == null ? "" : value;
        }
    }

    public final String sanitizeUiString(String raw) {
        return encode(decode(raw));
    }

    public final String deserialize(String raw) {
        return sanitizeUiString(raw);
    }

    public final boolean isVisible() {
        if (visible == null) return true;
        try {
            return visible.getAsBoolean();
        } catch (Throwable t) {
            return true;
        }
    }

    public final boolean isAvailable(boolean inWorld, boolean inContainer) {
        return switch (availability()) {
            case ALWAYS -> true;
            case IN_WORLD -> inWorld;
            case IN_CONTAINER -> inContainer;
        };
    }

    public final boolean isModified() {
        return !Objects.equals(serialize(), defaultValue());
    }
}
