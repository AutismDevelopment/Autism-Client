package autismclient.util;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.ProgressBar;
import autismclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class AutismNameScrape {
    private static final int MAX_NAMES = 120_000;
    private static final int MAX_QUERIES = 150_000;
    private static final char[] SPINNER = {'|', '/', '-', '\\'};

    private static volatile boolean active;
    private static volatile boolean paused;
    private static volatile boolean stopped;
    private static volatile int foundCount;
    private static volatile int queriesDone;
    private static volatile int limit;
    private static volatile boolean userLimited;
    private static volatile boolean deepMode;
    private static volatile int serverOnline;
    private static volatile String currentStatus = "";
    private static volatile ClientPacketListener activeConnection;

    private static final AutismNameHarvest.Control CONTROL = new AutismNameHarvest.Control() {
        @Override
        public boolean cancelled() {
            return isCancelled();
        }

        @Override
        public boolean paused() {
            return paused && active;
        }

        @Override
        public int limit() {
            return limit;
        }

        @Override
        public int maxQueries() {
            return MAX_QUERIES;
        }

        @Override
        public boolean deepSweep() {
            return deepMode;
        }

        @Override
        public void onProgress(int names, int queries, int maxQueries, String status) {
            foundCount = names;
            queriesDone = queries;
            currentStatus = status == null ? "" : status;
        }
    };

    private AutismNameScrape() {
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isPaused() {
        return active && paused;
    }

    public static void start(int requestedLimit) {
        start(requestedLimit, false);
    }

    public static synchronized void start(int requestedLimit, boolean deep) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null || mc.player == null) {
            AutismClientMessaging.sendPrefixed("Namescrape: join a server first.");
            return;
        }
        if (active) {
            AutismClientMessaging.sendPrefixed("Namescrape: already running. Use stop / pause / resume.");
            return;
        }
        active = true;
        paused = false;
        stopped = false;
        deepMode = deep;
        foundCount = 0;
        queriesDone = 0;
        currentStatus = "Starting...";
        userLimited = requestedLimit > 0;
        limit = userLimited ? Math.min(requestedLimit, MAX_NAMES) : MAX_NAMES;
        serverOnline = readServerOnline(mc);

        ClientPacketListener connection = mc.getConnection();
        activeConnection = connection;
        String self = mc.player.getName().getString();
        LinkedHashMap<String, String> names = AutismNameHarvest.instantNames(mc, self);
        foundCount = names.size();
        List<AutismNameHarvest.Vector> vectors = AutismNameHarvest.discoverVectors(connection);
        AutismBackgroundTasks.runTracked("namescrape", () -> run(names, connection, self, vectors));
    }

    public static void pause() {
        if (!active) {
            AutismClientMessaging.sendPrefixed("Namescrape: nothing to pause.");
            return;
        }
        paused = true;
        AutismClientMessaging.sendPrefixed("Namescrape: paused (" + foundCount + " so far). Use resume.");
    }

    public static void resume() {
        if (!active) {
            AutismClientMessaging.sendPrefixed("Namescrape: nothing to resume.");
            return;
        }
        paused = false;
        AutismClientMessaging.sendPrefixed("Namescrape: resumed.");
    }

    public static void stop() {
        if (!active) {
            AutismClientMessaging.sendPrefixed("Namescrape: nothing to stop.");
            return;
        }
        stopped = true;
        paused = false;
        active = false;
        AutismClientMessaging.sendPrefixed("Namescrape: stopping, copying " + foundCount + " so far...");
    }

    private static void run(LinkedHashMap<String, String> names, ClientPacketListener connection, String self,
                            List<AutismNameHarvest.Vector> vectors) {
        try {
            AutismNameHarvest.sweep(connection, names, self, vectors, CONTROL);
            List<String> result = new ArrayList<>(names.values());
            if (userLimited && result.size() > limit) result = new ArrayList<>(result.subList(0, limit));
            finish(result);
        } catch (Throwable t) {
            finishMessage("Namescrape failed.", "", 0);
        }
    }

    private static boolean isCancelled() {
        if (!active || PackHideState.isHardLocked()) return true;
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.getConnection() != activeConnection;
    }

    private static void finish(List<String> result) {
        String joined = String.join("\n", result);
        int bytes = joined.getBytes(StandardCharsets.UTF_8).length;
        String kb = String.format(Locale.ROOT, "%.1f", bytes / 1024.0);
        if (result.isEmpty()) {
            finishMessage("Namescrape: no names found.", "", 0);
            return;
        }
        finishMessage((stopped ? "Namescrape stopped: " : "Namescrape: ") + String.format(Locale.US, "%,d", result.size())
            + " names (" + kb + " KB) copied.", joined, result.size());
    }

    private static void finishMessage(String message, String clipboard, int count) {
        Minecraft mc = Minecraft.getInstance();
        Runnable done = () -> {
            active = false;
            paused = false;
            activeConnection = null;
            if (PackHideState.shouldSuppressClientOutput()) return;
            if (count > 0 && mc != null && mc.keyboardHandler != null && !clipboard.isEmpty()) {
                mc.keyboardHandler.setClipboard(clipboard);
            }
            AutismNotifications.success(message);
        };
        if (mc != null) mc.execute(done);
        else done.run();
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (graphics == null || mc == null || mc.font == null) return;

        UiContext ctx = UiContexts.overlay(graphics, mc.font, -1, -1);
        var colors = ctx.theme().colors();
        int names = foundCount;
        char spin = SPINNER[(int) ((System.currentTimeMillis() / 120) % SPINNER.length)];

        int width = 244;
        int height = 46;
        int pad = 9;
        int x = Math.max(4, (AutismUiScale.getVirtualScreenWidth() - width) / 2);
        int y = 6;
        UiRenderer.frame(graphics, UiBounds.of(x, y, width, height), colors.windowStrong, colors.border);

        String title = (paused ? "|| " : spin + " ") + (paused ? "Namescrape paused" : "Scraping names");
        int target = targetTotal();
        String count = fmt(names) + (target > 0 ? " / " + fmt(target) : "");
        ctx.text().draw(graphics, title, x + pad, y + 6, colors.text);
        int cw = ctx.text().width(count);
        ctx.text().draw(graphics, count, x + width - pad - cw, y + 6, colors.muted);

        ProgressBar.render(ctx, UiBounds.of(x + pad, y + 20, width - pad * 2, 8), progress());

        String status = currentStatus == null || currentStatus.isBlank() ? "Starting..." : currentStatus;
        ctx.text().draw(graphics, clip(ctx, status, width - pad * 2), x + pad, y + 33, colors.muted);
    }

    private static int targetTotal() {
        if (userLimited) return limit;
        return serverOnline;
    }

    private static double progress() {
        int target = targetTotal();
        if (target > 0) return Math.min(1.0, foundCount / (double) target);
        return foundCount / (foundCount + 1500.0);
    }

    private static int readServerOnline(Minecraft mc) {
        try {
            var data = mc.getCurrentServer();
            if (data != null && data.players != null) return Math.max(0, data.players.online());
        } catch (Throwable ignored) {  }
        return 0;
    }

    private static String fmt(int n) {
        return String.format(Locale.US, "%,d", n);
    }

    private static String clip(UiContext ctx, String text, int maxWidth) {
        if (ctx.text().width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int limit = maxWidth - ctx.text().width(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (ctx.text().width(sb.toString() + text.charAt(i)) > limit) break;
            sb.append(text.charAt(i));
        }
        return sb + ellipsis;
    }
}
