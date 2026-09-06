package autismclient.mixin;

import autismclient.modules.GoldenLeverModule;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public class AutismItemModelResolverMixin {
    @Inject(method = "appendItemLayers", at = @At("RETURN"))
    private void autism$goldenLeverItemTint(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext, @Nullable Level level, @Nullable ItemOwner owner, int seed, CallbackInfo ci) {
        if (GoldenLeverModule.shouldStyle(item)) {
            GoldenLeverModule.tintItemStackRenderState(output);
        }
    }
}
