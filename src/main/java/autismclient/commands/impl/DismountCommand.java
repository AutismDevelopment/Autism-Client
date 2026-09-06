package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.util.AutismClientMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;

public class DismountCommand extends Command {
    public DismountCommand() { super("dismount", "Force-dismount from any vehicle."); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.getConnection() == null) { AutismClientMessaging.sendPrefixed("§cNot in a world."); return SUCCESS; }
            if (mc.player.getVehicle() == null) { AutismClientMessaging.sendPrefixed("§eNot riding anything."); return SUCCESS; }
            try {
                mc.player.removeVehicle();
                AutismClientMessaging.sendPrefixed("§aDismounted.");
            } catch (Throwable t) {
                AutismClientMessaging.sendPrefixed("§cDismount failed: " + t.getMessage());
            }
            return SUCCESS;
        });
    }
}
