package autismclient.mixin;

import autismclient.modules.KillAuraModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(LocalPlayer.class)
public class AutismKillAuraRotationMixin {

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float autism$killAuraSilentYaw(float original) {
        return KillAuraModule.outgoingMovementYaw((LocalPlayer) (Object) this, original);
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float autism$killAuraSilentPitch(float original) {
        return KillAuraModule.outgoingMovementPitch((LocalPlayer) (Object) this, original);
    }

    @ModifyExpressionValue(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 autism$killAuraSilentViewVector(Vec3 original, Entity camera, double blockInteractionRange,
                                                        double entityInteractionRange, float tickDelta) {
        if (camera != Minecraft.getInstance().player) {
            return original;
        }
        return KillAuraModule.silentViewVector((LocalPlayer) camera, original);
    }
}
