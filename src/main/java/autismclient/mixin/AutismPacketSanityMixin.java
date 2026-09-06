package autismclient.mixin;

import autismclient.security.AutismNumericSanity;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class AutismPacketSanityMixin {

    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$saneSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (AutismNumericSanity.motionOutOfRange(packet.movement())) ci.cancel();
    }

    @Inject(method = "handleExplosion", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$saneExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        if (AutismNumericSanity.outOfRange(packet.center())
            || AutismNumericSanity.outOfRange(packet.radius())
            || (packet.playerKnockback().isPresent()
                && AutismNumericSanity.motionOutOfRange(packet.playerKnockback().get()))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$saneMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (AutismNumericSanity.positionMoveOutOfRange(packet.change())) ci.cancel();
    }

    @Inject(method = "handleTeleportEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$saneTeleportEntity(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        if (AutismNumericSanity.positionMoveOutOfRange(packet.change())) ci.cancel();
    }

    @Inject(method = "handleEntityPositionSync", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$saneEntityPositionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        if (AutismNumericSanity.positionMoveOutOfRange(packet.values())) ci.cancel();
    }

    @Inject(method = "handleAddEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$saneAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (AutismNumericSanity.outOfRange(packet.getX())
            || AutismNumericSanity.outOfRange(packet.getY())
            || AutismNumericSanity.outOfRange(packet.getZ())
            || AutismNumericSanity.motionOutOfRange(packet.getMovement())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$saneParticles(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (AutismNumericSanity.outOfRange(packet.getX())
            || AutismNumericSanity.outOfRange(packet.getY())
            || AutismNumericSanity.outOfRange(packet.getZ())
            || AutismNumericSanity.outOfRange(packet.getXDist())
            || AutismNumericSanity.outOfRange(packet.getYDist())
            || AutismNumericSanity.outOfRange(packet.getZDist())
            || AutismNumericSanity.outOfRange(packet.getMaxSpeed())
            || packet.getCount() > 100_000) {
            ci.cancel();
        }
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$saneMoveVehicle(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (AutismNumericSanity.outOfRange(packet.position())
            || AutismNumericSanity.outOfRange(packet.yRot())
            || AutismNumericSanity.outOfRange(packet.xRot())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleAddObjective", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$saneObjective(net.minecraft.network.protocol.game.ClientboundSetObjectivePacket packet, CallbackInfo ci) {
        if (!autismclient.security.AutismComponentSanity.isSafe(packet.getDisplayName())
            || (packet.getNumberFormat().isPresent()
                && !autismclient.security.AutismComponentSanity.isSafe(packet.getNumberFormat().get()))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSetScore", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$saneScore(net.minecraft.network.protocol.game.ClientboundSetScorePacket packet, CallbackInfo ci) {
        if ((packet.display().isPresent()
                && !autismclient.security.AutismComponentSanity.isSafe(packet.display().get()))
            || (packet.numberFormat().isPresent()
                && !autismclient.security.AutismComponentSanity.isSafe(packet.numberFormat().get()))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSetPlayerTeamPacket", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$saneTeam(net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket packet, CallbackInfo ci) {
        if (packet.getParameters().isEmpty()) return;
        net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Parameters parameters = packet.getParameters().get();
        if (!autismclient.security.AutismComponentSanity.isSafe(parameters.displayName())
            || !autismclient.security.AutismComponentSanity.isSafe(parameters.playerPrefix())
            || !autismclient.security.AutismComponentSanity.isSafe(parameters.playerSuffix())) {
            ci.cancel();
        }
    }
}
