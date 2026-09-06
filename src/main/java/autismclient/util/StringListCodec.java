package autismclient.util;

import java.util.ArrayList;
import java.util.List;

public final class StringListCodec {
    private StringListCodec() {
    }

    public static List<String> parse(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.isBlank()) return out;
        for (String raw : value.split("\\|")) {
            String item = raw.trim();
            if (!item.isEmpty()) out.add(item);
        }
        return out;
    }

    public static String encode(List<String> values) {
        return values == null ? "" : String.join("|", values);
    }
}
