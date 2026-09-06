package autismclient.mixin;

import autismclient.modules.NoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class AutismNoRenderHurtcamMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noHurtcam(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (NoRenderState.noHurtcam()) {
            ci.cancel();
        }
    }
}
