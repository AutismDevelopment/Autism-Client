package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class AutismNoRenderGlintMixin {
    @ModifyVariable(method = "setFoilType", at = @At("HEAD"), argsOnly = true, require = 0)
    private ItemStackRenderState.FoilType autism$noGlint(ItemStackRenderState.FoilType foilType) {
        return NoRenderState.noEnchantGlint() ? ItemStackRenderState.FoilType.NONE : foilType;
    }
}
