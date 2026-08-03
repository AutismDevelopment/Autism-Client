package autismclient.util;

import autismclient.AutismClientAddon;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.util.Util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class AutismMicrosoftLogin {

    private static final String DEFAULT_CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";
    public static final String CLIENT_ID = resolveClientId();
    public static final int PORT = 9675;

    private static final String AUTHORIZE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String SCOPE = "XboxLive.SignIn XboxLive.offline_access";
    private static volatile String codeVerifier = "";
    private static volatile String oauthState = "";

    private static String resolveClientId() {
        String prop = System.getProperty("autism.msauth.clientId");
        if (prop != null && !prop.isBlank()) return prop.trim();
        String env = System.getenv("AUTISM_MSAUTH_CLIENT_ID");
        if (env != null && !env.isBlank()) return env.trim();
        return DEFAULT_CLIENT_ID;
    }
    private static volatile HttpServer server;
    private static volatile Consumer<String> callback;

    private AutismMicrosoftLogin() {
    }

    public static String getRefreshToken(Consumer<String> callback) {
        AutismMicrosoftLogin.callback = callback;
        codeVerifier = newCodeVerifier();
        oauthState = newCodeVerifier();
        startServer();
        String url = AUTHORIZE_URL + "?client_id=" + CLIENT_ID
            + "&response_type=code"
            + "&redirect_uri=" + enc(redirectUri())
            + "&scope=" + enc(SCOPE)
            + "&code_challenge=" + enc(codeChallenge(codeVerifier))
            + "&code_challenge_method=S256"
            + "&state=" + enc(oauthState)
            + "&prompt=select_account";
        Util.getPlatform().openUri(url);
        return url;
    }

    private static final java.util.concurrent.Semaphore LOGIN_PERMITS = new java.util.concurrent.Semaphore(2, true);
    private static final Object LOGIN_SPACING = new Object();
    private static volatile long lastLoginStart;
    private static final long MIN_LOGIN_SPACING_MS = 400L;

    public static LoginData login(String refreshToken) {
        try {
            LOGIN_PERMITS.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return LoginData.fail("Login interrupted");
        }
        try {
            synchronized (LOGIN_SPACING) {
                long wait = lastLoginStart + MIN_LOGIN_SPACING_MS - System.currentTimeMillis();
                if (wait > 0) Thread.sleep(wait);
                lastLoginStart = System.currentTimeMillis();
            }
            return doLogin(refreshToken);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return LoginData.fail("Login interrupted");
        } finally {
            LOGIN_PERMITS.release();
        }
    }

    private static LoginData doLogin(String refreshToken) {

        HttpReply tokenReply = httpPostForm(TOKEN_URL,
            "client_id=" + CLIENT_ID
            + "&refresh_token=" + enc(refreshToken)
            + "&grant_type=refresh_token"
            + "&scope=" + enc(SCOPE));
        JsonObject tokenResponse = tokenReply.json();
        if (tokenResponse == null || !tokenResponse.has("access_token") || !tokenResponse.has("refresh_token")) {
            String reason = tokenErrorMessage(tokenReply);
            AutismClientAddon.LOG.warn("MS login: token refresh failed ({}): {}", tokenReply.status(), reason);
            return LoginData.fail(reason);
        }
        String accessToken = tokenResponse.get("access_token").getAsString();
        refreshToken = tokenResponse.get("refresh_token").getAsString();

        HttpReply xblReply = httpPostJson("https://user.auth.xboxlive.com/user/authenticate", "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d=" + accessToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}");
        JsonObject xbl = xblReply.json();
        if (!xblReply.ok() || xbl == null || !xbl.has("Token")) {
            String reason = "Xbox Live authentication failed (" + httpDetail(xblReply) + ")";
            AutismClientAddon.LOG.warn("MS login: {}", reason);
            return LoginData.fail(reason);
        }

        HttpReply xstsReply = httpPostJson("https://xsts.auth.xboxlive.com/xsts/authorize", "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xbl.get("Token").getAsString() + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}");
        JsonObject xsts = xstsReply.json();
        if (!xstsReply.ok() || xsts == null || !xsts.has("Token")) {
            String reason = xstsError(xsts);
            AutismClientAddon.LOG.warn("MS login: XSTS failed ({}): {}", xstsReply.status(), reason);
            return LoginData.fail(reason);
        }

        String uhs = extractUhs(xbl);
        if (uhs == null) uhs = extractUhs(xsts);
        if (uhs == null) return LoginData.fail("Xbox Live response was malformed (no user hash)");

        HttpReply mcReply = httpPostJson("https://api.minecraftservices.com/authentication/login_with_xbox", "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xsts.get("Token").getAsString() + "\"}");
        JsonObject mc = mcReply.json();
        if (!mcReply.ok() || mc == null || !mc.has("access_token")) {
            String reason = "Minecraft services login failed (" + httpDetail(mcReply) + ")";
            AutismClientAddon.LOG.warn("MS login: {}", reason);
            return LoginData.fail(reason);
        }
        String mcToken = mc.get("access_token").getAsString();

        long expiresIn = mc.has("expires_in") ? mc.get("expires_in").getAsLong() : 86400L;

        HttpReply profileReply = httpGetBearer("https://api.minecraftservices.com/minecraft/profile", mcToken);
        JsonObject profile = profileReply.json();
        if (profileReply.status() == 404 || profile == null || !profile.has("id") || !profile.has("name")) {
            String reason = profileReply.status() == 404
                ? "This Microsoft account doesn't own Minecraft: Java Edition"
                : "Couldn't read the Minecraft profile (" + httpDetail(profileReply) + ")";
            AutismClientAddon.LOG.warn("MS login: {}", reason);
            return LoginData.fail(reason);
        }
        return new LoginData(mcToken, refreshToken, profile.get("id").getAsString(), profile.get("name").getAsString(), expiresIn);
    }

    private static String extractUhs(JsonObject xboxResponse) {
        try {
            return xboxResponse.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String httpDetail(HttpReply reply) {
        if (reply == null) return "no response";
        JsonObject body = reply.json();
        if (body != null) {
            for (String key : new String[]{"error_description", "message", "errorMessage", "error"}) {
                if (body.has(key)) {
                    try {
                        String value = body.get(key).getAsString();
                        if (value != null && !value.isBlank()) return "HTTP " + reply.status() + ": " + value.split("\\r?\\n")[0].trim();
                    } catch (Exception ignored) {  }
                }
            }
        }
        return reply.status() < 0 ? "no response (network)" : "HTTP " + reply.status();
    }

    private static String tokenErrorMessage(HttpReply reply) {
        JsonObject body = reply == null ? null : reply.json();
        if (body != null) {
            try {
                if (body.has("error_description") && !body.get("error_description").getAsString().isBlank()) {
                    return "Microsoft: " + body.get("error_description").getAsString().split("\\r?\\n")[0].trim();
                }
                if (body.has("error") && !body.get("error").getAsString().isBlank()) {
                    return "Microsoft error: " + body.get("error").getAsString();
                }
            } catch (Exception ignored) {  }
        }
        int status = reply == null ? -1 : reply.status();
        return status < 0 ? "Couldn't reach Microsoft (check your connection)"
                          : "Microsoft token request failed (HTTP " + status + ")";
    }

    private static String xstsError(JsonObject xsts) {
        if (xsts == null) return "Xbox authorization failed";
        if (xsts.has("XErr")) {
            long code = xsts.get("XErr").getAsLong();
            if (code == 2148916233L) return "This account has no Xbox profile — create one at xbox.com first";
            if (code == 2148916235L) return "Xbox Live isn't available in this account's country/region";
            if (code == 2148916236L || code == 2148916237L) return "This account needs adult verification";
            if (code == 2148916238L) return "Child account — an adult must add it to a Microsoft Family";
        }
        return "Xbox authorization failed";
    }

    private static String redirectUri() {
        return "http://127.0.0.1:" + PORT;
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String newCodeVerifier() {
        byte[] bytes = new byte[64];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String codeChallenge(String verifier) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            return verifier;
        }
    }

    private static final String BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .proxy(AutismAuthNetwork.directProxySelector())
        .connectTimeout(Duration.ofSeconds(20))
        .build();

    private record HttpReply(int status, String body) {
        boolean ok() { return status >= 200 && status < 300; }
        JsonObject json() {
            try { return body == null || body.isBlank() ? null : JsonParser.parseString(body).getAsJsonObject(); }
            catch (Exception e) { return null; }
        }
    }

    private static HttpRequest.Builder base(String url) {
        return HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json");
    }

    private static HttpReply send(HttpRequest request) {
        try {
            HttpResponse<String> res = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpReply(res.statusCode(), res.body());
        } catch (Exception e) {
            return new HttpReply(-1, null);
        }
    }

    private static HttpReply httpPostForm(String url, String body) {
        return send(base(url).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build());
    }

    private static HttpReply httpPostJson(String url, String body) {
        return send(base(url).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build());
    }

    private static HttpReply httpGetBearer(String url, String bearer) {
        return send(base(url).header("Authorization", "Bearer " + bearer).GET().build());
    }

    private static void startServer() {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/", AutismMicrosoftLogin::handleRequest);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
        } catch (IOException e) {
            stopServer();
            AutismClientMessaging.sendPrefixed("Failed to start Microsoft login server.");
        }
    }

    public static void stopServer() {
        if (server == null) return;
        server.stop(0);
        server = null;
        callback = null;
        oauthState = "";
    }

    private static void handleRequest(HttpExchange request) throws IOException {
        if ("GET".equals(request.getRequestMethod())) {
            List<QueryPair> query = parseURL(request.getRequestURI().getRawQuery());
            String code = null;
            String returnedState = null;
            for (QueryPair pair : query) {
                if ("code".equals(pair.name())) code = pair.value();
                else if ("state".equals(pair.name())) returnedState = pair.value();
            }

            boolean ok = code != null && !oauthState.isBlank() && oauthState.equals(returnedState);
            if (ok) {
                handleCode(code);
            }
            if (!ok) {
                writeText(request, "Cannot authenticate.");
                Consumer<String> current = callback;
                if (current != null) current.accept(null);
            } else {
                writeText(request, "You may now close this page.");
            }
        }
        stopServer();
    }

    private static void handleCode(String code) {
        HttpReply reply = httpPostForm(TOKEN_URL,
            "client_id=" + CLIENT_ID
            + "&code=" + enc(code)
            + "&grant_type=authorization_code"
            + "&redirect_uri=" + enc(redirectUri())
            + "&scope=" + enc(SCOPE)
            + "&code_verifier=" + enc(codeVerifier));
        JsonObject response = reply.json();
        Consumer<String> current = callback;
        if (current != null) current.accept(response == null || !response.has("refresh_token") ? null : response.get("refresh_token").getAsString());
    }

    private static void writeText(HttpExchange request, String text) throws IOException {
        byte[] responseBody = text.getBytes(StandardCharsets.UTF_8);
        request.sendResponseHeaders(200, responseBody.length);
        try (var output = request.getResponseBody()) {
            output.write(responseBody);
        }
    }

    private record QueryPair(String name, String value) {}

    private static List<QueryPair> parseURL(String string) {
        List<QueryPair> query = new ArrayList<>();
        if (string == null) return query;
        char[] buf = string.toCharArray();
        int i = 0;
        while (i < buf.length) {
            StringBuilder name = new StringBuilder();
            StringBuilder value = new StringBuilder();
            for (; i < buf.length; i++) {
                if (buf[i] == '&' || buf[i] == ';' || buf[i] == '=') break;
                name.append(buf[i]);
            }
            if (i < buf.length) {
                char ch = buf[i++];
                if (ch == '=') {
                    for (; i < buf.length; i++) {
                        if (buf[i] == '&' || buf[i] == ';') {
                            i++;
                            break;
                        }
                        value.append(buf[i]);
                    }
                }
            }
            if (!name.isEmpty()) query.add(new QueryPair(urlDecode(name.toString()), urlDecode(value.toString())));
        }
        return query;
    }

    private static String urlDecode(String s) {
        if (s == null) return null;
        ByteBuffer bb = ByteBuffer.allocate(s.length());
        CharBuffer cb = CharBuffer.wrap(s);
        while (cb.hasRemaining()) {
            char c = cb.get();
            if (c == '%' && cb.remaining() >= 2) {
                char uc = cb.get();
                char lc = cb.get();
                int u = Character.digit(uc, 16);
                int l = Character.digit(lc, 16);
                if (u != -1 && l != -1) bb.put((byte) ((u << 4) + l));
                else {
                    bb.put((byte) '%');
                    bb.put((byte) uc);
                    bb.put((byte) lc);
                }
            } else if (c == '+') bb.put((byte) ' ');
            else bb.put((byte) c);
        }
        bb.flip();
        return StandardCharsets.UTF_8.decode(bb).toString();
    }

    public static final class LoginData {
        public final String mcToken;
        public final String newRefreshToken;
        public final String uuid;
        public final String username;
        public final String error;
        public final long expiresInSeconds;

        public LoginData() {
            this(null, null, null, null, 0L, null);
        }

        public LoginData(String mcToken, String newRefreshToken, String uuid, String username, long expiresInSeconds) {
            this(mcToken, newRefreshToken, uuid, username, expiresInSeconds, null);
        }

        private LoginData(String mcToken, String newRefreshToken, String uuid, String username, long expiresInSeconds, String error) {
            this.mcToken = mcToken;
            this.newRefreshToken = newRefreshToken;
            this.uuid = uuid;
            this.username = username;
            this.expiresInSeconds = expiresInSeconds;
            this.error = error;
        }

        static LoginData fail(String error) {
            return new LoginData(null, null, null, null, 0L, error);
        }

        public boolean isGood() {
            return mcToken != null;
        }

        public long expiresAtEpochMs() {
            return expiresInSeconds > 0 ? System.currentTimeMillis() + expiresInSeconds * 1000L : 0L;
        }
    }
}
