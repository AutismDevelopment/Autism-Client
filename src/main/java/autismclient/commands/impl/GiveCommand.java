package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.CommandSuggest;
import autismclient.util.AutismClientMessaging;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class GiveCommand extends Command {
    public GiveCommand() { super("give", "Give item to self (creative only)."); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            AutismClientMessaging.sendPrefixed("§eUsage: " + AutismCommands.effectivePrefix() + "give <item> [count]");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("item", StringArgumentType.word())
            .suggests(CommandSuggest::itemIds)
            .executes(ctx -> give(StringArgumentType.getString(ctx, "item"), 1))
            .then(RequiredArgumentBuilder.<AutismCommandSource, Integer>argument("count", IntegerArgumentType.integer(1, 64))
                .suggests(CommandSuggest::counts)
                .executes(ctx -> give(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count")))));
    }

    private static int give(String itemId, int count) {
        Minecraft mc = Minecraft.getInstance();
        boolean pov = autismclient.util.multi.MultiPilot.isActive();
        if (!pov && mc.player == null) { AutismClientMessaging.sendPrefixed("§cNot in a world."); return SUCCESS; }
        if (!pov && !mc.player.getAbilities().instabuild) { AutismClientMessaging.sendPrefixed("§cCreative mode required."); return SUCCESS; }
        Identifier id = itemId.contains(":") ? Identifier.tryParse(itemId) : Identifier.tryParse("minecraft:" + itemId);
        if (id == null) { AutismClientMessaging.sendPrefixed("§cInvalid item id: §f" + itemId); return SUCCESS; }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) { AutismClientMessaging.sendPrefixed("§cUnknown item: §f" + id); return SUCCESS; }
        ItemStack stack = new ItemStack(item, count);
        String povResult = autismclient.util.multi.MultiPilot.commandGiveCreative(stack);
        if (povResult != null) {
            AutismClientMessaging.sendPrefixed(("Sent".equals(povResult) ? "§a" : "§c")
                + "POV give: §f" + povResult);
            return SUCCESS;
        }
        int slot = mc.player.getInventory().getSelectedSlot();
        int containerSlot = 36 + slot;
        mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket((short) containerSlot, stack));
        mc.player.getInventory().setItem(slot, stack);
        AutismClientMessaging.sendPrefixed("§aGave §f" + id + " x" + count);
        return SUCCESS;
    }
}
