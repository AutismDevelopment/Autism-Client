package autismclient.api.module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class EnumSetting<E extends Enum<E>> extends Setting<E, EnumSetting<E>> {
    private final E[] values;
    private final Function<E, String> tokenizer;

    public EnumSetting(String name, String title, E defaultValue, E[] values, Function<E, String> tokenizer) {
        super(Kind.ENUM, name, title, defaultValue);
        this.values = values == null ? newArray(defaultValue) : values;
        this.tokenizer = tokenizer == null ? Enum::name : tokenizer;
        List<String> labels = new ArrayList<>();
        for (E value : this.values) labels.add(token(value));
        setChoices(labels);
    }

    public EnumSetting(String name, String title, E defaultValue, E[] values) {
        this(name, title, defaultValue, values, EnumSetting::titleCase);
    }

    private String token(E value) {
        if (value == null) return "";
        try {
            String label = tokenizer.apply(value);
            return label == null || label.isBlank() ? value.name() : label;
        } catch (Throwable t) {
            return value.name();
        }
    }

    @Override
    protected E decode(String raw) {
        if (raw == null) return defaultValueTyped();
        String needle = raw.trim();
        if (needle.isEmpty()) return defaultValueTyped();
        for (E value : values) {
            if (token(value).equalsIgnoreCase(needle)) return value;
        }
        for (E value : values) {
            if (value.name().equalsIgnoreCase(needle)) return value;
        }
        return defaultValueTyped();
    }

    @Override
    protected String encode(E value) {
        return token(value == null ? defaultValueTyped() : value);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E[] newArray(E single) {
        E[] array = (E[]) java.lang.reflect.Array.newInstance(single.getClass(), 1);
        array[0] = single;
        return array;
    }

    public static String titleCase(Enum<?> value) {
        String[] words = value.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
