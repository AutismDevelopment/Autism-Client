package autismclient.commands.args;

import autismclient.commands.AutismCommandSource;
import autismclient.util.AutismPacketRegistry;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.network.protocol.Packet;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PacketClassArgumentType implements ArgumentType<String> {
    private static volatile List<Candidate> cachedCandidates;

    private record Candidate(String value, String lower) {}

    public static PacketClassArgumentType packetClass() { return new PacketClassArgumentType(); }

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
            if (candidate.lower().contains(remaining)) builder.suggest(candidate.value());
        }
        return builder.buildFuture();
    }

    private static List<Candidate> candidates() {
        List<Candidate> hit = cachedCandidates;
        if (hit != null) return hit;
        List<Candidate> rebuilt = new ArrayList<>();
        for (Class<? extends Packet<?>> cls : AutismPacketRegistry.getC2SPackets()) {
            String name = AutismPacketRegistry.getName(cls);
            if (name != null) rebuilt.add(new Candidate(name, name.toLowerCase(Locale.ROOT)));
        }
        hit = List.copyOf(rebuilt);
        cachedCandidates = hit;
        return hit;
    }
}
