package autismclient.util;

import autismclient.AutismClientAddon;
import autismclient.modules.AntiVanishModule;
import autismclient.modules.AutismModule;
import autismclient.modules.ModuleRegistry;
import autismclient.modules.PackHideState;
import autismclient.util.macro.FpsLimitController;
import autismclient.util.macro.MacroConditionRegistry;
import autismclient.util.macro.MacroExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AutismLagWatchdog {
    private static final int SAMPLE_EVERY_TICKS = 20;
    private static final int FORCED_WINDOW_TICKS = 200;
    private static final boolean TEST_MODE = Boolean.getBoolean("autism.lagtest");
    private static final long LOW_HOLD_MS = TEST_MODE ? 3_000L : 15_000L;
    private static final long REPORT_COOLDOWN_MS = TEST_MODE ? 30_000L : 5 * 60_000L;
    private static final int WARMUP_SAMPLES = TEST_MODE ? 5 : 30;

    private static int tickCounter;
    private static double baselineFps = -1.0;
    private static int warmupSamples;
    private static long lowSinceMs;
    private static long lastReportMs;
    private static boolean censusPending;
    private static Map<String, long[]> countersAtTrigger;
    private static int fpsAtTrigger;
    private static long lastErrorLogMs;

    private AutismLagWatchdog() {
    }

    public static void onClientTick(Minecraft client) {
        try {
            AutismPerf.tickForcedWindow();
            if (censusPending && !AutismPerf.isForcedWindowActive()) {
                censusPending = false;
                emitCensus(client);
            }
            if (++tickCounter % SAMPLE_EVERY_TICKS != 0) return;
            sample(client);
        } catch (Throwable t) {
            long now = System.currentTimeMillis();
            if (now - lastErrorLogMs >= 5000L) {
                lastErrorLogMs = now;
                AutismClientAddon.LOG.warn("[Autism] lag watchdog failed; isolated to protect the tick", t);
            }
        }
    }

    public static void reset() {
        baselineFps = -1.0;
        warmupSamples = 0;
        lowSinceMs = 0;
        censusPending = false;
        countersAtTrigger = null;
    }

    private static void sample(Minecraft client) {
        if (client == null || client.level == null) {
            reset();
            return;
        }

        if (client.getWindow() == null || !client.getWindow().isFocused()) {
            lowSinceMs = 0;
            return;
        }

        if (FpsLimitController.isActive()) {
            lowSinceMs = 0;
            return;
        }
        int fps = client.getFps();
        if (fps <= 0) return;

        if (baselineFps < 0) baselineFps = fps;
        double alpha = fps > baselineFps ? 0.2 : 0.02;
        baselineFps += alpha * (fps - baselineFps);
        if (warmupSamples < WARMUP_SAMPLES) {
            warmupSamples++;
            lowSinceMs = 0;
            return;
        }

        double threshold = Math.max(15.0, baselineFps * 0.5);
        long now = System.currentTimeMillis();
        if (fps >= threshold) {
            lowSinceMs = 0;
            return;
        }
        if (lowSinceMs == 0) {
            lowSinceMs = now;
            return;
        }
        if (now - lowSinceMs < LOW_HOLD_MS) return;
        if (censusPending || now - lastReportMs < REPORT_COOLDOWN_MS) return;

        lastReportMs = now;
        fpsAtTrigger = fps;
        countersAtTrigger = AutismPerf.snapshotCounters();
        AutismPerf.beginForcedWindow(FORCED_WINDOW_TICKS);
        censusPending = true;
        AutismClientAddon.LOG.warn(
            "[AutismLagReport] sustained low fps detected (fps={} baseline={}); sampling frame time for 10s...",
            fps, String.format(java.util.Locale.ROOT, "%.1f", baselineFps));
    }

    private static void emitCensus(Minecraft client) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("===== lag census — paste this whole block when reporting =====");

        long heapUsedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20;
        long heapMaxMb = Runtime.getRuntime().maxMemory() >> 20;
        long lowForMs = lowSinceMs == 0 ? 0 : System.currentTimeMillis() - lowSinceMs;
        line(sb, "fps=" + (client == null ? -1 : client.getFps()) + " atTrigger=" + fpsAtTrigger
            + " baseline=" + String.format(java.util.Locale.ROOT, "%.1f", baselineFps)
            + " lowForMs=" + lowForMs
            + " heapMB=" + heapUsedMb + "/" + heapMaxMb);

        appendSafe(sb, "state", () -> {
            boolean focused = client != null && client.getWindow() != null && client.getWindow().isFocused();
            Screen screen = client == null ? null : client.gui.screen();
            AutismSharedState shared = AutismSharedState.get();
            boolean capture = shared != null && (shared.isCaptureMode() || shared.hasCaptureCancelCallback()
                || shared.hasAttackCaptureCallback() || shared.hasBlockCaptureCallback()
                || shared.hasEntityCaptureCallback() || shared.isGBreakCapturing());
            return "focused=" + focused
                + " screen=" + (screen == null ? "none" : screen.getClass().getSimpleName())
                + " capture=" + capture
                + " payloadStudy=" + AutismPayloadStudySession.isActive()
                + " packHide=" + PackHideState.isActive();
        });
        appendSafe(sb, "overlays", () -> AutismOverlayManager.get().censusSummary());
        appendSafe(sb, "packetLogger", () -> {
            AutismModule module = AutismModule.get();
            AutismPacketLoggerOverlay logger = module == null ? null : module.getPacketLoggerOverlayIfExists();
            return logger == null ? "not created" : logger.censusSummary();
        });
        appendSafe(sb, "macro", () -> MacroExecutor.oneShotCensus()
            + " pendingConditions=" + MacroConditionRegistry.pendingConditionCount());
        appendSafe(sb, "antiVanish", AntiVanishModule::censusSummary);
        appendSafe(sb, "hud", () -> "enabledElements=" + AutismHudManager.enabledElementCount()
            + " activeModules=" + ModuleRegistry.activeModules().size()
            + " toasts=" + AutismNotifications.pendingCount()
            + " mm=" + autismclient.util.mm.MatchmakingManager.get().censusSummary());
        appendSafe(sb, "frame sections (10s window, window-total desc, avg/max* ms, n)", () -> {
            StringBuilder sections = new StringBuilder();
            Map<String, long[]> before = countersAtTrigger;
            List<long[]> rows = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, long[]> entry : AutismPerf.snapshotCounters().entrySet()) {
                long[] nowC = entry.getValue();
                long[] beforeC = before == null ? null : before.get(entry.getKey());
                long dSamples = nowC[0] - (beforeC == null ? 0 : beforeC[0]);
                long dTotal = nowC[1] - (beforeC == null ? 0 : beforeC[1]);
                if (dSamples <= 0) continue;
                names.add(entry.getKey());
                rows.add(new long[] {dTotal, dSamples, nowC[2], names.size() - 1});
            }
            rows.sort((a, b) -> Long.compare(b[0], a[0]));
            int shown = Math.min(12, rows.size());
            for (int i = 0; i < shown; i++) {
                long[] row = rows.get(i);
                sections.append("\n[AutismLagReport]   ")
                    .append(names.get((int) row[3]))
                    .append(" avg=").append(String.format(java.util.Locale.ROOT, "%.3f", row[0] / (double) row[1] / 1_000_000.0))
                    .append(" max*=").append(String.format(java.util.Locale.ROOT, "%.3f", row[2] / 1_000_000.0))
                    .append(" n=").append(row[1]);
            }
            return sections.length() == 0 ? "(no samples)" : sections.toString();
        });
        line(sb, "===== end lag census =====");

        countersAtTrigger = null;
        AutismClientAddon.LOG.warn("[AutismLagReport] {}", sb);
    }

    private static void line(StringBuilder sb, String text) {
        sb.append("\n[AutismLagReport] ").append(text);
    }

    private static void appendSafe(StringBuilder sb, String label, java.util.function.Supplier<String> value) {
        String text;
        try {
            text = value.get();
        } catch (Throwable t) {
            text = "n/a (" + t.getClass().getSimpleName() + ")";
        }
        line(sb, label + ": " + text);
    }
}
