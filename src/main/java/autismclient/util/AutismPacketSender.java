package autismclient.util;

import autismclient.modules.PackHideState;
import autismclient.security.AutismProtector;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

public class AutismPacketSender {

    public static void send(Packet<?> packet) {
        if (PackHideState.isHardLocked()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        AutismProtector.markUserBypass(packet);
        mc.getConnection().send(packet);
    }

    public static void sendPacketDirect(Packet<?> packet) {
        if (PackHideState.isHardLocked()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        AutismProtector.markUserBypass(packet);
        mc.getConnection().send(packet);
    }

}
