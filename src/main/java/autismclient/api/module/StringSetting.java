package autismclient.api.module;

public final class StringSetting extends Setting<String, StringSetting> {
    public StringSetting(String name, String title, String defaultValue) {
        super(Kind.STRING, name, title, defaultValue == null ? "" : defaultValue);
    }

    @Override
    protected String decode(String raw) {
        return raw == null ? "" : raw;
    }

    @Override
    protected String encode(String value) {
        return value == null ? "" : value;
    }
}
