package autismclient.mixin.security;

import autismclient.security.AutismProtectorPackStrip;
import autismclient.security.AutismResourcePackTruthGuard;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class AutismProtectorClientCommonNetworkHandlerMixin {

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"))
    private void autism$onPackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        AutismProtectorPackStrip.onPackPush(packet.id());
    }

    @Inject(method = "handleResourcePackPop", at = @At("HEAD"))
    private void autism$onPackPop(ClientboundResourcePackPopPacket packet, CallbackInfo ci) {
        Optional<UUID> id = packet.id();
        AutismProtectorPackStrip.onPop(id.orElse(null));
        AutismResourcePackTruthGuard.onPop(id.orElse(null));

        autismclient.security.AutismPackResponseScheduler.cancel(id.orElse(null));
    }
}
