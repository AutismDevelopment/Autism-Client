package autismclient.util.macro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

final class MacroStringList {
    private MacroStringList() {}

    static ListTag toTag(List<String> values) {
        ListTag list = new ListTag();
        if (values == null) return list;
        for (String value : values) {
            if (value != null && !value.isBlank()) list.add(StringTag.valueOf(value));
        }
        return list;
    }

    static ArrayList<String> fromTag(ListTag list) {
        ArrayList<String> values = new ArrayList<>();
        if (list == null) return values;
        for (int i = 0; i < list.size(); i++) {
            String value = list.getString(i).orElse("");
            if (!value.isBlank()) values.add(value);
        }
        return values;
    }

    static <E extends Enum<E>> E enumValue(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
