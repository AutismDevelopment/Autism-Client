package autismclient.mixin;

import autismclient.modules.NoRenderState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class AutismNoRenderNauseaMixin {
    @ModifyExpressionValue(
        method = "renderLevel",
        at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F", ordinal = 0),
        require = 0)
    private float autism$noNauseaWarp(float original) {
        return NoRenderState.noNausea() ? 0.0F : original;
    }
}
