package autismclient.api.custommenu;

import java.util.Map;

public record CustomMenuSubmission(Map<String, String> values, CustomMenuButton button) {
    public CustomMenuSubmission {
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
