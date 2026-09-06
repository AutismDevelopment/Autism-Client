package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.Command;
import autismclient.gui.screen.AutismOverlayHostScreen;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismItemCommandSerializer;
import autismclient.util.AutismItemNbtInspectOverlay;
import autismclient.util.AutismNotifications;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public class NbtCommand extends Command {
    public NbtCommand() {
        super("nbt", "Inspect or copy the held item's components (NBT).");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            inspect();
            return SUCCESS;
        });
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("get").executes(ctx -> {
            inspect();
            return SUCCESS;
        }));
        root.then(LiteralArgumentBuilder.<AutismCommandSource>literal("copy").executes(ctx -> {
            copy();
            return SUCCESS;
        }));
    }

    private static ItemStack held() {
        net.minecraft.world.entity.player.Player player = autismclient.util.multi.MultiPilot.commandPlayer();
        if (player == null) return ItemStack.EMPTY;
        ItemStack main = player.getMainHandItem();
        return !main.isEmpty() ? main : player.getOffhandItem();
    }

    private static void inspect() {
        ItemStack stack = held();
        if (stack.isEmpty()) {
            AutismClientMessaging.sendPrefixed("\u00a7cHold an item in either hand first.");
            return;
        }
        if (!AutismItemNbtInspectOverlay.openGlobal(stack)) {
            AutismClientMessaging.sendPrefixed("\u00a7cCould not open the NBT inspector.");
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            autismclient.util.AutismItemNbtInspectOverlay overlay =
                autismclient.util.AutismItemNbtInspectOverlay.getSharedOverlay(mc.font);
            mc.execute(() -> {

                if (mc.gui.screen() == null) {
                    mc.gui.setScreen(new AutismOverlayHostScreen(overlay));
                }
            });
        }
    }

    private static void copy() {
        ItemStack stack = held();
        if (stack.isEmpty()) {
            AutismClientMessaging.sendPrefixed("\u00a7cHold an item in either hand first.");
            return;
        }
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(AutismItemCommandSerializer.giveCommand(stack));
            AutismNotifications.copied("Copied /give command.");
        } catch (Throwable t) {
            AutismNotifications.error("Copy failed.");
        }
    }
}
