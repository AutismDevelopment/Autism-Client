package autismclient.commands.impl;

import autismclient.commands.Command;
import autismclient.commands.AutismCommandSource;
import autismclient.commands.AutismCommands;
import autismclient.commands.args.PacketClassArgumentType;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismPacketArgumentBuilder;
import autismclient.util.AutismPacketRegistry;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

public class SendCommand extends Command {
    public SendCommand() { super("send", "Send a raw C2S packet by class name with optional field=value arguments."); }

    @Override
    public void build(LiteralArgumentBuilder<AutismCommandSource> root) {
        root.executes(ctx -> {
            AutismClientMessaging.sendPrefixed("§eUsage: §f" + AutismCommands.effectivePrefix() + "send <packetName> [field=value ...]");
            AutismClientMessaging.sendPrefixed("§7Tip: press Tab after the space to search C2S packet classes.");
            AutismClientMessaging.sendPrefixed("§7Example: §f" + AutismCommands.effectivePrefix() + "send ServerboundSetCarriedItemPacket slot=0");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("packet", PacketClassArgumentType.packetClass())
            .executes(ctx -> send(PacketClassArgumentType.get(ctx, "packet"), ""))
            .then(RequiredArgumentBuilder.<AutismCommandSource, String>argument("args", StringArgumentType.greedyString())
                .suggests((ctx, builder) -> {
                    Class<? extends Packet<?>> cls = AutismPacketRegistry.getPacket(PacketClassArgumentType.get(ctx, "packet"));
                    return AutismPacketArgumentBuilder.suggest(cls, builder);
                })
                .executes(ctx -> send(PacketClassArgumentType.get(ctx, "packet"), StringArgumentType.getString(ctx, "args")))));
    }

    private int send(String name, String args) {
        Class<? extends Packet<?>> cls = AutismPacketRegistry.getPacket(name);
        if (cls == null) {
            AutismClientMessaging.sendPrefixed("§cUnknown packet: §f" + name);
            return SUCCESS;
        }
        if (!AutismPacketRegistry.getC2SPackets().contains(cls)) {
            AutismClientMessaging.sendPrefixed("§cRefusing to send non-C2S packet: §f" + name);
            return SUCCESS;
        }

        AutismPacketArgumentBuilder.PreparedArgs prepared;
        try {
            prepared = AutismPacketArgumentBuilder.prepare(args);
        } catch (IllegalArgumentException e) {
            AutismClientMessaging.sendPrefixed("§cBad arguments: " + e.getMessage());
            return SUCCESS;
        }
        AutismPacketArgumentBuilder.Result result = AutismPacketArgumentBuilder.build(cls, prepared.args());
        if (result.help()) {
            sendLines("§e", result.message());
            return SUCCESS;
        }
        if (!result.ok()) {
            sendLines("§c", "Failed to build §f" + name + "§c: " + result.message());
            return SUCCESS;
        }

        if (prepared.dryRun()) {
            AutismClientMessaging.sendPrefixed("§eBuilt §f" + name + "§7 (" + result.source() + ") §e- dry run, not sent.");
            return SUCCESS;
        }
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) {
            AutismClientMessaging.sendPrefixed("§cNo network connection.");
            return SUCCESS;
        }
        autismclient.util.AutismSharedState.get().sendPacketBypassDelay(conn, result.packet());
        AutismClientMessaging.sendPrefixed("§aSent §f" + name + "§7 (" + result.source() + ")");
        return SUCCESS;
    }

    private static void sendLines(String color, String message) {
        if (message == null || message.isBlank()) return;
        for (String line : message.split("\\R")) {
            if (!line.isBlank()) AutismClientMessaging.sendPrefixed(color + line);
        }
    }
}
