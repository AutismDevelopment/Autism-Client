package autismclient.api.module;

import java.util.ArrayList;
import java.util.List;

abstract class ListSetting<S extends ListSetting<S>> extends Setting<List<String>, S> {
    protected ListSetting(Kind kind, String name, String title, List<String> defaultValue) {
        super(kind, name, title, defaultValue == null ? List.of() : List.copyOf(defaultValue));
    }

    @Override
    public boolean isListSelection() {
        return true;
    }

    protected ListSetting(Kind kind, String name, String title, String defaultRaw) {
        super(kind, name, title, parseRaw(defaultRaw));
    }

    static List<String> parseRaw(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : raw.split("\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return List.copyOf(out);
    }

    @Override
    protected List<String> decode(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : raw.split("\\|")) {
            String item = part.trim();
            if (!item.isEmpty()) out.add(item);
        }
        return List.copyOf(out);
    }

    @Override
    protected String encode(List<String> value) {
        if (value == null || value.isEmpty()) return "";
        List<String> out = new ArrayList<>();
        for (String item : value) {
            if (item == null) continue;
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return String.join("|", out);
    }
}
