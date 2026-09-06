package autismclient.mixin;

import autismclient.modules.ModuleRenderUtil;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer", remap = false)
public abstract class AutismSodiumDefaultFluidRendererMixin {
    @Shadow(remap = false) @Final private int[] quadColors;
    @Unique private int autism$xrayAlpha = -1;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$xraySodiumFluidStart(@Coerce Object level, BlockState blockState, FluidState fluidState, BlockPos blockPos, BlockPos offset, @Coerce Object collector, @Coerce Object buffers, @Coerce Object material, @Coerce Object colorProvider, FluidModel fluidModel, CallbackInfo ci) {
        autism$xrayAlpha = -1;
        if (!ModuleRenderUtil.hasXrayRenderWork()) return;
        autism$xrayAlpha = ModuleRenderUtil.xrayFluidAlpha(fluidState, blockPos);
        if (autism$xrayAlpha == 0) ci.cancel();
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void autism$xraySodiumFluidEnd(@Coerce Object level, BlockState blockState, FluidState fluidState, BlockPos blockPos, BlockPos offset, @Coerce Object collector, @Coerce Object buffers, @Coerce Object material, @Coerce Object colorProvider, FluidModel fluidModel, CallbackInfo ci) {
        autism$xrayAlpha = -1;
    }

    @Inject(method = "writeQuad", at = @At("HEAD"), require = 0)
    private void autism$xraySodiumFluidMaterial(@Coerce Object buffers, @Coerce Object collector, @Coerce Object material, BlockPos offset, @Coerce Object quad, @Coerce Object facing, boolean flip, CallbackInfo ci) {
        int alpha = autism$xrayAlpha;
        if (alpha < 0) return;
        for (int i = 0; i < quadColors.length; i++) {
            quadColors[i] = ((alpha & 0xFF) << 24) | (quadColors[i] & 0x00FFFFFF);
        }
    }

    @ModifyArgs(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/DefaultFluidRenderer;writeQuad(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuilder;Lnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/TranslucentGeometryCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;Lnet/minecraft/core/BlockPos;Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadView;Lnet/caffeinemc/mods/sodium/client/model/quad/properties/ModelQuadFacing;Z)V"
        ),
        require = 0
    )
    private void autism$xraySodiumFluidLayer(Args args) {
        int alpha = autism$xrayAlpha;
        if (alpha <= 0 || alpha >= 255) return;
        Object material = args.get(2);
        Object translucent = ModuleRenderUtil.sodiumTranslucentMaterial(material);
        if (translucent != null) {
            args.set(2, translucent);
        }
    }
}
