package autismclient.api.module;

import java.util.ArrayList;
import java.util.List;

public final class RegistryListSetting extends ListSetting<RegistryListSetting> {
    private final boolean placeableBlocksOnly;

    private RegistryListSetting(Kind kind, String name, String title, String defaultValue) {
        this(kind, name, title, defaultValue, false);
    }

    private RegistryListSetting(Kind kind, String name, String title, String defaultValue,
                                boolean placeableBlocksOnly) {
        super(registryKindOrDefault(kind), name, title, parseDefault(defaultValue));
        this.placeableBlocksOnly = placeableBlocksOnly && kind == Kind.BLOCK_LIST;
    }

    public static RegistryListSetting items(String name, String title) {
        return items(name, title, "");
    }

    public static RegistryListSetting items(String name, String title, String defaultValue) {
        return new RegistryListSetting(Kind.ITEM_LIST, name, title, defaultValue);
    }

    public static RegistryListSetting blocks(String name, String title) {
        return blocks(name, title, "");
    }

    public static RegistryListSetting blocks(String name, String title, String defaultValue) {
        return new RegistryListSetting(Kind.BLOCK_LIST, name, title, defaultValue);
    }

    public static RegistryListSetting placeableBlocks(String name, String title) {
        return placeableBlocks(name, title, "");
    }

    public static RegistryListSetting placeableBlocks(String name, String title, String defaultValue) {
        return new RegistryListSetting(Kind.BLOCK_LIST, name, title, defaultValue, true);
    }

    public boolean placeableBlocksOnly() {
        return placeableBlocksOnly;
    }

    public static RegistryListSetting entityTypes(String name, String title) {
        return entityTypes(name, title, "");
    }

    public static RegistryListSetting entityTypes(String name, String title, String defaultValue) {
        return new RegistryListSetting(Kind.ENTITY_TYPE_LIST, name, title, defaultValue);
    }

    public static RegistryListSetting soundEvents(String name, String title) {
        return soundEvents(name, title, "");
    }

    public static RegistryListSetting soundEvents(String name, String title, String defaultValue) {
        return new RegistryListSetting(Kind.SOUND_EVENT_LIST, name, title, defaultValue);
    }

    public static RegistryListSetting storages(String name, String title) {
        return storages(name, title, "");
    }

    public static RegistryListSetting storages(String name, String title, String defaultValue) {
        return new RegistryListSetting(Kind.STORAGE_LIST, name, title, defaultValue);
    }

    private static List<String> parseDefault(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : raw.split("\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return List.copyOf(out);
    }

    private static Kind registryKindOrDefault(Kind kind) {
        return switch (kind == null ? Kind.ITEM_LIST : kind) {
            case ITEM_LIST, BLOCK_LIST, ENTITY_TYPE_LIST, SOUND_EVENT_LIST, STORAGE_LIST -> kind;
            default -> Kind.ITEM_LIST;
        };
    }
}
