package autismclient.mixin;

import autismclient.modules.ScaffoldModule;
import autismclient.util.AutismSilentAim;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Entity.class)
public abstract class AutismScaffoldVelocityMixin {
    @ModifyArg(
        method = "moveRelative",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getInputVector(Lnet/minecraft/world/phys/Vec3;FF)Lnet/minecraft/world/phys/Vec3;"
        ),
        index = 2
    )
    private float autism$scaffoldSilentMovementYaw(float vanillaYaw) {
        Entity entity = (Entity) (Object) this;
        float scaffold = ScaffoldModule.correctedMovementYaw(entity, vanillaYaw);
        return AutismSilentAim.correctedMovementYaw(entity, scaffold);
    }
}
