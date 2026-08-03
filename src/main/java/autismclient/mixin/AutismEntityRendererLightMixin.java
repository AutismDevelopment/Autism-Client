package autismclient.mixin;

import autismclient.modules.NameCensorModule;
import autismclient.modules.ModuleRenderUtil;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class AutismEntityRendererLightMixin<T extends Entity> {
    @Inject(method = "getSkyLightLevel", at = @At("RETURN"), cancellable = true)
    private void autism$fullbrightSkyLight(T entity, BlockPos blockPos, CallbackInfoReturnable<Integer> cir) {
        if (!ModuleRenderUtil.hasFullbrightLuminanceWork()) return;
        int boosted = ModuleRenderUtil.fullbrightLuminance(LightLayer.SKY);
        if (boosted > cir.getReturnValue()) cir.setReturnValue(boosted);
    }

    @Inject(method = "getBlockLightLevel", at = @At("RETURN"), cancellable = true)
    private void autism$fullbrightBlockLight(T entity, BlockPos blockPos, CallbackInfoReturnable<Integer> cir) {
        if (!ModuleRenderUtil.hasFullbrightLuminanceWork()) return;
        int boosted = ModuleRenderUtil.fullbrightLuminance(LightLayer.BLOCK);
        if (boosted > cir.getReturnValue()) cir.setReturnValue(boosted);
    }

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void autism$censorNameTag(T entity, CallbackInfoReturnable<Component> cir) {

        if (!NameCensorModule.isActive()) return;
        if (cir.getReturnValue() != null) {
            cir.setReturnValue(NameCensorModule.censorServerComponent(cir.getReturnValue()));
        }
    }
}
