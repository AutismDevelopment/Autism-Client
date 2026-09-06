package autismclient.mixin;

import autismclient.util.SodiumTerrainPassGuard;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class AutismSodiumDefaultChunkRendererMixin {

    @WrapMethod(method = "render")
    private void autism$guardRender(@Coerce Object matrices, @Coerce Object lists, @Coerce Object pass,
                                    @Coerce Object camera, @Coerce Object fog, boolean useBlockFaceCulling,
                                    @Coerce Object sampler, @Coerce Object globalsBuffer, @Coerce Object sectionBuffer,
                                    Operation<Void> original) {
        if (SodiumTerrainPassGuard.shouldSkip(lists, pass)) {
            return;
        }
        original.call(matrices, lists, pass, camera, fog, useBlockFaceCulling, sampler, globalsBuffer, sectionBuffer);
    }
}
