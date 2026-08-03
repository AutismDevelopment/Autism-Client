package autismclient.util;

import java.util.Locale;

public final class MacroNames {
    private MacroNames() {
    }

    public static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean equal(String a, String b) {
        return key(a).equals(key(b));
    }

    public static boolean renamed(String before, String after) {
        if (before == null || after == null) return false;
        return !before.equals(after);
    }
}
