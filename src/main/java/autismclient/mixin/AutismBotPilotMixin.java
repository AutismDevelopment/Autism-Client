package autismclient.mixin;

import autismclient.util.multi.MultiPilot;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RemotePlayer.class)
public abstract class AutismBotPilotMixin extends AbstractClientPlayer {
    protected AutismBotPilotMixin(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Override
    protected boolean isLocalClientAuthoritative() {
        return MultiPilot.isManualControlEntity(this) || super.isLocalClientAuthoritative();
    }

    @Override
    public boolean canSimulateMovement() {
        return MultiPilot.isManualControlEntity(this) || super.canSimulateMovement();
    }

    @Override
    public boolean isEffectiveAi() {

        return MultiPilot.isManualControlEntity(this) || super.isEffectiveAi();
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void autism$pilotAiStep(CallbackInfo ci) {
        if (!MultiPilot.isPilotedEntity(this)) return;
        if (!MultiPilot.isManualControlEntity(this)) {

            MultiPilot.observeMacro((RemotePlayer) (Object) this);
            return;
        }
        try {
            if (MultiPilot.prePhysics((RemotePlayer) (Object) this)) {
                this.jumping = MultiPilot.jumpRequested();
                super.aiStep();
                MultiPilot.postPhysics((RemotePlayer) (Object) this);
            } else {
                MultiPilot.passiveTick((RemotePlayer) (Object) this);
            }

            ci.cancel();
        } catch (Throwable error) {

            autismclient.AutismClientAddon.LOG.error("POV pilot tick failed - leaving POV", error);
            try {
                MultiPilot.abortSimulation((RemotePlayer) (Object) this);
                autismclient.util.multi.MultiTakeoverState.exit();
            } catch (Throwable ignored) {

            }
        }
    }

    @WrapWithCondition(method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/RemotePlayer;calculateEntityAnimation(Z)V"))
    private boolean autism$skipDoubleAnimation(RemotePlayer self, boolean includeHeight) {
        return !MultiPilot.isManualControlEntity(self);
    }
}
