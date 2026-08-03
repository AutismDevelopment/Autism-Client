package autismclient.mixin;

import autismclient.modules.NoRenderState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.fog.environment.BlindnessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.DarknessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MobEffectFogEnvironment.class)
public abstract class AutismNoRenderFogEnvMixin {
    @ModifyReturnValue(method = "isApplicable", at = @At("RETURN"), require = 0)
    private boolean autism$noEffectFog(boolean original) {
        if (!original) return false;
        Object self = this;
        if (self instanceof BlindnessFogEnvironment && NoRenderState.noBlindness()) return false;
        if (self instanceof DarknessFogEnvironment && NoRenderState.noDarkness()) return false;
        return true;
    }
}
