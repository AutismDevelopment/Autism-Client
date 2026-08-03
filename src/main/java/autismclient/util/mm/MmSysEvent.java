package autismclient.util.mm;

import autismclient.util.mm.crypto.MmCrypto;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;

public final class MmSysEvent {
    public final long rv;
    public final int ke;
    public final int cke;
    public final long ts;
    private final String nB64;
    private final String ctB64;
    private final String sigB64;

    private MmSysEvent(long rv, int ke, int cke, long ts, String nB64, String ctB64, String sigB64) {
        this.rv = rv; this.ke = ke; this.cke = cke; this.ts = ts;
        this.nB64 = nB64; this.ctB64 = ctB64; this.sigB64 = sigB64;
    }

    public record Body(String t, String did, String fp, String name, int term, String hostDid, String hostFp,
                       boolean admin, boolean speaker, boolean allowed, int speakerVersion,
                       String contentTopic, String controlTopic, String role) {}

    public static MmSysEvent parse(byte[] payload) {
        try {
            JsonObject o = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.get("v").getAsInt() != 1) return null;
            return new MmSysEvent(o.get("rv").getAsLong(), o.get("ke").getAsInt(), o.get("cke").getAsInt(),
                o.get("ts").getAsLong(), o.get("n").getAsString(), o.get("ct").getAsString(),
                o.get("sig").getAsString());
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean verify(PublicKey serverKey, String lobbyTopic) {
        try {
            byte[] msg = (aad(lobbyTopic) + "|" + ctB64).getBytes(StandardCharsets.UTF_8);
            return MmCrypto.ed25519Verify(serverKey, msg, Base64.getDecoder().decode(sigB64));
        } catch (Throwable t) {
            return false;
        }
    }

    public Body open(byte[] key32, String lobbyTopic) {
        try {
            byte[] pt = MmCrypto.aesGcmOpen(key32, Base64.getDecoder().decode(nB64),
                Base64.getDecoder().decode(ctB64), aad(lobbyTopic).getBytes(StandardCharsets.UTF_8));
            if (pt == null) return null;
            JsonObject o = JsonParser.parseString(new String(pt, StandardCharsets.UTF_8)).getAsJsonObject();
            return new Body(str(o, "t"), str(o, "did"), str(o, "fp"), str(o, "name"),
                o.has("term") ? o.get("term").getAsInt() : 0, str(o, "hostDid"), str(o, "hostFp"),
                bool(o, "admin"), bool(o, "speaker"), bool(o, "allowed"),
                o.has("speakerVersion") ? o.get("speakerVersion").getAsInt() : 0,
                str(o, "contentTopic"), str(o, "controlTopic"), str(o, "role"));
        } catch (Throwable t) {
            return null;
        }
    }

    private String aad(String lobbyTopic) {
        return "mmsys|1|" + lobbyTopic + "|" + rv + "|" + ke + "|" + cke + "|" + ts + "|" + nB64;
    }

    private static String str(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; } catch (Throwable t) { return ""; }
    }

    private static boolean bool(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean(); } catch (Throwable t) { return false; }
    }
}
