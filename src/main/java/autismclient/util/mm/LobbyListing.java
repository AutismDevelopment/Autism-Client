package autismclient.util.mm;

import autismclient.util.mm.crypto.MmIdentity;

public final class LobbyListing {

    public static final java.util.Comparator<LobbyListing> DIRECTORY_ORDER = java.util.Comparator
        .comparing((LobbyListing l) -> !l.official)
        .thenComparing(l -> l.name, String.CASE_INSENSITIVE_ORDER);

    public final String lobbyId;
    public volatile String name;
    public volatile int members;
    public volatile int maxMembers;
    public volatile boolean serverShared;

    public volatile boolean official;
    public volatile String server = "";
    public volatile String plugins = "";
    public volatile int hostDupe = -1;
    public volatile boolean announcement;
    public final String hostFpHex;
    public final String hostFingerprint;
    public volatile String hostDid = "";
    public volatile long lastSeenMs;

    public LobbyListing(String lobbyId, byte[] hostFp) {
        this.lobbyId = lobbyId;
        this.hostFpHex = hex(hostFp);
        this.hostFingerprint = MmIdentity.formatFingerprint(hostFp);
    }

    public boolean isFull() { return maxMembers > 0 && members >= maxMembers; }
    public boolean isFresh(long now) { return now - lastSeenMs < 45_000; }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
