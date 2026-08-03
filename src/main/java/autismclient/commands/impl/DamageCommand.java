package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.CommandSuggest;
import autismclient.util.AutismClientMessaging;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;

public class DamageCommand extends Command {
    public DamageCommand() { super("damage", "Take damage to self (vanilla server allows via /damage if op)."); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            AutismClientMessaging.sendPrefixed("§eUsage: " + AutismCommands.effectivePrefix() + "damage <amount>");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AutismCommandSource, Double>argument("amount", DoubleArgumentType.doubleArg(0.1, 1024.0))
            .suggests(CommandSuggest::damage)
            .executes(ctx -> {
                double amt = DoubleArgumentType.getDouble(ctx, "amount");
                var conn = Minecraft.getInstance().getConnection();
                if (conn == null) { AutismClientMessaging.sendPrefixed("§cNot connected."); return SUCCESS; }
                conn.sendCommand("damage @s " + amt);
                return SUCCESS;
            }));
    }
}
