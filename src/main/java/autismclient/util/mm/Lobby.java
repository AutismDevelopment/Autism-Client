package autismclient.util.mm;

import autismclient.util.mm.crypto.RoomKey;

public final class Lobby {
    public volatile RoomKey key;
    public final String lobbyId;
    public final String name;
    public final boolean isPublic;
    public final int maxPlayers;
    public final long createdMs;
    public final String server;
    public final String plugins;
    public final String shareCode;
    public final boolean announcement;
    public final String ownerDid;
    public volatile int speakerVersion;
    public volatile boolean canSend;

    public Lobby(RoomKey key, String name, boolean isPublic, int maxPlayers,
                 String server, String plugins, String shareCode, boolean announcement, String ownerDid,
                 int speakerVersion, boolean canSend) {
        this.key = key;
        this.lobbyId = key.lobbyId();
        this.name = name;
        this.isPublic = isPublic;
        this.maxPlayers = maxPlayers;
        this.createdMs = System.currentTimeMillis();
        this.server = server == null ? "" : server;
        this.plugins = plugins == null ? "" : plugins;
        this.shareCode = shareCode == null ? "" : shareCode;
        this.announcement = announcement;
        this.ownerDid = ownerDid == null ? "" : ownerDid;
        this.speakerVersion = speakerVersion;
        this.canSend = canSend;
    }

    public String topic() { return key.topic(); }
    public String controlTopic() { return key.controlTopic(); }
    public String sysTopic() { return key.sysTopic(); }
    public void adoptKey(RoomKey updated) { if (updated != null) key = updated; }
}
