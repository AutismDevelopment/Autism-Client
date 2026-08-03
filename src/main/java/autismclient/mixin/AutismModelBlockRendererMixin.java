package autismclient.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import autismclient.modules.GoldenLeverModule;
import autismclient.modules.ModuleRenderUtil;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ModelBlockRenderer.class)
public class AutismModelBlockRendererMixin {
    @Shadow @Final private QuadInstance quadInstance;
    @Unique private static final ThreadLocal<Integer> PACKUTIL_XRAY_ALPHA = ThreadLocal.withInitial(() -> -1);
    @Unique private static final ThreadLocal<Integer> PACKUTIL_DARKEN_TINT = ThreadLocal.withInitial(() -> -1);

    @Inject(method = {"tesselateFlat", "tesselateAmbientOcclusion"}, at = @At("HEAD"), cancellable = true)
    private void autism$xrayAlpha(BlockQuadOutput output, float x, float y, float z, List<BlockStateModelPart> parts, BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfo ci) {

        boolean xrayActive = ModuleRenderUtil.hasXrayRenderWork();
        boolean darkenActive = ModuleRenderUtil.hasWorldDarkenWork();
        if (!xrayActive && !GoldenLeverModule.isStylingActive() && !darkenActive) return;
        int alpha = xrayActive ? ModuleRenderUtil.xrayAlpha(level, pos, state) : -1;
        if (alpha == 0) {
            PACKUTIL_XRAY_ALPHA.set(-1);
            PACKUTIL_DARKEN_TINT.set(-1);
            ci.cancel();
            return;
        }
        PACKUTIL_XRAY_ALPHA.set(alpha);

        PACKUTIL_DARKEN_TINT.set(darkenActive ? ModuleRenderUtil.worldDarkenTint(state, pos) : -1);
    }

    @Inject(method = {"tesselateFlat", "tesselateAmbientOcclusion"}, at = @At("RETURN"))
    private void autism$clearXrayAlpha(BlockQuadOutput output, float x, float y, float z, List<BlockStateModelPart> parts, BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfo ci) {
        if (!ModuleRenderUtil.hasXrayRenderWork() && !GoldenLeverModule.isStylingActive() && !ModuleRenderUtil.hasWorldDarkenWork()) return;
        PACKUTIL_XRAY_ALPHA.set(-1);
        PACKUTIL_DARKEN_TINT.set(-1);
    }

    @Inject(method = "putQuadWithTint", at = @At("HEAD"))
    private void autism$tintXrayAlpha(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad, CallbackInfo ci) {

        boolean xray = ModuleRenderUtil.hasXrayRenderWork();
        boolean golden = GoldenLeverModule.isStylingActive();
        boolean darken = ModuleRenderUtil.hasWorldDarkenWork();
        if (!xray && !golden && !darken) return;
        if (xray) {
            int alpha = PACKUTIL_XRAY_ALPHA.get();
            if (alpha != -1) quadInstance.multiplyColor(ARGB.color(alpha, 255, 255, 255));
        }
        if (golden && GoldenLeverModule.shouldStyle(state)) quadInstance.multiplyColor(GoldenLeverModule.GOLD_TINT);
        if (darken) {
            int tint = PACKUTIL_DARKEN_TINT.get();
            if (tint != -1) quadInstance.multiplyColor(tint);
        }
    }

    @ModifyArg(method = "putQuadWithTint", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"), index = 3)
    private BakedQuad autism$xrayTranslucentLayer(BakedQuad quad) {
        if (!ModuleRenderUtil.hasXrayRenderWork()) return quad;
        int alpha = PACKUTIL_XRAY_ALPHA.get();
        if (alpha <= 0 || alpha >= 255) return quad;
        BakedQuad.MaterialInfo materialInfo = quad.materialInfo();
        if (materialInfo.layer() == ChunkSectionLayer.TRANSLUCENT) return quad;
        BakedQuad.MaterialInfo translucentInfo = new BakedQuad.MaterialInfo(
            materialInfo.sprite(),
            ChunkSectionLayer.TRANSLUCENT,
            materialInfo.itemRenderType(),
            materialInfo.tintIndex(),
            materialInfo.shade(),
            materialInfo.lightEmission()
        );
        return new BakedQuad(
            quad.position0(),
            quad.position1(),
            quad.position2(),
            quad.position3(),
            quad.packedUV0(),
            quad.packedUV1(),
            quad.packedUV2(),
            quad.packedUV3(),
            quad.direction(),
            translucentInfo
        );
    }

    @Inject(method = "shouldRenderFace", at = @At("RETURN"), cancellable = true)
    private void autism$xrayFaces(BlockAndTintGetter level, BlockState state, Direction direction, BlockPos neighborPos, CallbackInfoReturnable<Boolean> cir) {
        if (!ModuleRenderUtil.hasXrayRenderWork()) return;
        BlockPos originalPos = neighborPos.relative(direction.getOpposite());
        cir.setReturnValue(ModuleRenderUtil.modifyXrayFace(level, state, direction, originalPos, cir.getReturnValue()));
    }
}
