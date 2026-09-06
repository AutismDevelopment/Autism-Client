package autismclient.mixin.indigo;

import autismclient.modules.ModuleRenderUtil;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
public abstract class AutismIndigoAltModelBlockRendererMixin {
    @Shadow private BlockAndTintGetter level;
    @Shadow private BlockPos pos;
    @Shadow private BlockState blockState;

    @Inject(method = "shouldCullFace", at = @At("RETURN"), cancellable = true)
    private void autism$xrayCullFace(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (direction == null || !ModuleRenderUtil.hasXrayRenderWork()) return;
        boolean shouldDraw = ModuleRenderUtil.modifyXrayFace(level, blockState, direction, pos, !cir.getReturnValue());
        cir.setReturnValue(!shouldDraw);
    }

    @Inject(method = "transform", at = @At("RETURN"), cancellable = true)
    private void autism$xrayTransform(MutableQuadView quad, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        boolean xray = ModuleRenderUtil.hasXrayRenderWork();
        boolean darken = ModuleRenderUtil.hasWorldDarkenWork();
        if (!xray && !darken) return;

        if (darken) {
            int tint = ModuleRenderUtil.worldDarkenTint(blockState, pos);
            if (tint != -1) {
                for (int i = 0; i < 4; i++) {
                    quad.color(i, ARGB.multiply(quad.color(i), tint));
                }
            }
        }

        if (!xray) return;

        int alpha = ModuleRenderUtil.xrayAlpha(blockState, pos);
        if (alpha == 0) {
            cir.setReturnValue(false);
            return;
        }

        if (alpha == -1) return;
        if (alpha > 0 && alpha < 255) quad.chunkLayer(ChunkSectionLayer.TRANSLUCENT);

        int alphaBits = (alpha & 0xFF) << 24;
        for (int i = 0; i < 4; i++) {
            quad.color(i, alphaBits | (quad.color(i) & 0x00FFFFFF));
        }
    }
}
