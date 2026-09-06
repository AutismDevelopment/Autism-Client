package autismclient.mixin;

import autismclient.modules.CrystalAuraModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EndCrystalRenderer.class)
public abstract class AutismCrystalViewRendererMixin {
    @WrapOperation(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"),
        require = 0)
    private void autism$crystalScale(PoseStack instance, float x, float y, float z, Operation<Void> original) {
        if (!CrystalAuraModule.crystalViewActive()) {
            original.call(instance, x, y, z);
            return;
        }
        instance.translate(0.0F, CrystalAuraModule.crystalViewYTranslate(), 0.0F);
        float scale = CrystalAuraModule.crystalViewSize();
        original.call(instance, x * scale, y * scale, z * scale);
    }
}
