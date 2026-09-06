package autismclient.api.module;

public final class BoolSetting extends Setting<Boolean, BoolSetting> {
    public BoolSetting(String name, String title, boolean defaultValue) {
        super(Kind.BOOLEAN, name, title, defaultValue);
    }

    @Override
    protected Boolean decode(String raw) {
        return raw != null && Boolean.parseBoolean(raw.trim());
    }

    @Override
    protected String encode(Boolean value) {
        return Boolean.toString(value != null && value);
    }
}
