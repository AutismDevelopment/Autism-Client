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

    public static <S> CompletableFuture<Suggestions> itemIds(CommandContext<S> ignored, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            String full = id.toString();
            String shortId = id.getNamespace().equals("minecraft") ? id.getPath() : full;
            if (full.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(full);
            if (!shortId.equals(full) && shortId.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(shortId);
        }
        return builder.buildFuture();
    }
}
