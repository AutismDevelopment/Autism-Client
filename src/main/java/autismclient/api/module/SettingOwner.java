package autismclient.api.module;

public interface SettingOwner {

    String settingValue(String name);

    void putSettingValue(String name, String value);
}
