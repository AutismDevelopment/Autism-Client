package autismclient.util.macro;

import autismclient.util.AutismCommandSuggestionIds;
import autismclient.util.AutismNameHarvest;
import autismclient.util.AutismNotifications;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

final class MacroCaptureAutofill {
    private MacroCaptureAutofill() {
    }
    private record CachedSuggestions(java.util.List<String> suggestions, Object connection) {}
    private record PendingFetch(CompletableFuture<java.util.List<String>> future, Object connection) {}
    private static final java.util.concurrent.ConcurrentHashMap<String, CachedSuggestions> SUGGESTION_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, PendingFetch> PENDING_FETCHES =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final int SUGGESTION_CACHE_MAX = 256;
    private static final long SUGGESTION_CACHE_IDLE_RESET_MS = 3000L;

    private static final int TRUNCATED_REPLY_SIZE = 1000;
    private static final int MIN_SWEEP_CAP = 8;
    private static final int MAX_SWEEP_NAMES = 60_000;
    private static final String DRILL_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_";
    static volatile long allRunsStoppedAtMs;

    private static final class SuggestionUnion {
        final Object connection;
        final java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        final java.util.ArrayDeque<String> sweepQueue = new java.util.ArrayDeque<>();
        int alphabetCursor;
        int cap;

        SuggestionUnion(Object connection, String baseQuery) {
            this.connection = connection;
            sweepQueue.add(baseQuery);
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, SuggestionUnion> SUGGESTION_UNIONS =
        new java.util.concurrent.ConcurrentHashMap<>();

    public static void expireSuggestionCacheAfterIdle() {
        long stoppedAt = allRunsStoppedAtMs;
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > SUGGESTION_CACHE_IDLE_RESET_MS) {
            SUGGESTION_CACHE.clear();
            PENDING_FETCHES.clear();
            SUGGESTION_UNIONS.clear();
        }
    }

    static String buildAutofillQuery(String resolvedCommand, CaptureListSelector.Filter filter, String filterText) {
        String query = resolvedCommand.startsWith("/") ? resolvedCommand : "/" + resolvedCommand;

        if (!query.contains(" ")) query = query + " ";
        String prefix = filter == CaptureListSelector.Filter.PREFIX && filterText != null ? filterText.trim() : "";
        if (!prefix.isEmpty() && !prefix.contains(" ") && query.endsWith(" ")) {

            int end = query.length();
            while (end > 0 && query.charAt(end - 1) == ' ') end--;
            query = query.substring(0, end) + " " + prefix;
        }
        return query;
    }

    private static java.util.List<String> capturePool(net.minecraft.client.multiplayer.ClientPacketListener connection,
                                                      CaptureValueAction action, String query, java.util.List<String> base) {

        if (!action.autofillCacheList && base.size() < TRUNCATED_REPLY_SIZE) return base;
        SuggestionUnion union = SUGGESTION_UNIONS.compute(query, (key, existing) ->
            existing != null && existing.connection == connection ? existing : new SuggestionUnion(connection, query));
        synchronized (union) {
            union.names.addAll(base);
            union.cap = Math.max(union.cap, base.size());
            advanceSweep(union, connection, action.autofillTimeoutMs);
            java.util.List<String> pool = new java.util.ArrayList<>(union.names);
            boolean ordered = action.listSelection == CaptureListSelector.Selection.SEQUENTIAL
                || action.listSelection == CaptureListSelector.Selection.FIRST
                || action.listSelection == CaptureListSelector.Selection.LAST
                || action.listSelection == CaptureListSelector.Selection.POSITION;

            if (ordered) pool.sort(String.CASE_INSENSITIVE_ORDER);
            return pool;
        }
    }

    private static void advanceSweep(SuggestionUnion union, net.minecraft.client.multiplayer.ClientPacketListener connection, int timeoutMs) {
        if (union.names.size() >= MAX_SWEEP_NAMES) { union.sweepQueue.clear(); return; }
        while (!union.sweepQueue.isEmpty()) {
            if (union.alphabetCursor >= DRILL_ALPHABET.length()) {
                union.sweepQueue.poll();
                union.alphabetCursor = 0;
                continue;
            }
            String target = union.sweepQueue.peek() + DRILL_ALPHABET.charAt(union.alphabetCursor);
            CachedSuggestions cached = SUGGESTION_CACHE.get(target);
            if (cached == null || cached.connection() != connection) {
                fireSuggestionFetch(connection, target, timeoutMs);
                return;
            }
            union.names.addAll(cached.suggestions());
            int size = cached.suggestions().size();

            boolean truncated = size >= union.cap && union.cap >= MIN_SWEEP_CAP;
            union.cap = Math.max(union.cap, size);
            if (truncated) union.sweepQueue.add(target);
            union.alphabetCursor++;
        }
    }

