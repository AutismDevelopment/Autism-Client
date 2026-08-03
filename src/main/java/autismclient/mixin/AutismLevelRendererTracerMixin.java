package autismclient.mixin;

import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;

import autismclient.modules.ModuleWorldRenderer;
import autismclient.util.AutismChamsRenderQueue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

@Mixin(LevelRenderer.class)
public class AutismLevelRendererTracerMixin {
    @Inject(
        method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
        at = @At("HEAD"))
    private void autism$beginDelayedOverlays(GraphicsResourceAllocator allocator,
        DeltaTracker tickCounter, boolean renderBlockOutline,
        CameraRenderState cameraState, Matrix4fc positionMatrix,
        GpuBufferSlice gpuBufferSlice, Vector4f vector4f,
        boolean shouldRenderSky, CallbackInfo ci) {

        AutismChamsRenderQueue.clear();
    }

    @Inject(
        method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
        at = @At("RETURN"))
    private void autism$drawTracers(GraphicsResourceAllocator allocator,
        DeltaTracker tickCounter, boolean renderBlockOutline,
        CameraRenderState cameraState, Matrix4fc positionMatrix,
        GpuBufferSlice gpuBufferSlice, Vector4f vector4f,
        boolean shouldRenderSky, CallbackInfo ci) {

        boolean chams = AutismChamsRenderQueue.hasPending();
        boolean tracers = ModuleWorldRenderer.hasPendingTracers();
        boolean esp = ModuleWorldRenderer.hasPendingEspWork();
        boolean waypoints = ModuleWorldRenderer.hasPendingWaypointArt();
        boolean skeleton = autismclient.util.AutismSkeletonRenderer.hasPending();
        if (!chams && !tracers && !esp && !waypoints && !skeleton) return;
        PoseStack matrixStack = new PoseStack();
        matrixStack.mulPose(positionMatrix);
        if (chams) AutismChamsRenderQueue.flush(matrixStack);
        if (tracers) ModuleWorldRenderer.flushTracers(matrixStack);
        if (esp) ModuleWorldRenderer.flushEsp(matrixStack);
        if (skeleton) autismclient.util.AutismSkeletonRenderer.flush(matrixStack);
        if (waypoints) ModuleWorldRenderer.flushWaypointArt(matrixStack);
    }
}
