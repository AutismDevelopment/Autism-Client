package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.Command;
import autismclient.commands.CommandSuggest;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismFakeGamemode;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.world.level.GameType;

public class FakeGmCommand extends Command {
    public FakeGmCommand() {
        super("fakegm", "Set a fake client-side game mode.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            AutismClientMessaging.sendPrefixed("§eUsage: " + AutismCommands.effectivePrefix()
                + "fakegm <survival|creative|adventure|spectator|reset>");
            AutismClientMessaging.sendPrefixed("§7Real change: " + AutismCommands.effectivePrefix() + "gamemode");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("mode", StringArgumentType.word())
            .suggests(CommandSuggest::gamemodes)
            .executes(ctx -> {
                String mode = StringArgumentType.getString(ctx, "mode").toLowerCase(java.util.Locale.ROOT);
                AutismFakeGamemode.Result result;
                if ("reset".equals(mode) || "r".equals(mode)) {
                    result = AutismFakeGamemode.reset();
                } else {
                    GameType resolved = AutismFakeGamemode.parseMode(mode);
                    if (resolved == null) {
                        AutismClientMessaging.sendPrefixed("§cUnknown mode: §f" + mode);
                        return SUCCESS;
                    }
                    result = AutismFakeGamemode.apply(resolved);
                }
                AutismClientMessaging.sendPrefixed((result.success() ? "§a" : "§c") + result.message());
                return SUCCESS;
            }));
    }
}
