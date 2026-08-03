package autismclient.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import autismclient.modules.ModuleRenderUtil;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidRenderer.class)
public class AutismFluidRendererMixin {
    @Unique private static final ThreadLocal<Integer> PACKUTIL_XRAY_FLUID_ALPHA = ThreadLocal.withInitial(() -> -1);
    @Unique private static final ThreadLocal<Boolean> PACKUTIL_FORCE_XRAY_FLUID_SIDES = ThreadLocal.withInitial(() -> false);

    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$xrayFluidMode(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState, FluidState fluidState, CallbackInfo ci) {

        if (!ModuleRenderUtil.hasXrayRenderWork()) return;
        int alpha = ModuleRenderUtil.xrayFluidAlpha(level, pos, fluidState);
        if (alpha == 0) {
            PACKUTIL_XRAY_FLUID_ALPHA.set(-1);
            PACKUTIL_FORCE_XRAY_FLUID_SIDES.set(false);
            ci.cancel();
            return;
        }
        PACKUTIL_XRAY_FLUID_ALPHA.set(alpha);
        PACKUTIL_FORCE_XRAY_FLUID_SIDES.set(ModuleRenderUtil.shouldForceXrayFluidSides());
    }

    @Inject(method = "tesselate", at = @At("RETURN"), require = 0)
    private void autism$clearXrayFluidMode(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        if (!ModuleRenderUtil.hasXrayRenderWork()) return;
        PACKUTIL_XRAY_FLUID_ALPHA.set(-1);
        PACKUTIL_FORCE_XRAY_FLUID_SIDES.set(false);
    }

    @Inject(method = "isFaceOccludedByNeighbor", at = @At("RETURN"), cancellable = true, require = 0)
    private static void autism$xrayFluidSides(Direction direction, float height, BlockState neighborState, CallbackInfoReturnable<Boolean> cir) {
        if (!ModuleRenderUtil.hasXrayRenderWork()) return;
        boolean occluded = cir.getReturnValue();
        if (!occluded) return;
        if (direction.getAxis().isVertical()) return;
        if (!PACKUTIL_FORCE_XRAY_FLUID_SIDES.get()) return;
        cir.setReturnValue(!ModuleRenderUtil.shouldKeepXrayFluidSide(neighborState));
    }

    @Inject(method = "vertex", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$xrayFluidAlpha(VertexConsumer builder, float x, float y, float z, int color, float u, float v, int lightCoords, CallbackInfo ci) {
        if (!ModuleRenderUtil.hasXrayRenderWork()) return;
        int alpha = PACKUTIL_XRAY_FLUID_ALPHA.get();
        if (alpha == -1) return;
        builder.addVertex(x, y, z, ARGB.color(alpha, color), u, v, OverlayTexture.NO_OVERLAY, lightCoords, 0.0F, 1.0F, 0.0F);
        ci.cancel();
    }

    @ModifyArg(method = "tesselate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/FluidRenderer$Output;getBuilder(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"), index = 0, require = 0)
    private ChunkSectionLayer autism$xrayFluidLayer(ChunkSectionLayer layer) {
        if (!ModuleRenderUtil.hasXrayRenderWork()) return layer;
        int alpha = PACKUTIL_XRAY_FLUID_ALPHA.get();
        if (alpha > 0 && alpha < 255) return ChunkSectionLayer.TRANSLUCENT;
        return layer;
    }
}
