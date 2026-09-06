package autismclient.util;

import autismclient.AutismClientAddon;
import net.minecraft.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class AutismSpotify {

    private static final String SEPARATOR = "\u001f";

    private static final long IDLE_TIMEOUT_MS = 15_000L;
    private static final long RESTART_BACKOFF_MS = 5_000L;
    private static final int MAX_RAPID_FAILURES = 3;
    private static final long HEALTHY_RUNTIME_MS = 30_000L;
    private static final long GIVE_UP_COOLDOWN_MS = 60_000L;
    private static final long WATCHDOG_INTERVAL_MS = 2_000L;
    private static final long TOOL_TIMEOUT_MS = 3_000L;
    private static final long GDBUS_POLL_MS = 2_000L;
    private static final long OSASCRIPT_POLL_MS = 1_500L;
    private static final long COMMAND_TIMEOUT_MS = 3_000L;

    private static final int ART_MAX_BYTES = 1 << 19;

    public enum Status {PLAYING, PAUSED, STOPPED, UNAVAILABLE}

    public enum Repeat {OFF, ONE, ALL, UNKNOWN}

    public record Snapshot(Status status, String artist, String title, long updatedAtMs,
                           double positionSec, double durationSec,
                           boolean shuffle, Repeat repeat, int volume, String artworkPath) {
        public Snapshot {
            if (status == null) status = Status.UNAVAILABLE;
            if (artist == null) artist = "";
            if (title == null) title = "";
            if (repeat == null) repeat = Repeat.UNKNOWN;
            if (artworkPath == null) artworkPath = "";
            if (volume > 100) volume = 100;
            if (volume < -1) volume = -1;
            if (Double.isNaN(positionSec) || positionSec < 0) positionSec = 0;
            if (Double.isNaN(durationSec) || durationSec < 0) durationSec = 0;
        }

        public Snapshot withTimestamp(long ms) {
            return new Snapshot(status, artist, title, ms, positionSec, durationSec,
                shuffle, repeat, volume, artworkPath);
        }

        public Snapshot withArtworkPath(String path) {
            return new Snapshot(status, artist, title, updatedAtMs, positionSec, durationSec,
                shuffle, repeat, volume, path);
        }
    }

    private static volatile Snapshot current =
        new Snapshot(Status.UNAVAILABLE, "", "", 0L, 0, 0, false, Repeat.UNKNOWN, -1, "");

    private static volatile long lastWantedAtMs;
    private static volatile boolean hooksInstalled;

    private static volatile boolean sourceAnywhere;

    private static final Object LOCK = new Object();
    private static Process process;
    private static Thread worker;
    private static int generation;
    private static int consecutiveFailures;
    private static boolean coolingDown;
    private static long gaveUpAtMs;

    private static final Map<String, Boolean> TOOL_AVAILABILITY = new ConcurrentHashMap<>();
    private static volatile Path windowsScriptPath;

    private static volatile Path artFilePath;

    private AutismSpotify() {
    }

    public static Snapshot snapshot() {
        return current;
    }

    public static void setWanted() {
        long now = System.currentTimeMillis();
        lastWantedAtMs = now;
        try {
            ensureHooks();
            synchronized (LOCK) {
                if (worker != null) return;
                if (coolingDown && now - gaveUpAtMs < GIVE_UP_COOLDOWN_MS) return;
                coolingDown = false;
                consecutiveFailures = 0;
                startLocked();
            }
        } catch (Throwable t) {

        }
    }

    public static void togglePlayPause() {
        command("PLAY_PAUSE");
    }

    public static void next() {
        command("NEXT");
    }

    public static void previous() {
        command("PREV");
    }

    public static void setShuffle(boolean on) {
        command(on ? "SHUFFLE_ON" : "SHUFFLE_OFF");
    }

    public static void cycleRepeat() {
        command("REPEAT=" + nextRepeat(snapshot().repeat()).name());
    }

    public static void setVolume(int percent) {
        command("VOLUME=" + clampVolume(percent));
    }

    public static void setSourceAnywhere(boolean anyMedia) {
        sourceAnywhere = anyMedia;

        command(anyMedia ? "SOURCE=ANY" : "SOURCE=SPOTIFY");
    }

    public static boolean sourceAnywhere() {
        return sourceAnywhere;
    }

    static Repeat nextRepeat(Repeat repeat) {
        if (repeat == null) return Repeat.OFF;
        return switch (repeat) {
            case OFF -> Repeat.ALL;
            case ALL -> Repeat.ONE;
            default -> Repeat.OFF;
        };
    }

    static int clampVolume(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private static void ensureHooks() {
        if (hooksInstalled) return;
        synchronized (LOCK) {
            if (hooksInstalled) return;
            hooksInstalled = true;
            Thread watchdog = new Thread(AutismSpotify::watchdogLoop, "autism-spotify-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
            Runtime.getRuntime().addShutdownHook(
                new Thread(AutismSpotify::shutdownChild, "autism-spotify-shutdown"));
            Thread artWorker = new Thread(AutismSpotify::artWorkerLoop, "autism-spotify-art");
            artWorker.setDaemon(true);
            artWorker.start();
        }
    }

    private static void watchdogLoop() {
        while (true) {
            try {
                Thread.sleep(WATCHDOG_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (LOCK) {
                if (worker != null
                    && System.currentTimeMillis() - lastWantedAtMs > IDLE_TIMEOUT_MS) {
                    stopLocked();
                }
            }
        }
    }

    private static void shutdownChild() {
        synchronized (LOCK) {
            stopLocked();
        }
    }

    private static void stopLocked() {
        generation++;
        Process child = process;
        process = null;
        worker = null;
        if (child != null) child.destroy();
        current = new Snapshot(Status.UNAVAILABLE, "", "", System.currentTimeMillis(),
            0, 0, false, Repeat.UNKNOWN, -1, "");
    }

    private static void startLocked() {
        Backend backend = pickBackend();
        if (backend == null) {
            if (current.status() != Status.UNAVAILABLE) {
                current = new Snapshot(Status.UNAVAILABLE, "", "", System.currentTimeMillis(),
                    0, 0, false, Repeat.UNKNOWN, -1, "");
            }
            return;
        }
        generation++;
        Thread thread = new Thread(() -> supervise(generation, backend), "autism-spotify-backend");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    private enum Backend {WINDOWS_PS, PLAYERCTL, GDBUS, OSX_OSA}

    private static Backend pickBackend() {
        Util.OS os = Util.getPlatform();
        if (os == Util.OS.WINDOWS) return Backend.WINDOWS_PS;
        if (os == Util.OS.OSX) return Backend.OSX_OSA;
        if (os == Util.OS.LINUX) {
            if (toolAvailable("playerctl")) return Backend.PLAYERCTL;
            if (toolAvailable("gdbus")) return Backend.GDBUS;
        }
        return null;
    }

    private static void supervise(int gen, Backend backend) {
        try {
            switch (backend) {
                case GDBUS -> gdbusLoop(gen);
                case OSX_OSA -> osascriptLoop(gen);
                default -> streamLoop(gen, backend);
            }
        } catch (Throwable t) {
            synchronized (LOCK) {
                if (worker == Thread.currentThread()) {
                    worker = null;
                    coolingDown = true;
                    gaveUpAtMs = System.currentTimeMillis();
                    current = new Snapshot(Status.UNAVAILABLE, "", "", System.currentTimeMillis(),
                        0, 0, false, Repeat.UNKNOWN, -1, "");
                }
            }
        }
    }

    private static void streamLoop(int gen, Backend backend) {
        boolean respawning = false;
        while (true) {
            if (respawning) {
                try {
                    Thread.sleep(RESTART_BACKOFF_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            synchronized (LOCK) {
                if (gen != generation) return;
            }
            Process child = spawn(backend);
            if (child == null) {
                if (!registerFailure(gen, 0L)) return;
                respawning = true;
                continue;
            }
            long startedAt = System.currentTimeMillis();
            synchronized (LOCK) {
                if (gen != generation) {
                    child.destroy();
                    return;
                }
                process = child;
            }
            readLines(gen, child, backend);
            long aliveMs = System.currentTimeMillis() - startedAt;
            child.destroy();
            synchronized (LOCK) {
                if (process == child) process = null;
            }
            if (!registerFailure(gen, aliveMs)) return;
            respawning = true;
        }
    }

    private static boolean registerFailure(int gen, long aliveMs) {
        synchronized (LOCK) {
            if (gen != generation) return false;
            consecutiveFailures = aliveMs >= HEALTHY_RUNTIME_MS ? 0 : consecutiveFailures + 1;
            if (consecutiveFailures >= MAX_RAPID_FAILURES) {
                coolingDown = true;
                gaveUpAtMs = System.currentTimeMillis();
                worker = null;
                current = new Snapshot(Status.UNAVAILABLE, "", "", gaveUpAtMs,
                    0, 0, false, Repeat.UNKNOWN, -1, "");
                return false;
            }
            return true;
        }
    }

    private static Process spawn(Backend backend) {
        try {
            ProcessBuilder builder = switch (backend) {
                case WINDOWS_PS -> new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", windowsScript().toString());
                case PLAYERCTL -> playerctlCommand();
                case GDBUS, OSX_OSA -> throw new IllegalStateException("polled backends have no streaming process");
            };
            if (backend == Backend.WINDOWS_PS) {

                builder.environment().put("AUTISM_SPOTIFY_SOURCE", sourceAnywhere ? "ANY" : "SPOTIFY");
            }
            return builder.redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (Exception e) {
            return null;
        }
    }

    private static ProcessBuilder playerctlCommand() {
        return new ProcessBuilder(playerctlArgv());
    }

    static java.util.List<String> playerctlArgv() {
        java.util.List<String> argv = new java.util.ArrayList<>();
        argv.add("playerctl");
        if (!sourceAnywhere) argv.add("--player=spotify");
        argv.addAll(java.util.List.of("--follow", "metadata", "--format",
            "{{status}}" + SEPARATOR + "{{artist}}" + SEPARATOR + "{{title}}" + SEPARATOR
                + "{{position}}" + SEPARATOR + "{{mpris:length}}" + SEPARATOR
                + "{{shuffle}}" + SEPARATOR + "{{loop}}" + SEPARATOR
                + "{{volume}}" + SEPARATOR + "{{mpris:artUrl}}"));
        return argv;
    }

    static String windowsScriptText() {
        return POWERSHELL_SCRIPT;
    }

    private static Path windowsScript() throws IOException {
        Path existing = windowsScriptPath;
        if (existing != null) return existing;
        synchronized (LOCK) {
            if (windowsScriptPath == null) {
                Path file = Files.createTempFile("autism_spotify", ".ps1");
                Files.writeString(file, POWERSHELL_SCRIPT, StandardCharsets.UTF_8);
                file.toFile().deleteOnExit();
                windowsScriptPath = file;
            }
            return windowsScriptPath;
        }
    }

    private static void readLines(int gen, Process child, Backend backend) {
        String lastArtUrl = "";
        String lastArtPath = "";
        try (BufferedReader reader =
                 new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Snapshot snap;
                try {
                    snap = backend == Backend.PLAYERCTL ? parsePlayerctl(line) : parseLine(line);
                } catch (Throwable t) {
                    continue;
                }
                if (backend == Backend.PLAYERCTL && snap.artworkPath().startsWith("http")) {

                    if (!snap.artworkPath().equals(lastArtUrl)) {
                        lastArtUrl = snap.artworkPath();
                        lastArtPath = downloadArt(lastArtUrl, snap.artist() + "|" + snap.title());
                    }
                    snap = snap.withArtworkPath(lastArtPath);
                }
                synchronized (LOCK) {
                    if (gen != generation) return;
                    store(snap);
                }
                maybeFetchArtFallback(snap);
            }
        } catch (Throwable t) {

        }
    }

    static void store(Snapshot next) {
        Snapshot prev = current;
        if (prev.status() == next.status()
            && prev.artist().equals(next.artist())
            && prev.title().equals(next.title())) {

            if (next.artworkPath().isEmpty() && !prev.artworkPath().isEmpty()) {
                next = next.withArtworkPath(prev.artworkPath());
            }
            current = next.withTimestamp(prev.updatedAtMs());
        } else {
            current = next;
        }
    }

    private static Snapshot unavailableNow() {
        return new Snapshot(Status.UNAVAILABLE, "", "", System.currentTimeMillis(),
            0, 0, false, Repeat.UNKNOWN, -1, "");
    }

    private static void command(String cmd) {
        try {
            Backend backend;
            Process child;
            synchronized (LOCK) {
                backend = pickBackend();
                child = process;
            }
            if (backend == null) return;
            final Process target = child;
            Thread t = new Thread(() -> runCommand(backend, target, cmd), "autism-spotify-command");
            t.setDaemon(true);
            t.start();
        } catch (Throwable t) {

        }
    }

    private static final Object COMMAND_WRITE_LOCK = new Object();

    private static void runCommand(Backend backend, Process child, String cmd) {
        try {
            switch (backend) {
                case WINDOWS_PS -> {
                    if (child == null || !child.isAlive()) return;
                    synchronized (COMMAND_WRITE_LOCK) {
                        OutputStream stdin = child.getOutputStream();
                    stdin.write((cmd + "\n").getBytes(StandardCharsets.UTF_8));
                        stdin.flush();
                    }
                }
                case PLAYERCTL -> runAndDiscard(playerctlAction(cmd));
                case OSX_OSA -> runAndDiscard(osascriptAction(cmd));
                default -> {  }
            }
        } catch (Throwable t) {

        }
    }

    static String[] playerctlAction(String cmd) {
        String[] base = sourceAnywhere ? new String[]{"playerctl"} : new String[]{"playerctl", "--player=spotify"};
        String[] action = switch (cmd) {
            case "PLAY_PAUSE" -> new String[]{"play-pause"};
            case "NEXT" -> new String[]{"next"};
            case "PREV" -> new String[]{"previous"};
            case "SHUFFLE_ON" -> new String[]{"shuffle", "On"};
            case "SHUFFLE_OFF" -> new String[]{"shuffle", "Off"};
            case "REPEAT=OFF" -> new String[]{"loop", "None"};
            case "REPEAT=ALL" -> new String[]{"loop", "Playlist"};
            case "REPEAT=ONE" -> new String[]{"loop", "Track"};
            default -> cmd.startsWith("VOLUME=")
                ? new String[]{"volume", String.format(Locale.ROOT, "%.2f",
                    clampVolume(Integer.parseInt(cmd.substring(7))) / 100.0)}
                : null;
        };
        if (action == null) return null;
        String[] argv = new String[base.length + action.length];
        System.arraycopy(base, 0, argv, 0, base.length);
        System.arraycopy(action, 0, argv, base.length, action.length);
        return argv;
    }

    static String[] osascriptAction(String cmd) {
        String apple = switch (cmd) {
            case "PLAY_PAUSE" -> "tell application \"Spotify\" to playpause";
            case "NEXT" -> "tell application \"Spotify\" to next track";
            case "PREV" -> "tell application \"Spotify\" to previous track";
            case "SHUFFLE_ON" -> "tell application \"Spotify\" to set shuffling to true";
            case "SHUFFLE_OFF" -> "tell application \"Spotify\" to set shuffling to false";
            case "REPEAT=OFF" -> "tell application \"Spotify\" to set repeating to false";
            case "REPEAT=ALL", "REPEAT=ONE" -> "tell application \"Spotify\" to set repeating to true";
            default -> cmd.startsWith("VOLUME=")
                ? "tell application \"Spotify\" to set sound volume to " + clampVolume(Integer.parseInt(cmd.substring(7)))
                : null;
        };
        return apple == null ? null : new String[]{"osascript", "-e", apple};
    }

    private static void runAndDiscard(String[] argv) {
        if (argv == null) return;
        Process process = null;
        try {
            process = new ProcessBuilder(argv)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            if (!process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
        }
    }

    private static boolean toolAvailable(String tool) {
        return TOOL_AVAILABILITY.computeIfAbsent(tool, AutismSpotify::probeTool);
    }

    private static boolean probeTool(String tool) {
        Process process = null;
        try {
            process = new ProcessBuilder("sh", "-c", "command -v " + tool)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            if (!process.waitFor(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            return false;
        }
    }

    private static void gdbusLoop(int gen) {
        while (true) {
            Snapshot parsed;
            try {
                parsed = parseGdbus(gdbusQuery());
            } catch (Throwable t) {
                parsed = unavailableNow();
            }
            synchronized (LOCK) {
                if (gen != generation) return;
                store(parsed);
            }
            maybeFetchArtFallback(parsed);
            try {
                Thread.sleep(GDBUS_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static String gdbusQuery() {
        Process process = null;
        try {
            process = new ProcessBuilder(
                "gdbus", "call", "--session",
                "--dest", "org.mpris.MediaPlayer2.spotify",
                "--object-path", "/org/mpris/MediaPlayer2",
                "--method", "org.freedesktop.DBus.Properties.GetAll",
                "org.mpris.MediaPlayer2.Player")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            final Process child = process;
            CompletableFuture<byte[]> reader = CompletableFuture.supplyAsync(() -> {
                try {
                    return child.getInputStream().readAllBytes();
                } catch (IOException e) {
                    return new byte[0];
                }
            });
            if (!process.waitFor(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return "";
            }
            byte[] out = reader.get(1, TimeUnit.SECONDS);
            if (process.exitValue() != 0) return "";
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            return "";
        }
    }

    private static void osascriptLoop(int gen) {
        while (true) {
            Snapshot parsed;
            try {
                parsed = parseOsascript(osascriptQuery());
            } catch (Throwable t) {
                parsed = unavailableNow();
            }
            synchronized (LOCK) {
                if (gen != generation) return;
                store(parsed);
            }
            maybeFetchArtFallback(parsed);
            try {
                Thread.sleep(OSASCRIPT_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static String osascriptQuery() {
        Process process = null;
        try {
            process = new ProcessBuilder("osascript", "-e", OSASCRIPT_QUERY)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            final Process child = process;
            CompletableFuture<byte[]> reader = CompletableFuture.supplyAsync(() -> {
                try {
                    return child.getInputStream().readAllBytes();
                } catch (IOException e) {
                    return new byte[0];
                }
            });
            if (!process.waitFor(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return "";
            }
            byte[] out = reader.get(1, TimeUnit.SECONDS);
            if (process.exitValue() != 0) return "";
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            return "";
        }
    }

    private static final Object ART_FILE_LOCK = new Object();

    static String downloadArt(String url) {
        return downloadArt(url, null);
    }

    static String downloadArt(String url, String ownerKey) {
        try {
            byte[] data;
            java.net.URLConnection connection = java.net.URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            try (var in = connection.getInputStream()) {
                data = in.readNBytes(ART_MAX_BYTES + 1);
            }
            if (!isImageBytes(data)) return "";

            data = AutismImageCodec.ensurePng(data);
            if (data == null) return "";
            Path file = artFilePath;
            if (file == null) {
                synchronized (LOCK) {
                    if (artFilePath == null) {
                        artFilePath = Files.createTempFile("autism_spotify_art", ".img");
                        artFilePath.toFile().deleteOnExit();
                    }
                    file = artFilePath;
                }
            }

            Path sibling = file.resolveSibling(file.getFileName() + "." + System.nanoTime() + ".part");
            Files.write(sibling, data);
            synchronized (ART_FILE_LOCK) {
                try {
                    Files.move(sibling, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(sibling);
                }

                lastArtFileKey = ownerKey;
                lastArtFilePath = ownerKey == null ? null : file.toString();
            }
            return file.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    static boolean isImageBytes(byte[] data) {
        if (data == null || data.length < 3) return false;
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) return true;
        return (data[0] & 0xFF) == 0x89 && (data[1] & 0xFF) == 0x50 && (data[2] & 0xFF) == 0x4E;
    }

    private static final Pattern ARTWORK_URL100 =
        Pattern.compile("\"artworkUrl100\"\s*:\s*\"([^\"]+)\"");

    private static volatile String pendingArtKey;

    private static final long ART_LOG_INTERVAL_MS = 30_000L;
    static final Map<String, Long> ART_LOG_LAST = new ConcurrentHashMap<>();

    static void logArt(String stage, String message) {

        if (!AutismClientAddon.DEBUG) return;
        long now = System.currentTimeMillis();
        Long last = ART_LOG_LAST.get(stage);
        if (last == null || now - last >= ART_LOG_INTERVAL_MS) {
            ART_LOG_LAST.put(stage, now);
            AutismClientAddon.LOG.info("[Autism] Spotify art [{}] {}", stage, message);
        }
    }

    static final int ART_CACHE_MAX_ENTRIES = 512;

    static final Map<String, String> ART_FALLBACK_CACHE =
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > ART_CACHE_MAX_ENTRIES;
            }
        };

    private static volatile String lastArtFileKey;
    private static volatile String lastArtFilePath;

    static String artCacheLookup(String key) {
        if (key != null && key.equals(lastArtFileKey)) return lastArtFilePath;
        return ART_FALLBACK_CACHE.get(key);
    }

    static void artCacheStore(String key, String path) {
        if (key == null || path == null) return;
        if (path.isEmpty()) {
            ART_FALLBACK_CACHE.put(key, "");
        } else {
            lastArtFileKey = key;
            lastArtFilePath = path;
        }
    }

    private static void maybeFetchArtFallback(Snapshot snap) {
        if (snap.status() != Status.PLAYING && snap.status() != Status.PAUSED) return;
        if (snap.artist().isEmpty() || snap.title().isEmpty() || !snap.artworkPath().isEmpty()) return;
        ensureHooks();
        pendingArtKey = snap.artist() + "|" + snap.title();
        logArt("enqueue", pendingArtKey);
    }

    private static void artWorkerLoop() {
        while (true) {
            String key = pendingArtKey;
            if (key == null) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }
            pendingArtKey = null;
            try {
                String path = artCacheLookup(key);
                boolean freshQuery = path == null;
                if (freshQuery) {
                    path = queryArtFallback(key);
                    artCacheStore(key, path);
                }
                logArt("query", key + " -> " + (path == null ? "transient-failure(retry)"
                    : path.isEmpty() ? "no-result(cached)" : path + (freshQuery ? artRungSuffix() : " (file alive)")));
                if (path != null && !path.isEmpty()) {
                    synchronized (LOCK) {
                        Snapshot cur = current;

                        if ((cur.artist() + "|" + cur.title()).equals(key) && cur.artworkPath().isEmpty()) {
                            store(cur.withArtworkPath(path));
                            logArt("publish", key + " -> " + path);
                        }
                    }
                }
            } catch (Throwable t) {

            }
        }
    }

    static String itunesBaseUrl = "https://itunes.apple.com/search";

    private static final String[] ART_RUNG_NAMES = {"strict", "title", "artist", "top-hit", "artist-query"};

    private static volatile int lastArtRung = -1;

    private static String artRungSuffix() {
        return lastArtRung < 0 ? "" : " (rung " + ART_RUNG_NAMES[lastArtRung] + ")";
    }

    record Selection(ArtCandidate candidate, int rung, boolean itunesSource, boolean transientSource) {
    }

    static String queryArtFallback(String key) {
        return queryArtFallback(key, (source, term) -> fetchJson("itunes".equals(source)
            ? itunesBaseUrl + "?term=" + URLEncoder.encode(term, StandardCharsets.UTF_8) + "&entity=song&limit=5"
            : deezerBaseUrl + "?q=" + URLEncoder.encode(term, StandardCharsets.UTF_8) + "&limit=5"));
    }

    static String queryArtFallback(String key, java.util.function.BiFunction<String, String, String> fetcher) {
        lastArtRung = -1;
        String trackTerm = keyArtist(key).isEmpty() ? keyTitle(key) : key.replace('|', ' ');
        Selection selection = selectBest(key,
            parseCandidates(fetcher.apply("itunes", trackTerm), true),
            parseCandidates(fetcher.apply("deezer", trackTerm), false));
        if (selection.candidate != null) return downloadSelection(selection, key, selection.rung());
        if (selection.transientSource()) return null;

        String artist = keyArtist(key).trim();
        if (artist.isEmpty() || keyTitle(key).trim().isEmpty()) return "";
        Selection widened = selectBest(key,
            parseCandidates(fetcher.apply("itunes", artist), true),
            parseCandidates(fetcher.apply("deezer", artist), false));
        if (widened.candidate != null) return downloadSelection(widened, key, 4);
        return widened.transientSource() ? null : "";
    }

    private static java.util.List<ArtCandidate> parseCandidates(String json, boolean itunes) {
        return json == null ? null : (itunes ? itunesCandidates(json) : deezerCandidates(json));
    }

    static Selection selectBest(String key, java.util.List<ArtCandidate> itunes,
                                java.util.List<ArtCandidate> deezer) {
        String artist = keyArtist(key);
        String title = keyTitle(key);
        for (int rung = 0; rung <= 3; rung++) {
            ArtCandidate candidate = pickAtRung(itunes, artist, title, rung);
            if (candidate != null) return new Selection(candidate, rung, true, false);
            candidate = pickAtRung(deezer, artist, title, rung);
            if (candidate != null) return new Selection(candidate, rung, false, false);
        }
        return new Selection(null, -1, false, itunes == null || deezer == null);
    }

    private static ArtCandidate pickAtRung(java.util.List<ArtCandidate> candidates, String artist, String title, int rung) {
        if (candidates == null) return null;
        ArtCandidate firstJunk = null;
        for (ArtCandidate candidate : candidates) {
            boolean junk = isJunkCover(artist, title, candidate);
            if (junk && rung < 3) continue;
            boolean match = switch (rung) {
                case 0 -> validatesArtworkCandidate(artist, title, candidate.artist(), candidate.title());
                case 1 -> titleMatches(title, candidate.title());
                case 2 -> artistMatches(artist, candidate.artist());
                default -> true;
            };
            if (!match) continue;
            if (!junk) return candidate;
            if (firstJunk == null) firstJunk = candidate;
        }
        return rung == 3 ? firstJunk : null;
    }

    private static final String[] COVER_JUNK_MARKERS = {
        "karaoke", "tribute", "emulation", "chiptune", "8-bit", "16-bit", "8 bit", "16 bit",
        "lofi version", "cover version", "made famous", "in the style of", "instrumental version"
    };

    static boolean isJunkCover(String snapshotArtist, String snapshotTitle, ArtCandidate candidate) {
        String snapArtist = snapshotArtist.toLowerCase(Locale.ROOT);
        String snapTitle = snapshotTitle.toLowerCase(Locale.ROOT);
        String candArtist = candidate.artist().toLowerCase(Locale.ROOT);
        String candTitle = candidate.title().toLowerCase(Locale.ROOT);
        for (String marker : COVER_JUNK_MARKERS) {
            if ((candArtist.contains(marker) || candTitle.contains(marker))
                && !snapArtist.contains(marker) && !snapTitle.contains(marker)) return true;
        }
        return false;
    }

    private static String downloadSelection(Selection selection, String key, int rungLabel) {
        String url = selection.itunesSource()
            ? upgradeArtworkUrl(selection.candidate().imageUrl())
            : selection.candidate().imageUrl();
        String path = downloadArt(url, key);
        if (path.isEmpty()) return null;
        lastArtRung = rungLabel;
        return path;
    }

    private static String keyArtist(String key) {
        int sep = key.indexOf('|');
        return sep < 0 ? key : key.substring(0, sep);
    }

    private static String keyTitle(String key) {
        int sep = key.indexOf('|');
        return sep < 0 ? key : key.substring(sep + 1);
    }

    private static String fetchJson(String url) {
        try {
            java.net.URLConnection connection = java.net.URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            try (var in = connection.getInputStream()) {
                return new String(in.readNBytes(1 << 18), StandardCharsets.UTF_8);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    static java.util.List<ArtCandidate> itunesCandidates(String json) {
        java.util.List<ArtCandidate> candidates = new java.util.ArrayList<>();
        if (json == null) return candidates;
        try {
            for (com.google.gson.JsonElement element : com.google.gson.JsonParser.parseString(json)
                    .getAsJsonObject().getAsJsonArray("results")) {
                try {
                    com.google.gson.JsonObject result = element.getAsJsonObject();
                    String artist = jsonString(result, "artistName");
                    String title = jsonString(result, "trackName");
                    String imageUrl = jsonString(result, "artworkUrl100");
                    if (artist != null && title != null && imageUrl != null) {
                        candidates.add(new ArtCandidate(artist, title, imageUrl));
                    }
                } catch (Throwable ignored) {

                }
            }
        } catch (Throwable ignored) {

        }
        return candidates;
    }

    static java.util.List<ArtCandidate> deezerCandidates(String json) {
        java.util.List<ArtCandidate> candidates = new java.util.ArrayList<>();
        if (json == null) return candidates;
        try {
            for (com.google.gson.JsonElement element : com.google.gson.JsonParser.parseString(json)
                    .getAsJsonObject().getAsJsonArray("data")) {
                try {
                    com.google.gson.JsonObject item = element.getAsJsonObject();
                    String title = jsonString(item, "title");
                    String artist = jsonString(item.getAsJsonObject("artist"), "name");
                    com.google.gson.JsonObject album = item.getAsJsonObject("album");
                    String imageUrl = jsonString(album, "cover_big");
                    if (imageUrl == null) imageUrl = jsonString(album, "cover_xl");
                    if (artist != null && title != null && imageUrl != null) {
                        candidates.add(new ArtCandidate(artist, title, imageUrl));
                    }
                } catch (Throwable ignored) {

                }
            }
        } catch (Throwable ignored) {

        }
        return candidates;
    }

    private static String jsonString(com.google.gson.JsonObject object, String field) {
        com.google.gson.JsonElement element = object == null ? null : object.get(field);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    static String extractArtworkUrl(String json) {
        if (json == null) return "";
        var matcher = ARTWORK_URL100.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    static String deezerBaseUrl = "https://api.deezer.com/search";

    record ArtCandidate(String artist, String title, String imageUrl) {
    }

    static String normalizeMusicText(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
            .replaceAll("\\([^)]*\\)", " ")
            .replaceAll("\\[[^]]*\\]", " ")
            .replaceAll("\\b(feat\\.|ft\\.|featuring|slowed|reverb|remix|sped up|nightcore)\\b", " ")
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .trim();
    }

    private static boolean titleMatches(String snapshotTitle, String candidateTitle) {
        String snapTitle = normalizeMusicText(snapshotTitle);
        String candTitle = normalizeMusicText(candidateTitle);
        return !snapTitle.isEmpty() && !candTitle.isEmpty()
            && (snapTitle.contains(candTitle) || candTitle.contains(snapTitle));
    }

    private static boolean artistMatches(String snapshotArtist, String candidateArtist) {
        String snapArtist = normalizeMusicText(snapshotArtist);
        String candArtist = normalizeMusicText(candidateArtist);
        if (snapArtist.isEmpty() || candArtist.isEmpty()) return false;
        for (String token : snapArtist.split(" ")) {
            if (!token.isEmpty() && candArtist.contains(token)) return true;
        }
        for (String token : candArtist.split(" ")) {
            if (!token.isEmpty() && snapArtist.contains(token)) return true;
        }
        return false;
    }

    static boolean validatesArtworkCandidate(String snapshotArtist, String snapshotTitle,
                                             String candidateArtist, String candidateTitle) {
        return titleMatches(snapshotTitle, candidateTitle) && artistMatches(snapshotArtist, candidateArtist);
    }

    static ArtCandidate firstValidatingCandidate(java.util.List<ArtCandidate> candidates,
                                                 String snapshotArtist, String snapshotTitle) {
        for (ArtCandidate candidate : candidates) {
            if (validatesArtworkCandidate(snapshotArtist, snapshotTitle, candidate.artist(), candidate.title())) {
                return candidate;
            }
        }
        return null;
    }

    static String upgradeArtworkUrl(String url) {
        return url == null ? "" : url.replace("100x100bb", "600x600bb");
    }

    static Snapshot parseLine(String line) {
        long now = System.currentTimeMillis();
        if (line == null || line.isBlank()) return unavailable(now);
        String[] p = line.split(SEPARATOR, -1);
        Status status = parseStatus(p[0]);
        if (status == Status.UNAVAILABLE) return unavailable(now);
        String artist = p.length > 1 ? p[1] : "";
        String title = p.length > 2 ? p[2] : "";
        if (status == Status.STOPPED || title.isEmpty()) {
            return new Snapshot(Status.STOPPED, "", "", now, 0, 0, false, Repeat.UNKNOWN, -1, "");
        }
        double pos = p.length > 3 ? parseDouble(p[3], 0) : 0;
        double dur = p.length > 4 ? parseDouble(p[4], 0) : 0;
        boolean shuffle = p.length > 5 && isTrueish(p[5]);
        Repeat repeat = p.length > 6 ? parseRepeat(p[6]) : Repeat.UNKNOWN;
        int volume = p.length > 7 ? parseVolume(p[7]) : -1;
        String art = p.length > 8 ? p[8] : "";
        return new Snapshot(status, artist, title, now, pos, dur, shuffle, repeat, volume, art);
    }

    static Snapshot parsePlayerctl(String line) {
        long now = System.currentTimeMillis();
        if (line == null || line.isBlank()) return unavailable(now);
        String[] p = line.split(SEPARATOR, -1);
        Status status = parseStatus(p[0]);
        if (status == Status.UNAVAILABLE) return unavailable(now);
        String artist = p.length > 1 ? p[1] : "";
        String title = p.length > 2 ? p[2] : "";
        if (status == Status.STOPPED || title.isEmpty()) {
            return new Snapshot(Status.STOPPED, "", "", now, 0, 0, false, Repeat.UNKNOWN, -1, "");
        }
        double pos = p.length > 3 ? parseMicros(p[3]) : 0;
        double dur = p.length > 4 ? parseMicros(p[4]) : 0;
        boolean shuffle = p.length > 5 && isTrueish(p[5]);
        Repeat repeat = p.length > 6 ? parseRepeat(p[6]) : Repeat.UNKNOWN;
        int volume = -1;
        if (p.length > 7 && !p[7].isBlank()) {
            double v = parseDouble(p[7], Double.NaN);
            if (!Double.isNaN(v)) volume = (int) Math.round(v * 100.0);
        }
        String art = "";
        if (p.length > 8) {
            String url = p[8];
            if (url.startsWith("file://")) art = url.substring("file://".length()).replace("%20", " ");
            else if (url.startsWith("http://") || url.startsWith("https://")) art = url;
        }
        return new Snapshot(status, artist, title, now, pos, dur, shuffle, repeat, volume, art);
    }

    static Snapshot parseOsascript(String rawStdout) {
        if (rawStdout == null) return unavailable(System.currentTimeMillis());
        String line = rawStdout;
        int newline = line.indexOf('\n');
        if (newline >= 0) line = line.substring(0, newline);
        if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
        long now = System.currentTimeMillis();
        if (line.isBlank()) return unavailable(now);
        String[] p = line.split(SEPARATOR, -1);
        Status status = parseStatus(p[0]);
        if (status == Status.UNAVAILABLE) return unavailable(now);
        String artist = p.length > 1 ? p[1] : "";
        String title = p.length > 2 ? p[2] : "";
        if (status == Status.STOPPED || title.isEmpty()) {
            return new Snapshot(Status.STOPPED, "", "", now, 0, 0, false, Repeat.UNKNOWN, -1, "");
        }
        double pos = p.length > 3 ? parseDouble(p[3], 0) / 1000.0 : 0;
        double dur = p.length > 4 ? parseDouble(p[4], 0) / 1000.0 : 0;
        boolean shuffle = p.length > 5 && isTrueish(p[5]);
        Repeat repeat = Repeat.UNKNOWN;
        if (p.length > 6) {
            if (isTrueish(p[6])) repeat = Repeat.ALL;
            else if (!p[6].isBlank()) repeat = Repeat.OFF;
        }
        int volume = p.length > 7 ? parseVolume(p[7]) : -1;
        String art = p.length > 8 ? p[8] : "";
        return new Snapshot(status, artist, title, now, pos, dur, shuffle, repeat, volume, art);
    }

    static Snapshot parseGdbus(String output) {
        long now = System.currentTimeMillis();
        if (output == null || output.isBlank()) return unavailable(now);
        String statusToken = gvariantString(output, "PlaybackStatus");
        if (statusToken == null) return unavailable(now);
        Status status = parseStatus(statusToken);
        if (status == Status.UNAVAILABLE) return unavailable(now);
        String title = gvariantString(output, "xesam:title");
        if (status == Status.STOPPED || title == null || title.isEmpty()) {
            return new Snapshot(Status.STOPPED, "", "", now, 0, 0, false, Repeat.UNKNOWN, -1, "");
        }
        String artist = gvariantFirstArrayString(output, "xesam:artist");
        double dur = 0;
        String length = gvariantString(output, "mpris:length");
        if (length != null) {

            String digits = length.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) dur = parseDouble(digits, 0) / 1_000_000.0;
        }
        return new Snapshot(status, artist == null ? "" : artist, title, now,
            0, dur, false, Repeat.UNKNOWN, -1, "");
    }

    private static Snapshot unavailable(long now) {
        return new Snapshot(Status.UNAVAILABLE, "", "", now, 0, 0, false, Repeat.UNKNOWN, -1, "");
    }

    private static Status parseStatus(String token) {
        if (token == null) return Status.UNAVAILABLE;
        return switch (token.trim().toUpperCase(Locale.ROOT)) {
            case "PLAYING" -> Status.PLAYING;
            case "PAUSED" -> Status.PAUSED;
            case "STOPPED" -> Status.STOPPED;
            default -> Status.UNAVAILABLE;
        };
    }

    private static double parseDouble(String token, double fallback) {
        try {
            return Double.parseDouble(token.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double parseMicros(String token) {
        return parseDouble(token, 0) / 1_000_000.0;
    }

    static int parseVolume(String token) {
        if (token == null || token.isBlank()) return -1;
        try {

            int v = Integer.parseInt(token.trim());
            return v < 0 ? -1 : clampVolume(v);
        } catch (Exception e) {
            return -1;
        }
    }

    private static boolean isTrueish(String token) {
        String t = token.trim().toLowerCase(Locale.ROOT);
        return t.equals("1") || t.equals("true") || t.equals("on");
    }

    static Repeat parseRepeat(String token) {
        return switch (token.trim().toUpperCase(Locale.ROOT)) {
            case "OFF", "NONE" -> Repeat.OFF;
            case "ONE", "TRACK" -> Repeat.ONE;
            case "ALL", "PLAYLIST" -> Repeat.ALL;
            default -> Repeat.UNKNOWN;
        };
    }

    private static String gvariantString(String output, String key) {
        return gvariantQuotedAfter(output, "'" + key + "': <");
    }

    private static String gvariantFirstArrayString(String output, String key) {
        return gvariantQuotedAfter(output, "'" + key + "': <[");
    }

    private static String gvariantQuotedAfter(String output, String needle) {
        int start = output.indexOf(needle);
        if (start < 0) return null;
        start += needle.length();
        if (start >= output.length()) return null;
        char quote = output.charAt(start);
        if (quote != '\'' && quote != '"') return null;
        int from = start + 1;
        while (true) {
            int end = output.indexOf(quote, from);
            if (end < 0) return null;
            int backslashes = 0;
            for (int j = end - 1; j >= from && output.charAt(j) == '\\'; j--) backslashes++;
            if (backslashes % 2 == 0) {
                return gvariantUnescape(output.substring(from, end), quote);
            }
            from = end + 1;
        }
    }

    private static String gvariantUnescape(String value, char quote) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()
                && (value.charAt(i + 1) == '\\' || value.charAt(i + 1) == quote)) {
                out.append(value.charAt(++i));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static final String POWERSHELL_SCRIPT = """
        $ErrorActionPreference = 'Continue'
        [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
        Add-Type -AssemblyName System.Runtime.WindowsRuntime

        $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
        function Await-Op($op, $resultType) {
            $task = $asTaskGeneric.MakeGenericMethod($resultType).Invoke($null, @($op))
            if (-not $task.Wait(5000)) { throw 'timed out waiting for a WinRT operation' }
            return $task.Result
        }

        $code = @'
        using System;
        using System.Collections.Generic;
        using System.Diagnostics;
        using System.Runtime.InteropServices;

        // ISimpleAudioVolume get/set for the session belonging to a process-name hint.
        // Raw vtable throughout: on machines with an audio enhancement driver (probed on
        // this one), the session ENUMERATOR object answers QueryInterface with
        // E_NOINTERFACE for interfaces its vtable actually implements, so the typed RCW
        // path cannot reach it; the vtable contracts are stable, so calling slots
        // directly works on wrapped and healthy machines alike.
        public static class AutismSpotifyVolume {
            [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            public interface IMMDeviceEnumerator {
                [PreserveSig] int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
                [PreserveSig] int GetDefaultAudioEndpoint(int dataFlow, int role, out IntPtr device);
                [PreserveSig] int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string id, out IntPtr device);
                [PreserveSig] int RegisterEndpointNotificationCallback(IntPtr client);
                [PreserveSig] int UnregisterEndpointNotificationCallback(IntPtr client);
            }

            [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
            public class MMDeviceEnumeratorComObject { }

            static readonly Guid IID_MGR2 = new Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F");
            static readonly Guid IID_VOLUME = new Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8");

            [UnmanagedFunctionPointer(CallingConvention.StdCall)]
            delegate int ComOutOut(IntPtr t, out IntPtr v);
            [UnmanagedFunctionPointer(CallingConvention.StdCall)]
            delegate int ComGetCount(IntPtr t, out int v);
            [UnmanagedFunctionPointer(CallingConvention.StdCall)]
            delegate int ComItem(IntPtr t, int i, out IntPtr v);
            [UnmanagedFunctionPointer(CallingConvention.StdCall)]
            delegate int ComActivate(IntPtr t, [MarshalAs(UnmanagedType.LPStruct)] Guid iid, int c, IntPtr p, out IntPtr v);
            [UnmanagedFunctionPointer(CallingConvention.StdCall)]
            delegate int ComGetPid(IntPtr t, out uint pid);
            [UnmanagedFunctionPointer(CallingConvention.StdCall)]
            delegate int ComGetVolume(IntPtr t, out float level);
            [UnmanagedFunctionPointer(CallingConvention.StdCall)]
            delegate int ComSetVolume(IntPtr t, float level, IntPtr ctx);

            static IntPtr Slot(IntPtr obj, int slot) {
                return Marshal.ReadIntPtr(Marshal.ReadIntPtr(obj), slot * IntPtr.Size);
            }

            // Album art, take four. Every earlier read path failed on this box, all
            // probed: PowerShell's binder cannot bind the WinRT AsStream extension, a
            // winmd reference for compile-time WinRT types fails to load (0x80131047),
            // the returned stream's RCW exposes no interfaces, and vtable reads against a
            // NATIVE buffer (IBufferFactory) either lie (hollow S_OK) or segfault the
            // helper (exit 139). So the read buffer is a .NET-BACKED one:
            // System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBuffer wraps a
            // managed byte[] as an IBuffer with a CLR-generated, guaranteed-valid vtable.
            // The only native vtable call left is IInputStream.ReadAsync (slot 6), proven
            // safe here. Bytes come back through WindowsRuntimeBufferExtensions.ToArray,
            // purely managed. Crash containment: the MTA worker (apartment sensitivity,
            // probed) plus HandleProcessCorruptedStateExceptions, and an all-zero guard -
            // on this stack the read returns S_OK with a NULL op and a zero-filled buffer
            // (hollow success), which must read as failure, never as a 4 MB black image.
            [System.Runtime.ExceptionServices.HandleProcessCorruptedStateExceptions]
            static byte[] ReadArtStreamMta(object streamObj) {
                IntPtr streamPtr = IntPtr.Zero, istream = IntPtr.Zero, bufPtr = IntPtr.Zero, op = IntPtr.Zero;
                try {
                    System.Reflection.Assembly wrtAsm = null;
                    foreach (System.Reflection.Assembly asm in AppDomain.CurrentDomain.GetAssemblies()) {
                        if (asm.GetName().Name == "System.Runtime.WindowsRuntime") { wrtAsm = asm; break; }
                    }
                    if (wrtAsm == null) return null;
                    Type wrb = wrtAsm.GetType("System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBuffer");
                    Type ext = wrtAsm.GetType("System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions");
                    if (wrb == null || ext == null) return null;
                    System.Reflection.MethodInfo create = null, toArray = null;
                    foreach (System.Reflection.MethodInfo m in wrb.GetMethods()) {
                        if (m.Name == "Create" && m.GetParameters().Length == 4) { create = m; break; }
                    }
                    foreach (System.Reflection.MethodInfo m in ext.GetMethods()) {
                        if (m.Name == "ToArray" && m.GetParameters().Length == 1) { toArray = m; break; }
                    }
                    if (create == null || toArray == null) return null;
                    byte[] backing = new byte[4 * 1024 * 1024];
                    object buffer = create.Invoke(null, new object[] { backing, 0, backing.Length, backing.Length });
                    Guid iidStream = new Guid("905a0fe1-bc53-11df-8c49-001e4fc686da");
                    streamPtr = Marshal.GetIUnknownForObject(streamObj);
                    if (Marshal.QueryInterface(streamPtr, ref iidStream, out istream) < 0 || istream == IntPtr.Zero) return null;
                    bufPtr = Marshal.GetIUnknownForObject(buffer);
                    int hrRead = Marshal.GetDelegateForFunctionPointer<ComReadAsync>(Slot(istream, 6))(istream, bufPtr, (uint)backing.Length, 0u, out op);
                    if (hrRead < 0) return null;
                    if (op != IntPtr.Zero) { // async op exists: wait it out; a null op means the fill was synchronous
                        int status = 0;
                        long deadline = DateTime.UtcNow.Ticks + 5L * 10000000L;
                        while (status == 0 && DateTime.UtcNow.Ticks < deadline) {
                            System.Threading.Thread.Sleep(10);
                            if (Marshal.GetDelegateForFunctionPointer<ComGetCount>(Slot(op, 7))(op, out status) < 0) return null;
                        }
                        if (status != 1) return null;
                    }
                    byte[] result = (byte[])toArray.Invoke(null, new object[] { buffer });
                    // The hollow-success guard: a hollow read leaves the wrapper at its
                    // initial all-zero content; real album art always has non-zero bytes.
                    bool anyNonZero = false;
                    for (int i = 0; i < result.Length; i++) {
                        if (result[i] != 0) { anyNonZero = true; break; }
                    }
                    return anyNonZero ? result : null;
                } catch (System.Exception) {
                    return null;
                } finally {
                    if (op != IntPtr.Zero) Marshal.Release(op);
                    if (bufPtr != IntPtr.Zero) Marshal.Release(bufPtr);
                    if (istream != IntPtr.Zero) Marshal.Release(istream);
                    if (streamPtr != IntPtr.Zero) Marshal.Release(streamPtr);
                }
            }

            [UnmanagedFunctionPointer(CallingConvention.StdCall)]
            delegate int ComReadAsync(IntPtr t, IntPtr buffer, uint count, uint options, out IntPtr op);

            static HashSet<int> hintPids = new HashSet<int>();
            static string hintCached = "";
            static DateTime hintRefreshed = DateTime.MinValue;

            // Process-name matching by either-way containment: "spotify" hits Spotify.exe,
            // and a packaged AUMID head like "SpotifyAB.SpotifyMusic_xxx" still contains it.
            static HashSet<int> PidsForHint(string appHint) {
                if (appHint != hintCached || (DateTime.UtcNow - hintRefreshed).TotalSeconds > 2.0) {
                    HashSet<int> set = new HashSet<int>();
                    string hint = (appHint ?? "").ToLowerInvariant();
                    if (hint.Length > 0) {
                        foreach (Process p in Process.GetProcesses()) {
                            try {
                                string n = p.ProcessName.ToLowerInvariant();
                                if (hint.Contains(n) || n.Contains(hint)) set.Add(p.Id);
                            } catch { }
                        }
                    }
                    hintPids = set;
                    hintCached = appHint ?? "";
                    hintRefreshed = DateTime.UtcNow;
                }
                return hintPids;
            }

            public static int GetSessionVolume(string appHint) {
                IntPtr volume = FindVolume(appHint);
                if (volume == IntPtr.Zero) return -1;
                try {
                    float v;
                    if (Marshal.GetDelegateForFunctionPointer<ComGetVolume>(Slot(volume, 4))(volume, out v) < 0) return -1;
                    return (int)Math.Round(v * 100.0f);
                } finally {
                    Marshal.Release(volume);
                }
            }

            public static bool SetSessionVolume(string appHint, double fraction) {
                IntPtr volume = FindVolume(appHint);
                if (volume == IntPtr.Zero) return false;
                try {
                    return Marshal.GetDelegateForFunctionPointer<ComSetVolume>(Slot(volume, 3))(volume, (float)fraction, IntPtr.Zero) >= 0;
                } finally {
                    Marshal.Release(volume);
                }
            }

            static IntPtr FindVolume(string appHint) {
                HashSet<int> pids = PidsForHint(appHint);
                if (pids.Count == 0) return IntPtr.Zero;
                IMMDeviceEnumerator enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorComObject();
                IntPtr collection;
                if (enumerator.EnumAudioEndpoints(0, 1, out collection) < 0) return IntPtr.Zero;
                try {
                    int deviceCount;
                    if (Marshal.GetDelegateForFunctionPointer<ComGetCount>(Slot(collection, 3))(collection, out deviceCount) < 0) return IntPtr.Zero;
                    for (int d = 0; d < deviceCount; d++) {
                        IntPtr device;
                        if (Marshal.GetDelegateForFunctionPointer<ComItem>(Slot(collection, 4))(collection, d, out device) < 0) continue;
                        if (device == IntPtr.Zero) continue;
                        try {
                            IntPtr manager;
                            if (Marshal.GetDelegateForFunctionPointer<ComActivate>(Slot(device, 3))(device, IID_MGR2, 23, IntPtr.Zero, out manager) < 0) continue;
                            if (manager == IntPtr.Zero) continue;
                            try {
                                IntPtr enumPtr;
                                if (Marshal.GetDelegateForFunctionPointer<ComOutOut>(Slot(manager, 5))(manager, out enumPtr) < 0) continue;
                                if (enumPtr == IntPtr.Zero) continue;
                                try {
                                    int count;
                                    if (Marshal.GetDelegateForFunctionPointer<ComGetCount>(Slot(enumPtr, 3))(enumPtr, out count) < 0) continue;
                                    for (int i = 0; i < count; i++) {
                                        IntPtr sessionPtr;
                                        if (Marshal.GetDelegateForFunctionPointer<ComItem>(Slot(enumPtr, 4))(enumPtr, i, out sessionPtr) < 0) continue;
                                        if (sessionPtr == IntPtr.Zero) continue;
                                        try {
                                            uint sessionPid;
                                            if (Marshal.GetDelegateForFunctionPointer<ComGetPid>(Slot(sessionPtr, 14))(sessionPtr, out sessionPid) < 0) continue;
                                            if (!pids.Contains((int)sessionPid)) continue;
                                            Guid iid = IID_VOLUME;
                                            IntPtr volumePtr;
                                            if (Marshal.QueryInterface(sessionPtr, ref iid, out volumePtr) < 0) continue;
                                            return volumePtr; // caller releases
                                        } finally {
                                            Marshal.Release(sessionPtr);
                                        }
                                    }
                                } finally {
                                    Marshal.Release(enumPtr);
                                }
                            } finally {
                                Marshal.Release(manager);
                            }
                        } finally {
                            Marshal.Release(device);
                        }
                    }
                    return IntPtr.Zero;
                } finally {
                    Marshal.Release(collection);
                }
            }
        }
        '@
        Add-Type -TypeDefinition $code

        $managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
        $propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]
        $boolType = [bool]
        $us = [char]31
        $inv = [Globalization.CultureInfo]::InvariantCulture
        $manager = $null
        $artKey = ''
        $artPath = Join-Path $env:TEMP 'autism_spotify_art.png'
        $anyMedia = $env:AUTISM_SPOTIFY_SOURCE -eq 'ANY'
        # Stdin commands are read through a raw StreamReader over the standard input,
        # NOT [Console]::In.ReadLineAsync: on .NET Framework that call takes the console
        # sync root while it blocks on an EMPTY anonymous pipe (exactly what Java's
        # ProcessBuilder hands us - probed: the helper froze silently after 'before
        # readline' with zero output). StreamReader.ReadLineAsync runs on a pool thread
        # with no console lock, so the status loop never stalls on an empty pipe.
        $stdinReader = New-Object System.IO.StreamReader([Console]::OpenStandardInput())
        $readTask = $stdinReader.ReadLineAsync()
        $stdinDead = $false
        # Event-driven refresh: MediaPropertiesChanged (track skip) and
        # PlaybackInfoChanged (play/pause) set a GLOBAL dirty flag (global, not script
        # scope, because the handler executes off the main runspace thread). The loop
        # below re-reads immediately on dirty, otherwise every 450 ms. NOTE: on this box
        # GSMTC property-change events register fine but NEVER fire (probed: two real
        # skips, zero firings - the same stack that hollows art streams and returns null
        # RepeatMode), so the event path is inert here and the 450 ms poll is what
        # actually delivers faster updates: worst-case staleness is halved.
        $global:spotifyDirty = $false
        $subscribedAumid = ''
        $lastPoll = [DateTime]::UtcNow.AddSeconds(-1)

        function Save-Art($props, $path) {
            try {
                if ($null -eq $props.Thumbnail) { return $false }
                $stream = Await-Op ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                if ($null -eq $stream) { return $false }
                $bytes = [AutismSpotifyVolume]::ReadArtStream($stream)
                if ($null -eq $bytes) { return $false }
                # Atomic-ish publish: write a temp sibling, then Copy over the stable path
                # and delete the temp. [IO.File]::Move(source, dest, overwrite) does NOT
                # exist on .NET Framework 4.x (only .NET Core 3.0+), so Copy+Delete is the
                # safe form here - a render-thread read never sees a torn image.
                $tmp = $path + '.part'
                [System.IO.File]::WriteAllBytes($tmp, $bytes)
                [System.IO.File]::Copy($tmp, $path, $true)
                Remove-Item $tmp -Force
                return $true
            } catch {
                return $false
            }
        }

        function Invoke-SpotifyCommand($session, $hint, $line) {
            if ($line -eq 'PLAY_PAUSE') {
                if ($null -ne $session) { try { Await-Op ($session.TryTogglePlayPauseAsync()) $boolType | Out-Null } catch { } }
            } elseif ($line -eq 'NEXT') {
                if ($null -ne $session) { try { Await-Op ($session.TrySkipNextAsync()) $boolType | Out-Null } catch { } }
            } elseif ($line -eq 'PREV') {
                if ($null -ne $session) { try { Await-Op ($session.TrySkipPreviousAsync()) $boolType | Out-Null } catch { } }
            } elseif ($line -eq 'SHUFFLE_ON') {
                if ($null -ne $session) { try { Await-Op ($session.TryChangeShuffleActiveAsync($true)) $boolType | Out-Null } catch { } }
            } elseif ($line -eq 'SHUFFLE_OFF') {
                if ($null -ne $session) { try { Await-Op ($session.TryChangeShuffleActiveAsync($false)) $boolType | Out-Null } catch { } }
            } elseif ($line -eq 'SOURCE=ANY') {
                $script:anyMedia = $true
            } elseif ($line -eq 'SOURCE=SPOTIFY') {
                $script:anyMedia = $false
            } elseif ($line -match '^REPEAT=(OFF|ALL|ONE)$') {
                if ($null -ne $session) {
                    # WinRT enum values passed as their underlying int (None=0, Track=1,
                    # All=2): on some boxes the repeat-mode enum type cannot be projected
                    # into PowerShell at all (probed: [type] load fails and the binder then
                    # hides the method). There the call simply no-ops inside this catch;
                    # where the enum projects, the conversion binds and the command works.
                    $modeInt = switch ($Matches[1]) { 'OFF' { 0 } 'ALL' { 2 } 'ONE' { 1 } }
                    try { Await-Op ($session.TryChangeRepeatModeAsync($modeInt)) $boolType | Out-Null } catch { }
                }
            } elseif ($line -match '^VOLUME=(\\d+)$') {
                [AutismSpotifyVolume]::SetSessionVolume($hint, [double]$Matches[1] / 100.0) | Out-Null
            }
        }

        while ($true) {
            $elapsed = ([DateTime]::UtcNow - $lastPoll).TotalMilliseconds
            if ($global:spotifyDirty -or $elapsed -ge 450) {
                $global:spotifyDirty = $false
                $lastPoll = [DateTime]::UtcNow
            try {
                if ($null -eq $manager) {
                    $subscribedAumid = ''
                    $manager = Await-Op ($managerType::RequestAsync()) $managerType
                }
                $session = $null
                if ($anyMedia) {
                    $session = $manager.GetCurrentSession()
                } else {
                    foreach ($candidate in $manager.GetSessions()) {
                        if ($candidate.SourceAppUserModelId -match 'spotify') { $session = $candidate; break }
                    }
                }
                if ($null -ne $session -and ([string]$session.SourceAppUserModelId) -ne $subscribedAumid) {
                    # Re-subscribe keyed on the AUMID STRING, not RCW object identity:
                    # if the projection ever yields a fresh RCW for the same session, an
                    # object-reference comparison would re-register handlers every poll.
                    try {
                        $null = $session.add_MediaPropertiesChanged({ $global:spotifyDirty = $true })
                        $null = $session.add_PlaybackInfoChanged({ $global:spotifyDirty = $true })
                        $subscribedAumid = [string]$session.SourceAppUserModelId
                    } catch { }
                }
                if ($null -eq $session) {
                    [Console]::WriteLine('STOPPED')
                } else {
                    $hint = if ($anyMedia) { ([string]$session.SourceAppUserModelId).Split('!')[0] } else { 'spotify' }
                    $info = $session.GetPlaybackInfo()
                    $status = ([string]$info.PlaybackStatus).ToUpperInvariant()
                    $props = Await-Op ($session.TryGetMediaPropertiesAsync()) $propsType
                    $artist = ([string]$props.Artist) -replace "[\\r\\n]", ' '
                    $title = ([string]$props.Title) -replace "[\\r\\n]", ' '
                    $pos = '0'
                    $dur = '0'
                    try {
                        $tl = $session.GetTimelineProperties()
                        if ($null -ne $tl -and $tl.EndTime -gt $tl.StartTime) {
                            $pos = [Math]::Max(0.0, $tl.Position.TotalSeconds).ToString('F1', $inv)
                            $dur = ($tl.EndTime - $tl.StartTime).TotalSeconds.ToString('F1', $inv)
                        }
                    } catch { }
                    $shuffle = if ($info.ShuffleActive) { '1' } else { '0' }
                    $repeat = switch ([string]$info.RepeatMode) { 'None' { 'OFF' } 'Track' { 'ONE' } 'All' { 'ALL' } default { 'UNKNOWN' } }
                    $vol = [AutismSpotifyVolume]::GetSessionVolume($hint)
                    $art = ''
                    if ($title.Length -gt 0) {
                        $key = $artist + '|' + $title
                        if ($key -ne $artKey) {
                            $artKey = $key
                            if (Save-Art $props $artPath) {
                                $art = $artPath
                            } elseif (Test-Path $artPath) {
                                # A failed save must not leave the PREVIOUS track's art
                                # behind for the elseif below to serve with the NEW track.
                                Remove-Item $artPath -Force
                            }
                        } elseif (Test-Path $artPath) {
                            $art = $artPath
                        }
                    }
                    [Console]::WriteLine($status + $us + $artist + $us + $title + $us + $pos + $us + $dur + $us + $shuffle + $us + $repeat + $us + $vol + $us + $art)
                }
            } catch {
                $manager = $null
                $subscribedAumid = ''
                [Console]::WriteLine('UNAVAILABLE')
            }
            }
            if (-not $stdinDead -and $readTask.IsCompleted) {
                try {
                    $line = $readTask.Result
                    if ($null -eq $line) {
                        $stdinDead = $true
                    } else {
                        $readTask = $stdinReader.ReadLineAsync()
                        $hintNow = if ($anyMedia -and $null -ne $session) { ([string]$session.SourceAppUserModelId).Split('!')[0] } else { 'spotify' }
                        Invoke-SpotifyCommand $session $hintNow $line
                    }
                } catch {
                    $stdinDead = $true
                }
            }
            Start-Sleep -Milliseconds 100
        }
        """;

    private static final String OSASCRIPT_QUERY = """
        if application "Spotify" is running then
            tell application "Spotify"
                set out to (player state as string) & (ASCII character 31)
                try
                    set out to out & (artist of current track) & (ASCII character 31) & (name of current track)
                on error
                    set out to out & (ASCII character 31) & (ASCII character 31)
                end try
                try
                    set out to out & (ASCII character 31) & (((player position) * 1000) as integer) & (ASCII character 31) & ((duration of current track) as integer)
                on error
                    set out to out & (ASCII character 31) & "0" & (ASCII character 31) & "0"
                end try
                try
                    set out to out & (ASCII character 31) & (shuffling as string) & (ASCII character 31) & (repeating as string) & (ASCII character 31) & (sound volume as string)
                on error
                    set out to out & (ASCII character 31) & "0" & (ASCII character 31) & "" & (ASCII character 31) & "-1"
                end try
                try
                    set artPath to POSIX path of (path to temporary items folder) & "autism_spotify_art.png"
                    set tiffPath to POSIX path of (path to temporary items folder) & "autism_spotify_art.tiff"
                    set keyPath to POSIX path of (path to temporary items folder) & "autism_spotify_art.key"
                    set trackKey to (artist of current track) & "|" & (name of current track)
                    set haveKey to false
                    try
                        set haveKey to (read (POSIX file keyPath) as string) is trackKey
                    end try
                    if haveKey then
                        set out to out & (ASCII character 31) & artPath
                    else
                        set d to artwork of current track
                        set f to open for access (POSIX file tiffPath) with write permission
                        write d to f
                        close access f
                        do shell script "sips -s format png " & quoted form of tiffPath & " --out " & quoted form of artPath & " >/dev/null 2>&1"
                        set kf to open for access (POSIX file keyPath) with write permission
                        write trackKey to kf
                        close access kf
                        set out to out & (ASCII character 31) & artPath
                    end if
                on error
                    try
                        close access (POSIX file tiffPath)
                    end try
                    set out to out & (ASCII character 31) & ""
                end try
                return out
            end tell
        end if
        """;
}

