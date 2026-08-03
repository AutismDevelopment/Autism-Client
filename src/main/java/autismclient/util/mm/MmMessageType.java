package autismclient.util.mm;

public enum MmMessageType {
    PRESENCE(1),
    CHAT(2),
    MACRO_OFFER(3),
    COMMAND_OFFER(4),
    PACKET_OFFER(5),
    LOCATION(6),
    LEAVE(7),
    BLOB_OFFER(9),
    KICK(10),
    RECEIPT(16);

    public final int id;
    MmMessageType(int id) { this.id = id; }

    public static MmMessageType byId(int id) {
        for (MmMessageType t : values()) if (t.id == id) return t;
        return null;
    }

    public boolean isControl() {
        return switch (this) {
            case PRESENCE, LEAVE, KICK -> true;

            case CHAT, MACRO_OFFER, COMMAND_OFFER, PACKET_OFFER, LOCATION, BLOB_OFFER, RECEIPT -> false;
        };
    }

    public boolean usesControlTopic() {
        return switch (this) {
            case PRESENCE, LEAVE, KICK, RECEIPT -> true;
            case CHAT, MACRO_OFFER, COMMAND_OFFER, PACKET_OFFER, LOCATION, BLOB_OFFER -> false;
        };
    }

    public boolean isUserContent() { return !usesControlTopic(); }

    public boolean isDurable() {
        return switch (this) {
            case CHAT, MACRO_OFFER, COMMAND_OFFER, PACKET_OFFER, BLOB_OFFER, KICK, LEAVE -> true;
            case PRESENCE, LOCATION, RECEIPT -> false;
        };
    }
}
