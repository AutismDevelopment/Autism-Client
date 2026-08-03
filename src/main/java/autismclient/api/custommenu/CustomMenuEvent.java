package autismclient.api.custommenu;

public record CustomMenuEvent(Type type, CustomMenuSnapshot snapshot) {
    public enum Type { NONE, OPEN, CLEAR }

    public static final CustomMenuEvent NONE = new CustomMenuEvent(Type.NONE, null);
    public static final CustomMenuEvent CLEAR = new CustomMenuEvent(Type.CLEAR, null);

    public static CustomMenuEvent open(CustomMenuSnapshot snapshot) {
        return new CustomMenuEvent(Type.OPEN, snapshot);
    }
}
