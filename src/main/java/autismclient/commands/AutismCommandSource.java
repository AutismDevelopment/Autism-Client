package autismclient.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class AutismCommandSource {
    public static final AutismCommandSource INSTANCE = new AutismCommandSource();

    private AutismCommandSource() {}

    public Minecraft mc() { return Minecraft.getInstance(); }
    public LocalPlayer player() { return Minecraft.getInstance().player; }
    public ClientPacketListener connection() { return Minecraft.getInstance().getConnection(); }

    public boolean hasPlayer() { return Minecraft.getInstance().player != null; }
    public boolean hasConnection() { return Minecraft.getInstance().getConnection() != null; }
}
