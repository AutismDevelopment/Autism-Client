package autismclient.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.security.AutismPackResponseScheduler;
import autismclient.security.AutismProtectorPackStrip;
import autismclient.security.AutismResourcePackTruthGuard;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismSharedState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class AutismClientCommonNetworkHandlerMixin {
    @Shadow @Final protected Minecraft minecraft;

    @Shadow public abstract void send(Packet<?> packet);

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    private void yang$onResourcePackSend(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        AutismSharedState shared = AutismSharedState.get();
        AutismModule module = AutismModule.get();
        boolean shouldForceDeny = shared.shouldForceDenyResourcePack() || (module != null && module.isForceDenyResourcePack());
        boolean shouldBypass = shared.shouldBypassResourcePack() || (module != null && module.isBypassResourcePack());

        AutismResourcePackTruthGuard.Verdict verdict =
            AutismResourcePackTruthGuard.classify(packet, shouldForceDeny, shouldBypass);
        if (!verdict.shouldCancelVanilla()) return;

        AutismProtectorPackStrip.onPop(packet.id());

        java.util.function.Consumer<ServerboundResourcePackPacket> sender = this::send;
        switch (verdict.kind()) {
            case BYPASS_SUCCESS -> {
                long accepted = AutismPackResponseScheduler.acceptDelayMs();
                long downloaded = AutismPackResponseScheduler.downloadedDelayMs(accepted);
                long applied = AutismPackResponseScheduler.appliedDelayMs(downloaded);
                AutismPackResponseScheduler.schedule(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED, accepted, sender);
                AutismPackResponseScheduler.schedule(packet.id(), ServerboundResourcePackPacket.Action.DOWNLOADED, downloaded, sender);
                AutismPackResponseScheduler.schedule(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED, applied, sender);
            }
            case DECLINE -> AutismPackResponseScheduler.schedule(packet.id(),
                ServerboundResourcePackPacket.Action.DECLINED, AutismPackResponseScheduler.declineDelayMs(), sender,
                () -> AutismClientMessaging.sendPrefixed("Autism denied server resource pack."));

            case INVALID_URL -> send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.INVALID_URL));
            case FAILED_DOWNLOAD -> {
                long accepted = AutismPackResponseScheduler.acceptDelayMs();
                AutismPackResponseScheduler.schedule(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED, accepted, sender);
                AutismPackResponseScheduler.schedule(packet.id(), ServerboundResourcePackPacket.Action.FAILED_DOWNLOAD,
                    AutismPackResponseScheduler.failedDelayMs(accepted), sender);
            }
            default -> {
            }
        }

        ci.cancel();
    }

    @Inject(method = "handleShowDialog", at = @At("TAIL"))
    private void autism$trackCustomMenu(ClientboundShowDialogPacket packet, CallbackInfo ci) {
        String phase = ((Object) this) instanceof ClientConfigurationPacketListener ? "CONFIGURATION" : "PLAY";
        autismclient.util.custommenu.CustomMenuTracker.accept(packet, phase);
    }

    @Inject(method = "handleClearDialog", at = @At("TAIL"))
    private void autism$clearCustomMenu(ClientboundClearDialogPacket packet, CallbackInfo ci) {
        String phase = ((Object) this) instanceof ClientConfigurationPacketListener ? "CONFIGURATION" : "PLAY";
        autismclient.util.custommenu.CustomMenuTracker.accept(packet, phase);
    }
}
