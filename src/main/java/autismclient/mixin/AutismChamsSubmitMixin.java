package autismclient.mixin;

import autismclient.util.AutismChams;
import autismclient.util.AutismChamsContext;
import autismclient.util.AutismChamsRenderQueue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public abstract class AutismChamsSubmitMixin {
    @Inject(method = "submitModel", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$chamsModel(Model model, Object object, PoseStack pose, RenderType type, int light, int overlay,
                                   int tint, TextureAtlasSprite sprite, int outlineColor,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfo ci) {
        if (!AutismChamsContext.active()) return;

        if (!AutismChamsContext.claimBody()) {

            if (!AutismChamsContext.drawArmor()) {
                ci.cancel();
            } else {
                AutismChamsRenderQueue.submitLayer(model, object, pose.last().copy(), type,
                    light, overlay, tint, sprite);
                ci.cancel();
            }
            return;
        }

        RenderType visible = AutismChams.chamsVisible(type);
        RenderType occluded = AutismChams.chamsOccluded(type);
        if (visible == null || occluded == null) return;

        AutismChamsRenderQueue.submitBody(model, object, pose.last().copy(), visible, occluded,
            AutismChams.FULLBRIGHT, overlay, AutismChamsContext.visible(), AutismChamsContext.occluded(), sprite);
        ci.cancel();
    }
}
