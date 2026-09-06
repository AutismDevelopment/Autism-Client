package autismclient.api.custommenu;

import java.util.List;

public record CustomMenuInput(
    int index,
    String key,
    String label,
    Kind kind,
    String initialValue,
    int maxLength,
    double min,
    double max,
    double step,
    List<String> options
) {
    public enum Kind { TEXT, BOOLEAN, NUMBER, OPTION }

    public CustomMenuInput {
        key = key == null ? "" : key;
        label = label == null ? "" : label;
        kind = kind == null ? Kind.TEXT : kind;
        initialValue = initialValue == null ? "" : initialValue;
        options = options == null ? List.of() : List.copyOf(options);
    }
}
