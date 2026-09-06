package autismclient.api.custommenu;

public record CustomMenuButton(int index, String label, String actionId, Kind kind, String labelColor) {
    public enum Kind { CUSTOM, COMMAND, DIALOG, URL, CLIPBOARD, OTHER, EMPTY }

    public CustomMenuButton(int index, String label, String actionId, Kind kind) {
        this(index, label, actionId, kind, "");
    }

    public CustomMenuButton {
        label = label == null ? "" : label;
        actionId = actionId == null ? "" : actionId;
        kind = kind == null ? Kind.OTHER : kind;
        labelColor = labelColor == null ? "" : labelColor;
    }

    public boolean serverRelevant() {
        return kind == Kind.CUSTOM || kind == Kind.COMMAND || kind == Kind.DIALOG;
    }
}
