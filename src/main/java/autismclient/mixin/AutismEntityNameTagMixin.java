package autismclient.mixin;

import autismclient.modules.ModuleNameTagRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class AutismEntityNameTagMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void autism$suppressVanillaNameTag(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {

        if (state != null && state.nameTag != null && ModuleNameTagRenderer.tags(entity)) {
            state.nameTag = null;
        }
    }
}
