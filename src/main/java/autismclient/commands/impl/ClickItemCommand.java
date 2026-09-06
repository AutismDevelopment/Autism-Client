package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.Command;
import autismclient.commands.args.VisibleItemNameArgumentType;
import autismclient.util.AutismClientMessaging;
import autismclient.util.macro.ItemTarget;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class ClickItemCommand extends Command {
    public ClickItemCommand() {
        super("click-item", "Click an item by its visible in-game name.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(context -> {
            AutismClientMessaging.sendPrefixed(
                "§eUsage: §f" + AutismCommands.effectivePrefix()
                    + "click-item \"<item name>\" [click] [times]");
            return SUCCESS;
        });

        RequiredArgumentBuilder<AutismCommandSource, String> item = RequiredArgumentBuilder
            .<AutismCommandSource, String>argument("item-name", VisibleItemNameArgumentType.itemName())
            .executes(context -> click(context, ItemClickCommandSupport.ClickSpec.click(
                "Left", ContainerInput.PICKUP, 0), 1));
        ItemClickCommandSupport.attachModes(item, ClickItemCommand::click);
        root.then(item);
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

        String requestedName = VisibleItemNameArgumentType.get(context, "item-name");
        int handlerSlot = findByVisibleName(mc.player.containerMenu, requestedName);
        if (handlerSlot < 0) {
            AutismClientMessaging.sendPrefixed(
                "§cNo visible item named §f" + requestedName + "§c exists in the current GUI.");
            return SUCCESS;
        }
        return ItemClickCommandSupport.clickHandlerSlot(
            handlerSlot, spec, times, "\"" + requestedName + "\"");
    }

    private static int findByVisibleName(AbstractContainerMenu menu, String requestedName) {
        String normalizedTarget = ItemTarget.normalize(requestedName);
        if (normalizedTarget.isEmpty()) return -1;
        for (int handlerSlot = 0; handlerSlot < menu.slots.size(); handlerSlot++) {
            ItemStack stack = menu.slots.get(handlerSlot).getItem();
            if (stack.isEmpty()) continue;
            if (normalizedTarget.equals(ItemTarget.normalize(stack.getHoverName().getString()))) {
                return handlerSlot;
            }
        }
        return -1;
    }
}
