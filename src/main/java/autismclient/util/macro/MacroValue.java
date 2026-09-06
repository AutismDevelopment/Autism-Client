package autismclient.util.macro;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MacroValue {
    public enum Kind { TEXT, NUMBER, BOOLEAN, IDENTIFIER, ITEM, SLOT, ENTITY, PACKET, POSITION, GUI }

    private final Kind kind;
    private final String value;
    private final Map<String, MacroValue> properties;

    private MacroValue(Kind kind, String value, Map<String, MacroValue> properties) {
        this.kind = kind == null ? Kind.TEXT : kind;
        this.value = sanitize(value);
        this.properties = properties == null || properties.isEmpty()
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static MacroValue text(Object value) {
        return new MacroValue(Kind.TEXT, value == null ? "" : String.valueOf(value), Map.of());
    }

    public static MacroValue number(Number value) {
        String rendered = value == null ? "0" : new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        return new MacroValue(Kind.NUMBER, rendered, Map.of());
    }

    public static MacroValue identifier(String value) {
        return new MacroValue(Kind.IDENTIFIER, value, Map.of());
    }

    public static MacroValue slot(int value) {
        return new MacroValue(Kind.SLOT, Integer.toString(value), Map.of());
    }

    public static MacroValue structured(Kind kind, String value, Map<String, MacroValue> properties) {
        return new MacroValue(kind, value, properties);
    }

    public Kind kind() {
        return kind;
    }

    public String value() {
        return value;
    }

    public Map<String, MacroValue> properties() {
        return properties;
    }

    public Optional<MacroValue> property(String path) {
        if (path == null || path.isBlank()) return Optional.of(this);
        MacroValue current = this;
        for (String segment : path.split("\\.")) {
            if (segment.isBlank()) continue;
            current = current.properties.get(segment.toLowerCase(Locale.ROOT));
            if (current == null) return Optional.empty();
        }
        return Optional.of(current);
    }

    public MacroValue withProperty(String name, MacroValue property) {
        if (name == null || name.isBlank() || property == null) return this;
        Map<String, MacroValue> copy = new LinkedHashMap<>(properties);
        copy.put(name.trim().toLowerCase(Locale.ROOT), property);
        return new MacroValue(kind, value, copy);
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder out = new StringBuilder(Math.min(raw.length(), 4096));
        for (int offset = 0; offset < raw.length() && out.length() < 4096;) {
            int cp = raw.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp == '\n' || cp == '\r' || Character.isISOControl(cp)) {
                out.append(' ');
            } else {
                out.appendCodePoint(cp);
            }
        }
        return out.toString().trim();
    }
}
