package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.Command;
import autismclient.util.AutismClientMessaging;
import autismclient.util.mm.MatchmakingManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public class IrcCommand extends Command {
    public IrcCommand() { super("irc", "Send a message to the Matchmaking lobby chat."); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            AutismClientMessaging.sendPrefixed("§eUsage: " + AutismCommands.effectivePrefix() + "irc <message>");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("message", StringArgumentType.greedyString())
            .executes(ctx -> {
                String msg = StringArgumentType.getString(ctx, "message");
                MatchmakingManager mm = MatchmakingManager.get();
                if (!mm.inLobby()) {
                    AutismClientMessaging.sendPrefixed("§cNot in a lobby. Open Matchmaking and join or create one first.");
                    return SUCCESS;
                }

                mm.sendChat(msg);
                return SUCCESS;
            }));
    }
}
