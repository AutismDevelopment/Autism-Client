package autismclient.mixin;

import autismclient.util.multi.MultiPilot;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class AutismBotEquipmentMixin {
    @Inject(method = "handleSetEquipment", at = @At("HEAD"), cancellable = true)
    private void autism$suppressPilotedEquipmentEcho(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
        if (MultiPilot.isActive() && MultiPilot.isPilotedEntityId(packet.getEntity())) {
            ci.cancel();
        }
    }
}
