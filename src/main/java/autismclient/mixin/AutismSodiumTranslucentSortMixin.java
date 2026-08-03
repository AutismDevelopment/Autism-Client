package autismclient.mixin;

import autismclient.modules.ModuleRenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector", remap = false)
public abstract class AutismSodiumTranslucentSortMixin {

    @Inject(method = "filterSortType", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void autism$xrayDisableTranslucentSort(CallbackInfoReturnable<Object> cir) {
        if (!ModuleRenderUtil.hasXrayRenderWork()) return;
        Object current = cir.getReturnValue();

        if (!(current instanceof Enum<?> sort)) return;
        String name = sort.name();
        if (!"STATIC_TOPO".equals(name) && !"DYNAMIC".equals(name)) return;
        Object none = ModuleRenderUtil.sodiumNoneSortType(current);
        if (none == current) return;
        cir.setReturnValue(none);
    }
}
