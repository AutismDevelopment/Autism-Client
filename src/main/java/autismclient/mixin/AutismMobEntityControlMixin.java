package autismclient.mixin;

import autismclient.modules.EntityControlModule;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mob.class)
public abstract class AutismMobEntityControlMixin {
    @ModifyReturnValue(method = "isSaddled", at = @At("RETURN"))
    private boolean autism$entityControlSaddle(boolean original) {
        return original || EntityControlModule.shouldSpoofSaddle((Mob) (Object) this);
    }
}
