package autismclient.mixin;

import autismclient.modules.ScaffoldModule;
import autismclient.modules.KillAuraModule;
import autismclient.modules.BuiltinModules;
import autismclient.modules.PackFreecamState;
import autismclient.modules.ModuleMovementUtil;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.player.LocalPlayer;
import autismclient.modules.EntityControlModule;
import net.minecraft.world.entity.PlayerRideableJumping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class AutismLocalPlayerMovementMixin {
    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float autism$scaffoldSilentMovementYaw(float original) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        float fastExp = BuiltinModules.outgoingFastExpMovementYaw(player, original);
        float scaffold = ScaffoldModule.outgoingMovementYaw(player, fastExp);
        return KillAuraModule.outgoingMovementYaw(player, scaffold);
    }

    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float autism$scaffoldSilentMovementPitch(float original) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        float fastExp = BuiltinModules.outgoingFastExpMovementPitch(player, original);
        float scaffold = ScaffoldModule.outgoingMovementPitch(player, fastExp);
        return KillAuraModule.outgoingMovementPitch(player, scaffold);
    }

    @Inject(method = "isShiftKeyDown", at = @At("HEAD"), cancellable = true)
    private void autism$flightNoSneak(CallbackInfoReturnable<Boolean> cir) {
        if (ModuleMovementUtil.flightNoSneak((LocalPlayer) (Object) this) || PackFreecamState.isActive()) cir.setReturnValue(false);
    }

    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;canStartSprinting()Z"))
    private boolean autism$sprintDecisionMovementTick(boolean original) {
        return ModuleMovementUtil.sprintDecision(original, true) && !KillAuraModule.blocksSprintForCrit();
    }

    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Input;sprint()Z"))
    private boolean autism$sprintDecisionInput(boolean original) {
        return ModuleMovementUtil.sprintDecision(original, false) && !KillAuraModule.blocksSprintForCrit();
    }

    @ModifyExpressionValue(method = "sendIsSprintingIfNeeded", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;isSprinting()Z"))
    private boolean autism$sprintNetworkDecision(boolean original) {
        return ModuleMovementUtil.sprintNetworkAllowed(original);
    }

    @ModifyExpressionValue(method = "canStartSprinting", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
    private boolean autism$omniSprintForwardImpulse(boolean original) {
        if (ModuleMovementUtil.sprintIsOmnidirectional()) {
            return ((LocalPlayer) (Object) this).input.getMoveVector().length() > 1.0E-5F;
        }
        return original;
    }

    @ModifyExpressionValue(method = "shouldStopRunSprinting", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/player/LocalPlayer;horizontalCollision:Z",
        opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private boolean autism$sprintIgnoreCollision(boolean original) {
        return !ModuleMovementUtil.sprintIgnoresCollision() && original;
    }

    @ModifyReturnValue(method = "shouldStopRunSprinting", at = @At("RETURN"))
    private boolean autism$sprintForceStop(boolean shouldStop) {
        return shouldStop || ModuleMovementUtil.sprintShouldPrevent() || KillAuraModule.blocksSprintForCrit();
    }

    @ModifyExpressionValue(method = "canStartSprinting", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;isMovingSlowly()Z"))
    private boolean autism$sprintIgnoreBlindness(boolean original) {
        return !ModuleMovementUtil.sprintIgnoresBlindness() && original;
    }

    @Inject(method = "getJumpRidingScale", at = @At("RETURN"), cancellable = true)
    private void autism$entityControlMaxJump(CallbackInfoReturnable<Float> cir) {
        if (EntityControlModule.shouldMaxJump()) cir.setReturnValue(1.0F);
    }

    @Inject(method = "jumpableVehicle", at = @At("RETURN"), cancellable = true)
    private void autism$entityControlFlightJump(CallbackInfoReturnable<PlayerRideableJumping> cir) {
        if (EntityControlModule.shouldCancelRidingJump()) cir.setReturnValue(null);
    }
}
