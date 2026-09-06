package autismclient.util.macro;

public enum PacketOrder {
    INSTANT,
    GRIM;

    public static PacketOrder parse(String raw, PacketOrder fallback) {
        if (raw == null) return fallback;
        switch (raw.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "INSTANT": return INSTANT;
            case "GRIM":    return GRIM;
            default:        return fallback;
        }
    }
}
