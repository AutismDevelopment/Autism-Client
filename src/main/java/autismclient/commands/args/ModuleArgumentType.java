package autismclient.commands.args;

import autismclient.commands.AutismCommandSource;
import autismclient.modules.ModuleRegistry;
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

public class ModuleArgumentType implements ArgumentType<String> {
    private static volatile int cachedRevision = Integer.MIN_VALUE;
    private static volatile List<Candidate> cachedCandidates = List.of();

    private record Candidate(String value, String lower) {}

    public static ModuleArgumentType moduleName() { return new ModuleArgumentType(); }

    public static String get(CommandContext<AutismCommandSource> ctx, String name) {
        return ctx.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readUnquotedString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Candidate candidate : candidates()) {
            if (candidate.lower().startsWith(remaining)) builder.suggest(candidate.value());
        }

        if ("all".startsWith(remaining)) builder.suggest("all");
        if ("hud".startsWith(remaining)) builder.suggest("hud");
        return builder.buildFuture();
    }

    private static List<Candidate> candidates() {
        int revision = ModuleRegistry.revision();
        List<Candidate> hit = cachedCandidates;
        if (cachedRevision == revision) return hit;
        List<Candidate> rebuilt = new ArrayList<>();
        for (String name : ModuleRegistry.names()) {
            if (name == null) continue;

            String commandName = name.replace(' ', '-');
            rebuilt.add(new Candidate(commandName, commandName.toLowerCase(Locale.ROOT)));
        }
        hit = List.copyOf(rebuilt);
        cachedCandidates = hit;
        cachedRevision = revision;
        return hit;
    }
}
