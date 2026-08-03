package autismclient.util;

import autismclient.util.mm.MmPrefs;
import autismclient.util.mm.ServerClock;
import autismclient.util.mm.crypto.AtRestSeal;
import autismclient.util.mm.crypto.MmCrypto;
import autismclient.util.mm.crypto.MmIdentity;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.util.Util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class AutismDiscordLogin {

    public static final String CLIENT_ID = "1520738158457655407";
    public static final String AUTH_BASE = "https://auth.autismclient.com";
    private static final int PORT = 9676;
    private static final String REDIRECT = "http://127.0.0.1:" + PORT + "/cb";
    private static final String VERSION = modVersion();
    private static final long REFRESH_SKEW_MS = 60_000;

    private static volatile HttpServer server;
    private static volatile Consumer<String> signInDone;

    private static volatile String jwt = "";
    private static volatile long jwtExpMs = 0;
    private static volatile boolean admin;

    private static volatile String role = "user";

    private static volatile String authGateNotice = "";
    private static volatile String minVersion = "";

    private AutismDiscordLogin() {}

    public static String authGateNotice() { return authGateNotice; }

    public static String requiredMinVersion() { return minVersion; }

    public static String modVersionString() { return VERSION; }

    public static boolean isAuthed() { return currentJwt() != null; }

    public static boolean hasSession() {
        String s = MmPrefs.get().discordSession();
        return s != null && !s.isEmpty();
    }

    public static String displayName() { return MmPrefs.get().discordName(); }

    public static boolean isAdmin() { return admin; }

    public static String role() { return role; }

    public static void adoptServerRole(String newRole) {
        role = switch (newRole == null ? "" : newRole) {
            case "god", "admin", "gold", "aqua", "notbroke", "blue" -> newRole;
            default -> "user";
        };
    }

    public static String currentIdToken() { return MmPrefs.get().discordIdToken(); }

    private static String selfPubKeyB64() {
        try { return Base64.getEncoder().encodeToString(MmIdentity.get().publicKeySpki()); }
        catch (Throwable t) { return ""; }
    }

    public static String errorMessage(String code) {
        return switch (code == null ? "" : code) {
            case "not_member" -> "Join our Discord first.";
            case "banned" -> "You're Discord banned.";

            case "old_version" -> "Update needed. autismclient.com";
            case "rate_limited" -> "Too many tries. Wait a bit.";
            case "bad_session", "expired_session", "proof_required" -> "Signed out. Sign in again.";
            case "warming_up" -> "Server starting up. Retry soon.";
            case "cancelled" -> "Sign-in cancelled.";
            case "bad_code", "bad_request" -> "Sign-in failed. Try again.";
            default -> "Can't reach login. Try again.";
        };
    }

    public static boolean isGateCode(String code) {
        return "old_version".equals(code);
    }

    private static void noteGate(JsonObject r, boolean success) {
        if (success) { authGateNotice = ""; return; }
        if (r == null || !r.has("error")) return;
        String code = r.get("error").getAsString();
        if (isGateCode(code)) {
            authGateNotice = code;
            if (r.has("minVersion")) minVersion = r.get("minVersion").getAsString();
        }
    }

    public static synchronized void signIn(Consumer<String> done) {
        signInDone = done;
        startServer();
        String url = "https://discord.com/oauth2/authorize"
            + "?client_id=" + CLIENT_ID
            + "&response_type=code"
            + "&redirect_uri=" + enc(REDIRECT)
            + "&scope=identify"
            + "&prompt=consent";
        Util.getPlatform().openUri(url);
    }

    public static synchronized void signOut() {
        jwt = ""; jwtExpMs = 0; admin = false; role = "user";
        MmPrefs.get().clearDiscord();
        lastDefinitiveCode = "";

        autismclient.util.mm.MatchmakingManager.get().resetAutoJoinAttempt();

        try {
            autismclient.util.mm.MatchmakingManager.get().closeRelays();
        } catch (Throwable ignored) {  }
    }

    public static synchronized void adoptJwt(String newJwt, long expSeconds) {
        if (newJwt != null && !newJwt.isEmpty()) {
            jwt = newJwt;
            jwtExpMs = expSeconds * 1000L;
            admin = booleanClaim(newJwt, "admin");
            ServerClock.adopt(newJwt);
        }
    }

    public static String currentDid() {
        String j = jwt;
        if (j == null || j.isEmpty()) return "";
        try {
            int d1 = j.indexOf('.'), d2 = j.indexOf('.', d1 + 1);
            if (d1 <= 0 || d2 <= d1) return "";
            byte[] payload = Base64.getUrlDecoder().decode(j.substring(d1 + 1, d2));
            com.google.gson.JsonObject o = com.google.gson.JsonParser
                .parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has("sub") ? o.get("sub").getAsString() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    public static String cachedJwt() {
        String j = jwt;
        return (j == null || j.isEmpty()) ? null : j;
    }

    public static String currentJwt() { return freshJwt(REFRESH_SKEW_MS); }

    public static String freshJwt(long minTtlMs) {
        String session, before;
        synchronized (AutismDiscordLogin.class) {

            long now = ServerClock.nowMs();
            if (!jwt.isEmpty() && now < jwtExpMs - minTtlMs) return jwt;
            session = unsealedSession();
            if (session == null) return jwt.isEmpty() || now >= jwtExpMs ? null : jwt;
            before = jwt;
        }

        JsonObject r = null;
        int status = -1;
        try {
            String form = "session=" + enc(session) + "&version=" + enc(VERSION) + "&pubkey=" + enc(selfPubKeyB64())
                + AutismAttest.formSuffix(AUTH_BASE, VERSION);
            Map<String, String> proof = proofHeaders("POST", "/token", form);
            if (proof.isEmpty()) {

                synchronized (AutismDiscordLogin.class) {
                    long now = ServerClock.nowMs();
                    return jwt.isEmpty() || now >= jwtExpMs ? null : jwt;
                }
            }
            AutismHttp.JsonResult result = AutismHttp.postFormResult(AUTH_BASE + "/token", form, proof);
            status = result.status();
            r = result.body();

            if ((status == 401 || (r != null && r.has("error") && "proof_required".equals(r.get("error").getAsString())))
                && r != null && r.has("serverTime")) {
                ServerClock.adoptServerTime(r.get("serverTime").getAsLong());
                try {
                    String retryForm = "session=" + enc(session) + "&version=" + enc(VERSION)
                        + "&pubkey=" + enc(selfPubKeyB64()) + AutismAttest.formSuffix(AUTH_BASE, VERSION);
                    Map<String, String> retryProof = proofHeaders("POST", "/token", retryForm);
                    if (!retryProof.isEmpty()) {
                        AutismHttp.JsonResult retry = AutismHttp.postFormResult(AUTH_BASE + "/token",
                            retryForm, retryProof);
                        status = retry.status();
                        r = retry.body();
                    }
                } catch (Throwable ignored) {  }
            }
        } catch (Throwable ignored) {  }
        synchronized (AutismDiscordLogin.class) {
            long now = ServerClock.nowMs();

            if (!jwt.equals(before) && !jwt.isEmpty() && now < jwtExpMs - minTtlMs) return jwt;
            if (status == 200 && r != null && r.has("jwt") && r.has("exp")) {
                jwt = r.get("jwt").getAsString();
                jwtExpMs = r.get("exp").getAsLong() * 1000L;
                admin = r.has("admin") && r.get("admin").getAsBoolean();
                if (r.has("role")) adoptServerRole(r.get("role").getAsString());
                ServerClock.adopt(jwt);
                noteGate(r, true);
                lastDefinitiveCode = "";
                if (r.has("idtoken")) MmPrefs.get().setDiscordIdToken(r.get("idtoken").getAsString());
                return jwt;
            }

            if (status == 200 && r != null && r.has("error")) {
                String code = r.get("error").getAsString();
                noteGate(r, false);
                if (isDefinitiveRevocation(code) && confirmDefinitive(code, now)) {
                    AutismNotifications.show(errorMessage(code), 0xFFFF5B5B);
                    signOut();
                }
            }
            return jwt.isEmpty() || now >= jwtExpMs ? null : jwt;
        }
    }

    private static volatile String lastDefinitiveCode = "";
    private static volatile long firstDefinitiveAtMs = 0L;

    private static boolean confirmDefinitive(String code, long now) {
        String bucket = "expired_session".equals(code) ? "bad_session" : code;
        if (bucket.equals(lastDefinitiveCode) && now - firstDefinitiveAtMs >= 5_000L) {
            lastDefinitiveCode = "";
            firstDefinitiveAtMs = 0L;
            return true;
        }
        if (!bucket.equals(lastDefinitiveCode)) {
            lastDefinitiveCode = bucket;
            firstDefinitiveAtMs = now;
        }
        return false;
    }

    private static boolean isDefinitiveRevocation(String code) {
        return "bad_session".equals(code) || "expired_session".equals(code)
            || "banned".equals(code) || "not_member".equals(code);
    }

    private static String unsealedSession() {
        String sealedB64 = MmPrefs.get().discordSession();
        if (sealedB64 == null || sealedB64.isEmpty()) return null;
        try {
            byte[] plain = AtRestSeal.unseal(Base64.getDecoder().decode(sealedB64));
            if (plain != null) return new String(plain, StandardCharsets.UTF_8);
        } catch (Throwable ignored) { }

        MmPrefs.get().clearDiscord();
        return null;
    }

    private static void startServer() {
        if (server != null) return;
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            s.createContext("/", AutismDiscordLogin::handle);
            s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            s.start();
            server = s;
        } catch (IOException e) {
            stopServer();
            finish("network");
        }
    }

    private static void stopServer() {
        HttpServer s = server;
        server = null;
        if (s != null) s.stop(0);
    }

    private static void handle(HttpExchange ex) throws IOException {
        String code = null;
        String q = ex.getRequestURI().getRawQuery();
        if (q != null) for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && "code".equals(part.substring(0, eq))) { code = dec(part.substring(eq + 1)); break; }
        }
        String err = code == null ? "cancelled" : exchangeCode(code);
        write(ex, err.isEmpty() ? "Signed in. You can close this page and return to the game."
                                : "Sign-in failed. Return to the game to see why.");
        stopServer();
        finish(err);
    }

    private static String exchangeCode(String code) {
        try {
            JsonObject r = AutismHttp.postForm(AUTH_BASE + "/login",
                "code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT) + "&version=" + enc(VERSION)
                    + "&pubkey=" + enc(selfPubKeyB64()) + AutismAttest.formSuffix(AUTH_BASE, VERSION));
            if (r == null) return "network";
            if (r.has("error")) { noteGate(r, false); return r.get("error").getAsString(); }
            if (!r.has("session") || !r.has("jwt") || !r.has("exp")) return "network";
            byte[] sealed = AtRestSeal.seal(r.get("session").getAsString().getBytes(StandardCharsets.UTF_8));
            String name = r.has("name") ? r.get("name").getAsString() : "Discord user";
            String idToken = r.has("idtoken") ? r.get("idtoken").getAsString() : "";
            MmPrefs.get().setDiscord(sealed == null ? "" : Base64.getEncoder().encodeToString(sealed), name, idToken);
            jwt = r.get("jwt").getAsString();
            jwtExpMs = r.get("exp").getAsLong() * 1000L;
            admin = r.has("admin") && r.get("admin").getAsBoolean();
            if (r.has("role")) adoptServerRole(r.get("role").getAsString());
            ServerClock.adopt(jwt);
            noteGate(r, true);
            return "";
        } catch (Throwable t) { return "network"; }
    }

    private static void finish(String error) {
        Consumer<String> cb = signInDone;
        signInDone = null;
        if (cb != null) try { cb.accept(error == null ? "" : error); } catch (Throwable ignored) {  }
    }

    public static Map<String, String> proofHeaders(String method, String path, String body) {
        try {
            String verb = method == null ? "GET" : method.toUpperCase(java.util.Locale.ROOT);
            String requestPath = path == null || path.isBlank() ? "/" : path;
            byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
            long timestamp = ServerClock.nowMs();
            String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(MmCrypto.randomBytes(18));
            String digest = MmCrypto.hex(MmCrypto.sha256(bytes));
            String canonical = "MM-POP-V1\n" + verb + "\n" + requestPath + "\n" + digest + "\n"
                + timestamp + "\n" + nonce;
            String signature = Base64.getEncoder().encodeToString(
                MmIdentity.get().sign(canonical.getBytes(StandardCharsets.UTF_8)));
            return Map.of("X-MM-Time", Long.toString(timestamp), "X-MM-Nonce", nonce, "X-MM-Signature", signature);
        } catch (Throwable t) {
            return Map.of();
        }
    }

    private static boolean booleanClaim(String token, String name) {
        if (token == null || token.isEmpty()) return false;
        try {
            int d1 = token.indexOf('.'), d2 = token.indexOf('.', d1 + 1);
            byte[] payload = Base64.getUrlDecoder().decode(token.substring(d1 + 1, d2));
            JsonObject o = com.google.gson.JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has(name) && o.get(name).getAsBoolean();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String modVersion() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("autism")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        } catch (Throwable t) { return "unknown"; }
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static String dec(String s) { return URLDecoder.decode(s, StandardCharsets.UTF_8); }

    private static void write(HttpExchange ex, String text) throws IOException {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, b.length);
        try (var o = ex.getResponseBody()) { o.write(b); }
    }
}
