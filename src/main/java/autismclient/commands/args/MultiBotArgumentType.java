package autismclient.commands.args;

import autismclient.commands.AutismCommandSource;
import autismclient.util.multi.MultiManager;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class MultiBotArgumentType implements ArgumentType<String> {
    public static MultiBotArgumentType botName() {
        return new MultiBotArgumentType();
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
        for (MultiManager.BotHandle bot : MultiManager.get().liveBots()) {
            String name = bot.username();
            if (name == null || name.isBlank()) continue;
            if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name.contains(" ") ? "\"" + name + "\"" : name);
            }
        }
        return builder.buildFuture();
    }
}