    private static void applyCaptureSuggestions(MacroExecutor.RunState run, CaptureValueAction action,
                                                java.util.List<String> suggestions, String query) {
        CaptureValueAction.Preview preview = action.previewSuggestions(suggestions, query);
        if (!preview.success()) return;
        if (run != null) run.variables.setAll(preview.values());
        else MacroVariables.setAll(preview.values());
    }

    private static PendingFetch fireSuggestionFetch(net.minecraft.client.multiplayer.ClientPacketListener connection,
                                                    String query, int timeoutMs) {
        PendingFetch existing = PENDING_FETCHES.get(query);
        if (existing != null && existing.connection() == connection && !existing.future().isDone()) return existing;
        if (PENDING_FETCHES.size() > SUGGESTION_CACHE_MAX) return existing;
        int requestId = autismclient.util.AutismCommandSuggestionIds.nextMacroId();
        CompletableFuture<java.util.List<String>> future = new CompletableFuture<>();
        MacroExecutor.OneShotPacketListener listener = MacroExecutor.awaitReceive(packet -> {
            if (!(packet instanceof net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket reply)
                    || reply.id() != requestId) {
                return false;
            }
            java.util.List<String> texts = new java.util.ArrayList<>();
            if (reply.suggestions() != null) {
                for (var entry : reply.suggestions()) {
                    if (entry != null && entry.text() != null && !entry.text().isBlank()) texts.add(entry.text());
                }
            }
            future.complete(texts);
            return true;
        });
        PendingFetch pending = new PendingFetch(future, connection);
        PENDING_FETCHES.put(query, pending);
        future.completeOnTimeout(null, timeoutMs > 0 ? Math.max(1000, timeoutMs) : 30_000,
            java.util.concurrent.TimeUnit.MILLISECONDS);
        future.whenComplete((texts, error) -> {
            listener.cancel();
            PENDING_FETCHES.remove(query, pending);

            if (texts != null) {
                if (SUGGESTION_CACHE.size() >= SUGGESTION_CACHE_MAX) SUGGESTION_CACHE.clear();
                SUGGESTION_CACHE.put(query, new CachedSuggestions(java.util.List.copyOf(texts), connection));
            }
        });
        try {
            connection.send(new net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket(requestId, query));
        } catch (Throwable error) {
            future.complete(null);
        }
        return pending;
    }

    private static void runCaptureCommandAutofill(Minecraft mc, CaptureValueAction action) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        MacroPrearm.clearCaptureOutputs(action);
        MacroTemplate.Resolution resolved = MacroVariables.resolve(action.autofillCommand, mc);
        if (!resolved.success() || resolved.value().isBlank()) return;
        String query = buildAutofillQuery(resolved.value(), action.listFilter, action.listFilterText);
        var connection = mc.getConnection();
        if (connection == null) return;

        if (action.autofillCacheList) {
            CachedSuggestions listCache = SUGGESTION_CACHE.get(query);
            if (listCache != null && listCache.connection() == connection && !listCache.suggestions().isEmpty()) {
                MacroExecutor.setCurrentStatus("Using cached suggestions");
                applyCaptureSuggestions(run, action, listCache.suggestions(), query);
                return;
            }
            MacroExecutor.setCurrentStatus("Sweeping command suggestions");
            java.util.List<String> all = sweepAutofillCommand(connection, query);
            if (all.isEmpty()) return;
            if (SUGGESTION_CACHE.size() >= SUGGESTION_CACHE_MAX) SUGGESTION_CACHE.clear();
            SUGGESTION_CACHE.put(query, new CachedSuggestions(java.util.List.copyOf(all), connection));
            applyCaptureSuggestions(run, action, all, query);
            return;
        }

        CachedSuggestions cached = SUGGESTION_CACHE.get(query);

        if (cached != null && (cached.connection() != connection || cached.suggestions().isEmpty())) cached = null;

        if (cached != null) {

            MacroExecutor.setCurrentStatus("Using cached suggestions");
            applyCaptureSuggestions(run, action, capturePool(connection, action, query, cached.suggestions()), query);
            fireSuggestionFetch(connection, query, action.autofillTimeoutMs);
            return;
        }

        MacroExecutor.setCurrentStatus("Requesting command suggestions");
        PendingFetch pending = fireSuggestionFetch(connection, query, action.autofillTimeoutMs);
        if (pending == null) return;
        if (!MacroRace.waitForFutureDone(pending.future(), 0)) return;
        java.util.List<String> suggestions = pending.future().getNow(null);
        if (suggestions == null || suggestions.isEmpty()) return;
        applyCaptureSuggestions(run, action, capturePool(connection, action, query, suggestions), query);
    }

