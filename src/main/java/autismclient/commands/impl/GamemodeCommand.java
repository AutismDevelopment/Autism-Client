package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.Command;
import autismclient.commands.CommandSuggest;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismFakeGamemode;
import autismclient.util.AutismGamemode;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.world.level.GameType;

public class GamemodeCommand extends Command {
    public GamemodeCommand() {
        super("gamemode", "Set your real game mode (no chat command sent).");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            AutismClientMessaging.sendPrefixed("§eUsage: " + AutismCommands.effectivePrefix()
                + "gamemode <survival|creative|adventure|spectator>");
            AutismClientMessaging.sendPrefixed("§7Client-side only: " + AutismCommands.effectivePrefix() + "fakegm");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("mode", StringArgumentType.word())
            .suggests(CommandSuggest::realGamemodes)
            .executes(ctx -> {
                String mode = StringArgumentType.getString(ctx, "mode").toLowerCase(java.util.Locale.ROOT);
                GameType resolved = AutismFakeGamemode.parseMode(mode);
                if (resolved == null) {
                    AutismClientMessaging.sendPrefixed("§cUnknown mode: §f" + mode);
                    if ("reset".equals(mode) || "r".equals(mode)) {
                        AutismClientMessaging.sendPrefixed("§7Reset only applies to the fake mode: §f"
                            + AutismCommands.effectivePrefix() + "fakegm reset");
                    }
                    return SUCCESS;
                }
                AutismFakeGamemode.Result result = AutismGamemode.real(resolved);
                AutismClientMessaging.sendPrefixed((result.success() ? "§a" : "§c") + result.message());
                return SUCCESS;
            }));
    }
}
