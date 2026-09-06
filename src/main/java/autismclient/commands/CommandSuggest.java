package autismclient.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class CommandSuggest {
    private CommandSuggest() {}

    public static <S> CompletableFuture<Suggestions> literals(SuggestionsBuilder builder, String... values) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(value);
        }
        return builder.buildFuture();
    }

    public static <S> CompletableFuture<Suggestions> state(CommandContext<S> ignored, SuggestionsBuilder builder) {
        return literals(builder, "on", "off", "toggle", "enable", "disable");
    }

    public static <S> CompletableFuture<Suggestions> offsets(CommandContext<S> ignored, SuggestionsBuilder builder) {
        return literals(builder, "1", "5", "10", "50", "100", "-1", "-5", "-10", "-50", "-100");
    }

    public static <S> CompletableFuture<Suggestions> vclipSegments(CommandContext<S> ignored, SuggestionsBuilder builder) {
        return literals(builder, "1", "5", "10", "20", "50");
    }

    public static <S> CompletableFuture<Suggestions> vclipPacketLimits(CommandContext<S> ignored, SuggestionsBuilder builder) {
        return literals(builder, "1", "5", "10", "20", "40", "100");
    }

    public static <S> CompletableFuture<Suggestions> damage(CommandContext<S> ignored, SuggestionsBuilder builder) {
        return literals(builder, "1", "2", "4", "8", "20");
    }

    public static <S> CompletableFuture<Suggestions> counts(CommandContext<S> ignored, SuggestionsBuilder builder) {
        return literals(builder, "1", "16", "32", "64", "1000", "10000", "100000");
    }

    public static <S> CompletableFuture<Suggestions> gamemodes(CommandContext<S> ignored, SuggestionsBuilder builder) {
        return literals(builder, "survival", "creative", "adventure", "spectator", "reset", "0", "1", "2", "3");
    }

    public static <S> CompletableFuture<Suggestions> realGamemodes(CommandContext<S> ignored, SuggestionsBuilder builder) {
        return literals(builder, "survival", "creative", "adventure", "spectator", "0", "1", "2", "3");
    }

    public static <S> CompletableFuture<Suggestions> prefixes(CommandContext<S> ignored, SuggestionsBuilder builder) {
        return literals(builder, ".", "%", "-", "_", "*", "#", "@", "&", "=");
    }

    private static volatile String[][] itemIdCache;
    private static volatile String[][] blockIdCache;

    public static <S> CompletableFuture<Suggestions> itemIds(CommandContext<S> ignored, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        String[][] cache = itemIdCache;
        if (cache == null) cache = itemIdCache = buildIdCache(BuiltInRegistries.ITEM.keySet());
        for (String[] entry : cache) {
            if (entry[1].startsWith(remaining)) builder.suggest(entry[0]);
        }
        return builder.buildFuture();
    }

    public static <S> CompletableFuture<Suggestions> blockIds(CommandContext<S> ignored, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        String[][] cache = blockIdCache;
        if (cache == null) cache = blockIdCache = buildIdCache(BuiltInRegistries.BLOCK.keySet());
        for (String[] entry : cache) {
            if (entry[1].startsWith(remaining)) builder.suggest(entry[0]);
        }
        return builder.buildFuture();
    }

    private static String[][] buildIdCache(java.util.Set<Identifier> ids) {
        java.util.List<String[]> entries = new java.util.ArrayList<>(ids.size() * 2);
        for (Identifier id : ids) {
            String full = id.toString();
            String shortId = id.getNamespace().equals("minecraft") ? id.getPath() : full;
            entries.add(new String[]{full, full.toLowerCase(Locale.ROOT)});
            if (!shortId.equals(full)) entries.add(new String[]{shortId, shortId.toLowerCase(Locale.ROOT)});
        }
        return entries.toArray(new String[0][]);
    }
}
