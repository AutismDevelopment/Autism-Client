package autismclient.util.mm;

import autismclient.util.AutismDiscordLogin;
import autismclient.util.AutismHttp;
import autismclient.util.mm.crypto.MmCrypto;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;

public final class MmDiscordProof {
    private MmDiscordProof() {}

    public record Identity(String id, String username) {}

    private static volatile PublicKey serverKey;
    private static volatile long lastFetchMs;
    private static final long REFETCH_MS = 60_000;

    public static boolean serverKeyReady() { return serverKey() != null; }

    public static PublicKey serverPublicKey() { return serverKey(); }

    private static PublicKey serverKey() {
        PublicKey k = serverKey;
        if (k != null) return k;
        long now = System.currentTimeMillis();
        if (now - lastFetchMs < REFETCH_MS) return null;
        lastFetchMs = now;
        try {
            JsonObject r = AutismHttp.getJson(AutismDiscordLogin.AUTH_BASE + "/idpubkey", null);
            if (r != null && r.has("pub")) {
                byte[] spki = Base64.getDecoder().decode(r.get("pub").getAsString());
                serverKey = MmCrypto.ed25519PublicFromSpki(spki);
            }
        } catch (Throwable ignored) {  }
        return serverKey;
    }

    public static Identity verify(String idtoken, byte[] senderSpki) {
        if (idtoken == null || idtoken.isEmpty() || senderSpki == null) return null;
        PublicKey key = serverKey();
        if (key == null) return null;
        try {
            int dot1 = idtoken.indexOf('.');
            int dot2 = idtoken.indexOf('.', dot1 + 1);
            if (dot1 <= 0 || dot2 <= dot1) return null;
            String signingInput = idtoken.substring(0, dot2);
            byte[] sig = Base64.getUrlDecoder().decode(idtoken.substring(dot2 + 1));
            if (!MmCrypto.ed25519Verify(key, signingInput.getBytes(StandardCharsets.US_ASCII), sig)) return null;

            JsonObject claims = claims(idtoken, dot1, dot2);
            long exp = claims.has("exp") ? claims.get("exp").getAsLong() : 0;
            if (exp * 1000L <= ServerClock.nowMs()) return null;
            String fp = claims.has("fp") ? claims.get("fp").getAsString() : "";
            if (!fp.equalsIgnoreCase(MmCrypto.hex(MmCrypto.sha256(senderSpki)))) return null;
            String did = claims.has("did") ? claims.get("did").getAsString() : "";
            if (did.isEmpty()) return null;
            String uname = claims.has("uname") ? claims.get("uname").getAsString() : "";
            return new Identity(did, uname);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String ownName() {
        String token = AutismDiscordLogin.currentIdToken();
        if (token == null || token.isEmpty()) return "";
        try {
            int dot1 = token.indexOf('.');
            int dot2 = token.indexOf('.', dot1 + 1);
            if (dot1 <= 0 || dot2 <= dot1) return "";
            JsonObject claims = claims(token, dot1, dot2);
            return claims.has("uname") ? claims.get("uname").getAsString() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static JsonObject claims(String jwt, int dot1, int dot2) {
        byte[] payload = Base64.getUrlDecoder().decode(jwt.substring(dot1 + 1, dot2));
        return JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
