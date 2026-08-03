package autismclient.util;

import autismclient.AutismClientAddon;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class SaveCoordinator {
    static final long CONFIG_DEBOUNCE_MS = 300L;

    private static final Object LOCK = new Object();
    private static final Map<String, Runnable> LATEST = new HashMap<>();
    private static final Map<String, Boolean> DRAINING = new HashMap<>();
    private static final Map<String, ScheduledFuture<?>> DEBOUNCED = new HashMap<>();
    private static final Map<String, Long> DEBOUNCE_VERSIONS = new HashMap<>();
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "autism-persistence");
        thread.setDaemon(true);
        return thread;
    });

    private static AutismConfig pendingConfig;
    private static long pendingConfigVersion;
    private static final String CONFIG_DEBOUNCE_KEY = "config-capture";

    private SaveCoordinator() {
    }

    static void requestConfigSave(AutismConfig config) {
        if (config == null) return;
        long version;
        synchronized (LOCK) {
            pendingConfig = config;
            version = ++pendingConfigVersion;
            scheduleDebouncedLocked(CONFIG_DEBOUNCE_KEY, CONFIG_DEBOUNCE_MS,
                () -> dispatchConfigCapture(version));
        }
    }

    private static void dispatchConfigCapture(long version) {
        Runnable capture = () -> captureConfigIfCurrent(version);
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.execute(capture);
                return;
            }
        } catch (Throwable t) {
            AutismClientAddon.LOG.debug("Could not dispatch config capture to client thread", t);
        }

        capture.run();
    }

    private static void captureConfigIfCurrent(long version) {
        AutismConfig config;
        synchronized (LOCK) {
            if (version != pendingConfigVersion || pendingConfig == null) return;
            config = pendingConfig;
            pendingConfig = null;
        }
        AutismConfigWriter.captureAndEnqueue(config);
    }

    static void capturePendingConfigNow() {
        AutismConfig config;
        synchronized (LOCK) {
            config = pendingConfig;
            if (config == null) return;
            pendingConfig = null;
            pendingConfigVersion++;
            cancelDebouncedLocked(CONFIG_DEBOUNCE_KEY);
        }
        AutismConfigWriter.captureAndEnqueue(config);
    }

    static void scheduleDebounced(String key, long delayMs, Runnable work) {
        if (key == null || work == null) return;
        synchronized (LOCK) {
            scheduleDebouncedLocked(key, delayMs, work);
        }
    }

    private static void scheduleDebouncedLocked(String key, long delayMs, Runnable work) {
        ScheduledFuture<?> old = DEBOUNCED.remove(key);
        if (old != null) old.cancel(false);
        long version = DEBOUNCE_VERSIONS.getOrDefault(key, 0L) + 1L;
        DEBOUNCE_VERSIONS.put(key, version);
        ScheduledFuture<?> next = WORKER.schedule(() -> {
            synchronized (LOCK) {
                if (DEBOUNCE_VERSIONS.getOrDefault(key, 0L) != version) return;
                DEBOUNCED.remove(key);
            }
            work.run();
        }, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
        DEBOUNCED.put(key, next);
    }

    private static void cancelDebouncedLocked(String key) {
        ScheduledFuture<?> pending = DEBOUNCED.remove(key);
        if (pending != null) pending.cancel(false);
        DEBOUNCE_VERSIONS.put(key, DEBOUNCE_VERSIONS.getOrDefault(key, 0L) + 1L);
    }

    static void enqueueLatest(String key, Runnable work) {
        if (key == null || work == null) return;
        synchronized (LOCK) {
            LATEST.put(key, work);
            if (DRAINING.putIfAbsent(key, Boolean.TRUE) == null) {
                WORKER.execute(() -> drain(key));
            }
        }
    }

    private static void drain(String key) {
        while (true) {
            Runnable work;
            synchronized (LOCK) {
                work = LATEST.remove(key);
                if (work == null) {
                    DRAINING.remove(key);
                    return;
                }
            }
            try {
                work.run();
            } catch (Throwable t) {
                AutismClientAddon.LOG.error("Persistence task failed for {}", key, t);
            }
        }
    }

    static void flushBlocking(long timeoutMs) {
        capturePendingConfigNow();
        CountDownLatch latch = new CountDownLatch(1);
        WORKER.execute(latch::countDown);
        try {
            latch.await(Math.max(0L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable ignored) {

        }
    }
}
