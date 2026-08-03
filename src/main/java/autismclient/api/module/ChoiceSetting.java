package autismclient.api.module;

import java.util.Arrays;
import java.util.List;

public final class ChoiceSetting extends Setting<String, ChoiceSetting> {
    public ChoiceSetting(String name, String title, String defaultValue, String... choices) {
        super(Kind.ENUM, name, title, defaultValue == null ? "" : defaultValue);
        setChoices(choices == null ? List.of() : Arrays.asList(choices));
    }

    public ChoiceSetting(String name, String title, String defaultValue, List<String> choices) {
        super(Kind.ENUM, name, title, defaultValue == null ? "" : defaultValue);
        setChoices(choices);
    }

    @Override
    protected String decode(String raw) {
        if (raw == null) return defaultValueTyped();
        String needle = raw.trim();
        List<String> options = choices();
        if (options.isEmpty()) return needle;
        for (String choice : options) {
            if (choice.equalsIgnoreCase(needle)) return choice;
        }
        return defaultValueTyped();
    }

    @Override
    protected String encode(String value) {
        if (value == null) return defaultValueTyped();
        List<String> options = choices();
        if (options.isEmpty()) return value;
        for (String choice : options) {
            if (choice.equalsIgnoreCase(value)) return choice;
        }
        return defaultValueTyped();
    }
}
