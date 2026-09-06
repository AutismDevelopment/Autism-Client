package autismclient.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ModuleCategory {
    private static final List<ModuleCategory> CATEGORIES = new ArrayList<>();

    public static final ModuleCategory COMBAT = register("Combat");
    public static final ModuleCategory MOVEMENT = register("Movement");
    public static final ModuleCategory PLAYER = register("Player");
    public static final ModuleCategory MISC = register("Misc");
    public static final ModuleCategory RENDER = register("Render");

    private final String key;
    private final String label;
    private final boolean addon;

    private ModuleCategory(String key, String label, boolean addon) {
        this.key = key;
        this.label = label;
        this.addon = addon;
    }

    public static ModuleCategory register(String label) {
        return registerInternal(toKey(label), label, false);
    }

    public static ModuleCategory registerAddon(String addonId, String label) {
        String ns = addonId == null || addonId.isBlank() ? "addon" : addonId;
        String safeLabel = label == null || label.isBlank() ? ns : label;
        String key = toKey("ADDON " + ns);
        return registerInternal(key, safeLabel, true);
    }

    private static synchronized ModuleCategory registerInternal(String key, String label, boolean addon) {
        for (ModuleCategory existing : CATEGORIES) {
            if (existing.key.equals(key)) return existing;
        }
        ModuleCategory category = new ModuleCategory(key, label, addon);
        CATEGORIES.add(category);
        return category;
    }

    public boolean isAddon() {
        return addon;
    }

    private static String toKey(String label) {
        return label == null ? "" : label.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
    }

    public static List<ModuleCategory> values() {
        return Collections.unmodifiableList(new ArrayList<>(CATEGORIES));
    }

    public String name() {
        return key;
    }

    public String label() {
        return label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModuleCategory that)) return false;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return label;
    }
}
