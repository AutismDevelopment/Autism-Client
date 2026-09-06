package autismclient.mixin;

import autismclient.modules.ModuleRenderUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.util.profiling.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Vector4f;

@Mixin(Lightmap.class)
public abstract class AutismLightmapMixin {
    @Shadow
    @Final
    private GpuTexture texture;

    @Unique
    private boolean autism$wasBright;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void autism$fullbrightLightmap(LightmapRenderState renderState, CallbackInfo ci) {
        if (ModuleRenderUtil.hasBrightLightmapWork()) {
            var profiler = Profiler.get();
            profiler.push("autism_lightmap");
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
            profiler.pop();
            autism$wasBright = true;
            ci.cancel();
            return;
        }

        if (autism$wasBright) {
            autism$wasBright = false;
            renderState.needsUpdate = true;
        }
    }
}
