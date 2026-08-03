package autismclient.util.mm;

import autismclient.AutismClientAddon;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MmPrefs {
    private static final File FILE = new File(AutismClientAddon.FOLDER, "mm_prefs.properties");
    private static final File TMP = new File(AutismClientAddon.FOLDER, "mm_prefs.properties.tmp");
    private static final int MAX_SET = 4096;
    private static final long SAVE_DEBOUNCE_MS = 400;
    private static volatile MmPrefs instance;

    private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mm-prefs-writer");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean scheduled = new AtomicBoolean(false);

    private volatile boolean shareServer;
    private volatile boolean shareLocation;

    private volatile boolean autoJoinPublic;

    private volatile boolean killSwitch;

    private volatile boolean chatMirror = true;

    private volatile boolean debugLog;

    private volatile int defaultMaxPlayers = 8;
    private volatile boolean defaultPublic = true;

    private volatile String discordSession = "";
    private volatile String discordName = "";
    private volatile String discordIdToken = "";

    private volatile String defaultLobbyName = "";
    private volatile String defaultServer = "";
    private volatile String defaultPlugins = "";
    private volatile int myDupeStatus = -1;

    private final Set<String> blocked = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<String> trusted = Collections.synchronizedSet(new LinkedHashSet<>());

    private final Map<String, String> blockedDiscord = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, String> bannedDiscord = Collections.synchronizedMap(new LinkedHashMap<>());

    private final Map<String, boolean[]> lobbyShare = Collections.synchronizedMap(new LinkedHashMap<>());

    private MmPrefs() {}

    public static MmPrefs get() {
        MmPrefs local = instance;
        if (local == null) {
            synchronized (MmPrefs.class) {
                if (instance == null) instance = load();
                local = instance;
            }
        }
        return local;
    }

    public boolean shareServer() { return shareServer; }
    public void setShareServer(boolean v) { shareServer = v; save(); }
    public boolean shareLocation() { return shareLocation; }
    public void setShareLocation(boolean v) { shareLocation = v; save(); }

    public boolean autoJoinPublic() { return autoJoinPublic; }
    public void setAutoJoinPublic(boolean v) { autoJoinPublic = v; save(); }

    public boolean lobbyShareServer(String lobbyId) { return lobbyShare(lobbyId, 0); }
    public boolean lobbyShareLocation(String lobbyId) { return lobbyShare(lobbyId, 1); }
    public void setLobbyShareServer(String lobbyId, boolean v) { setLobbyShare(lobbyId, 0, v); }
    public void setLobbyShareLocation(String lobbyId, boolean v) { setLobbyShare(lobbyId, 1, v); }

    private boolean lobbyShare(String lobbyId, int idx) {
        boolean[] cur = lobbyId == null ? null : lobbyShare.get(lobbyId);
        return cur != null && cur[idx];
    }

    private void setLobbyShare(String lobbyId, int idx, boolean v) {
        if (lobbyId == null || lobbyId.isBlank() || lobbyId.length() > 128) return;
        synchronized (lobbyShare) {
            boolean[] cur = lobbyShare.get(lobbyId);
            if (cur == null) {
                if (!v) return;
                if (lobbyShare.size() >= MAX_SET) return;
                cur = new boolean[2];
                lobbyShare.put(lobbyId, cur);
            }
            cur[idx] = v;
            if (!cur[0] && !cur[1]) lobbyShare.remove(lobbyId);
        }
        save();
    }

    public boolean killSwitch() { return killSwitch; }
    public void setKillSwitch(boolean v) { killSwitch = v; save(); }

    public boolean chatMirror() { return chatMirror; }
    public void setChatMirror(boolean v) { chatMirror = v; save(); }

    public boolean debugLog() { return debugLog; }
    public void setDebugLog(boolean v) { debugLog = v; save(); }

    public int defaultMaxPlayers() { return defaultMaxPlayers; }
    public void setDefaultMaxPlayers(int v) {
        defaultMaxPlayers = v <= 0 ? 0 : Math.max(2, Math.min(LobbySettings.ADMIN_MAX_PLAYERS, v));
        save();
    }
    public boolean defaultPublic() { return defaultPublic; }
    public void setDefaultPublic(boolean v) { defaultPublic = v; save(); }

    public String defaultLobbyName() { return defaultLobbyName; }
    public void setDefaultLobbyName(String v) { defaultLobbyName = v == null ? "" : v; save(); }
    public String defaultServer() { return defaultServer; }
    public void setDefaultServer(String v) { defaultServer = v == null ? "" : v; save(); }
    public String defaultPlugins() { return defaultPlugins; }
    public void setDefaultPlugins(String v) { defaultPlugins = v == null ? "" : v; save(); }
    public int myDupeStatus() { return myDupeStatus; }
    public void setMyDupeStatus(int v) { myDupeStatus = (v < 0 || v > 1) ? -1 : v; save(); }

    public String discordSession() { return discordSession; }
    public String discordName() { return discordName; }
    public String discordIdToken() { return discordIdToken; }
    public void setDiscord(String sealedSession, String name, String idToken) {
        discordSession = sealedSession == null ? "" : sealedSession;
        discordName = name == null ? "" : name;
        discordIdToken = idToken == null ? "" : idToken;
        save();
    }

    public void setDiscordIdToken(String idToken) { discordIdToken = idToken == null ? "" : idToken; save(); }
    public void clearDiscord() { discordSession = ""; discordName = ""; discordIdToken = ""; save(); }

    public boolean isBlocked(String fpHex) { return blocked.contains(fpHex); }
    public void block(String fpHex) { if (validFp(fpHex)) { addCapped(blocked, fpHex); trusted.remove(fpHex); save(); } }
    public void unblock(String fpHex) { if (blocked.remove(fpHex)) save(); }
    public boolean isTrusted(String fpHex) { return trusted.contains(fpHex); }
    public void trust(String fpHex) { if (validFp(fpHex)) { addCapped(trusted, fpHex); blocked.remove(fpHex); save(); } }
    public void untrust(String fpHex) { if (trusted.remove(fpHex)) save(); }
    public Set<String> blockedSnapshot() { synchronized (blocked) { return new LinkedHashSet<>(blocked); } }

    public boolean isBlockedDiscord(String id) { return id != null && blockedDiscord.containsKey(id); }
    public void blockDiscord(String id, String name) { if (validId(id)) { putCapped(blockedDiscord, id, name); save(); } }
    public void unblockDiscord(String id) { if (blockedDiscord.remove(id) != null) save(); }
    public boolean isBannedDiscord(String id) { return id != null && bannedDiscord.containsKey(id); }
    public void banDiscord(String id, String name) { if (validId(id)) { putCapped(bannedDiscord, id, name); save(); } }
    public void unbanDiscord(String id) { if (bannedDiscord.remove(id) != null) save(); }
    public Map<String, String> blockedDiscordSnapshot() { synchronized (blockedDiscord) { return new LinkedHashMap<>(blockedDiscord); } }
    public Map<String, String> bannedDiscordSnapshot() { synchronized (bannedDiscord) { return new LinkedHashMap<>(bannedDiscord); } }

    public void refreshDiscordName(String id, String name) {
        if (!validId(id) || name == null || name.isBlank()) return;
        String clean = sanitizeName(name);
        if (clean.isEmpty()) return;
        boolean changed = false;
        synchronized (blockedDiscord) { if (blockedDiscord.containsKey(id) && !clean.equals(blockedDiscord.get(id))) { blockedDiscord.put(id, clean); changed = true; } }
        synchronized (bannedDiscord) { if (bannedDiscord.containsKey(id) && !clean.equals(bannedDiscord.get(id))) { bannedDiscord.put(id, clean); changed = true; } }
        if (changed) save();
    }

    private static MmPrefs load() {
        MmPrefs p = new MmPrefs();
        if (FILE.exists()) {
            try (FileInputStream in = new FileInputStream(FILE)) {
                Properties props = new Properties();
                props.load(in);
                p.shareServer = bool(props, "shareServer");
                p.shareLocation = bool(props, "shareLocation");
                p.autoJoinPublic = bool(props, "autoJoinPublic");
                p.killSwitch = bool(props, "killSwitch");
                p.chatMirror = bool(props, "chatMirror", true);
                p.debugLog = bool(props, "debugLog");
                p.defaultPublic = bool(props, "defaultPublic", true);
                try { p.defaultMaxPlayers = Integer.parseInt(props.getProperty("defaultMaxPlayers", "8")); } catch (NumberFormatException ignored) {  }
                p.defaultMaxPlayers = p.defaultMaxPlayers <= 0 ? 0
                    : Math.max(2, Math.min(LobbySettings.ADMIN_MAX_PLAYERS, p.defaultMaxPlayers));
                p.defaultLobbyName = props.getProperty("defaultLobbyName", "");
                p.defaultServer = props.getProperty("defaultServer", "");
                p.defaultPlugins = props.getProperty("defaultPlugins", "");
                p.discordSession = props.getProperty("discordSession", "");
                p.discordName = props.getProperty("discordName", "");
                p.discordIdToken = props.getProperty("discordIdToken", "");
                try { p.myDupeStatus = Integer.parseInt(props.getProperty("myDupeStatus", "-1")); } catch (NumberFormatException ignored) {  }
                addAll(p.blocked, props.getProperty("blocked", ""));
                addAll(p.trusted, props.getProperty("trusted", ""));
                addIdMap(p.blockedDiscord, props.getProperty("blockedDiscord", ""));
                addIdMap(p.bannedDiscord, props.getProperty("bannedDiscord", ""));
                readLobbyShare(p.lobbyShare, props);
            } catch (Throwable t) {
                AutismClientAddon.LOG.warn("Failed to read Matchmaking prefs", t);
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(p::flush, "mm-prefs-flush"));
        return p;
    }

    private void save() {
        dirty.set(true);
        if (scheduled.compareAndSet(false, true)) {
            try {
                WRITER.schedule(() -> { scheduled.set(false); flush(); }, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
            } catch (Throwable t) {
                scheduled.set(false);
                flush();
            }
        }
    }

    private void flush() {
        if (dirty.compareAndSet(true, false)) writeNow();
    }

    private synchronized void writeNow() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null) dir.mkdirs();
            Properties props = new Properties();
            props.setProperty("shareServer", Boolean.toString(shareServer));
            props.setProperty("shareLocation", Boolean.toString(shareLocation));
            props.setProperty("autoJoinPublic", Boolean.toString(autoJoinPublic));
            props.setProperty("killSwitch", Boolean.toString(killSwitch));
            props.setProperty("chatMirror", Boolean.toString(chatMirror));
            props.setProperty("debugLog", Boolean.toString(debugLog));
            props.setProperty("defaultPublic", Boolean.toString(defaultPublic));
            props.setProperty("defaultMaxPlayers", Integer.toString(defaultMaxPlayers));
            props.setProperty("defaultLobbyName", defaultLobbyName);
            props.setProperty("defaultServer", defaultServer);
            props.setProperty("defaultPlugins", defaultPlugins);
            props.setProperty("discordSession", discordSession);
            props.setProperty("discordName", discordName);
            props.setProperty("discordIdToken", discordIdToken);
            props.setProperty("myDupeStatus", Integer.toString(myDupeStatus));
            props.setProperty("blocked", String.join(",", blockedSnapshot()));
            synchronized (trusted) { props.setProperty("trusted", String.join(",", trusted)); }
            props.setProperty("blockedDiscord", idMapCsv(blockedDiscord));
            props.setProperty("bannedDiscord", idMapCsv(bannedDiscord));
            writeLobbyShare(lobbyShare, props);

            try (FileOutputStream out = new FileOutputStream(TMP)) {
                props.store(out, "Autism Matchmaking preferences (passphrases are never stored)");
            }
            try {
                java.nio.file.Files.move(TMP.toPath(), FILE.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
                java.nio.file.Files.move(TMP.toPath(), FILE.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("Failed to save Matchmaking prefs", t);
        }
    }

    private static boolean bool(Properties p, String key) { return bool(p, key, false); }
    private static boolean bool(Properties p, String key, boolean def) {
        return Boolean.parseBoolean(p.getProperty(key, Boolean.toString(def)));
    }

    private static void addAll(Set<String> set, String csv) {
        if (csv == null || csv.isBlank()) return;
        for (String s : csv.split(",")) { if (validFp(s)) addCapped(set, s.trim()); }
    }

    private static void readLobbyShare(Map<String, boolean[]> out, Properties props) {
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("lobby.")) continue;
            String rest = key.substring("lobby.".length());
            int idx = rest.endsWith(".shareServer") ? 0 : rest.endsWith(".shareLocation") ? 1 : -1;
            if (idx < 0 || !Boolean.parseBoolean(props.getProperty(key))) continue;
            String id = rest.substring(0, rest.length() - (idx == 0 ? ".shareServer" : ".shareLocation").length());
            if (id.isBlank() || id.length() > 128) continue;
            synchronized (out) {
                if (out.size() >= MAX_SET && !out.containsKey(id)) continue;
                boolean[] cur = out.get(id);
                if (cur == null) { cur = new boolean[2]; out.put(id, cur); }
                cur[idx] = true;
            }
        }
    }

    private static void writeLobbyShare(Map<String, boolean[]> map, Properties props) {
        synchronized (map) {
            for (Map.Entry<String, boolean[]> e : map.entrySet()) {
                if (e.getValue()[0]) props.setProperty("lobby." + e.getKey() + ".shareServer", "true");
                if (e.getValue()[1]) props.setProperty("lobby." + e.getKey() + ".shareLocation", "true");
            }
        }
    }

    private static void addIdMap(Map<String, String> map, String csv) {
        if (csv == null || csv.isBlank()) return;
        for (String entry : csv.split(",")) {
            int eq = entry.indexOf('=');
            String id = (eq >= 0 ? entry.substring(0, eq) : entry).trim();
            String name = eq >= 0 ? entry.substring(eq + 1).trim() : "";
            if (validId(id)) putCapped(map, id, name);
        }
    }

    private static String idMapCsv(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        synchronized (map) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (sb.length() > 0) sb.append(',');
                sb.append(e.getKey()).append('=').append(sanitizeName(e.getValue()));
            }
        }
        return sb.toString();
    }

    private static String sanitizeName(String n) {
        return n == null ? "" : n.replace(",", "").replace("=", "").trim();
    }

    private static void putCapped(Map<String, String> map, String id, String name) {
        synchronized (map) {
            map.put(id, sanitizeName(name));
            while (map.size() > MAX_SET) {
                Iterator<String> it = map.keySet().iterator();
                if (!it.hasNext()) break;
                it.next();
                it.remove();
            }
        }
    }

    private static void addCapped(Set<String> set, String value) {
        synchronized (set) {
            set.add(value);
            while (set.size() > MAX_SET) {
                Iterator<String> it = set.iterator();
                if (!it.hasNext()) break;
                it.next();
                it.remove();
            }
        }
    }

    private static boolean validFp(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty() || t.length() > 64) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    private static boolean validId(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty() || t.length() > 32) return false;
        for (int i = 0; i < t.length(); i++) if (t.charAt(i) < '0' || t.charAt(i) > '9') return false;
        return true;
    }
}
