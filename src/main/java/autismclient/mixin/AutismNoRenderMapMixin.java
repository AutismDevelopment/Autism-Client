package autismclient.mixin;

import autismclient.modules.NoRenderState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(MapRenderer.class)
public abstract class AutismNoRenderMapMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noMapContents(CallbackInfo ci) {
        if (NoRenderState.noMapContents()) ci.cancel();
    }

    @ModifyExpressionValue(
        method = "render",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/MapRenderState;decorations:Ljava/util/List;", opcode = Opcodes.GETFIELD),
        require = 0)
    private List<MapRenderState.MapDecorationRenderState> autism$noMapMarkers(List<MapRenderState.MapDecorationRenderState> original) {
        return NoRenderState.noMapMarkers() ? List.of() : original;
    }
}
