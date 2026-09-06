package autismclient.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class AutismLevelRendererGuardMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void autism$skipRenderWithoutPlayer(GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean renderBlockOutline, CameraRenderState cameraRenderState, Matrix4fc frustumMatrix, GpuBufferSlice fog, Vector4f clearColor, boolean renderSky, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            ci.cancel();
            return;
        }

        LevelRenderer renderer = (LevelRenderer) (Object) this;
        if (renderer.viewArea() == null || renderer.sectionRenderDispatcher() == null) {
            autismclient.modules.ModuleRenderUtil.refreshWorldRenderer();
            ci.cancel();
        }
    }
}
