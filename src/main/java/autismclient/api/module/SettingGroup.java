package autismclient.api.module;

public final class SettingGroup {
    private final String name;

    public SettingGroup(String name) {
        this.name = name == null || name.isBlank() ? "General" : name;
    }

    public String name() {
        return name;
    }

    public <T, S extends Setting<T, S>> S apply(S setting) {
        if (setting != null) setting.group(name);
        return setting;
    }
}
