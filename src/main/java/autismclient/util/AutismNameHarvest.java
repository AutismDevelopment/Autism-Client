package autismclient.util;

import autismclient.util.macro.MacroExecutor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AutismNameHarvest {

    private static final String DRILL_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_.";
    private static final int MIN_SWEEP_CAP = 4;
    private static final int FAST_DRILL_DEPTH = 1;

    private static final int FAST_STALL_QUERIES = 20;
    private static final int MAX_VECTORS = 6;
    private static final int DRY_VECTORS = 2;

    private static final int MIN_CONCURRENCY = 4;
    private static final int MAX_CONCURRENCY = 28;
    private static final int START_CONCURRENCY = 10;
    private static final long SEND_STAGGER_MS = 2L;
    private static final long PROBE_TIMEOUT_MS = 2_500L;

    private static final long DRILL_TIMEOUT_MS = 1_500L;
    private static final int DRILL_RETRIES = 2;
    private static final long MIN_GAP_MS = 8L;
    private static final long MAX_GAP_MS = 500L;

    private static final List<String> PRIORITY_ORDER = List.of(
        "msg", "tell", "w", "whisper", "message", "pm", "dm", "emsg", "etell",
        "tpa", "tpahere", "tpask", "pay", "mail", "friend", "party", "invite");
    private static final Set<String> PLAYER_ARG_NAMES = Set.of(
        "player", "players", "target", "targets", "name", "username", "who", "victim", "user", "users",
        "recipient", "nick", "gameprofile", "profile", "targetplayer", "playername");

    private static final String[] FALLBACK_PREFIXES = {
        "msg ", "tell ", "w ", "whisper ", "message ", "pm ", "pay ", "tpa ", "tpahere ", "tpask ",
        "mail send ", "friend add ", "party invite ", "invite ", "minecraft:msg ", "minecraft:tell ", "minecraft:w "};

    private AutismNameHarvest() {
    }

    public interface Control {
        boolean cancelled();

        boolean paused();

        int limit();

        int maxQueries();

        default boolean usernamesOnly() {
            return true;
        }

        default boolean deepSweep() {
            return false;
        }

        default void onProgress(int names, int queries, int maxQueries, String vector) {
        }
    }

    public record Vector(String prefix, int rank, String label) {
    }

    public static LinkedHashMap<String, String> instantNames(Minecraft mc, String self) {
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        if (mc == null) return names;
        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            addPlayerInfos(names, safe(connection::getOnlinePlayers), self);
            addPlayerInfos(names, safe(connection::getListedOnlinePlayers), self);
            try {
                addPlayerInfos(names, connection.getSeenPlayers().values(), self);
            } catch (Throwable ignored) {  }
        }
        if (mc.level != null) {
            try {
                for (ScoreHolder holder : mc.level.getScoreboard().getTrackedPlayers()) {
                    if (holder != null) addValid(names, holder.getScoreboardName(), self);
                }
            } catch (Throwable ignored) {  }
            try {
                for (PlayerTeam team : mc.level.getScoreboard().getPlayerTeams()) {
                    if (team == null) continue;
                    for (String member : team.getPlayers()) addValid(names, member, self);
                }
            } catch (Throwable ignored) {  }
        }
        return names;
    }

    private static Collection<PlayerInfo> safe(java.util.function.Supplier<Collection<PlayerInfo>> supplier) {
        try {
            Collection<PlayerInfo> value = supplier.get();
            return value == null ? List.of() : value;
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static void addPlayerInfos(Map<String, String> names, Collection<PlayerInfo> infos, String self) {
        for (PlayerInfo info : infos) {
            if (info != null && info.getProfile() != null) add(names, info.getProfile().name(), self);
        }
    }

    public static List<Vector> discoverVectors(ClientPacketListener connection) {
        Map<String, Vector> byPrefix = new LinkedHashMap<>();
        if (connection != null) {
            CommandDispatcher<ClientSuggestionProvider> dispatcher = null;
            try {
                dispatcher = connection.getCommands();
            } catch (Throwable ignored) {  }
            if (dispatcher != null && dispatcher.getRoot() != null) {
                try {
                    walk(dispatcher.getRoot(), "", byPrefix, 0);
                } catch (Throwable ignored) {  }
            }
        }
        List<Vector> out = new ArrayList<>(byPrefix.values());
        out.sort(Comparator.comparingInt(Vector::rank));
        if (out.size() > MAX_VECTORS) out = new ArrayList<>(out.subList(0, MAX_VECTORS));
        if (out.isEmpty()) {
            for (String prefix : FALLBACK_PREFIXES) out.add(new Vector(prefix, 0, prefix.trim()));
        }
        return out;
    }

    private static void walk(CommandNode<ClientSuggestionProvider> node, String path,
                             Map<String, Vector> byPrefix, int depth) {
        if (node == null || depth > 6) return;
        CommandNode<ClientSuggestionProvider> source = node;
        Collection<CommandNode<ClientSuggestionProvider>> children = node.getChildren();
        if ((children == null || children.isEmpty()) && node.getRedirect() != null) {
            source = node.getRedirect();
            children = source.getChildren();
        }
        if (children == null) return;
        for (CommandNode<ClientSuggestionProvider> child : children) {
            if (child == null) continue;
            String name = child.getName();
            if (name == null || name.isBlank()) continue;
            if (child instanceof ArgumentCommandNode<?, ?> arg) {
                if (!path.isEmpty() && isPlayerNameArg(arg)) {
                    String prefix = path + " ";
                    Vector candidate = new Vector(prefix, rank(path, arg), path);
                    byPrefix.merge(prefix, candidate, (a, b) -> a.rank() <= b.rank() ? a : b);
                }

            } else {
                String childPath = path.isEmpty() ? name : path + " " + name;
                walk(child, childPath, byPrefix, depth + 1);
            }
        }
    }

    private static boolean isPlayerNameArg(ArgumentCommandNode<?, ?> arg) {
        Object type = arg.getType();
        if (type instanceof EntityArgument || type instanceof GameProfileArgument || type instanceof ScoreHolderArgument) {
            return true;
        }
        return isAskServer(arg.getCustomSuggestions());
    }

    private static boolean isAskServer(SuggestionProvider<?> suggestions) {
        if (suggestions == null) return false;
        try {
            Identifier id = SuggestionProviders.getName(suggestions);
            return id != null && "ask_server".equals(id.getPath());
        } catch (Throwable t) {
            return false;
        }
    }

    private static int rank(String path, ArgumentCommandNode<?, ?> arg) {
        String first = stripNamespace(path.contains(" ") ? path.substring(0, path.indexOf(' ')) : path)
            .toLowerCase(Locale.ROOT);
        int priority = PRIORITY_ORDER.indexOf(first);
        if (priority >= 0) return priority;
        String argName = arg.getName() == null ? "" : arg.getName().toLowerCase(Locale.ROOT);
        if (PLAYER_ARG_NAMES.contains(argName)) return 100;
        Object type = arg.getType();
        if (type instanceof EntityArgument || type instanceof GameProfileArgument || type instanceof ScoreHolderArgument) {
            return 200;
        }
        return 300;
    }

    private static String stripNamespace(String literal) {
        int colon = literal.indexOf(':');
        return colon >= 0 ? literal.substring(colon + 1) : literal;
    }

    public static void sweep(ClientPacketListener connection, Map<String, String> names, String self,
                             List<Vector> vectors, Control control) {
        if (connection == null || vectors == null || control == null) return;
        int[] queries = {0};
        long[] gap = {MIN_GAP_MS};
        int[] conc = {START_CONCURRENCY};
        int[] stall = {0};
        int stallLimit = control.deepSweep() ? Integer.MAX_VALUE : FAST_STALL_QUERIES;

        control.onProgress(names.size(), queries[0], control.maxQueries(), "Probing " + vectors.size() + " commands");
        List<String> baseTexts = new ArrayList<>(vectors.size());
        for (Vector vector : vectors) baseTexts.add(vector.prefix());
        List<List<String>> bases = fireBatch(connection, baseTexts, control, queries, names, self,
            "Probing commands", PROBE_TIMEOUT_MS, 0, gap, conc, stall, stallLimit);

        List<Vector> productive = new ArrayList<>();
        for (int i = 0; i < vectors.size() && i < bases.size(); i++) {
            if (anyKept(bases.get(i), control.usernamesOnly())) productive.add(vectors.get(i));
        }
        productive.sort(Comparator.comparingInt(Vector::rank));
        int maxDepth = control.deepSweep() ? Integer.MAX_VALUE : FAST_DRILL_DEPTH;
        int dry = 0;
        for (Vector vector : productive) {
            if (control.cancelled() || names.size() >= control.limit() || queries[0] >= control.maxQueries()
                || stall[0] >= stallLimit) break;
            int before = names.size();
            drillVector(connection, names, self, vector, control, queries, gap, conc, maxDepth, stall, stallLimit);
            if (names.size() == before) {
                if (++dry >= DRY_VECTORS) break;
            } else {
                dry = 0;
            }
        }
        control.onProgress(names.size(), queries[0], control.maxQueries(), "Done");
    }

    public static List<String> sweepCommand(ClientPacketListener connection, String query, Control control) {
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        if (connection != null && query != null && !query.isBlank() && control != null) {
            sweep(connection, names, "", List.of(new Vector(query, 0, query.trim())), control);
        }
        return new ArrayList<>(names.values());
    }

    private static void drillVector(ClientPacketListener connection, Map<String, String> names, String self,
                                    Vector vector, Control control, int[] queries, long[] gap, int[] conc, int maxDepth,
                                    int[] stall, int stallLimit) {
        int afterBase = names.size();
        List<String> frontier = new ArrayList<>();
        frontier.add("");
        boolean firstLevel = true;
        int depth = 0;
        while (!frontier.isEmpty()) {
            if (control.cancelled() || names.size() >= control.limit() || queries[0] >= control.maxQueries()
                || stall[0] >= stallLimit) break;
            depth++;
            int before = names.size();
            List<String> childPrefixes = new ArrayList<>(frontier.size() * DRILL_ALPHABET.length());
            List<String> texts = new ArrayList<>(childPrefixes.size());
            for (String parent : frontier) {
                for (int i = 0; i < DRILL_ALPHABET.length(); i++) {
                    String childPrefix = parent + DRILL_ALPHABET.charAt(i);
                    childPrefixes.add(childPrefix);
                    texts.add(vector.prefix() + childPrefix);
                }
            }
            String status = "Sweeping " + vector.label() + " — " + fmt(names.size()) + " names (depth " + depth + ")";
            List<List<String>> replies = fireBatch(connection, texts, control, queries, names, self,
                status, DRILL_TIMEOUT_MS, DRILL_RETRIES, gap, conc, stall, stallLimit);
            int levelCap = 0;
            for (List<String> reply : replies) levelCap = Math.max(levelCap, reply.size());
            if (firstLevel && names.size() == afterBase) return;
            firstLevel = false;
            if (depth >= maxDepth) return;
            List<String> next = new ArrayList<>();

            if (names.size() > before && levelCap >= MIN_SWEEP_CAP) {
                for (int i = 0; i < replies.size(); i++) {
                    if (replies.get(i).size() >= levelCap) next.add(childPrefixes.get(i));
                }
            }
            frontier = next;
        }
    }

    private static String fmt(int n) {
        return String.format(java.util.Locale.US, "%,d", n);
    }

    private static List<List<String>> fireBatch(ClientPacketListener connection, List<String> texts, Control control,
                                                int[] queries, Map<String, String> names, String self, String status,
                                                long timeoutMs, int retries, long[] gap, int[] conc,
                                                int[] stall, int stallLimit) {
        int n = texts.size();
        List<List<String>> results = new ArrayList<>(n);
        for (int i = 0; i < n; i++) results.add(null);
        List<Integer> pending = new ArrayList<>(n);
        for (int i = 0; i < n; i++) pending.add(i);

        for (int round = 0; round <= retries && !pending.isEmpty(); round++) {
            boolean lastRound = round == retries;
            List<Integer> retry = new ArrayList<>();
            int start = 0;
            while (start < pending.size()) {
                if (control.cancelled() || names.size() >= control.limit() || queries[0] >= control.maxQueries()
                    || stall[0] >= stallLimit) {
                    pending = List.of();
                    retry = new ArrayList<>();
                    break;
                }
                awaitUnpause(control);

                int batch = Math.max(MIN_CONCURRENCY, conc[0]);
                int end = Math.min(pending.size(), start + batch);
                List<MacroExecutor.OneShotPacketListener> listeners = new ArrayList<>(end - start);
                for (int k = start; k < end; k++) {
                    int idx = pending.get(k);
                    final int requestId = AutismCommandSuggestionIds.nextMacroId();
                    MacroExecutor.OneShotPacketListener listener = MacroExecutor.awaitReceive(packet ->
                        packet instanceof ClientboundCommandSuggestionsPacket reply && reply.id() == requestId);
                    listeners.add(listener);
                    try {
                        connection.send(new ServerboundCommandSuggestionPacket(requestId, texts.get(idx)));
                    } catch (Throwable t) {
                        listener.future.complete(null);
                    }
                    sleep(SEND_STAGGER_MS);
                }

                long deadline = System.currentTimeMillis() + timeoutMs;
                boolean[] done = new boolean[end - start];
                int remaining = end - start;
                int timeouts = 0;
                while (remaining > 0) {
                    if (control.cancelled() || stall[0] >= stallLimit) {
                        for (int li = 0; li < listeners.size(); li++) if (!done[li]) listeners.get(li).cancel();
                        break;
                    }
                    boolean progressed = false;
                    for (int k = start; k < end; k++) {
                        int li = k - start;
                        if (done[li] || !listeners.get(li).future.isDone()) continue;
                        done[li] = true;
                        remaining--;
                        queries[0]++;
                        List<String> reply = drain(listeners.get(li));
                        results.set(pending.get(k), reply);
                        int prev = names.size();
                        mergeValid(names, reply, self, control.limit(), control.usernamesOnly());
                        stall[0] = names.size() > prev ? 0 : stall[0] + 1;
                        control.onProgress(names.size(), queries[0], control.maxQueries(), status);
                        progressed = true;
                    }
                    if (remaining == 0) break;
                    if (System.currentTimeMillis() >= deadline) {
                        for (int k = start; k < end; k++) {
                            int li = k - start;
                            if (done[li]) continue;
                            queries[0]++;
                            timeouts++;
                            stall[0]++;
                            if (lastRound) results.set(pending.get(k), List.of());
                            else retry.add(pending.get(k));
                            listeners.get(li).cancel();
                        }
                        control.onProgress(names.size(), queries[0], control.maxQueries(), status);
                        break;
                    }
                    if (!progressed) sleep(5L);
                }

                if (timeouts > 0) {
                    conc[0] = Math.max(MIN_CONCURRENCY, conc[0] / 2);
                    gap[0] = Math.min(MAX_GAP_MS, Math.max(MIN_GAP_MS, gap[0] * 2 + 20));
                } else {
                    conc[0] = Math.min(MAX_CONCURRENCY, conc[0] + 1);
                    gap[0] = Math.max(MIN_GAP_MS, gap[0] - 10);
                }
                if (!control.cancelled()) sleep(gap[0]);
                start = end;
            }
            pending = retry;
        }
        for (int i = 0; i < n; i++) {
            if (results.get(i) == null) results.set(i, List.of());
        }
        return results;
    }

    private static List<String> drain(MacroExecutor.OneShotPacketListener listener) {
        try {
            var reply = listener.future.getNow(null);
            List<String> out = new ArrayList<>();
            if (reply instanceof ClientboundCommandSuggestionsPacket suggestions && suggestions.suggestions() != null) {
                for (var entry : suggestions.suggestions()) {
                    if (entry != null && entry.text() != null && !entry.text().isBlank()) out.add(entry.text().trim());
                }
            }
            return out;
        } catch (Throwable t) {
            return List.of();
        } finally {
            listener.cancel();
        }
    }

    private static int mergeValid(Map<String, String> names, List<String> suggestions, String self, int cap,
                                  boolean usernamesOnly) {
        int added = 0;
        for (String suggestion : suggestions) {
            if (names.size() >= cap) break;
            boolean ok = usernamesOnly ? addValid(names, suggestion, self) : addAny(names, suggestion, self);
            if (ok) added++;
        }
        return added;
    }

    private static boolean anyKept(List<String> suggestions, boolean usernamesOnly) {
        for (String suggestion : suggestions) {
            if (suggestion == null) continue;
            String trimmed = suggestion.trim();
            if (trimmed.isEmpty()) continue;
            if (!usernamesOnly || AutismPlayerScanner.isRealName(trimmed)) return true;
        }
        return false;
    }

    private static boolean addAny(Map<String, String> names, String name, String self) {
        if (name == null) return false;
        String trimmed = name.trim();
        if (trimmed.isEmpty() || (self != null && !self.isEmpty() && trimmed.equalsIgnoreCase(self))) return false;
        return names.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed) == null;
    }

    private static void add(Map<String, String> names, String name, String self) {
        if (name == null) return;
        String trimmed = name.trim();
        if (trimmed.equalsIgnoreCase(self) || !AutismPlayerScanner.isRealName(trimmed)) return;
        names.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
    }

    private static boolean addValid(Map<String, String> names, String name, String self) {
        if (name == null) return false;
        String trimmed = name.trim();
        if (trimmed.equalsIgnoreCase(self) || !AutismPlayerScanner.isRealName(trimmed)) return false;
        return names.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed) == null;
    }

    private static void awaitUnpause(Control control) {
        while (control.paused() && !control.cancelled()) {
            sleep(120L);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
