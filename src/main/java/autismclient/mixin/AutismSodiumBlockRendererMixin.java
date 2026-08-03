package autismclient.mixin;

import autismclient.modules.GoldenLeverModule;
import autismclient.modules.ModuleRenderUtil;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class AutismSodiumBlockRendererMixin {
    @Unique private int autism$xrayAlpha = -1;
    @Unique private boolean autism$goldenLever;
    @Unique private int autism$darkenTint = -1;

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void autism$xraySodiumBlockStart(@Coerce Object model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        boolean xrayActive = ModuleRenderUtil.hasXrayRenderWork();
        boolean goldenLeverActive = GoldenLeverModule.isStylingActive();
        boolean darkenActive = ModuleRenderUtil.hasWorldDarkenWork();
        if (!xrayActive && !goldenLeverActive && !darkenActive) {
            autism$xrayAlpha = -1;
            autism$goldenLever = false;
            autism$darkenTint = -1;
            return;
        }
        autism$xrayAlpha = xrayActive ? ModuleRenderUtil.xrayAlpha(state, pos) : -1;
        autism$goldenLever = goldenLeverActive && GoldenLeverModule.shouldStyle(state);
        autism$darkenTint = darkenActive ? ModuleRenderUtil.worldDarkenTint(state, pos) : -1;
        if (autism$xrayAlpha == 0) ci.cancel();
    }

    @Inject(method = "renderModel", at = @At("RETURN"))
    private void autism$xraySodiumBlockEnd(@Coerce Object model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        autism$xrayAlpha = -1;
        autism$goldenLever = false;
        autism$darkenTint = -1;
    }

    @Inject(method = "processQuad", at = @At("HEAD"))
    private void autism$xraySodiumBlockMaterial(@Coerce Object quad, CallbackInfo ci) {
        int alpha = autism$xrayAlpha;
        if (autism$goldenLever) ModuleRenderUtil.applySodiumQuadTint(quad, GoldenLeverModule.GOLD_TINT);
        if (autism$darkenTint != -1) ModuleRenderUtil.applySodiumQuadTint(quad, autism$darkenTint);
        if (alpha < 0) return;
        ModuleRenderUtil.applySodiumQuadAlpha(quad, alpha);
        if (alpha > 0 && alpha < 255) ModuleRenderUtil.applySodiumQuadRenderLayer(quad, ChunkSectionLayer.TRANSLUCENT);
    }
}
