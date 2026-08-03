package autismclient.api.module;

public final class KeybindSetting extends Setting<Integer, KeybindSetting> {
    public KeybindSetting(String name, String title, int defaultKey) {
        super(Kind.KEYBIND, name, title, defaultKey);
    }

    @Override
    protected Integer decode(String raw) {
        if (raw == null) return defaultValueTyped();
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return defaultValueTyped();
        }
    }

    @Override
    protected String encode(Integer value) {
        return Integer.toString(value == null ? defaultValueTyped() : value);
    }
}
