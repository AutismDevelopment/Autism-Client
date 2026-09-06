package autismclient.util.mm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ServerClock {
    private record Anchor(long serverMs, long nanos) {}

    private static volatile Anchor anchor;

    private ServerClock() {}

    public static void adopt(String jwt) {
        long iatMs = claimMs(jwt, "iat");
        if (iatMs > 0) anchor = new Anchor(iatMs, System.nanoTime());
    }

    public static void adoptServerTime(long serverMs) {
        if (serverMs > 0) anchor = new Anchor(serverMs, System.nanoTime());
    }

    public static long nowMs() {
        Anchor a = anchor;
        return a == null ? System.currentTimeMillis()
                         : a.serverMs + (System.nanoTime() - a.nanos) / 1_000_000L;
    }

    private static long claimMs(String jwt, String name) {
        if (jwt == null || jwt.isEmpty()) return 0;
        try {
            int d1 = jwt.indexOf('.'), d2 = jwt.indexOf('.', d1 + 1);
            if (d1 <= 0 || d2 <= d1) return 0;
            byte[] payload = Base64.getUrlDecoder().decode(jwt.substring(d1 + 1, d2));
            JsonObject o = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has(name) ? o.get(name).getAsLong() * 1000L : 0;
        } catch (Throwable t) {
            return 0;
        }
    }
}
