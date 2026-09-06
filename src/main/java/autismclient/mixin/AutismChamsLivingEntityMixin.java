package autismclient.mixin;

import autismclient.modules.ModuleRenderUtil;
import autismclient.util.AutismChamsContext;
import autismclient.util.AutismChamsHolder;
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
public abstract class AutismChamsLivingEntityMixin {
    @Inject(method = "submit", at = @At("HEAD"), require = 0)
    private void autism$chamsContextStart(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                          CameraRenderState camera, CallbackInfo ci) {
        AutismChamsContext.clear();
        if (ModuleRenderUtil.hasChamsWork() && state instanceof AutismChamsHolder holder && holder.autism$chamsActive()) {
            AutismChamsContext.set(holder.autism$chamsVisible(), holder.autism$chamsOccluded(),
                ModuleRenderUtil.chamsDrawArmor());
        }
    }

    @Inject(method = "submit", at = @At("RETURN"), require = 0)
    private void autism$chamsContextEnd(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                        CameraRenderState camera, CallbackInfo ci) {
        AutismChamsContext.clear();
    }
}
