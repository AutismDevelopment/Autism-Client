package autismclient.util.macro;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MacroVariableContext {
    private final ConcurrentHashMap<String, MacroValue> values = new ConcurrentHashMap<>();

    public void clear() {
        values.clear();
    }

    public void set(String name, MacroValue value) {
        String key = cleanRootName(name);
        if (key.isEmpty() || value == null) return;
        values.put(key, value);
    }

    public void setAll(Map<String, MacroValue> additions) {
        if (additions == null) return;
        additions.forEach(this::set);
    }

    public Optional<MacroValue> get(String name) {
        String key = cleanName(name);
        if (key.isEmpty()) return Optional.empty();
        int dot = key.indexOf('.');
        String root = dot < 0 ? key : key.substring(0, dot);
        MacroValue value = values.get(root);
        if (value == null) return Optional.empty();
        return dot < 0 ? Optional.of(value) : value.property(key.substring(dot + 1));
    }

    public void remove(String name) {
        String key = cleanName(name);
        int dot = key.indexOf('.');
        values.remove(dot < 0 ? key : key.substring(0, dot));
    }

    public Map<String, MacroValue> snapshot() {
        return Map.copyOf(new LinkedHashMap<>(values));
    }

    public static String cleanName(String name) {
        if (name == null) return "";
        String cleaned = name.trim().replace("{", "").replace("}", "").toLowerCase(Locale.ROOT);
        return cleaned.matches("[a-z_][a-z0-9_.-]{0,63}") ? cleaned : "";
    }

    public static String cleanRootName(String name) {
        String cleaned = cleanName(name);
        return cleaned.contains(".") ? "" : cleaned;
    }
}
