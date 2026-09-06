package autismclient.util.oresim;

import autismclient.AutismClientAddon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AutismOreSimSeedStore {
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_SCOPES = 2_048;
    private static final long SAVE_DEBOUNCE_MS = 400L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LEGACY_MAP_TYPE = new TypeToken<Map<String, String>>() { }.getType();
    private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ore-sim-seeds-writer");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile AutismOreSimSeedStore instance;

    private final File file;
    private final File temporaryFile;
    private final Map<String, String> seedsByScope = new LinkedHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private boolean legacyMigrationComplete;

    private AutismOreSimSeedStore(File file) {
        this.file = file;
        this.temporaryFile = new File(file.getParentFile(), file.getName() + ".tmp");
    }

    public static AutismOreSimSeedStore get() {
        AutismOreSimSeedStore local = instance;
        if (local == null) {
            synchronized (AutismOreSimSeedStore.class) {
                if (instance == null) {

                    instance = load(new File(AutismClientAddon.FOLDER, "ore-sim-seeds.json"));
                    Runtime.getRuntime().addShutdownHook(
                        new Thread(instance::flush, "ore-sim-seeds-flush"));
                }
                local = instance;
            }
        }
        return local;
    }

    public String value(String scope, String legacyGlobalValue) {
        String key = knownScope(scope);
        if (key == null) return "";

        boolean changed = false;
        String result;
        synchronized (seedsByScope) {
            if (!legacyMigrationComplete) {
                legacyMigrationComplete = true;
                if (!seedsByScope.containsKey(key)
                    && legacyGlobalValue != null && !legacyGlobalValue.isBlank()) {
                    seedsByScope.put(key, legacyGlobalValue);
                }
                changed = true;
            }
            result = seedsByScope.getOrDefault(key, "");
        }
        if (changed) touch();
        return result;
    }

    public void put(String scope, String rawValue) {
        String key = knownScope(scope);
        if (key == null) return;
        String value = rawValue == null ? "" : rawValue;
        boolean changed;
        synchronized (seedsByScope) {

            changed = !legacyMigrationComplete;
            legacyMigrationComplete = true;
            if (value.isBlank()) {
                changed |= seedsByScope.remove(key) != null;
            } else {
                if (seedsByScope.containsKey(key) || seedsByScope.size() < MAX_SCOPES) {
                    changed |= !Objects.equals(seedsByScope.put(key, value), value);
                }
            }
        }
        if (changed) touch();
    }

    private static String knownScope(String scope) {
        if (scope == null || scope.isBlank()) return null;
        String normalized = scope.trim();
        return "unknown".equals(normalized) ? null : normalized;
    }

    private void touch() {
        dirty.set(true);
        if (!scheduled.compareAndSet(false, true)) return;
        try {
            WRITER.schedule(() -> {
                scheduled.set(false);
                flush();
            }, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable unavailable) {
            scheduled.set(false);
            flush();
        }
    }

    private void flush() {
        if (dirty.compareAndSet(true, false)) writeNow();
    }

    private synchronized void writeNow() {
        try {
            File directory = file.getParentFile();
            if (directory != null) Files.createDirectories(directory.toPath());
            Persisted snapshot = new Persisted();
            snapshot.version = FORMAT_VERSION;
            synchronized (seedsByScope) {
                snapshot.legacyMigrationComplete = legacyMigrationComplete;
                snapshot.seeds = new LinkedHashMap<>(seedsByScope);
            }
            try (java.io.Writer writer = Files.newBufferedWriter(temporaryFile.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(snapshot, writer);
            }
            try {
                Files.move(temporaryFile.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable failure) {

            dirty.set(true);
            AutismClientAddon.LOG.warn("Failed to save scoped OreSim seeds", failure);
        }
    }

    private static AutismOreSimSeedStore load(File file) {
        AutismOreSimSeedStore store = new AutismOreSimSeedStore(file);
        if (!file.exists()) return store;
        try (java.io.Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root instanceof JsonObject object && object.has("seeds")) {
                Persisted parsed = GSON.fromJson(object, Persisted.class);
                if (parsed != null) {
                    store.legacyMigrationComplete = parsed.legacyMigrationComplete;
                    store.copyValidEntries(parsed.seeds);
                }
            } else {

                Map<String, String> legacyMap = GSON.fromJson(root, LEGACY_MAP_TYPE);
                store.copyValidEntries(legacyMap);
                store.legacyMigrationComplete = true;
            }
        } catch (Throwable failure) {
            AutismClientAddon.LOG.warn("Failed to read scoped OreSim seeds", failure);
        }
        return store;
    }

    private void copyValidEntries(Map<String, String> entries) {
        if (entries == null) return;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String key = knownScope(entry.getKey());
            String value = entry.getValue();
            if (key == null || value == null || value.isBlank()) continue;
            seedsByScope.put(key, value);
            if (seedsByScope.size() >= MAX_SCOPES) break;
        }
    }

    static AutismOreSimSeedStore loadForTest(File file) {
        return load(file);
    }

    void flushForTest() {
        flush();
    }

    private static final class Persisted {
        int version = FORMAT_VERSION;
        boolean legacyMigrationComplete;
        Map<String, String> seeds = new LinkedHashMap<>();
    }
}
