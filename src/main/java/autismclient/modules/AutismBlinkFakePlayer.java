package autismclient.modules;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;

public final class AutismBlinkFakePlayer extends RemotePlayer {

    private static final int CLONE_ENTITY_ID = -0x42_4C_4B;

    private final PlayerInfo info;

    public AutismBlinkFakePlayer(ClientLevel level, LocalPlayer source) {
        super(level, source.getGameProfile());
        setId(CLONE_ENTITY_ID);
        setUUID(UUID.randomUUID());

        getInventory().replaceWith(source.getInventory());
        try {
            getAttributes().assignAllValues(source.getAttributes());
        } catch (Throwable ignored) {

        }
        setPose(source.getPose());
        PlayerInfo resolved = null;
        try {
            if (Minecraft.getInstance().getConnection() != null) {
                resolved = Minecraft.getInstance().getConnection().getPlayerInfo(source.getGameProfile().id());
            }
        } catch (Throwable ignored) {  }
        this.info = resolved;
    }

    public void freezeHeadRotation(float headYaw, float bodyYaw) {
        this.yHeadRot = headYaw;
        this.yHeadRotO = headYaw;
        this.yBodyRot = bodyYaw;
        this.yBodyRotO = bodyYaw;
    }

    @Override
    protected PlayerInfo getPlayerInfo() {
        return info != null ? info : super.getPlayerInfo();
    }

    @Override
    protected void doPush(Entity entity) {

    }

    @Override
    public void tick() {

    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
