package autismclient.api.custommenu;

public record CustomMenuButton(int index, String label, String actionId, Kind kind) {
    public enum Kind { CUSTOM, COMMAND, DIALOG, URL, CLIPBOARD, OTHER, EMPTY }

    public CustomMenuButton {
        label = label == null ? "" : label;
        actionId = actionId == null ? "" : actionId;
        kind = kind == null ? Kind.OTHER : kind;
    }

    public boolean serverRelevant() {
        return kind == Kind.CUSTOM || kind == Kind.COMMAND || kind == Kind.DIALOG;
    }
}
