package autismclient.util.mm;

public final class MmPeer {
    public final String fpHex;
    public final String fingerprint;
    public volatile String nickname = "";
    public volatile long lastSeenMs;

    public volatile boolean serverShared;
    public volatile String serverName = "";
    public volatile String serverIp = "";
    public volatile boolean hasLocation;
    public volatile String dimension = "";
    public volatile double x, y, z;

    public volatile int dupeStatus = -1;

    public volatile String discordId = "";
    public volatile String discordUser = "";
    public volatile boolean admin;
    public volatile boolean speaker;

    public volatile String role = "user";

    public volatile boolean muted;

    public MmPeer(byte[] senderFp) {
        this.fpHex = hex(senderFp);
        this.fingerprint = formatFingerprint(fpHex);
    }

    public boolean isOnline(long now) { return now - lastSeenMs < 30_000; }

    public String displayName() {
        String discord = discordUser == null ? "" : discordUser.trim();
        return discord.isEmpty() ? "Discord member" : discord;
    }

    public String commandName() {
        return nickname == null ? "" : nickname.trim();
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }

    private static String formatFingerprint(String hex) {
        String upper = hex.toUpperCase(java.util.Locale.ROOT);
        StringBuilder out = new StringBuilder(upper.length() + upper.length() / 4);
        for (int i = 0; i < upper.length(); i += 4) {
            if (i > 0) out.append('-');
            out.append(upper, i, Math.min(upper.length(), i + 4));
        }
        return out.toString();
    }
}
