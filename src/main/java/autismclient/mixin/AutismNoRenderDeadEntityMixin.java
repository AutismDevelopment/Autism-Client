package autismclient.mixin;

import autismclient.modules.NoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class AutismNoRenderDeadEntityMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noDeadEntity(LivingEntityRenderState state, PoseStack poseStack,
                                     SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo ci) {
        if (state != null && state.deathTime > 0.0F && NoRenderState.noDeadEntities()) ci.cancel();
    }
}
