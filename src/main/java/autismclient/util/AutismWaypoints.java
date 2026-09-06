package autismclient.util;

import autismclient.AutismClientAddon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class AutismWaypoints {

    public static final long DEATH_DEDUPE_MS = 30_000L;
    private static final File FILE = new File(AutismClientAddon.FOLDER, "waypoints.json");
    private static final File TMP = new File(AutismClientAddon.FOLDER, "waypoints.json.tmp");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORE_TYPE = new TypeToken<Map<String, List<Waypoint>>>() { }.getType();
    private static final int MAX_PER_SCOPE = 512;
    private static final long SAVE_DEBOUNCE_MS = 400;
    private static volatile AutismWaypoints instance;

    private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "waypoints-writer");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private final AtomicLong revision = new AtomicLong();

    private final Map<String, List<Waypoint>> byScope = new LinkedHashMap<>();

    public record Waypoint(String name, int x, int y, int z, int color, long createdMs, boolean death) {

        public Waypoint(String name, int x, int y, int z, int color, long createdMs) {
            this(name, x, y, z, color, createdMs, false);
        }
    }

    private AutismWaypoints() {}

    public static AutismWaypoints get() {
        AutismWaypoints local = instance;
        if (local == null) {
            synchronized (AutismWaypoints.class) {
                if (instance == null) instance = load();
                local = instance;
            }
        }
        return local;
    }

    public long revision() {
        return revision.get();
    }

    public static String scopeKey(Minecraft mc) {
        if (mc == null) return "unknown";
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return server.ip.trim().toLowerCase(Locale.ROOT);
        }
        if (mc.hasSingleplayerServer()) {
            IntegratedServer integrated = mc.getSingleplayerServer();
            if (integrated != null && integrated.getWorldData() != null) {
                String levelName = integrated.getWorldData().getLevelName();
                if (levelName != null && !levelName.isBlank()) return levelName.trim();
            }
        }
        return "unknown";
    }

    public List<Waypoint> list(String scope) {
        synchronized (byScope) {
            List<Waypoint> list = byScope.get(normalize(scope));
            return list == null ? List.of() : List.copyOf(list);
        }
    }

    public void add(String scope, Waypoint waypoint) {
        if (waypoint == null || waypoint.name() == null || waypoint.name().isBlank()) return;
        synchronized (byScope) {
            List<Waypoint> list = byScope.computeIfAbsent(normalize(scope), key -> new ArrayList<>());
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).name().equalsIgnoreCase(waypoint.name())) {
                    list.set(i, waypoint);
                    touch();
                    return;
                }
            }
            if (list.size() >= MAX_PER_SCOPE) return;
            list.add(waypoint);
        }
        touch();
    }

    public boolean remove(String scope, String name) {
        if (name == null || name.isBlank()) return false;
        boolean removed = false;
        synchronized (byScope) {
            List<Waypoint> list = byScope.get(normalize(scope));
            if (list != null) {
                removed = list.removeIf(waypoint -> waypoint.name().equalsIgnoreCase(name.trim()));
                if (list.isEmpty()) byScope.remove(normalize(scope));
            }
        }
        if (removed) touch();
        return removed;
    }

    public void clear(String scope) {
        boolean removed;
        synchronized (byScope) {
            removed = byScope.remove(normalize(scope)) != null;
        }
        if (removed) touch();
    }

    public Waypoint find(String scope, String name) {
        if (name == null) return null;
        synchronized (byScope) {
            List<Waypoint> list = byScope.get(normalize(scope));
            if (list == null) return null;
            for (Waypoint waypoint : list) {
                if (waypoint.name().equalsIgnoreCase(name.trim())) return waypoint;
            }
            return null;
        }
    }

    public int colorOf(String scope, String name, int fallback) {
        Waypoint waypoint = find(scope, name);
        return waypoint == null ? fallback : waypoint.color();
    }

    public boolean setColor(String scope, String name, int color) {
        Waypoint updated = null;
        synchronized (byScope) {
            List<Waypoint> list = byScope.get(normalize(scope));
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    Waypoint waypoint = list.get(i);
                    if (waypoint.name().equalsIgnoreCase(name == null ? "" : name.trim())) {
                        updated = new Waypoint(waypoint.name(), waypoint.x(), waypoint.y(), waypoint.z(),
                            color, waypoint.createdMs(), waypoint.death());
                        list.set(i, updated);
                        break;
                    }
                }
            }
        }
        if (updated == null) return false;
        touch();
        return true;
    }

    public boolean rename(String scope, String oldName, String newName) {
        if (oldName == null || newName == null) return false;
        String trimmed = newName.trim();
        if (trimmed.isEmpty()) return false;
        String old = oldName.trim();
        synchronized (byScope) {
            List<Waypoint> list = byScope.get(normalize(scope));
            if (list == null) return false;
            int index = -1;
            for (int i = 0; i < list.size(); i++) {
                String name = list.get(i).name();
                if (name.equalsIgnoreCase(trimmed) && !name.equalsIgnoreCase(old)) return false;
                if (name.equalsIgnoreCase(old)) index = i;
            }
            if (index < 0) return false;
            Waypoint current = list.get(index);
            if (current.name().equals(trimmed)) return true;
            list.set(index, new Waypoint(trimmed, current.x(), current.y(), current.z(),
                current.color(), current.createdMs(), current.death()));
        }
        touch();
        return true;
    }

    public String nextName(String scope, String base) {
        String stem = (base == null || base.isBlank() ? "Waypoint" : base.trim()) + " #";
        int max = 0;
        synchronized (byScope) {
            List<Waypoint> list = byScope.get(normalize(scope));
            if (list != null) {
                for (Waypoint waypoint : list) {
                    String name = waypoint.name();
                    if (name == null || !name.startsWith(stem)) continue;
                    try {
                        max = Math.max(max, Integer.parseInt(name.substring(stem.length()).trim()));
                    } catch (NumberFormatException ignored) {  }
                }
            }
        }
        return stem + (max + 1);
    }

    public Waypoint addDeath(String scope, int x, int y, int z, int color, long nowMs, int maxDeaths) {
        synchronized (byScope) {
            List<Waypoint> list = byScope.get(normalize(scope));
            if (list != null) {
                for (Waypoint waypoint : list) {
                    if (waypoint.x() == x && waypoint.y() == y && waypoint.z() == z
                        && nowMs >= waypoint.createdMs() && nowMs - waypoint.createdMs() < DEATH_DEDUPE_MS) {
                        return null;
                    }
                }
            }
        }
        Waypoint waypoint = new Waypoint(nextName(scope, "Death"), x, y, z, color, nowMs, true);
        add(scope, waypoint);
        pruneDeaths(scope, maxDeaths);
        return waypoint;
    }

    public void pruneDeaths(String scope, int maxDeaths) {
        if (maxDeaths < 1) return;
        boolean changed = false;
        synchronized (byScope) {
            List<Waypoint> list = byScope.get(normalize(scope));
            if (list == null) return;
            List<Waypoint> deaths = new ArrayList<>();
            for (Waypoint waypoint : list) {
                if (waypoint.death()) deaths.add(waypoint);
            }
            if (deaths.size() <= maxDeaths) return;
            deaths.sort((a, b) -> Long.compare(b.createdMs(), a.createdMs()));
            for (int i = maxDeaths; i < deaths.size(); i++) {
                changed |= list.remove(deaths.get(i));
            }
        }
        if (changed) touch();
    }

    private static String normalize(String scope) {
        return scope == null || scope.isBlank() ? "unknown" : scope.trim();
    }

    private void touch() {
        revision.incrementAndGet();
        save();
    }

    private static AutismWaypoints load() {
        AutismWaypoints waypoints = new AutismWaypoints();
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                Map<String, List<Waypoint>> parsed = GSON.fromJson(reader, STORE_TYPE);
                if (parsed != null) {
                    for (Map.Entry<String, List<Waypoint>> entry : parsed.entrySet()) {
                        if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
                        List<Waypoint> list = new ArrayList<>();
                        for (Waypoint waypoint : entry.getValue()) {
                            if (waypoint == null || waypoint.name() == null || waypoint.name().isBlank()) continue;
                            if (list.size() >= MAX_PER_SCOPE) break;
                            list.add(waypoint);
                        }
                        if (!list.isEmpty()) waypoints.byScope.put(entry.getKey().trim(), list);
                    }
                }
            } catch (Throwable t) {
                AutismClientAddon.LOG.warn("Failed to read waypoints", t);
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(waypoints::flush, "waypoints-flush"));
        return waypoints;
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
            Map<String, List<Waypoint>> snapshot = new LinkedHashMap<>();
            synchronized (byScope) {
                for (Map.Entry<String, List<Waypoint>> entry : byScope.entrySet()) {
                    snapshot.put(entry.getKey(), List.copyOf(entry.getValue()));
                }
            }

            try (FileWriter out = new FileWriter(TMP)) {
                GSON.toJson(snapshot, STORE_TYPE, out);
            }
            try {
                java.nio.file.Files.move(TMP.toPath(), FILE.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
                java.nio.file.Files.move(TMP.toPath(), FILE.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("Failed to save waypoints", t);
        }
    }
}
