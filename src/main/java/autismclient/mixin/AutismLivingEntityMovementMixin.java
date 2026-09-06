package autismclient.mixin;

import autismclient.modules.AirJumpModule;
import autismclient.modules.ModuleMovementUtil;
import autismclient.modules.ScaffoldModule;
import autismclient.util.AutismSilentAim;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class AutismLivingEntityMovementMixin {
    @Shadow
    protected boolean jumping;

    @Shadow
    private int noJumpDelay;

    @Inject(method = "aiStep", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/entity/LivingEntity;jumping:Z", opcode = Opcodes.GETFIELD))
    private void autism$airJump(CallbackInfo ci) {
        if (this.jumping && this.noJumpDelay == 0 && AirJumpModule.shouldAirJump()) {
            ((LivingEntity) (Object) this).jumpFromGround();
            this.noJumpDelay = 10;
        }
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void autism$airJumpConsume(CallbackInfo ci) {
        AirJumpModule.onJumpFromGround((LivingEntity) (Object) this);
    }

    @ModifyExpressionValue(method = "jumpFromGround", at = @At(value = "NEW",
        target = "(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 autism$killAuraSilentJump(Vec3 original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        Vec3 scaffold = ScaffoldModule.correctedJumpImpulse(entity, original);
        return AutismSilentAim.correctedJumpImpulse(entity, scaffold);
    }

    @ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
    private float autism$killAuraSilentGlidePitch(float original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        float scaffold = ScaffoldModule.correctedFallFlyingPitch(entity, original);
        return AutismSilentAim.correctedFallFlyingPitch(entity, scaffold);
    }

    @ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 autism$killAuraSilentGlideLook(Vec3 original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        Vec3 scaffold = ScaffoldModule.correctedFallFlyingLook(entity, original);
        return AutismSilentAim.correctedFallFlyingLook(entity, scaffold);
    }

    @Inject(method = "travelInFluid", at = @At("RETURN"))
    private void autism$restoreLiquidSpeed(Vec3 input, CallbackInfo ci) {
        ModuleMovementUtil.applySpeedAfterLiquidTravel((LivingEntity) (Object) this);
    }
}
