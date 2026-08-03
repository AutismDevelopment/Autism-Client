package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.client.renderer.blockentity.SpawnerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpawnerRenderer.class)
public abstract class AutismNoRenderSpawnerMixin {
    @Inject(method = "submitEntityInSpawner", at = @At("HEAD"), cancellable = true, require = 0)
    private static void autism$noSpawnerEntity(CallbackInfo ci) {
        if (NoRenderState.noSpawnerEntities()) ci.cancel();
    }
}
