package autismclient.mixin;

import autismclient.util.multi.MultiManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.EntityTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(ClientPacketListener.class)
public abstract class AutismBotPlayerInfoMixin {
    @Shadow
    @Final
    private Map<UUID, PlayerInfo> playerInfoMap;

    @Inject(method = "handleAddEntity", at = @At("HEAD"))
    private void autism$mintBotPlayerInfo(ClientboundAddEntityPacket packet, CallbackInfo ci) {

        if (!Minecraft.getInstance().isSameThread()) return;
        try {
            if (packet.getType() != EntityTypes.PLAYER) return;
            UUID uuid = packet.getUUID();
            if (uuid == null || playerInfoMap.containsKey(uuid)) return;
            GameProfile profile = MultiManager.botProfileByServerUuid(uuid);
            if (profile != null) {
                playerInfoMap.put(uuid, new PlayerInfo(profile, false));
            }
        } catch (Throwable ignored) {

        }
    }
}
