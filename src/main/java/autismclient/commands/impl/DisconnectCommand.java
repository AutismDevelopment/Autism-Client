package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.util.AutismClientMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class DisconnectCommand extends Command {
    public DisconnectCommand() { super("disconnect", "Disconnect from the current server."); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) { AutismClientMessaging.sendPrefixed("§cNot connected."); return SUCCESS; }
            try {
                mc.getConnection().getConnection().disconnect(Component.literal("Disconnected via .disconnect"));
            } catch (Throwable t) {
                AutismClientMessaging.sendPrefixed("§cDisconnect failed: " + t.getMessage());
            }
            return SUCCESS;
        });
    }
}
