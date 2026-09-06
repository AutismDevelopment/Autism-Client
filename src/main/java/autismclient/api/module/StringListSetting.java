package autismclient.api.module;

import java.util.List;

public final class StringListSetting extends ListSetting<StringListSetting> {
    public StringListSetting(String name, String title) {
        super(Kind.STRING_LIST, name, title, List.of());
    }

    public StringListSetting(String name, String title, List<String> defaultValue) {
        super(Kind.STRING_LIST, name, title, defaultValue);
    }

    public StringListSetting(String name, String title, String defaultValue) {
        super(Kind.STRING_LIST, name, title, defaultValue);
    }
}
