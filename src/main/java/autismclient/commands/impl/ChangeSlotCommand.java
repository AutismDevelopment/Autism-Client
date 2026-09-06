package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.Command;
import autismclient.commands.CommandSuggest;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismInventoryHelper;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;

public final class ChangeSlotCommand extends Command {
    public ChangeSlotCommand() {
        super("change-slot", "Select hotbar slot 1-9.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(context -> {
            AutismClientMessaging.sendPrefixed(
                "§eUsage: §f" + AutismCommands.effectivePrefix() + "change-slot <1-9>");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AutismCommandSource, Integer>argument(
                "hotbar-slot", IntegerArgumentType.integer(1, 9))
            .suggests((context, builder) -> CommandSuggest.literals(
                builder, "1", "2", "3", "4", "5", "6", "7", "8", "9"))
            .executes(context -> changeSlot(IntegerArgumentType.getInteger(context, "hotbar-slot"))));
    }

    private static int changeSlot(int oneBasedSlot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            AutismClientMessaging.sendPrefixed("§cNot in a world.");
            return SUCCESS;
        }

        AutismInventoryHelper.selectHotbarSlot(mc, oneBasedSlot - 1);
        AutismClientMessaging.sendPrefixed("§aSelected hotbar slot §f" + oneBasedSlot + "§a.");
        return SUCCESS;
    }
}
