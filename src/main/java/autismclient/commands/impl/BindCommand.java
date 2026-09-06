package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.args.KeyArgumentType;
import autismclient.modules.AutismModule;
import autismclient.util.AutismClientMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public class BindCommand extends Command {
    public BindCommand() { super("bind", "Bind a key to a command. Usage: bind <key> <command…> | bind clear <key>"); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            String prefix = AutismCommands.effectivePrefix();
            AutismClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "bind <key> <command>");
            AutismClientMessaging.sendPrefixed("§7Example: §f" + prefix + "bind G " + prefix + "macro myMacro");
            return SUCCESS;
        });
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("clear")
            .then(RequiredArgumentBuilder.<AutismCommandSource, Integer>argument("key", KeyArgumentType.key())
                .executes(ctx -> {
                    int key = KeyArgumentType.get(ctx, "key");
                    AutismModule.get().clearCommandBind(key);
                    AutismClientMessaging.sendPrefixed("§eCleared bind for §f" + KeyArgumentType.keyName(key));
                    return SUCCESS;
                })));
        root.then(RequiredArgumentBuilder.<AutismCommandSource, Integer>argument("key", KeyArgumentType.key())
            .then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("command", StringArgumentType.greedyString())
                .executes(ctx -> {
                    int key = KeyArgumentType.get(ctx, "key");
                    String cmd = StringArgumentType.getString(ctx, "command");
                    AutismModule.get().setCommandBind(key, cmd);
                    AutismClientMessaging.sendPrefixed("§aBound §f" + KeyArgumentType.keyName(key) + "§a → §f" + cmd);
                    return SUCCESS;
                })));
    }
}
