package autismclient.security;

import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class AutismPackResponseScheduler {

    private static final long ACCEPT_BASE_MS = 250L;
    private static final long ACCEPT_SPREAD_MS = 650L;

    private static final long DOWNLOAD_BASE_MS = 1_200L;
    private static final long DOWNLOAD_SPREAD_MS = 3_800L;

    private static final long APPLY_BASE_MS = 1_500L;
    private static final long APPLY_SPREAD_MS = 2_500L;

    private static final long FAIL_BASE_MS = 3_000L;
    private static final long FAIL_SPREAD_MS = 6_000L;

    private static final long DECLINE_SPREAD_MS = 6_000L;

    private record Pending(UUID packId, ServerboundResourcePackPacket.Action action, long dueAtMs,
                           Consumer<ServerboundResourcePackPacket> sender, Runnable onSent) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private AutismPackResponseScheduler() {
    }

    private static long jitter(long baseMs, long spreadMs) {
        return baseMs + ThreadLocalRandom.current().nextLong(spreadMs + 1L);
    }

    public static long declineDelayMs() {
        long configured = Math.max(0L, autismclient.util.AutismConfig.getGlobal().packResponseDelayMs);
        return configured == 0L ? 0L : jitter(configured, DECLINE_SPREAD_MS);
    }

    public static long acceptDelayMs() {
        return jitter(ACCEPT_BASE_MS, ACCEPT_SPREAD_MS);
    }

    public static long downloadedDelayMs(long afterAcceptMs) {
        return afterAcceptMs + jitter(DOWNLOAD_BASE_MS, DOWNLOAD_SPREAD_MS);
    }

    public static long appliedDelayMs(long afterDownloadedMs) {
        return afterDownloadedMs + jitter(APPLY_BASE_MS, APPLY_SPREAD_MS);
    }

    public static long failedDelayMs(long afterAcceptMs) {
        return afterAcceptMs + jitter(FAIL_BASE_MS, FAIL_SPREAD_MS);
    }

    public static void schedule(UUID packId, ServerboundResourcePackPacket.Action action, long delayMs,
                                Consumer<ServerboundResourcePackPacket> sender) {
        schedule(packId, action, delayMs, sender, null);
    }

    public static void schedule(UUID packId, ServerboundResourcePackPacket.Action action, long delayMs,
                                Consumer<ServerboundResourcePackPacket> sender, Runnable onSent) {
        if (packId == null || action == null || sender == null) return;
        Pending pending = new Pending(packId, action, System.currentTimeMillis() + Math.max(0L, delayMs), sender, onSent);
        synchronized (PENDING) {
            PENDING.add(pending);
        }
    }

    public static void tick() {
        List<Pending> due = null;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;
            long now = System.currentTimeMillis();
            for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
                Pending pending = it.next();
                if (pending.dueAtMs() > now) continue;
                it.remove();
                if (due == null) due = new ArrayList<>(3);
                due.add(pending);
            }
        }
        if (due == null) return;

        for (Pending pending : due) {
            try {
                pending.sender().accept(new ServerboundResourcePackPacket(pending.packId(), pending.action()));
                if (pending.onSent() != null) pending.onSent().run();
            } catch (Throwable ignored) {

            }
        }
    }

    public static void cancel(UUID packId) {
        synchronized (PENDING) {
            if (packId == null) {
                PENDING.clear();
                return;
            }
            PENDING.removeIf(pending -> packId.equals(pending.packId()));
        }
    }

    public static void clearAll() {
        cancel(null);
    }

    public static boolean hasPending() {
        synchronized (PENDING) {
            return !PENDING.isEmpty();
        }
    }
}
