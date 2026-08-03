package autismclient.commands.args;

import autismclient.commands.AutismCommandSource;
import autismclient.util.multi.MultiProfile;
import autismclient.util.multi.MultiProfileManager;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MultiProfileArgumentType implements ArgumentType<String> {
    private static volatile long cachedRevision = Long.MIN_VALUE;
    private static volatile List<Candidate> cachedCandidates = List.of();

    private record Candidate(String value, String lower) {}

    public static MultiProfileArgumentType profileName() {
        return new MultiProfileArgumentType();
    }

    public static String get(CommandContext<AutismCommandSource> ctx, String name) {
        return ctx.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '"') return reader.readQuotedString();
        return reader.readUnquotedString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Candidate candidate : candidates()) {
            if (candidate.lower().startsWith(remaining)) builder.suggest(candidate.value());
        }
        return builder.buildFuture();
    }

    private static List<Candidate> candidates() {
        MultiProfileManager manager = MultiProfileManager.get();
        long revision = manager.revision();
        List<Candidate> hit = cachedCandidates;
        if (cachedRevision == revision) return hit;
        List<Candidate> rebuilt = new ArrayList<>();
        for (MultiProfile profile : manager) {
            if (profile == null || profile.name == null) continue;
            String name = profile.name;
            String value = name.contains(" ") ? "\"" + name + "\"" : name;
            rebuilt.add(new Candidate(value, name.toLowerCase(Locale.ROOT)));
        }
        hit = List.copyOf(rebuilt);
        cachedCandidates = hit;
        cachedRevision = revision;
        return hit;
    }
}
