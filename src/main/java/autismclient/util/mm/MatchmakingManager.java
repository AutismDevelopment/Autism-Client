package autismclient.util.mm;

import autismclient.AutismClientAddon;
import autismclient.commands.AutismCommands;
import autismclient.modules.PackHideState;
import autismclient.util.AutismDiscordLogin;
import autismclient.util.AutismHttp;
import autismclient.util.AutismNotifications;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import autismclient.util.mm.crypto.MmCrypto;
import autismclient.util.mm.crypto.MmIdentity;
import autismclient.util.mm.crypto.MmSession;
import autismclient.util.mm.crypto.RoomKey;
import autismclient.util.mm.msg.MmMessages;
import autismclient.util.mm.relay.MqttRelay;
import autismclient.util.mm.relay.Relay;
import autismclient.util.mm.relay.RelayManager;
import autismclient.util.mm.relay.RelayStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class MatchmakingManager {
    private static final MatchmakingManager INSTANCE = new MatchmakingManager();
    public static MatchmakingManager get() { return INSTANCE; }

    private static final int MAX_CHAT_LINES = 300;
    private static final long UI_CACHE_MS = 250;
    private static final long PRESENCE_INTERVAL_MS = 8_000;
    private static final long PEER_TIMEOUT_MS = 60_000;
    private static final long JOIN_RESPONSE_MS = 10_000;

    private static final int MAX_PEERS = 2_000;
    private static final int MAX_DIRECTORY = 2_000;

    private final MmIdentity identity = MmIdentity.get();
    private final MmPrefs prefs = MmPrefs.get();
    private final MmSafety safety = new MmSafety();
    private final String selfFpHex = identity.fingerprint().replace("-", "").toLowerCase(java.util.Locale.ROOT);

    private final Map<String, MmPeer> peers = new ConcurrentHashMap<>();
    private final Map<String, LobbyListing> directory = new ConcurrentHashMap<>();

    private static final String DIRECTORY_URL = AutismDiscordLogin.AUTH_BASE + "/lobbies";
    private static final long DIR_POLL_MS = 6_000;
    private static final long DIR_ANNOUNCE_MS = 10_000;
    private ScheduledExecutorService directoryHttp;
    private volatile long lastHttpAnnounceMs;
    private final Deque<MmChatLine> chat = new ArrayDeque<>();

    private volatile List<MmPeer> cachedMembers = List.of();
    private volatile long cachedMembersAtMs;
    private volatile List<LobbyListing> cachedDirectory = List.of();
    private volatile long cachedDirectoryAtMs;
    private volatile int chatVersion;

    private static AutismHttp.JsonResult directoryPost(String endpoint, JsonObject body) {
        return directoryPost(endpoint, body, 15_000);
    }

    private static AutismHttp.JsonResult directoryPost(String endpoint, JsonObject body, int timeoutMs) {
        String suffix = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        String json = body == null ? "{}" : body.toString();
        return AutismHttp.postJsonResult(DIRECTORY_URL + suffix, json, timeoutMs,
            AutismDiscordLogin.proofHeaders("POST", "/lobbies" + suffix, json));
    }

    private static JsonObject directoryGet(String jwt) {
        return AutismHttp.getJson(DIRECTORY_URL, jwt,
            AutismDiscordLogin.proofHeaders("GET", "/lobbies", ""));
    }

    private volatile RelayManager relays;
    private volatile Lobby lobby;
    private volatile MmSession session;
    private volatile boolean directoryOpen;
    private volatile long lastImmediatePollMs;
    private volatile long lastPresenceMs;
    private volatile long lastPeerReplyMs;
    private volatile String lastPresenceNick = "";

    private volatile long joinedAtMs;
    private volatile boolean joinResponsePending;
    private volatile boolean aclReconnectPending;

    public enum JoinPhase { NONE, RESERVING, CONNECTING, FAILED }
    private volatile JoinPhase joinPhase = JoinPhase.NONE;
    private volatile String joinError = "";

    private volatile boolean autoJoinAttempted;

    private volatile boolean dirRefreshing;
    private volatile long lastDirPullMs;
    private volatile boolean connectivitySeen;
    private volatile boolean lastConnectivity;
    private volatile long engagedSinceMs;
    private volatile boolean warnedNoConnect;
    private volatile long connLostSinceMs;
    private volatile boolean warnedOffline;
    private volatile long lastDiagLogMs;

    private static final long CONN_TOAST_DEBOUNCE_MS = 10_000;

    private volatile long gateOfflineSinceMs;
    private volatile boolean gateEverOnline;
    private static final long CONNECT_GRACE_MS = 12_000;
    private static final long CONNECT_FAST_FAIL_MS = 2_000;
    private static final long IDLE_RELAY_CLOSE_MS = 15_000;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> idleRelayClose;
    private long idleRelayGeneration;

    private volatile byte[] currentKey;
    private volatile int keyEpoch;
    private record KeyEpoch(int epoch, byte[] key) {}
    private volatile List<KeyEpoch> oldKeys = List.of();
    private static final int KEY_HISTORY = 2;
    private volatile long lastServerRefreshMs;

    private volatile long rosterVersion;
    private volatile boolean rosterSeeded;

    private volatile long selfEchoOk;
    private volatile long selfEchoBad;

    private static final long RECEIPT_TTL_MS = 60_000;
    private static final int RECEIPT_TRACK_MAX = 64;
    private final Map<String, SentContent> awaitingReceipts = new ConcurrentHashMap<>();
    private static final class SentContent {
        final String type;
        final long sentMs;
        final java.util.Set<String> confirmedFps = ConcurrentHashMap.newKeySet();
        SentContent(String type, long sentMs) { this.type = type; this.sentMs = sentMs; }
    }

    private final java.util.Set<String> droppedFps = ConcurrentHashMap.newKeySet();

    private final java.util.Set<String> kickedFromLobbies = ConcurrentHashMap.newKeySet();

    private final java.util.Set<String> officialLobbyIds = ConcurrentHashMap.newKeySet();

    private final Map<String, String> fpToDid = new ConcurrentHashMap<>();
    private final java.util.Set<String> mutedFps = ConcurrentHashMap.newKeySet();

    private volatile int hostTerm;
    private volatile String hostFp = "";

    public record RosterEntry(String did, String fp, String name, boolean admin, boolean speaker, String role) {}
    private volatile List<RosterEntry> serverRoster = List.of();
    private volatile long lastAnnounceOkMs;

    private MatchmakingManager() {}

    public MmPrefs prefs() { return prefs; }
    public MmSafety safety() { return safety; }
    public Lobby currentLobby() { return lobby; }
    public boolean inLobby() { return lobby != null; }

    public String currentShareCode() { Lobby lb = lobby; return lb == null ? "" : lb.shareCode; }
    public String selfFingerprint() { return identity.fingerprint(); }
    public String selfFpHex() { return selfFpHex; }

    public boolean isHost() { return isActingHost(); }
    public boolean canSend() { Lobby lb = lobby; return lb != null && (!lb.announcement || lb.canSend); }
    public boolean canManageSpeakers() {
        Lobby lb = lobby;
        return lb != null && lb.announcement && AutismDiscordLogin.isAdmin()
            && lb.ownerDid.equals(AutismDiscordLogin.currentDid());
    }

    public String actingHostFp() { Lobby lb = lobby; return lb == null ? "" : hostFp; }

    private boolean isActingHost() { return lobby != null && !hostFp.isEmpty() && hostFp.equals(selfFpHex); }

    public boolean currentLobbyOfficial() {
        Lobby lb = lobby;
        return lb != null && officialLobbyIds.contains(lb.lobbyId);
    }

    public boolean effectiveShareServer() {
        Lobby lb = lobby;
        return lb != null && officialLobbyIds.contains(lb.lobbyId)
            ? prefs.lobbyShareServer(lb.lobbyId) : prefs.shareServer();
    }

    public boolean effectiveShareLocation() {
        Lobby lb = lobby;
        return lb != null && officialLobbyIds.contains(lb.lobbyId)
            ? prefs.lobbyShareLocation(lb.lobbyId) : prefs.shareLocation();
    }

    public boolean serverLinkHealthy() {
        return lobby == null || System.currentTimeMillis() - lastAnnounceOkMs < 25_000;
    }

    private boolean eligibleHost(String fp) {
        return fp != null && !droppedFps.contains(fp);
    }

    public static String currentServerIp() {
        try {
            ServerData sd = Minecraft.getInstance().getCurrentServer();
            return sd == null || sd.ip == null || sd.ip.isBlank() ? null : sd.ip.trim();
        } catch (Throwable t) { return null; }
    }

    public static boolean sameServerAs(MmPeer peer) {
        if (peer == null || !peer.serverShared || peer.serverIp == null || peer.serverIp.isBlank()) return false;
        String mine = currentServerIp();
        return mine != null && mine.equalsIgnoreCase(peer.serverIp.trim());
    }

    public static boolean alreadyOn(String ip) {
        if (ip == null || ip.isBlank()) return false;
        String mine = currentServerIp();
        return mine != null && mine.equalsIgnoreCase(ip.trim());
    }

    public List<RelayStatus> relayStatuses() {
        RelayManager rm = relays;
        return rm == null ? List.of() : rm.statuses();
    }

    public String censusSummary() {
        RelayManager rm = relays;
        if (rm == null) return "idle";
        int connected = 0;
        List<RelayStatus> statuses = rm.statuses();
        for (RelayStatus status : statuses) if (status.connected) connected++;
        return "lobby=" + inLobby() + " relays=" + connected + "/" + statuses.size();
    }

    public enum ConnState { CONNECTING, ONLINE, OFFLINE }

    public ConnState connState() {
        if (relays == null) return ConnState.CONNECTING;
        long now = System.currentTimeMillis();
        if (relayUp()) { gateEverOnline = true; gateOfflineSinceMs = 0; return ConnState.ONLINE; }
        if (gateOfflineSinceMs == 0) gateOfflineSinceMs = now;
        long offlineFor = now - gateOfflineSinceMs;

        if (!gateEverOnline && offlineFor >= CONNECT_FAST_FAIL_MS && !connError().isEmpty()) return ConnState.OFFLINE;
        return offlineFor < CONNECT_GRACE_MS ? ConnState.CONNECTING : ConnState.OFFLINE;
    }

    public boolean matchmakingReady() {
        ConnState s = connState();
        return s == ConnState.ONLINE || (gateEverOnline && s == ConnState.CONNECTING);
    }

    public String connError() {
        RelayManager rm = relays;
        if (rm == null) return "";
        for (RelayStatus s : rm.statuses()) if (s.lastError != null && !s.lastError.isEmpty()) return s.lastError;
        return "";
    }

    public void reconnectNow() {
        gateOfflineSinceMs = 0;
        ensureRelays().reconnectAll();
    }

    public void closeRelays() {
        RelayManager rm = relays;
        if (rm != null) rm.closeAll();
    }

    public JoinPhase joinPhase() { return joinPhase; }
    public String joinError() { return joinError; }
    public boolean directoryRefreshing() { return dirRefreshing; }
    public long lastDirectoryPullMs() { return lastDirPullMs; }

    public synchronized void cancelJoin() {
        joinPhase = JoinPhase.NONE;
        joinError = "";
        if (lobby != null) leave();
        else closeIdleRelays();
    }

    public MmPeer peer(String fpHex) {
        if (fpHex == null) return null;
        MmPeer live = peers.get(fpHex);
        if (live != null) return live;
        for (RosterEntry e : serverRoster) {
            if (!fpHex.equalsIgnoreCase(e.fp())) continue;
            byte[] fp = hexToBytes(e.fp());
            if (fp == null) return null;
            MmPeer rosterPeer = new MmPeer(fp);
            rosterPeer.discordId = e.did();
            rosterPeer.discordUser = MmText.clean(e.name(), 32);
            rosterPeer.admin = e.admin();
            rosterPeer.speaker = e.speaker();
            rosterPeer.role = e.role();
            return rosterPeer;
        }
        return null;
    }

    public List<MmPeer> members() {
        long now = System.currentTimeMillis();
        List<MmPeer> cache = cachedMembers;
        if (cache != null && now - cachedMembersAtMs < UI_CACHE_MS) return cache;
        List<RosterEntry> roster = serverRoster;
        List<MmPeer> out;
        if (lobby != null && !roster.isEmpty()) {
            out = new ArrayList<>(roster.size());
            for (RosterEntry e : roster) {
                String fp = e.fp() == null ? "" : e.fp().toLowerCase(java.util.Locale.ROOT);
                if (fp.equals(selfFpHex)) continue;
                MmPeer live = fp.isEmpty() ? null : peers.get(fp);
                if (live != null) {
                    String discordName = MmText.clean(e.name(), 32);
                    if (!discordName.isBlank()) live.discordUser = discordName;
                    if (e.did() != null && !e.did().isBlank()) live.discordId = e.did();
                    live.admin = e.admin();
                    live.speaker = e.speaker();
                    live.role = e.role();
                    out.add(live);
                    continue;
                }
                byte[] fpb = hexToBytes(fp);
                MmPeer ghost = new MmPeer(fpb != null ? fpb : new byte[8]);
                ghost.discordUser = MmText.clean(e.name(), 32);
                ghost.discordId = e.did() == null ? "" : e.did();
                ghost.admin = e.admin();
                ghost.speaker = e.speaker();
                ghost.role = e.role();
                out.add(ghost);
            }
        } else {

            out = new ArrayList<>(peers.values());
            out.removeIf(p -> now - p.lastSeenMs > PEER_TIMEOUT_MS);
        }
        out.sort(Comparator.comparing(MmPeer::displayName, String.CASE_INSENSITIVE_ORDER));
        cachedMembers = out;
        cachedMembersAtMs = now;
        return out;
    }

    public int memberCount() {
        List<RosterEntry> roster = serverRoster;
        if (lobby != null && !roster.isEmpty()) return roster.size();
        long now = System.currentTimeMillis();
        int live = 0;
        for (MmPeer p : peers.values()) if (now - p.lastSeenMs <= PEER_TIMEOUT_MS) live++;
        return live + (lobby != null ? 1 : 0);
    }

    public long rosterUiVersion() {
        Lobby lb = lobby;
        return rosterVersion * 1_001L + (lb == null ? 0 : lb.speakerVersion);
    }

    public int chatVersion() { return chatVersion; }

    public List<LobbyListing> directoryListings() {
        long now = System.currentTimeMillis();
        List<LobbyListing> cache = cachedDirectory;
        if (cache != null && now - cachedDirectoryAtMs < UI_CACHE_MS) return cache;
        List<LobbyListing> out = new ArrayList<>(directory.values());
        out.removeIf(l -> !l.isFresh(now) || kickedFromLobbies.contains(l.lobbyId));
        out.sort(LobbyListing.DIRECTORY_ORDER);
        cachedDirectory = out;
        cachedDirectoryAtMs = now;
        return out;
    }

    public synchronized List<MmChatLine> chatSnapshot() { return new ArrayList<>(chat); }

    private static final String BROKER_URL = "wss://mm.autismclient.com/mqtt";

    private synchronized RelayManager ensureRelays() {
        if (relays == null) {

            List<Relay> set = new ArrayList<>();

            addRelay(set, () -> MqttRelay.authed("mm", BROKER_URL,
                () -> autismclient.util.AutismDiscordLogin.freshJwt(180_000),
                autismclient.util.AutismDiscordLogin::currentDid));
            relays = new RelayManager(set);
        }
        ensureScheduler();
        ensureDirectoryWorker();
        return relays;
    }

    private static void addRelay(List<Relay> set, java.util.function.Supplier<Relay> factory) {
        try { set.add(factory.get()); } catch (Throwable t) { AutismClientAddon.LOG.warn("mm relay init failed", t); }
    }

    private synchronized void ensureScheduler() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mm-engine");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(this::tick, 2, 2, TimeUnit.SECONDS);
        }
    }

    private synchronized void ensureDirectoryWorker() {
        if (directoryHttp == null) {
            directoryHttp = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mm-directory");
                t.setDaemon(true);
                return t;
            });
            directoryHttp.scheduleWithFixedDelay(this::directoryTick, 1, DIR_POLL_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void directoryTick() {
        try {
            Lobby lb = lobby;
            if (lb != null) {
                long now = System.currentTimeMillis();
                if (now - lastHttpAnnounceMs >= DIR_ANNOUNCE_MS) { lastHttpAnnounceMs = httpHeartbeat(lb) ? now : 0; }
            }
            if (directoryOpen || autoJoinWanted()) httpPullDirectory();
        } catch (Throwable t) {
            AutismClientAddon.LOG.debug("mm directory tick error", t);
        }
    }

    public synchronized void openDirectory() {

        if (!AutismDiscordLogin.hasSession()) return;
        if (directoryOpen) return;
        directoryOpen = true;
        cancelIdleRelayClose();
        ensureRelays();
        long now = System.currentTimeMillis();
        if (now - lastImmediatePollMs > 3000) { lastImmediatePollMs = now; submitPoll(); }
    }

    public synchronized void closeDirectory() {
        if (!directoryOpen) return;
        directoryOpen = false;
        scheduleIdleRelayClose();
    }

    private boolean autoJoinWanted() {
        return prefs.autoJoinPublic() && !autoJoinAttempted && lobby == null && !prefs.killSwitch()
            && AutismDiscordLogin.hasSession();
    }

    private void maybeAutoJoinPublic() {
        if (!autoJoinWanted()) return;
        if (joinPhase == JoinPhase.RESERVING || joinPhase == JoinPhase.CONNECTING) return;
        LobbyListing official = null;
        for (LobbyListing l : directory.values()) {
            if (l.official) { official = l; break; }
        }
        if (official == null) return;
        autoJoinAttempted = true;
        joinPublic(official.lobbyId);
    }

    public void resetAutoJoinAttempt() { autoJoinAttempted = false; }

    public void autoJoinNudge() {
        if (!autoJoinWanted()) return;
        ensureScheduler();
        ensureDirectoryWorker();
    }

    private void scheduleIdleRelayClose() {
        if (lobby != null || joinPhase == JoinPhase.RESERVING || joinPhase == JoinPhase.CONNECTING || relays == null) return;
        cancelIdleRelayClose();
        RelayManager expected = relays;
        long generation = idleRelayGeneration;
        ensureScheduler();
        idleRelayClose = scheduler.schedule(
            () -> closeIdleRelays(expected, generation), IDLE_RELAY_CLOSE_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelIdleRelayClose() {
        idleRelayGeneration++;
        ScheduledFuture<?> pending = idleRelayClose;
        idleRelayClose = null;
        if (pending != null) pending.cancel(false);
    }

    private void closeIdleRelays() {
        RelayManager expected = relays;
        cancelIdleRelayClose();
        closeIdleRelays(expected, idleRelayGeneration);
    }

    private synchronized void closeIdleRelays(RelayManager expected, long generation) {
        if (generation != idleRelayGeneration) return;
        idleRelayClose = null;
        if (lobby != null || directoryOpen
            || joinPhase == JoinPhase.RESERVING || joinPhase == JoinPhase.CONNECTING) return;
        RelayManager rm = relays;
        if (rm == null || rm != expected) return;
        relays = null;
        connectivitySeen = false;
        engagedSinceMs = 0;
        warnedNoConnect = false;
        connLostSinceMs = 0;
        warnedOffline = false;
        closeRelaysAsync(rm);
    }

    private void closeRelaysAsync(RelayManager rm) {
        Runnable close = rm::closeAll;
        ScheduledExecutorService ex = scheduler;
        if (ex != null) {
            try {
                ex.execute(close);
                return;
            } catch (Throwable ignored) {  }
        }
        Thread t = new Thread(close, "mm-relay-close");
        t.setDaemon(true);
        t.start();
    }

    private void submitPoll() {
        ScheduledExecutorService ex = directoryHttp;
        if (ex != null) try { ex.execute(this::httpPullDirectory); } catch (Throwable ignored) {  }
    }

    private void submitHeartbeat() {
        submitDirectory(() -> {
            Lobby lb = lobby;
            if (lb != null) lastHttpAnnounceMs = httpHeartbeat(lb) ? System.currentTimeMillis() : 0;
        });
    }

    private boolean httpHeartbeat(Lobby lb) {
        String jwt = AutismDiscordLogin.currentJwt();
        if (jwt == null || jwt.isBlank()) return false;
        JsonObject body = new JsonObject();
        body.addProperty("jwt", jwt);
        body.addProperty("lobbyId", lb.lobbyId);

        body.addProperty("rosterVersion", rosterVersion);
        if (isActingHost() && lb.isPublic) {
            body.addProperty("name", lb.name);
            body.addProperty("server", lb.server);
            body.addProperty("plugins", lb.plugins);
            body.addProperty("hostDupe", prefs.myDupeStatus());
            body.addProperty("serverShared", prefs.shareServer());
        }
        AutismHttp.JsonResult r = directoryPost("/announce", body);
        if (r.status() == 404) { reestablishMembership(lb); return false; }
        if (!r.ok() || r.body() == null) return false;
        applyServerState(r.body());
        return true;
    }

    private void reestablishMembership(Lobby lb) {
        String jwt = AutismDiscordLogin.currentJwt();
        if (jwt == null || jwt.isBlank()) return;
        JsonObject body = new JsonObject();
        body.addProperty("jwt", jwt);
        if (lb.isPublic) body.addProperty("lobbyId", lb.lobbyId);
        else body.addProperty("joinToken", lb.shareCode);
        body.addProperty("fp", selfFpHex);
        AutismHttp.JsonResult r = directoryPost("/join", body);
        if (r.status() == -1) return;
        if (r.ok() && r.body() != null) {
            synchronized (this) {
                if (lobby != lb) return;
                JsonObject b = r.body();
                if (b.has("jwt") && b.has("exp"))
                    AutismDiscordLogin.adoptJwt(b.get("jwt").getAsString(), b.get("exp").getAsLong());
                applyServerState(b);
                RelayManager rm = relays;
                if (rm != null) rm.reconnectAll();
            }
            return;
        }
        String err = r.error();
        synchronized (this) {
            if (lobby != lb) return;
            AutismNotifications.show(switch (err) {
                case "lobby_banned" -> "You were removed from that lobby.";
                case "full" -> "Couldn't rejoin. Lobby is full.";
                default -> "That lobby is no longer available.";
            }, 0xFFFF5B5B);
            leave();
        }
    }

    private synchronized void applyServerState(JsonObject b) {
        if (lobby == null || b == null) return;
        lastAnnounceOkMs = System.currentTimeMillis();
        if (b.has("jwt") && b.has("exp"))
            AutismDiscordLogin.adoptJwt(b.get("jwt").getAsString(), b.get("exp").getAsLong());
        if (b.has("role")) AutismDiscordLogin.adoptServerRole(optString(b, "role"));
        String contentTopic = optString(b, "contentTopic");
        if (contentTopic.isBlank()) contentTopic = optString(b, "topic");
        adoptTopics(contentTopic, optString(b, "controlTopic"), optString(b, "sysTopic"));
        Lobby active = lobby;
        if (active == null) return;
        if (b.has("speakerVersion")) active.speakerVersion = b.get("speakerVersion").getAsInt();
        if (b.has("canSend")) {
            boolean nextCanSend = b.get("canSend").getAsBoolean();
            boolean permissionChanged = active.canSend != nextCanSend;
            active.canSend = nextCanSend;
            if (permissionChanged && relays != null) relays.reconnectAll();
        }
        if (aclReconnectPending && b.has("jwt") && relays != null) {
            aclReconnectPending = false;
            relays.reconnectAll();
        }

        adoptHost(optString(b, "hostFp"), b.has("term") ? b.get("term").getAsInt() : null);

        int ke = b.has("keyEpoch") ? b.get("keyEpoch").getAsInt() : -1;
        if (ke >= 0 && (ke != keyEpoch || currentKey == null)) {
            byte[] k = resolveLobbyKey(b);
            if (k != null && k.length == 32) {
                if (currentKey != null) {
                    List<KeyEpoch> old = new ArrayList<>(KEY_HISTORY);
                    old.add(new KeyEpoch(keyEpoch, currentKey));
                    for (KeyEpoch o : oldKeys) { if (old.size() >= KEY_HISTORY) break; old.add(o); }
                    oldKeys = List.copyOf(old);
                }
                currentKey = k;
                keyEpoch = ke;
            }
        }

        long rv = b.has("rosterVersion") ? b.get("rosterVersion").getAsLong() : -1;
        if (b.has("roster") && b.get("roster").isJsonArray()) {
            List<RosterEntry> fresh = new ArrayList<>();
            for (JsonElement el : b.getAsJsonArray("roster")) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                fresh.add(new RosterEntry(optString(o, "did"),
                    optString(o, "fp").toLowerCase(java.util.Locale.ROOT), optString(o, "name"),
                    o.has("admin") && o.get("admin").getAsBoolean(),
                    o.has("speaker") && o.get("speaker").getAsBoolean(),
                    optString(o, "role")));
            }
            if (rv < 0) {
                serverRoster = fresh;
            } else if (rv >= rosterVersion) {
                if (rosterSeeded && rv > rosterVersion) renderRosterDiff(serverRoster, fresh);
                serverRoster = fresh;
                rosterVersion = rv;
                rosterSeeded = true;
                cachedMembersAtMs = 0;
            }
            for (RosterEntry e : fresh) {
                if (!e.did().isBlank() && e.fp().matches("[0-9a-f]{16}")) fpToDid.put(e.fp(), e.did());
            }
        } else if (rv > rosterVersion) {

            maybeRefreshServerState();
        }
    }

    private void adoptTopics(String contentTopic, String controlTopic, String sysTopic) {
        Lobby lb = lobby;
        RelayManager rm = relays;
        if (lb == null || rm == null || contentTopic.isBlank() || controlTopic.isBlank() || sysTopic.isBlank()) return;
        RoomKey old = lb.key;
        if (contentTopic.equals(old.contentTopic()) && controlTopic.equals(old.controlTopic())
                && sysTopic.equals(old.sysTopic())) return;
        try {
            RoomKey updated = RoomKey.fromServer(lb.lobbyId, contentTopic, controlTopic, sysTopic, lb.isPublic);
            if (!contentTopic.equals(old.contentTopic())) rm.subscribe(contentTopic, this::onContentFrame);
            if (!controlTopic.equals(old.controlTopic())) rm.subscribe(controlTopic, this::onControlFrame);
            if (!sysTopic.equals(old.sysTopic())) rm.subscribeRaw(sysTopic, this::onSysEvent);
            lb.adoptKey(updated);
            if (!contentTopic.equals(old.contentTopic())) rm.unsubscribe(old.contentTopic());
            if (!controlTopic.equals(old.controlTopic())) rm.unsubscribe(old.controlTopic());
            if (!sysTopic.equals(old.sysTopic())) rm.unsubscribeRaw(old.sysTopic());
            if (!aclReconnectPending) rm.reconnectAll();
        } catch (IllegalArgumentException badTopic) {
            AutismClientAddon.LOG.warn("ignored invalid matchmaking topic transition", badTopic);
        }
    }

    private void adoptHost(String hostFpHex, Integer term) {
        String hf = hostFpHex == null ? "" : hostFpHex.toLowerCase(java.util.Locale.ROOT);
        if (hf.matches("[0-9a-f]{16}") && !hf.equals(hostFp)) {
            boolean wasHost = isActingHost();
            hostFp = hf;
            if (term != null) hostTerm = term;
            if (!wasHost && isActingHost()) addSystem("You are the host now");
            else if (!isActingHost()) {
                MmPeer p = peers.get(hf);
                if (p != null) addSystem(displayNameFor(hf) + " is the host now");
            }
        } else if (term != null) {
            hostTerm = term;
        }
    }

    private void renderRosterDiff(List<RosterEntry> before, List<RosterEntry> after) {
        java.util.Set<String> old = new java.util.HashSet<>();
        for (RosterEntry e : before) old.add(e.did());
        java.util.Set<String> now = new java.util.HashSet<>();
        for (RosterEntry e : after) now.add(e.did());
        for (RosterEntry e : after) {
            if (!old.contains(e.did()) && !e.fp().equals(selfFpHex)) addSystem(rosterName(e) + " joined");
        }
        for (RosterEntry e : before) {
            if (!now.contains(e.did()) && !e.fp().equals(selfFpHex)) addSystem(rosterName(e) + " left");
        }
    }

    private String rosterName(RosterEntry e) {
        String n = MmText.clean(e.name(), 32);
        if (!n.isBlank()) return n;
        MmPeer live = e.fp().isEmpty() ? null : peers.get(e.fp());
        return live != null ? live.displayName() : "Discord member";
    }

    private void httpPullDirectory() {
        if (!directoryOpen && !autoJoinWanted()) return;
        String jwt = AutismDiscordLogin.currentJwt();
        if (jwt == null || jwt.isBlank()) return;
        dirRefreshing = true;
        try {
            JsonObject resp = directoryGet(jwt);
            if (resp == null || !resp.has("lobbies")) return;
            long now = System.currentTimeMillis();
            Map<String, LobbyListing> fresh = new java.util.HashMap<>();
            java.util.Set<String> seenIds = new java.util.HashSet<>();
            java.util.Set<String> flaggedOfficial = new java.util.HashSet<>();
            JsonArray arr = resp.getAsJsonArray("lobbies");
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String lid = o.has("lobbyId") ? o.get("lobbyId").getAsString() : "";
                if (lid == null || lid.isBlank()) continue;

                boolean official = o.has("official") && o.get("official").getAsBoolean();
                seenIds.add(lid);
                if (official) flaggedOfficial.add(lid);
                if (lid.equals(currentLobbyId())) continue;
                String hostDid = o.has("hostDid") ? o.get("hostDid").getAsString() : "";
                if (prefs.isBlockedDiscord(hostDid)) continue;
                byte[] fpb = hexToBytes(parseHostFp(lid));
                LobbyListing l = new LobbyListing(lid, fpb != null ? fpb : new byte[8]);
                l.name = MmText.clean(optString(o, "name"), 48);
                l.members = optInt(o, "members");
                l.maxMembers = optInt(o, "maxMembers");
                l.serverShared = o.has("serverShared") && o.get("serverShared").getAsBoolean();
                l.server = MmText.clean(optString(o, "server"), 48);
                l.plugins = MmText.clean(optString(o, "plugins"), 64);
                l.hostDupe = o.has("hostDupe") ? o.get("hostDupe").getAsInt() : -1;
                l.announcement = o.has("announcement") && o.get("announcement").getAsBoolean();
                l.official = official;
                l.hostDid = hostDid;
                l.lastSeenMs = now;
                fresh.put(lid, l);
            }

            kickedFromLobbies.removeIf(fresh::containsKey);

            synchronized (this) {
                directory.keySet().retainAll(fresh.keySet());
                directory.putAll(fresh);

                officialLobbyIds.removeAll(seenIds);
                officialLobbyIds.addAll(flaggedOfficial);
            }
            lastDirPullMs = now;
        } catch (Throwable t) {
            AutismClientAddon.LOG.debug("mm directory parse error", t);
        } finally {
            dirRefreshing = false;
        }
    }

    private static String optString(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; } catch (Throwable t) { return ""; }
    }

    private static int optInt(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : 0; } catch (Throwable t) { return 0; }
    }

    private static byte[] resolveLobbyKey(JsonObject b) {
        try {
            String kb64 = optString(b, "key");
            if (kb64.isEmpty()) kb64 = optString(b, "baseKey");
            if (kb64.isEmpty()) return null;
            byte[] k = java.util.Base64.getDecoder().decode(kb64);
            return k.length == 32 ? k : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private String currentLobbyId() { Lobby lb = lobby; return lb == null ? null : lb.lobbyId; }

    private synchronized void tick() {
        try {
            long now = System.currentTimeMillis();
            peers.values().removeIf(p -> now - p.lastSeenMs > PEER_TIMEOUT_MS);
            directory.values().removeIf(l -> !l.isFresh(now));
            if (peers.size() > MAX_PEERS) evictOldest(peers, p -> p.lastSeenMs, MAX_PEERS);
            if (directory.size() > MAX_DIRECTORY) evictOldest(directory, l -> l.lastSeenMs, MAX_DIRECTORY);
            awaitingReceipts.values().removeIf(sc -> now - sc.sentMs > RECEIPT_TTL_MS);
            if (awaitingReceipts.size() > RECEIPT_TRACK_MAX)
                evictOldest(awaitingReceipts, sc -> sc.sentMs, RECEIPT_TRACK_MAX);

            if (joinPhase == JoinPhase.CONNECTING && lobby != null && relayUp()) {
                joinPhase = JoinPhase.NONE;

                sendPresence();
                lastPresenceMs = now;
            }

            if (lobby != null && !AutismDiscordLogin.hasSession()) { addSystem("Signed out. Left the lobby."); leave(); }

            maybeAutoJoinPublic();

            Lobby lb = lobby;
            if (lb != null) {

                long presenceInterval = (lb.announcement || lb.maxPlayers > 100) ? 20_000 : PRESENCE_INTERVAL_MS;
                if (now - lastPresenceMs >= presenceInterval || !selfTpaName().equals(lastPresenceNick)) {
                    lastPresenceMs = now;
                    sendPresence();
                }

                if (currentKey == null) maybeRefreshServerState();

                if (joinResponsePending && now - joinedAtMs > JOIN_RESPONSE_MS) {
                    joinResponsePending = false;
                    if (peers.isEmpty() && !isActingHost())
                        AutismNotifications.show("You're alone here. Share the code to invite people.", 0xFFFFC857);
                }

                if (prefs.debugLog() && now - lastDiagLogMs >= 16_000) {
                    lastDiagLogMs = now;
                    StringBuilder sb = new StringBuilder("[mm-diag]");
                    RelayManager rmDiag = relays;
                    if (rmDiag != null) for (RelayStatus s : rmDiag.statuses())
                        sb.append(' ').append(s.name).append(s.connected ? "+" : "-")
                          .append(" up=").append(s.published.get()).append(" down=").append(s.received.get())
                          .append(" qDrop=").append(s.queueDropped.get())
                          .append(s.lastError.isEmpty() ? "" : " err=" + s.lastError);
                    sb.append(" | peers=").append(peers.size()).append(" keyEpoch=").append(keyEpoch)
                      .append(" key=").append(currentKey != null).append(" host=").append(isActingHost())
                      .append(" srvRoster=").append(serverRoster.size())
                      .append(" srvLink=").append(serverLinkHealthy())
                      .append(" echo=").append(selfEchoOk).append('/').append(selfEchoBad)
                      .append(" | drop stale=").append(safety.droppedStale.get())
                      .append(" replay=").append(safety.droppedReplay.get())
                      .append(" flood=").append(safety.droppedFlood.get())
                      .append(" auth=").append(safety.droppedAuth.get())
                      .append(" oversize=").append(safety.droppedOversize.get());
                    AutismClientAddon.LOG.info(sb.toString());
                }
            }
            updateConnectivity();
        } catch (Throwable t) {
            AutismClientAddon.LOG.debug("mm tick error", t);
        }
    }

    private void updateConnectivity() {
        RelayManager rm = relays;
        if (rm == null || lobby == null) {
            connectivitySeen = false; engagedSinceMs = 0; warnedNoConnect = false;
            connLostSinceMs = 0; warnedOffline = false; return;
        }
        long now = System.currentTimeMillis();
        if (engagedSinceMs == 0) engagedSinceMs = now;
        boolean anyConnected = false;
        for (RelayStatus s : rm.statuses()) if (s.connected) { anyConnected = true; break; }
        if (!connectivitySeen) {
            if (anyConnected) {
                connectivitySeen = true; lastConnectivity = true; warnedNoConnect = false;

            } else if (!warnedNoConnect && now - engagedSinceMs > 9000) {

                warnedNoConnect = true;
                AutismNotifications.show("Can't reach matchmaking. Check your connection.", 0xFFFF5B5B);
            }
            return;
        }
        if (anyConnected != lastConnectivity) {
            lastConnectivity = anyConnected;
            if (anyConnected) {
                if (lobby != null) { sendPresence(); lastPresenceMs = now; }

                if (warnedOffline) AutismNotifications.show("Matchmaking: reconnected to relays.", 0xFF35D873);
                connLostSinceMs = 0; warnedOffline = false;
            } else {
                connLostSinceMs = now;
            }
        }
        if (!anyConnected && !warnedOffline && connLostSinceMs != 0 && now - connLostSinceMs > CONN_TOAST_DEBOUNCE_MS) {
            warnedOffline = true;
            AutismNotifications.show("Matchmaking offline. Messages won't send.", 0xFFFF5B5B);
        }
    }

    private static <V> void evictOldest(Map<String, V> map, java.util.function.ToLongFunction<V> ts, int cap) {
        int over = map.size() - cap;
        if (over <= 0) return;
        map.entrySet().stream()
            .sorted(Comparator.comparingLong(e -> ts.applyAsLong(e.getValue())))
            .limit(over)
            .map(Map.Entry::getKey)
            .toList()
            .forEach(map::remove);
    }

    public synchronized void createLobby(LobbySettings s) {
        if (!beginJoin()) return;
        ensureRelays();
        final LobbySettings settings = s;
        submitDirectory(() -> doServerCreate(settings));
    }

    public synchronized void joinPublic(String lobbyId) {
        String id = lobbyId == null ? "" : lobbyId.trim();
        if (id.isEmpty()) return;
        if (!beginJoin()) return;
        ensureRelays();
        submitDirectory(() -> doServerJoin(id, null));
    }

    public synchronized void joinPrivate(char[] passkey) {
        String code = passkey == null ? "" : new String(passkey).trim();
        if (code.isEmpty()) return;
        if (!beginJoin()) return;
        ensureRelays();
        submitDirectory(() -> doServerJoin(null, code));
    }

    private boolean beginJoin() {
        if (!ensureAuthed()) return false;
        if (joinPhase == JoinPhase.RESERVING || joinPhase == JoinPhase.CONNECTING) return false;
        joinPhase = JoinPhase.RESERVING;
        joinError = "";
        return true;
    }

    private void submitDirectory(Runnable task) {
        ScheduledExecutorService ex = directoryHttp;
        if (ex != null) try { ex.execute(task); } catch (Throwable ignored) {  }
    }

    private synchronized void failJoin(String error) {
        joinError = (error == null || error.isEmpty()) ? "failed" : error;
        joinPhase = JoinPhase.FAILED;
        closeIdleRelays();
    }

    private void doServerCreate(LobbySettings s) {
        String jwt = AutismDiscordLogin.currentJwt();
        if (jwt == null || jwt.isBlank()) { failJoin("network"); return; }
        JsonObject body = new JsonObject();
        body.addProperty("jwt", jwt);
        body.addProperty("name", s.name);
        body.addProperty("isPublic", s.isPublic);
        body.addProperty("cap", s.maxPlayers);
        body.addProperty("server", s.isPublic ? s.server : "");
        body.addProperty("plugins", s.isPublic ? s.plugins : "");
        body.addProperty("hostDupe", prefs.myDupeStatus());
        body.addProperty("serverShared", prefs.shareServer());
        body.addProperty("fp", selfFpHex);
        body.addProperty("announcement", s.announcement);
        AutismHttp.JsonResult r = directoryPost("/create", body);
        if (r.status() == -1) { failJoin("network"); return; }
        if (!r.ok() || r.body() == null) { failJoin(r.error()); return; }
        applyServerLobby(r.body(), true, null);
    }

    private void doServerJoin(String publicId, String privateToken) {
        String jwt = AutismDiscordLogin.currentJwt();
        if (jwt == null || jwt.isBlank()) { failJoin("network"); return; }
        JsonObject body = new JsonObject();
        body.addProperty("jwt", jwt);
        if (publicId != null) body.addProperty("lobbyId", publicId);
        if (privateToken != null) body.addProperty("joinToken", privateToken);
        body.addProperty("fp", selfFpHex);
        AutismHttp.JsonResult r = directoryPost("/join", body);
        if (r.status() == -1) { failJoin("network"); return; }
        if (!r.ok() || r.body() == null) { failJoin(r.error()); return; }
        applyServerLobby(r.body(), false, privateToken);
    }

    private synchronized void applyServerLobby(JsonObject b, boolean isHost, String joinTokenUsed) {
        try {
            if (b.has("jwt") && b.has("exp"))
                AutismDiscordLogin.adoptJwt(b.get("jwt").getAsString(), b.get("exp").getAsLong());
            String lobbyId = b.get("lobbyId").getAsString();
            boolean isPublic = b.has("isPublic") && b.get("isPublic").getAsBoolean();
            int cap = b.has("cap") ? b.get("cap").getAsInt() : LobbySettings.MAX_PLAYERS;
            int term = b.has("term") ? b.get("term").getAsInt() : 0;
            String hostFp = isHost ? selfFpHex : optString(b, "hostFp");

            String privateToken = optString(b, "joinToken");
            if (privateToken.isBlank() && joinTokenUsed != null) privateToken = joinTokenUsed.trim();
            String shareCode = isPublic ? lobbyId : privateToken;
            String contentTopic = optString(b, "contentTopic");
            if (contentTopic.isBlank()) contentTopic = optString(b, "topic");
            RoomKey key = RoomKey.fromServer(lobbyId, contentTopic, optString(b, "controlTopic"),
                optString(b, "sysTopic"), isPublic);
            boolean announcement = b.has("announcement") && b.get("announcement").getAsBoolean();
            String ownerDid = optString(b, "ownerDid");
            int speakerVersion = optInt(b, "speakerVersion");
            boolean canSend = !announcement || (b.has("canSend") && b.get("canSend").getAsBoolean());
            joinRoom(key, optString(b, "name"), isPublic, cap, isHost,
                optString(b, "server"), optString(b, "plugins"), hostFp, shareCode, term,
                announcement, ownerDid, speakerVersion, canSend);
            applyServerState(b);
            sendPresence();
            if (isHost) addSystem((isPublic ? "Created public lobby \"" : "Created private lobby \"")
                + optString(b, "name") + "\"  (" + (isPublic ? "code: " : "invite: ") + shareCode + ")");
            else addSystem("Joined lobby");
            joinPhase = JoinPhase.CONNECTING;
            RelayManager rm = relays;
            if (rm != null) rm.reconnectAll();
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("apply server lobby failed", t);
            failJoin(isHost ? "create_failed" : "join_failed");
        }
    }

    private synchronized void joinRoom(RoomKey key, String name, boolean isPublic, int maxPlayers, boolean isHost,
                                       String server, String plugins, String hostFpHex, String shareCode, int hostTermInit,
                                       boolean announcement, String ownerDid, int speakerVersion, boolean canSend) {
        if (!ensureAuthed()) return;
        leave();
        RelayManager rm = ensureRelays();
        lobby = new Lobby(key, name, isPublic, maxPlayers, server, plugins, shareCode,
            announcement, ownerDid, speakerVersion, canSend);
        autoJoinAttempted = false;
        session = new MmSession();
        peers.clear();
        droppedFps.clear();
        fpToDid.clear();
        mutedFps.clear();
        awaitingReceipts.clear();
        selfEchoOk = 0;
        selfEchoBad = 0;

        hostTerm = hostTermInit;
        hostFp = hostFpHex;
        rosterVersion = 0;
        rosterSeeded = false;
        lastPresenceMs = 0;
        lastHttpAnnounceMs = 0;
        subscribeLobbyTopics(rm, key);
        joinedAtMs = System.currentTimeMillis();
        lastAnnounceOkMs = joinedAtMs;
        joinResponsePending = !isHost;
    }

    private void subscribeLobbyTopics(RelayManager rm, RoomKey key) {
        rm.subscribe(key.contentTopic(), this::onContentFrame);
        rm.subscribe(key.controlTopic(), this::onControlFrame);
        rm.subscribeRaw(key.sysTopic(), this::onSysEvent);
    }

    public synchronized void leave() {
        clearLobby(true, "Left lobby");
    }

    private void clearLobby(boolean notifyServer, String message) {
        Lobby lb = lobby;
        if (lb == null) return;

        if (notifyServer) submitLeave(lb.lobbyId);
        if (relays != null) {
            relays.unsubscribe(lb.topic());
            relays.unsubscribe(lb.controlTopic());
            relays.unsubscribeRaw(lb.sysTopic());
        }
        if (message != null && !message.isBlank()) addSystem(message);
        lobby = null;
        session = null;
        peers.clear();
        droppedFps.clear();
        fpToDid.clear();
        mutedFps.clear();
        awaitingReceipts.clear();
        selfEchoOk = 0;
        selfEchoBad = 0;
        hostTerm = 0;
        hostFp = "";
        currentKey = null;
        oldKeys = List.of();
        keyEpoch = 0;
        rosterVersion = 0;
        rosterSeeded = false;
        serverRoster = List.of();

        cachedMembers = List.of();
        cachedMembersAtMs = 0L;
        lastAnnounceOkMs = 0;
        joinResponsePending = false;
        aclReconnectPending = false;

        directory.clear();
        closeIdleRelays();
    }

    public synchronized void refreshDirectory() {
        directory.clear();
        directoryOpen = true;
        cancelIdleRelayClose();
        ensureRelays();
        submitPoll();
    }

    private final java.util.concurrent.atomic.AtomicBoolean shutdownLeaveSent = new java.util.concurrent.atomic.AtomicBoolean();

    public void shutdownLeave() {
        if (!shutdownLeaveSent.compareAndSet(false, true)) return;
        Lobby lb = lobby;
        String jwt = AutismDiscordLogin.cachedJwt();
        if (lb != null && jwt != null && !jwt.isBlank()) {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("jwt", jwt);
                body.addProperty("lobbyId", lb.lobbyId);
                directoryPost("/leave", body, 2000);
            } catch (Throwable ignored) {  }
        }
        RelayManager rm = relays;
        if (rm != null) try { rm.closeAll(); } catch (Throwable ignored) {  }
    }

    private void submitLeave(String lobbyId) {

        final String jwt = AutismDiscordLogin.cachedJwt();
        submitDirectory(() -> {
            if (jwt == null || jwt.isBlank() || lobbyId == null) return;
            JsonObject body = new JsonObject();
            body.addProperty("jwt", jwt);
            body.addProperty("lobbyId", lobbyId);
            AutismHttp.JsonResult r = directoryPost("/leave", body);
            if (r.ok() && r.body() != null && r.body().has("jwt") && r.body().has("exp"))
                AutismDiscordLogin.adoptJwt(r.body().get("jwt").getAsString(), r.body().get("exp").getAsLong());
        });
    }

    private boolean outboundBlocked() {

        if (prefs.killSwitch()) {
            AutismNotifications.show("Kill switch on. Sharing off.", 0xFFFFC857);
            return true;
        }
        return autismclient.modules.PackHideState.isHardLocked();
    }

    private boolean broadcast(MmMessageType type, byte[] payload) {
        Lobby lb = lobby;
        MmSession se = session;
        if (lb == null || se == null) return false;
        if (type.isUserContent() && !canSend()) return false;
        if (!safety.withinSizeLimit(type, payload.length)) return false;
        try {

            byte[] key = currentKey;
            if (key == null) return false;
            byte[] frame = MmEnvelope.seal(identity, se, lb.key, key, type, payload);

            String topic = type.usesControlTopic() ? lb.controlTopic() : lb.topic();
            boolean published = relays.publish(topic, frame, type.isDurable());
            if (!published) {
                AutismClientAddon.LOG.warn("mm broadcast dropped: {} frame too large for relays ({} bytes)", type, frame.length);
            } else if (receiptsEnabled() && expectsReceipt(type)) {
                String id = MmEnvelope.peekMsgIdHex(frame);
                if (id != null) awaitingReceipts.put(id, new SentContent(type.name(), System.currentTimeMillis()));
            }
            return published;
        } catch (Throwable t) {
            AutismClientAddon.LOG.debug("mm broadcast failed", t);
            return false;
        }
    }

    private static final long MIN_USER_SEND_MS = 1000;
    private volatile long lastUserSendMs;

    private boolean rateOk() {
        long now = System.currentTimeMillis();
        if (now - lastUserSendMs < MIN_USER_SEND_MS) {
            AutismNotifications.show("Slow down. 1 message per second.", 0xFFFFC857);
            return false;
        }
        lastUserSendMs = now;
        return true;
    }

    private boolean ensureAuthed() {
        if (autismclient.util.AutismDiscordLogin.hasSession()) return true;
        AutismNotifications.show("Sign in with Discord to use matchmaking.", 0xFFFFC857);
        return false;
    }

    public synchronized void sendChat(String text) {
        if (text == null || text.isBlank() || lobby == null) return;
        if (prefs.killSwitch()) { AutismNotifications.show("Kill switch on. Sending off.", 0xFFFFC857); return; }
        if (!rateOk()) return;
        String trimmed = text.length() > 1024 ? text.substring(0, 1024) : text;
        if (sendContent(MmMessageType.CHAT, new MmMessages.Chat(trimmed).encode()))
            addChat(new MmChatLine(selfFpHex, selfDisplayName(), trimmed, true, false));
    }

    private boolean sendContent(MmMessageType type, byte[] payload) {
        if (!canSend()) {
            AutismNotifications.show("Only admins and approved speakers can send here.", 0xFFFFC857);
            return false;
        }
        if (broadcast(type, payload)) return true;
        if (currentKey == null) AutismNotifications.show("Joining lobby. Try again.", 0xFFFFC857);
        else if (!relayUp()) AutismNotifications.show("Not connected. Try again.", 0xFFFFC857);
        else AutismNotifications.show("Couldn't send. Try again.", 0xFFE0533A);
        return false;
    }

    private boolean relayUp() {
        RelayManager rm = relays;
        if (rm == null) return false;
        for (RelayStatus s : rm.statuses()) if (s.connected) return true;
        return false;
    }

    public synchronized void offerMacro(MmMessages.MacroOffer m) {
        if (m == null || lobby == null || outboundBlocked()) return;
        if (!rateOk()) return;
        if (sendContent(MmMessageType.MACRO_OFFER, m.encode())) addChat(MmChatLine.macroCard(selfFpHex, selfDisplayName(), true, m));
    }

    public synchronized void offerPacket(MmMessages.PacketOffer p) {
        if (p == null || lobby == null || outboundBlocked()) return;
        if (!rateOk()) return;
        if (sendContent(MmMessageType.PACKET_OFFER, p.encode())) addChat(MmChatLine.packetCard(selfFpHex, selfDisplayName(), true, p));
    }

    public synchronized boolean offerBlob(MmMessages.BlobOffer b) {
        if (b == null || lobby == null || outboundBlocked()) return false;
        if (!rateOk()) return false;
        byte[] payload = b.encode();
        if (!safety.withinSizeLimit(MmMessageType.BLOB_OFFER, payload.length)) {
            AutismNotifications.show("Share too large to send.", 0xFFE0533A);
            return false;
        }

        if (!sendContent(MmMessageType.BLOB_OFFER, payload)) return false;
        addChat(MmChatLine.blobCard(selfFpHex, selfDisplayName(), true, b));
        return true;
    }

    public synchronized void offerCommand(String typedLine) {
        if (typedLine == null || typedLine.isBlank() || lobby == null || outboundBlocked()) return;
        if (!rateOk()) return;
        MmMessages.CommandOffer offer = classifyCommand(typedLine.trim());
        if (sendContent(MmMessageType.COMMAND_OFFER, offer.encode())) addChat(MmChatLine.commandCard(selfFpHex, selfDisplayName(), true, offer));
    }

    public static MmMessages.CommandOffer classifyCommand(String line) {
        if (AutismCommands.isAutismCommandMessage(line)) {
            return new MmMessages.CommandOffer(MmMessages.CommandOffer.KIND_AUTISM, AutismCommands.commandBody(line));
        }
        if (line.startsWith("/")) {
            return new MmMessages.CommandOffer(MmMessages.CommandOffer.KIND_VANILLA, line.substring(1).trim());
        }
        return new MmMessages.CommandOffer(MmMessages.CommandOffer.KIND_CHAT, line);
    }

    public static String renderCommandOffer(MmMessages.CommandOffer offer) {
        if (offer == null) return "";
        return switch (offer.kind) {
            case MmMessages.CommandOffer.KIND_AUTISM -> AutismCommands.effectivePrefix() + offer.body;
            case MmMessages.CommandOffer.KIND_VANILLA -> "/" + offer.body;
            default -> offer.body;
        };
    }

    public void runCommandOffer(MmMessages.CommandOffer offer) {
        if (offer == null || prefs.killSwitch()) return;
        var conn = Minecraft.getInstance().getConnection();
        switch (offer.kind) {
            case MmMessages.CommandOffer.KIND_AUTISM -> {
                if (isBlockedSharedAutismCommand(offer.body)) {
                    AutismNotifications.show("Blocked: shared commands can't change your prefix or disable protections.",
                        0xFFFF5B5B);
                    return;
                }
                AutismCommands.dispatch(offer.body);
            }
            case MmMessages.CommandOffer.KIND_VANILLA -> { if (conn != null) conn.sendCommand(offer.body); }
            default -> { if (conn != null) conn.sendChat(offer.body); }
        }
    }

    private static boolean isBlockedSharedAutismCommand(String body) {
        if (body == null) return false;

        String norm = body.trim();
        while (norm.startsWith("/")) norm = norm.substring(1).trim();
        if (norm.isEmpty()) return false;
        String[] parts = norm.split("\\s+");
        String head = parts[0].toLowerCase(java.util.Locale.ROOT);
        if (head.equals("prefix")) return true;
        if (head.equals("toggle") || head.equals("module")) {
            for (int i = 1; i < parts.length; i++) {
                if (PackHideState.isHideModuleName(parts[i])) return true;
            }
        }
        return false;
    }

    public void tpaPeer(String name) {
        String n = name == null ? "" : name.trim();

        if (!n.matches("[A-Za-z0-9_]{1,16}")) {
            AutismNotifications.show("Can't TPA: that player's name isn't a valid Minecraft username.", 0xFFFFC857);
            return;
        }
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.sendCommand("tpa " + n);
    }

    public void tradePeer(String name) {
        String n = name == null ? "" : name.trim();
        if (!n.matches("[A-Za-z0-9_]{1,16}")) {
            AutismNotifications.show("Can't trade: that player's name isn't a valid Minecraft username.", 0xFFFFC857);
            return;
        }
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.sendCommand("trade " + n);
    }

    private void sendPresence() {
        MmSession se = session;
        if (se == null) return;
        String nick = selfTpaName();
        lastPresenceNick = nick;
        MmMessages.Presence p = new MmMessages.Presence(nick);

        boolean ks = prefs.killSwitch();
        boolean mayShare = canSend();

        boolean shareServer = effectiveShareServer();
        boolean shareLocation = effectiveShareLocation();
        if (!ks && mayShare && shareServer) {
            try {
                ServerData sd = Minecraft.getInstance().getCurrentServer();
                if (sd != null && sd.ip != null && !sd.ip.isBlank()) {
                    p.shareServer = true;
                    p.serverIp = sd.ip;
                    p.serverName = sd.name == null ? sd.ip : sd.name;
                }
            } catch (Throwable ignored) {  }
        }
        if (!ks && mayShare && shareLocation) p.shareLocation = true;
        p.dupeStatus = mayShare ? prefs.myDupeStatus() : -1;
        p.identityToken = autismclient.util.AutismDiscordLogin.currentIdToken();
        broadcast(MmMessageType.PRESENCE, p.encode());
        if (!ks && mayShare && shareLocation) sendLocation();
    }

    private void sendLocation() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            String dim = mc.player.level().dimension().identifier().toString();
            MmMessages.Location loc = new MmMessages.Location(dim, mc.player.getX(), mc.player.getY(), mc.player.getZ());
            broadcast(MmMessageType.LOCATION, loc.encode());
        } catch (Throwable ignored) {  }
    }

    private void onContentFrame(byte[] frame) { onLobbyFrame(frame, false); }
    private void onControlFrame(byte[] frame) { onLobbyFrame(frame, true); }

    private synchronized void onLobbyFrame(byte[] frame, boolean controlTopic) {
        try {
            Lobby lb = lobby;
            if (lb == null) return;
            String fp = MmEnvelope.peekSenderFpHex(frame);
            if (fp == null) return;
            MmMessageType peeked = MmMessageType.byId(MmEnvelope.peekTypeId(frame));
            if (peeked == null || peeked.usesControlTopic() != controlTopic) return;
            if (fp.equals(selfFpHex)) { verifySelfEcho(frame); return; }
            if (droppedFps.contains(fp)) return;
            if (!safety.admitPreDecrypt()) return;

            boolean content = peeked != null && !peeked.isControl();

            if (content && (mutedFps.contains(fp) || prefs.isBlocked(fp))) return;

            byte[] k1 = currentKey;
            if (k1 == null) { maybeRefreshServerState(); return; }
            MmEnvelope env = MmEnvelope.open(frame, k1);
            if (env == null) {
                for (KeyEpoch o : oldKeys) { env = MmEnvelope.open(frame, o.key()); if (env != null) break; }
            }
            if (env == null) {
                safety.droppedAuth.incrementAndGet();

                maybeRefreshServerState();
                return;
            }
            if (!safety.accept(env)) return;

            MmMessageType type = env.type();
            if (type == null) return;
            if (type.usesControlTopic() != controlTopic) return;
            if (type.isUserContent() && !isAuthorizedContentSender(fp)) return;

            if (!type.isControl() && prefs.killSwitch()) return;
            switch (type) {
                case PRESENCE -> handlePresence(env);
                case CHAT -> handleChat(env);
                case MACRO_OFFER -> handleMacroOffer(env);
                case COMMAND_OFFER -> handleCommandOffer(env);
                case PACKET_OFFER -> handlePacketOffer(env);
                case BLOB_OFFER -> handleBlob(env);
                case LOCATION -> handleLocation(env);
                case LEAVE -> handleLeave(env);
                case KICK -> handleKick(env);
                case RECEIPT -> handleReceipt(env);
            }
        } catch (Throwable t) {
            AutismClientAddon.LOG.debug("mm inbound handler error", t);
        }
    }

    private boolean isAuthorizedContentSender(String fp) {
        Lobby lb = lobby;
        if (lb == null || !lb.announcement) return true;
        for (RosterEntry e : serverRoster) {
            if (e.speaker() && fp.equalsIgnoreCase(e.fp())) return true;
        }
        return false;
    }

    private synchronized void onSysEvent(byte[] payload) {
        try {
            Lobby lb = lobby;
            if (lb == null) return;
            MmSysEvent ev = MmSysEvent.parse(payload);
            if (ev == null) return;
            java.security.PublicKey serverKey = MmDiscordProof.serverPublicKey();
            if (serverKey == null) { maybeRefreshServerState(); return; }
            if (!ev.verify(serverKey, lb.sysTopic())) return;
            long last = rosterVersion;
            if (ev.rv <= last) return;
            if (ev.rv > last + 1) { maybeRefreshServerState(); return; }
            byte[] key = keyForEpoch(ev.ke);
            MmSysEvent.Body body = key == null ? null : ev.open(key, lb.sysTopic());
            if (body == null) { maybeRefreshServerState(); return; }
            rosterVersion = ev.rv;
            applySysEvent(body);

            if (ev.cke > keyEpoch) maybeRefreshServerState();
        } catch (Throwable t) {
            AutismClientAddon.LOG.debug("mm sys event error", t);
        }
    }

    private void applySysEvent(MmSysEvent.Body b) {
        String fp = b.fp() == null ? "" : b.fp().toLowerCase(java.util.Locale.ROOT);
        boolean self = fp.equals(selfFpHex);
        switch (b.t()) {
            case "join" -> {
                if (self) return;
                if (!fp.isBlank() && b.did() != null && !b.did().isBlank()) fpToDid.put(fp, b.did());
                List<RosterEntry> fresh = new ArrayList<>(serverRoster);
                if (fresh.removeIf(e -> e.did().equals(b.did()))) {
                    fresh.add(new RosterEntry(b.did(), fp, b.name(), b.admin(), b.speaker(), b.role()));
                    serverRoster = fresh;
                } else {
                    RosterEntry e = new RosterEntry(b.did(), fp, b.name(), b.admin(), b.speaker(), b.role());
                    fresh.add(e);
                    serverRoster = fresh;
                    addSystem(rosterName(e) + " joined");
                }
                cachedMembersAtMs = 0;
            }
            case "leave", "kick" -> {
                boolean kick = b.t().equals("kick");
                if (self) {
                    if (kick) onSelfKicked();
                    return;
                }
                RosterEntry gone = null;
                List<RosterEntry> fresh = new ArrayList<>(serverRoster);
                for (java.util.Iterator<RosterEntry> it = fresh.iterator(); it.hasNext(); ) {
                    RosterEntry e = it.next();
                    if (e.did().equals(b.did())) { gone = e; it.remove(); }
                }
                serverRoster = fresh;
                cachedMembersAtMs = 0;
                String name = gone != null ? rosterName(gone)
                    : rosterName(new RosterEntry(b.did(), fp, b.name(), b.admin(), b.speaker(), b.role()));
                if (!fp.isEmpty()) {
                    if (kick) droppedFps.add(fp);
                    peers.remove(fp);
                    fpToDid.remove(fp);
                    safety.forgetPeer(fp);
                }
                addSystem(kick ? name + " was removed by the host" : name + " left");
            }
            case "host" -> adoptHost(b.hostFp(), b.term());
            case "speaker" -> {
                Lobby lb = lobby;
                if (lb == null || !lb.announcement || b.speakerVersion() < lb.speakerVersion) return;
                lb.speakerVersion = b.speakerVersion();
                List<RosterEntry> fresh = new ArrayList<>(serverRoster.size());
                RosterEntry changed = null;
                for (RosterEntry e : serverRoster) {
                    if (e.did().equals(b.did())) {
                        changed = new RosterEntry(e.did(), e.fp(), e.name(), e.admin(), e.admin() || b.allowed(), e.role());
                        fresh.add(changed);
                    } else fresh.add(e);
                }
                serverRoster = fresh;
                if (changed != null && !changed.fp().isBlank()) {
                    MmPeer peer = peers.get(changed.fp());
                    if (peer != null) peer.speaker = changed.speaker();
                }
                boolean selfDid = b.did().equals(AutismDiscordLogin.currentDid());
                if (selfDid && !b.allowed()) lb.canSend = false;
                String oldContent = lb.topic(), oldControl = lb.controlTopic();
                boolean announcedRotation = !oldContent.equals(b.contentTopic()) || !oldControl.equals(b.controlTopic());
                if (selfDid || announcedRotation) aclReconnectPending = true;
                adoptTopics(b.contentTopic(), b.controlTopic(), lb.sysTopic());
                boolean rotated = !oldContent.equals(lb.topic()) || !oldControl.equals(lb.controlTopic());
                if (selfDid || rotated) maybeRefreshServerState();
                cachedMembersAtMs = 0;
                if (changed != null) addSystem(rosterName(changed)
                    + (changed.speaker() ? " can send now" : " can no longer send"));
            }
            case "role" -> {

                List<RosterEntry> fresh = new ArrayList<>(serverRoster.size());
                RosterEntry changed = null;
                for (RosterEntry e : serverRoster) {
                    if (e.did().equals(b.did())) {
                        changed = new RosterEntry(e.did(), e.fp(), e.name(), e.admin(), e.speaker(), b.role());
                        fresh.add(changed);
                    } else fresh.add(e);
                }
                serverRoster = fresh;
                if (changed != null && !changed.fp().isBlank()) {
                    MmPeer peer = peers.get(changed.fp());
                    if (peer != null) peer.role = changed.role();
                }
                if (b.did() != null && b.did().equals(AutismDiscordLogin.currentDid())) {
                    AutismDiscordLogin.adoptServerRole(b.role());
                }
                cachedMembersAtMs = 0;
            }
            case "close" -> {
                clearLobby(false, "Lobby closed: the host left.");
            }
            default -> {  }
        }
    }

    private void onSelfKicked() {
        Lobby lb = lobby;
        if (lb == null) return;
        if (lb.lobbyId != null && !lb.lobbyId.isBlank()) kickedFromLobbies.add(lb.lobbyId);
        directory.remove(lb.lobbyId);
        AutismNotifications.show("You're banned from this lobby.", 0xFFFF5B5B);
        addSystem("You were removed from the lobby by the host.");
        leave();
    }

    private byte[] keyForEpoch(int epoch) {
        if (currentKey != null && epoch == keyEpoch) return currentKey;
        for (KeyEpoch o : oldKeys) if (o.epoch() == epoch) return o.key();
        return null;
    }

    private MmPeer peerFor(MmEnvelope env) {
        return peers.computeIfAbsent(env.senderFpHex(), k -> new MmPeer(env.senderFp));
    }

    private void verifySelfEcho(byte[] frame) {
        byte[] k1 = currentKey;
        if (k1 == null) return;
        MmEnvelope env = MmEnvelope.open(frame, k1);
        if (env == null) {
            for (KeyEpoch o : oldKeys) { env = MmEnvelope.open(frame, o.key()); if (env != null) break; }
        }
        if (env != null) {
            if (selfEchoOk++ == 0 && prefs.debugLog())
                AutismClientAddon.LOG.info("[mm-loopback] round-trip verified: publish -> broker -> subscribe -> decrypt OK"
                    + " (every member on this key receives our frames)");
        } else {
            selfEchoBad++;
        }
    }

    private void handleReceipt(MmEnvelope env) {
        MmMessages.Receipt r = MmMessages.Msg.decodeInto(new MmMessages.Receipt(), env.payload);
        if (r == null) return;
        SentContent sc = awaitingReceipts.get(MmCrypto.hex(r.msgId));
        if (sc == null) return;
        String fp = env.senderFpHex();
        if (!sc.confirmedFps.add(fp)) return;
        if (!prefs.debugLog()) return;
        MmPeer peer = peers.get(fp);
        AutismClientAddon.LOG.info("[mm-receipt] {} delivered to {} ({} member(s) confirmed, {}ms after send)",
            sc.type, peer != null ? displayNameFor(peer.fpHex) : "Discord member", sc.confirmedFps.size(),
            System.currentTimeMillis() - sc.sentMs);
    }

    private void sendReceipt(MmEnvelope env) {
        if (!receiptsEnabled()) return;
        try { broadcast(MmMessageType.RECEIPT, new MmMessages.Receipt(env.msgId).encode()); }
        catch (Throwable ignored) {  }
    }

    private boolean receiptsEnabled() {
        Lobby lb = lobby;
        return lb != null && !lb.announcement && memberCount() <= 100;
    }

    private static boolean expectsReceipt(MmMessageType type) {
        return switch (type) {
            case CHAT, MACRO_OFFER, COMMAND_OFFER, PACKET_OFFER, BLOB_OFFER -> true;
            default -> false;
        };
    }

    private void handlePresence(MmEnvelope env) {
        MmMessages.Presence p = MmMessages.Msg.decodeInto(new MmMessages.Presence(), env.payload);
        if (p == null) return;
        String fp = env.senderFpHex();

        MmDiscordProof.Identity id = MmDiscordProof.verify(p.identityToken, env.senderSpki);
        if (id != null) fpToDid.put(fp, id.id());
        String did = id != null ? id.id() : fpToDid.get(fp);

        if (did != null && isActingHost() && prefs.isBannedDiscord(did)) {
            byte[] fpb = hexToBytes(fp);
            if (fpb != null) broadcast(MmMessageType.KICK, new MmMessages.Kick(fpb).encode());
            droppedFps.add(fp); peers.remove(fp);
            if (lobby != null) submitBan(lobby.lobbyId, did, fp);
            return;
        }
        boolean isNew = !peers.containsKey(fp);

        MmPeer peer = peerFor(env);
        peer.discordId = did == null ? "" : did;
        for (RosterEntry e : serverRoster) {
            if (fp.equalsIgnoreCase(e.fp())) {
                peer.admin = e.admin();
                peer.speaker = e.speaker();
                peer.role = e.role();
                if (peer.discordId.isBlank()) peer.discordId = e.did();
                break;
            }
        }
        if (id != null) {
            peer.discordUser = MmText.clean(id.username(), 32);

            if (!peer.discordUser.isBlank()) prefs.refreshDiscordName(did, peer.discordUser);
        }

        boolean blockedNow = (did != null && prefs.isBlockedDiscord(did)) || prefs.isBlocked(fp);
        peer.muted = blockedNow;
        if (blockedNow) mutedFps.add(fp); else mutedFps.remove(fp);
        peer.nickname = MmText.clean(p.nickname, 32);
        peer.lastSeenMs = System.currentTimeMillis();
        boolean senderMayShare = isAuthorizedContentSender(fp);
        peer.serverShared = senderMayShare && p.shareServer;
        peer.serverName = senderMayShare ? MmText.clean(p.serverName, 48) : "";
        peer.serverIp = senderMayShare ? MmText.clean(p.serverIp, 64) : "";
        peer.dupeStatus = senderMayShare ? p.dupeStatus : -1;

        if (isNew) {

            long nowMs = System.currentTimeMillis();
            Lobby lb = lobby;
            boolean largeRoom = lb != null && (lb.announcement || lb.maxPlayers > 100);
            if (!largeRoom && nowMs - lastPeerReplyMs > 1_500) {
                lastPeerReplyMs = nowMs;
                sendPresence();
                lastPresenceMs = nowMs;
            }
        }
    }

    private void handleChat(MmEnvelope env) {
        MmMessages.Chat c = MmMessages.Msg.decodeInto(new MmMessages.Chat(), env.payload);
        if (c == null) return;
        MmPeer peer = peerFor(env);
        peer.lastSeenMs = System.currentTimeMillis();
        addChat(new MmChatLine(peer.fpHex, displayNameFor(peer.fpHex), MmText.clean(c.text, 512), false, false));
        sendReceipt(env);
    }

    private void handleMacroOffer(MmEnvelope env) {
        MmMessages.MacroOffer m = MmMessages.Msg.decodeInto(new MmMessages.MacroOffer(), env.payload);
        if (m == null) return;
        m.macroName = MmText.clean(m.macroName, 48);
        m.singleActionLabel = MmText.clean(m.singleActionLabel, 64);
        MmPeer peer = peerFor(env);
        peer.lastSeenMs = System.currentTimeMillis();
        addChat(MmChatLine.macroCard(peer.fpHex, displayNameFor(peer.fpHex), false, m));
        sendReceipt(env);
    }

    private void handleCommandOffer(MmEnvelope env) {
        MmMessages.CommandOffer c = MmMessages.Msg.decodeInto(new MmMessages.CommandOffer(), env.payload);
        if (c == null) return;
        MmPeer peer = peerFor(env);
        peer.lastSeenMs = System.currentTimeMillis();
        addChat(MmChatLine.commandCard(peer.fpHex, displayNameFor(peer.fpHex), false, c));
        sendReceipt(env);
    }

    private void handlePacketOffer(MmEnvelope env) {
        MmMessages.PacketOffer p = MmMessages.Msg.decodeInto(new MmMessages.PacketOffer(), env.payload);
        if (p == null) return;
        p.friendlyName = MmText.clean(p.friendlyName, 48);
        MmPeer peer = peerFor(env);
        peer.lastSeenMs = System.currentTimeMillis();
        addChat(MmChatLine.packetCard(peer.fpHex, displayNameFor(peer.fpHex), false, p));
        sendReceipt(env);
    }

    private void handleBlob(MmEnvelope env) {
        MmMessages.BlobOffer b = MmMessages.Msg.decodeInto(new MmMessages.BlobOffer(), env.payload);
        if (b == null) return;
        b.friendlyName = MmText.clean(b.friendlyName, 64);
        MmPeer peer = peerFor(env);
        peer.lastSeenMs = System.currentTimeMillis();
        addChat(MmChatLine.blobCard(peer.fpHex, displayNameFor(peer.fpHex), false, b));
        sendReceipt(env);
    }

    private void handleLocation(MmEnvelope env) {
        MmMessages.Location loc = MmMessages.Msg.decodeInto(new MmMessages.Location(), env.payload);
        if (loc == null) return;
        MmPeer peer = peerFor(env);
        peer.hasLocation = true;
        peer.dimension = MmText.clean(loc.dimension, 48);
        peer.x = loc.x; peer.y = loc.y; peer.z = loc.z;
        peer.lastSeenMs = System.currentTimeMillis();
    }

    private void handleLeave(MmEnvelope env) {
        peers.remove(env.senderFpHex());
        safety.forgetPeer(env.senderFpHex());
    }

    public synchronized void setSpeaker(String targetFpHex, boolean allowed) {
        if (!canManageSpeakers() || lobby == null || targetFpHex == null) return;
        RosterEntry target = null;
        for (RosterEntry e : serverRoster) {
            if (targetFpHex.equalsIgnoreCase(e.fp())) { target = e; break; }
        }
        if (target == null || target.did().isBlank() || target.admin()) return;
        String lobbyId = lobby.lobbyId;
        String targetDid = target.did();
        int expectedVersion = lobby.speakerVersion;
        submitDirectory(() -> {
            String jwt = AutismDiscordLogin.currentJwt();
            if (jwt == null || jwt.isBlank()) return;
            JsonObject body = new JsonObject();
            body.addProperty("jwt", jwt);
            body.addProperty("lobbyId", lobbyId);
            body.addProperty("targetDid", targetDid);
            body.addProperty("allowed", allowed);
            body.addProperty("speakerVersion", expectedVersion);
            AutismHttp.JsonResult r = directoryPost("/speaker", body);
            if (r.ok() && r.body() != null) {
                applyServerState(r.body());
                submitHeartbeat();
            } else if (r.status() == 409) {
                submitHeartbeat();
                AutismNotifications.show("Speaker permissions changed; try again.", 0xFFFFC857);
            } else {
                AutismNotifications.show("Could not change speaker permission.", 0xFFFF5B5B);
            }
        });
    }

    public synchronized void banPeer(String fpHex) {
        if (!isActingHost() || fpHex == null || fpHex.equals(selfFpHex)) return;
        String did = fpToDid.get(fpHex);
        MmPeer target = peers.get(fpHex);
        if (did != null) prefs.banDiscord(did, target != null ? target.discordUser : "");
        String who = displayNameFor(fpHex);
        droppedFps.add(fpHex);
        peers.remove(fpHex);
        safety.forgetPeer(fpHex);
        byte[] fp = hexToBytes(fpHex);
        if (fp != null) broadcast(MmMessageType.KICK, new MmMessages.Kick(fp).encode());

        if (lobby != null) { submitBan(lobby.lobbyId, did, fpHex); submitHeartbeat(); }
        addSystem("Banned " + who + " from the lobby");
    }

    private void submitBan(String lobbyId, String targetDid, String targetFp) {
        submitDirectory(() -> {
            String jwt = AutismDiscordLogin.currentJwt();
            if (jwt == null || jwt.isBlank()) return;
            JsonObject body = new JsonObject();
            body.addProperty("jwt", jwt);
            body.addProperty("lobbyId", lobbyId);
            if (targetDid != null) body.addProperty("targetDid", targetDid);
            if (targetFp != null) body.addProperty("targetFp", targetFp);
            directoryPost("/ban", body);
        });
    }

    public synchronized void makeHost(String targetFpHex) {
        if (!isActingHost()) return;
        String t = targetFpHex == null ? "" : targetFpHex.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.isEmpty() || t.equals(selfFpHex) || !t.matches("[0-9a-f]{16}")) return;
        MmPeer p = peers.get(t);
        if (p == null || !eligibleHost(t)) { addSystem("Can't make host. Not ready."); return; }
        String targetDid = fpToDid.get(t);
        if (targetDid == null || lobby == null) {
            addSystem("Can't make host. Not verified yet.");
            return;
        }
        submitTransfer(lobby.lobbyId, targetDid);
        addSystem("Made " + displayNameFor(p.fpHex) + " the host");
    }

    private void submitTransfer(String lobbyId, String targetDid) {
        submitDirectory(() -> {
            String jwt = AutismDiscordLogin.currentJwt();
            if (jwt == null || jwt.isBlank()) return;
            JsonObject body = new JsonObject();
            body.addProperty("jwt", jwt);
            body.addProperty("lobbyId", lobbyId);
            body.addProperty("targetDid", targetDid);
            AutismHttp.JsonResult r = directoryPost("/transfer", body);
            if (r.ok() && r.body() != null) applyServerState(r.body());
        });
    }

    private void handleKick(MmEnvelope env) {
        Lobby lb = lobby;
        if (lb == null) return;
        if (!env.senderFpHex().equals(actingHostFp())) return;
        MmMessages.Kick k = MmMessages.Msg.decodeInto(new MmMessages.Kick(), env.payload);
        if (k == null) return;
        String fp = MmCrypto.hex(k.bannedFp);
        if (fp.equals(selfFpHex)) {
            onSelfKicked();
            return;
        }

        droppedFps.add(fp);
        peers.remove(fp);
        safety.forgetPeer(fp);
    }

    private void maybeRefreshServerState() {
        long now = System.currentTimeMillis();
        if (now - lastServerRefreshMs < 3000) return;
        lastServerRefreshMs = now;
        submitHeartbeat();
    }

    private static String parseHostFp(String code) {
        if (code != null && code.length() > 17 && code.charAt(16) == ':'
                && code.substring(0, 16).matches("[0-9a-fA-F]{16}")) {
            return code.substring(0, 16).toLowerCase(java.util.Locale.ROOT);
        }
        return "";
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() != 16) return null;
        try {
            byte[] out = new byte[8];
            for (int i = 0; i < 8; i++) out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            return out;
        } catch (Exception e) { return null; }
    }

    private synchronized void addChat(MmChatLine line) {
        chat.addLast(line);
        while (chat.size() > MAX_CHAT_LINES) chat.removeFirst();
        chatVersion++;

        if (prefs.chatMirror()) MmChatComponents.emit(line);
    }

    public synchronized void clearChat() {
        chat.clear();
        chatVersion++;
    }

    public synchronized boolean hasChat() { return !chat.isEmpty(); }

    private void addSystem(String text) { addChat(MmChatLine.system(text)); }

    public synchronized void blockPeer(String fpHex) {
        MmPeer peer = peers.get(fpHex);
        String did = fpToDid.get(fpHex);
        if (did != null) {
            prefs.blockDiscord(did, peer != null ? peer.discordUser : "");
            directory.values().removeIf(l -> did.equals(l.hostDid));
        } else {
            prefs.block(fpHex);
        }
        mutedFps.add(fpHex);
        if (peer != null) peer.muted = true;
    }

    public synchronized void unblockDiscord(String did) {
        if (did == null) return;
        prefs.unblockDiscord(did);
        mutedFps.removeIf(fp -> did.equals(fpToDid.get(fp)));
        for (MmPeer p : peers.values()) if (did.equals(p.discordId)) p.muted = false;
    }

    public synchronized void unblockPeer(String fpHex) {
        String did = fpToDid.get(fpHex);
        if (did != null) prefs.unblockDiscord(did); else prefs.unblock(fpHex);
        mutedFps.remove(fpHex);
        MmPeer p = peers.get(fpHex);
        if (p != null) p.muted = false;
    }

    public synchronized void unbanDiscord(String did) {
        if (did == null) return;
        prefs.unbanDiscord(did);
        for (Map.Entry<String, String> e : fpToDid.entrySet()) {
            if (did.equals(e.getValue())) droppedFps.remove(e.getKey());
        }
        Lobby lb = lobby;
        if (lb != null && isActingHost()) submitUnban(lb.lobbyId, did);
    }

    private void submitUnban(String lobbyId, String targetDid) {
        submitDirectory(() -> {
            String jwt = AutismDiscordLogin.currentJwt();
            if (jwt == null || jwt.isBlank()) return;
            JsonObject body = new JsonObject();
            body.addProperty("jwt", jwt);
            body.addProperty("lobbyId", lobbyId);
            body.addProperty("targetDid", targetDid);
            directoryPost("/unban", body);
        });
    }

    public String selfTpaName() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String inWorld = mc.player.getGameProfile().name();
                if (inWorld != null && !inWorld.isBlank()) return inWorld;
            }
            String mcName = mc.getUser().getName();
            if (mcName != null && !mcName.isBlank()) return mcName;
        } catch (Throwable ignored) {  }
        return "";
    }

    public String selfDisplayName() {
        String proofName = MmText.clean(MmDiscordProof.ownName(), 32);
        if (!proofName.isBlank()) return proofName;
        String loginName = MmText.clean(AutismDiscordLogin.displayName(), 32);
        return loginName.isBlank() ? "Discord member" : loginName;
    }

    public String displayNameFor(String fpHex) {
        if (fpHex != null && fpHex.equalsIgnoreCase(selfFpHex)) return selfDisplayName();
        MmPeer peer = peer(fpHex);
        if (peer != null && peer.discordUser != null && !peer.discordUser.isBlank()) return peer.displayName();
        if (fpHex != null) {
            for (RosterEntry e : serverRoster) {
                if (fpHex.equalsIgnoreCase(e.fp())) {
                    String name = MmText.clean(e.name(), 32);
                    if (!name.isBlank()) return name;
                }
            }
        }
        return "Discord member";
    }

    public static String roleFor(String fpHex, boolean self) {
        if (self) return AutismDiscordLogin.role();
        if (fpHex == null || fpHex.isEmpty()) return "user";
        MatchmakingManager mm = get();
        MmPeer peer = mm == null ? null : mm.peer(fpHex);
        String role = peer == null ? null : peer.role;
        return role == null || role.isBlank() ? "user" : role;
    }

    public static boolean isGradientName(String fpHex, boolean self) {
        return MmRoleColors.isGradient(roleFor(fpHex, self));
    }

    public static int nameColor(String fpHex, boolean self) {
        return MmRoleColors.roleColor(roleFor(fpHex, self));
    }

    public static int gradientNameColor(String fpHex, boolean self, int index, int length) {
        return MmRoleColors.gradientNameColor(roleFor(fpHex, self), index, length);
    }
}
