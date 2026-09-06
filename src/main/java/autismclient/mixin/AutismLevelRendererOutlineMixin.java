package autismclient.mixin;

import autismclient.util.AutismFreecamHighlightRenderer;
import autismclient.util.AutismWorldHighlightRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class AutismLevelRendererOutlineMixin {
    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
    private void autism$hideVanillaOutlineInFreecam(PoseStack poseStack, SubmitNodeCollector collector,
                                                    LevelRenderState state, CallbackInfo ci) {

        if (AutismFreecamHighlightRenderer.isReplacingVanillaOutline() || AutismWorldHighlightRenderer.isActive()) {
            ci.cancel();
        }
    }
}
