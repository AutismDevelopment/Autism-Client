package autismclient.commands.args;

import autismclient.commands.AutismCommandSource;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class KeyArgumentType implements ArgumentType<Integer> {
    private static final Map<String, Integer> NAMES = new LinkedHashMap<>();
    private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(Component.literal("Unknown key"));

    static {
        for (Field f : GLFW.class.getDeclaredFields()) {
            String n = f.getName();
            if (!n.startsWith("GLFW_KEY_")) continue;
            try {
                int code = f.getInt(null);
                NAMES.put(n.substring("GLFW_KEY_".length()).toUpperCase(Locale.ROOT), code);
            } catch (Throwable ignored) {  }
        }
    }

    public static KeyArgumentType key() { return new KeyArgumentType(); }

    public static int get(CommandContext<AutismCommandSource> ctx, String name) {
        return ctx.getArgument(name, Integer.class);
    }

    public static String keyName(int code) {
        if (code < 0) return "NONE";
        for (Map.Entry<String, Integer> e : NAMES.entrySet()) {
            if (e.getValue() == code) return e.getKey();
        }
        return "KEY_" + code;
    }

    @Override
    public Integer parse(StringReader reader) throws CommandSyntaxException {
        String raw = reader.readUnquotedString();
        String upper = raw.toUpperCase(Locale.ROOT);
        Integer code = NAMES.get(upper);
        if (code != null) return code;

        if (upper.length() == 1) {
            char c = upper.charAt(0);
            if (c >= 'A' && c <= 'Z') return GLFW.GLFW_KEY_A + (c - 'A');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }
        throw UNKNOWN.create();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toUpperCase(Locale.ROOT);
        for (String n : NAMES.keySet()) {
            if (n.startsWith(remaining)) builder.suggest(n);
        }
        return builder.buildFuture();
    }
}
