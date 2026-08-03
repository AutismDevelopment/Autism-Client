package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.Command;
import autismclient.commands.args.MenuSlotArgumentType;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismInventoryHelper;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;

public final class ClickSlotCommand extends Command {
    public ClickSlotCommand() {
        super("click-slot", "Click a GUI/player slot with Item Click actions.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(context -> {
            AutismClientMessaging.sendPrefixed(
                "§eUsage: §f" + AutismCommands.effectivePrefix() + "click-slot <slot> [click] [times]");
            return SUCCESS;
        });

        RequiredArgumentBuilder<AutismCommandSource, Integer> slot = RequiredArgumentBuilder
            .<AutismCommandSource, Integer>argument("slot", MenuSlotArgumentType.slot())
            .executes(context -> click(context, ItemClickCommandSupport.ClickSpec.click(
                "Left", ContainerInput.PICKUP, 0), 1));
        ItemClickCommandSupport.attachModes(slot, ClickSlotCommand::click);
        root.then(slot);
    }

    private static int click(
        com.mojang.brigadier.context.CommandContext<AutismCommandSource> context,
        ItemClickCommandSupport.ClickSpec spec,
        int times
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu == null) {
            AutismClientMessaging.sendPrefixed("§cNo active inventory or GUI.");
            return SUCCESS;
        }

        int visibleSlot = MenuSlotArgumentType.get(context, "slot");
        int handlerSlot = AutismInventoryHelper.toHandlerSlot(mc, visibleSlot);
        return ItemClickCommandSupport.clickHandlerSlot(
            handlerSlot, spec, times, MenuSlotArgumentType.displayToken(visibleSlot));
    }
}
