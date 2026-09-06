package autismclient.mixin;

import autismclient.modules.AutismModule;
import autismclient.util.AutismSharedState;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResultSlot.class)
public abstract class AutismResultSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void autism$xcarryMayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        AutismSharedState shared = AutismSharedState.get();
        AutismModule module = AutismModule.get();
        if (shared.isXCarryForced() || (module != null && module.isXCarryEnabled() && module.isXCarryUseCrafting())) {
            cir.setReturnValue(true);
        }
    }
}
