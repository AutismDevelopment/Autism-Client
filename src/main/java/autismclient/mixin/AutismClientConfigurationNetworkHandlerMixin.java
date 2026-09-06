package autismclient.mixin;

import autismclient.util.AutismSharedState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.server.packs.repository.KnownPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class AutismClientConfigurationNetworkHandlerMixin {
    @Inject(method = "handleSelectKnownPacks", at = @At("HEAD"))
    private void yang$onSelectKnownPacks(ClientboundSelectKnownPacks packet, CallbackInfo ci) {
        if (packet == null || packet.knownPacks() == null) return;

        String serverAddress = "";
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getCurrentServer() != null && client.getCurrentServer().ip != null) {
            serverAddress = client.getCurrentServer().ip;
        }

        String capturedVersion = null;
        for (KnownPack knownPack : packet.knownPacks()) {
            if (knownPack == null) continue;
            if ("core".equals(knownPack.id())) {
                AutismSharedState.get().setRealServerVersion(serverAddress, knownPack.version());
                return;
            }
            if (capturedVersion == null && !knownPack.id().isBlank() && !knownPack.version().isBlank()) {
                capturedVersion = knownPack.version();
            }
        }
        if (capturedVersion != null) {
            AutismSharedState.get().setRealServerVersion(serverAddress, capturedVersion);
        }
    }
}