    private static java.util.List<String> sweepAutofillCommand(net.minecraft.client.multiplayer.ClientPacketListener connection, String query) {
        autismclient.util.AutismNameHarvest.Control control = new autismclient.util.AutismNameHarvest.Control() {
            @Override public boolean cancelled() {
                Minecraft mc = Minecraft.getInstance();
                return !MacroExecutor.isCurrentRunActive() || mc == null || mc.getConnection() != connection;
            }
            @Override public boolean paused() { return false; }
            @Override public int limit() { return MAX_SWEEP_NAMES; }
            @Override public int maxQueries() { return 50_000; }
            @Override public boolean usernamesOnly() { return false; }
        };
        java.util.List<String> all = autismclient.util.AutismNameHarvest.sweepCommand(connection, query, control);
        all.sort(String.CASE_INSENSITIVE_ORDER);
        return all;
    }

    private static void runCaptureNamescrape(Minecraft mc, CaptureValueAction action) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        MacroPrearm.clearCaptureOutputs(action);
        var connection = mc.getConnection();
        if (connection == null) return;
        String cacheKey = " namescrape";
        CachedSuggestions cached = SUGGESTION_CACHE.get(cacheKey);
        if (cached != null && cached.connection() == connection && !cached.suggestions().isEmpty()) {
            MacroExecutor.setCurrentStatus("Using cached names");
            applyCaptureSuggestions(run, action, cached.suggestions(), "");
            return;
        }
        MacroExecutor.setCurrentStatus("Scraping player names");
        java.util.List<String> names = scrapeAllNames(mc, connection);
        if (names.isEmpty()) return;
        if (SUGGESTION_CACHE.size() >= SUGGESTION_CACHE_MAX) SUGGESTION_CACHE.clear();
        SUGGESTION_CACHE.put(cacheKey, new CachedSuggestions(java.util.List.copyOf(names), connection));
        applyCaptureSuggestions(run, action, names, "");
    }

    private static java.util.List<String> scrapeAllNames(Minecraft mc, net.minecraft.client.multiplayer.ClientPacketListener connection) {
        String self = mc.player == null ? "" : mc.player.getName().getString();
        java.util.LinkedHashMap<String, String> names = autismclient.util.AutismNameHarvest.instantNames(mc, self);
        java.util.List<autismclient.util.AutismNameHarvest.Vector> vectors =
                autismclient.util.AutismNameHarvest.discoverVectors(connection);
        autismclient.util.AutismNameHarvest.Control control = new autismclient.util.AutismNameHarvest.Control() {
            @Override public boolean cancelled() {
                Minecraft m = Minecraft.getInstance();
                return !MacroExecutor.isCurrentRunActive() || m == null || m.getConnection() != connection;
            }
            @Override public boolean paused() { return false; }
            @Override public int limit() { return MAX_SWEEP_NAMES; }
            @Override public int maxQueries() { return 100_000; }
        };
        autismclient.util.AutismNameHarvest.sweep(connection, names, self, vectors, control);
        java.util.List<String> out = new java.util.ArrayList<>(names.values());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static void runCaptureValueAction(Minecraft mc, CaptureValueAction action) {
        if (action == null) return;
        String modifierError = action.numberModifierError();
        if (!modifierError.isBlank()) {

            MacroExecutor.setCurrentStatus("Capture failed: " + modifierError);
            autismclient.util.AutismNotifications.error("Capture Value: " + modifierError);
            return;
        }
        if (action.source == CaptureValueAction.Source.COMMAND_AUTOFILL) {
            MacroExecutor.setCurrentStatus("Requesting command suggestions");
            runCaptureCommandAutofill(mc, action);
            reportMissingCapture(action);
            return;
        }
        if (action.source == CaptureValueAction.Source.NAMESCRAPE) {
            MacroExecutor.setCurrentStatus("Scraping player names");
            runCaptureNamescrape(mc, action);
            reportMissingCapture(action);
            return;
        }
        if (action.waitForTrigger) {
            CaptureValueAction.Source source = action.source == null ? CaptureValueAction.Source.GUI_TITLE : action.source;
            MacroExecutor.setCurrentStatus("Waiting for capture: " + source.name().toLowerCase(Locale.ROOT).replace('_', ' '));
            if (source == CaptureValueAction.Source.RECENT_CHAT) MacroPacketWait.awaitCaptureValueChat(action);
            else if (source == CaptureValueAction.Source.SCOREBOARD) awaitCaptureValueScoreboard(mc, action);
            else awaitCaptureValueState(mc, action);
            return;
        }
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        java.util.concurrent.atomic.AtomicReference<CaptureValueAction.Preview> result =
            new java.util.concurrent.atomic.AtomicReference<>(CaptureValueAction.Preview.unavailable("Not available"));

        long deadline = System.nanoTime() + MacroExecutor.CAPTURE_SETTLE_MS * 1_000_000L;
        while (MacroExecutor.isCurrentRunActive()) {
            CompletableFuture<Void> done = new CompletableFuture<>();
            mc.execute(() -> MacroExecutor.withRunContext(run, () -> {
                try {
                    result.set(action.run(mc));
                    done.complete(null);
                } catch (Throwable error) {
                    done.completeExceptionally(error);
                }
            }));

            MacroRace.waitForFutureDone(done, 0);
            if (result.get().success() || System.nanoTime() >= deadline) break;
            MacroExecutor.waitOneTick();
        }
        CaptureValueAction.Preview preview = result.get();
        if (!preview.success()) {

            MacroExecutor.setCurrentStatus("Capture failed: " + preview.message());
            autismclient.util.AutismNotifications.error(
                "Capture " + captureOutputLabel(action) + ": " + preview.message());
        }
    }

    private static void reportMissingCapture(CaptureValueAction action) {
        String name = MacroVariableContext.cleanRootName(action.saveAs);
        if (name.isBlank() || MacroVariables.value(name).isPresent()) return;
        MacroExecutor.setCurrentStatus("Capture failed: nothing captured");
        autismclient.util.AutismNotifications.error(
            "Capture " + captureOutputLabel(action) + ": nothing captured");
    }

    private static String captureOutputLabel(CaptureValueAction action) {
        String name = MacroVariableContext.cleanRootName(action.saveAs);
        return name.isBlank() ? "value" : "{" + name + "}";
    }

    private static void awaitCaptureValueState(Minecraft mc, CaptureValueAction action) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        MacroPrearm.clearCaptureOutputs(action);
        String lastMessage = "";
        while (MacroExecutor.isCurrentRunActive()) {
            CaptureValueAction.Preview preview;
            try {
                preview = MacroClientThread.callOnClientThread(mc, () -> action.previewCurrent(mc),
                    CaptureValueAction.Preview.unavailable("Not available"));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (preview.success()) {
                if (run != null) run.variables.setAll(preview.values());
                else MacroVariables.setAll(preview.values());
                return;
            }
            if (!preview.message().isBlank() && !preview.message().equals(lastMessage)) {
                lastMessage = preview.message();
                MacroExecutor.setCurrentStatus("Capture waiting: " + lastMessage);
            }
            MacroExecutor.waitOneTick();
        }
    }

    private static void awaitCaptureValueScoreboard(Minecraft mc, CaptureValueAction action) {
        MacroExecutor.RunState run = MacroExecutor.CURRENT_RUN.get();
        MacroPrearm.clearCaptureOutputs(action);
        String trackedKey = action.scoreboardRow;
        int trackedRow = action.scoreboardRowIndex;
        String trackedObjective = action.scoreboardObjective;
        String previousText = null;
        String lastMessage = "";
        boolean baselineReady = false;
        while (MacroExecutor.isCurrentRunActive()) {
            CaptureValueAction.ScoreboardLine line;
            try {
                String keySnapshot = trackedKey;
                int rowSnapshot = trackedRow;
                String objectiveSnapshot = trackedObjective;
                line = MacroClientThread.callOnClientThread(mc,
                    () -> action.resolveScoreboardLine(mc, keySnapshot, rowSnapshot, objectiveSnapshot), null);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (line != null) {
                String currentText = line.text() == null ? "" : line.text();
                if (!baselineReady) {
                    trackedKey = line.key();
                    trackedRow = line.row();
                    trackedObjective = line.objective();
                    previousText = currentText;
                    baselineReady = true;
                } else if (!currentText.equals(previousText)) {
                    CaptureValueAction.Preview preview = action.previewScoreboardLine(line);
                    if (preview.success()) {
                        if (run != null) run.variables.setAll(preview.values());
                        else MacroVariables.setAll(preview.values());
                        return;
                    }
                    if (!preview.message().isBlank() && !preview.message().equals(lastMessage)) {
                        lastMessage = preview.message();
                        MacroExecutor.setCurrentStatus("Capture waiting: " + lastMessage);
                    }
                    previousText = currentText;
                }
            }
            MacroExecutor.waitOneTick();
        }
    }
}
