package autismclient.commands.impl;

import autismclient.commands.AutismCommandSource;
import autismclient.commands.Command;
import autismclient.util.AutismClientMessaging;
import autismclient.util.multi.MultiPilot;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class CopyPosCommand extends Command {
    public CopyPosCommand() {
        super("copypos", "Copies current coordinates.");
    }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            Player controlled = MultiPilot.commandPlayer();
            if (mc == null || controlled == null || mc.keyboardHandler == null) {
                AutismClientMessaging.sendPrefixed("§cNo controlled position available.");
                return SUCCESS;
            }
            Entity positionOwner = !MultiPilot.isActive() && controlled.getVehicle() != null
                ? controlled.getVehicle() : controlled;
            String coordinates = format(positionOwner.position());
            mc.keyboardHandler.setClipboard(coordinates);
            AutismClientMessaging.sendPrefixed("§aPosition copied: §f" + coordinates);
            return SUCCESS;
        });
    }

    static String format(Vec3 position) {
        if (position == null) return "0 0 0";
        return floorCoordinate(position.x) + " " + safeFeetY(position.y) + " " + floorCoordinate(position.z);
    }

    private static int floorCoordinate(double value) {
        return Double.isFinite(value) ? (int) Math.floor(value) : 0;
    }

    private static int safeFeetY(double value) {
        return Double.isFinite(value) ? (int) Math.ceil(value - 1.0E-7D) : 0;
    }
}
