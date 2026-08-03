package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class AutismNoRenderEntityRendererMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$hideEntity(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) return;
        if (NoRenderState.noFallingBlocks() && entity instanceof FallingBlockEntity) {
            cir.setReturnValue(false);
            return;
        }
        if (NoRenderState.noEntity(entity)) cir.setReturnValue(false);
    }

    @Inject(method = "getNameTag", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noNametag(Entity entity, CallbackInfoReturnable<Component> cir) {
        if (NoRenderState.noNametags()) cir.setReturnValue(null);
    }
}
