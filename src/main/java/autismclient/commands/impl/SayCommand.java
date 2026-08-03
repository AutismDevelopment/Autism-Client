package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.util.AutismClientMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;

public class SayCommand extends Command {
    public SayCommand() { super("say", "Send a chat message."); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            AutismClientMessaging.sendPrefixed("§eUsage: " + AutismCommands.effectivePrefix() + "say <message>");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("message", StringArgumentType.greedyString())
            .executes(ctx -> {
                String msg = StringArgumentType.getString(ctx, "message");
                var conn = Minecraft.getInstance().getConnection();
                if (conn == null) { AutismClientMessaging.sendPrefixed("§cNot connected."); return SUCCESS; }
                if (msg.startsWith("/")) conn.sendCommand(msg.substring(1));
                else conn.sendChat(msg);
                return SUCCESS;
            }));
    }
}
