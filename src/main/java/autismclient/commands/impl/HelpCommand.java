package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.util.AutismClientMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Locale;

public class HelpCommand extends Command {
    public HelpCommand() { super("help", "Show usage info for a command (or list all)."); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> { listAll(); return SUCCESS; });
        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("command", StringArgumentType.word())
            .suggests((ctx, builder) -> {
                String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
                for (Command command : AutismCommands.all()) {
                    if (command.name().toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(command.name());
                    for (String alias : command.aliases()) {
                        if (alias != null && alias.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(alias);
                    }
                }
                return builder.buildFuture();
            })
            .executes(ctx -> {
                String name = StringArgumentType.getString(ctx, "command");
                Command cmd = AutismCommands.find(name);
                if (cmd == null) { AutismClientMessaging.sendPrefixed("§cNo such command: §f" + name); return SUCCESS; }
                String prefix = AutismCommands.effectivePrefix();
                AutismClientMessaging.sendPrefixed("§b" + prefix + cmd.name() + " §7" + cmd.description());
                if (cmd.aliases().length > 0) AutismClientMessaging.sendPrefixed("§7aliases: §f" + String.join(", ", cmd.aliases()));
                return SUCCESS;
            }));
    }

    private static void listAll() {
        String prefix = AutismCommands.effectivePrefix();
        for (Command c : AutismCommands.all()) {
            AutismClientMessaging.sendPrefixed("§b" + prefix + c.name() + "§7 - " + c.description());
        }
    }
}
