package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.args.XCarrySlotArgumentType;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismSharedState;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;

public class XCarryCommand extends Command {
    public XCarryCommand() {
        super("xcarry",
            "Stash the held item into an XCarry slot: craft1..craft5 / helmet / chestplate / leggings / boots / offhand / cursor.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            String prefix = AutismCommands.effectivePrefix();
            AutismClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "xcarry <slot>");
            AutismClientMessaging.sendPrefixed("§7Slots: §fcraft1§7..§fcraft5§7, §fhelmet§7, §fchestplate§7, §fleggings§7, §fboots§7, §foffhand§7, §fcursor§7.");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AutismCommandSource, Integer>argument("slot", XCarrySlotArgumentType.slot())
            .executes(ctx -> stash(XCarrySlotArgumentType.get(ctx, "slot"))));
    }

    private static int stash(int targetSlot) {
        String povResult = autismclient.util.multi.MultiPilot.commandStashXCarry(targetSlot);
        if (povResult != null) {
            AutismClientMessaging.sendPrefixed(("Sent".equals(povResult) ? "§a" : "§c")
                + "POV XCarry: §f" + povResult);
            return SUCCESS;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) {
            AutismClientMessaging.sendPrefixed("§cNot in a world.");
            return SUCCESS;
        }

        ItemStack heldStack = p.getMainHandItem().copy();
        if (heldStack == null || heldStack.isEmpty()) {
            AutismClientMessaging.sendPrefixed("§cHold an item first.");
            return SUCCESS;
        }

        if (p.containerMenu != p.inventoryMenu) {
            AutismClientMessaging.sendPrefixed("§cOpen your inventory first (E), then run the command.");
            return SUCCESS;
        }

        AbstractContainerMenu container = p.containerMenu;

        int hotbar = p.getInventory().getSelectedSlot();
        int sourceSlotId = 36 + hotbar;

        AutismSharedState shared = AutismSharedState.get();
        boolean prevBypass = shared.isXCarryArmorBypass();
        shared.setXCarryArmorBypass(true);
        try {

            mc.gameMode.handleContainerInput(container.containerId, sourceSlotId, 0, ContainerInput.PICKUP, p);
            if (container.getCarried().isEmpty()) {
                AutismClientMessaging.sendPrefixed("§cCould not pick up the held item.");
                return SUCCESS;
            }

            String itemName = heldStack.getHoverName().getString();
            String slotName = XCarrySlotArgumentType.displayName(targetSlot);
            LinkedHashSet<Integer> forcedSlots = mergedForcedSlots(shared);
            boolean forcedCursor = shared.isXCarryForced() && shared.isXCarryForcedCarryCursor();

            if (targetSlot == XCarrySlotArgumentType.CURSOR) {

                shared.setXCarryForcedTargets(forcedSlots, true);
                shared.setXCarryForced(true);
                shared.setXCarryActive(true);
                AutismClientMessaging.sendPrefixed("§aHolding §f" + itemName + "§a on cursor (XCarry).");
                return SUCCESS;
            }

            if (targetSlot < 0 || targetSlot >= container.slots.size()) {
                AutismClientMessaging.sendPrefixed("§cInvalid slot (" + targetSlot + ").");

                mc.gameMode.handleContainerInput(container.containerId, sourceSlotId, 0, ContainerInput.PICKUP, p);
                return SUCCESS;
            }

            mc.gameMode.handleContainerInput(container.containerId, targetSlot, 0, ContainerInput.PICKUP, p);

            boolean placed = container.getCarried().isEmpty()
                    && targetSlot >= 0
                    && targetSlot < container.slots.size()
                    && !container.slots.get(targetSlot).getItem().isEmpty();
            if (!placed && !container.getCarried().isEmpty()) {
                mc.gameMode.handleContainerInput(container.containerId, sourceSlotId, 0, ContainerInput.PICKUP, p);
            }

            if (!placed) {
                AutismClientMessaging.sendPrefixed("§cCould not stash into §f" + slotName + "§c.");
                return SUCCESS;
            }

            forcedSlots.add(targetSlot);
            shared.setXCarryForcedTargets(forcedSlots, forcedCursor);
            shared.setXCarryForced(true);
            shared.setXCarryActive(true);
            AutismClientMessaging.sendPrefixed("§aStashed §f" + itemName + "§a into §f" + slotName + "§a.");
            return SUCCESS;
        } finally {
            shared.setXCarryArmorBypass(prevBypass);
        }
    }

    private static LinkedHashSet<Integer> mergedForcedSlots(AutismSharedState shared) {
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        if (shared != null && shared.isXCarryForced()) {
            Set<Integer> existing = shared.getXCarryForcedSlotMask();
            if (existing != null) {
                for (Integer slot : existing) {
                    if (slot != null && slot >= 0) slots.add(slot);
                }
            }
        }
        return slots;
    }
}
