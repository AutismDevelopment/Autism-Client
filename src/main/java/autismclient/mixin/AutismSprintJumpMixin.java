package autismclient.mixin;

import autismclient.modules.ModuleMovementUtil;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class AutismSprintJumpMixin {
    @ModifyExpressionValue(method = "jumpFromGround", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float autism$omniSprintJumpYaw(float original) {
        if ((Object) this instanceof LocalPlayer player && ModuleMovementUtil.sprintJumpUsesMovementYaw()) {
            return ModuleMovementUtil.movementDirectionYaw(player);
        }
        return original;
    }
}
