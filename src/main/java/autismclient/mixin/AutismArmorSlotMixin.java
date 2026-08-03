package autismclient.mixin;

import autismclient.modules.AutismModule;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
public abstract class AutismArmorSlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void autism$mayPlaceForXCarry(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (autism$armorAllowed()) cir.setReturnValue(true);
    }

    @Inject(method = "getMaxStackSize()I", at = @At("HEAD"), cancellable = true)
    private void autism$maxStackSizeForXCarry(CallbackInfoReturnable<Integer> cir) {
        if (autism$armorAllowed()) cir.setReturnValue(64);
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean autism$armorAllowed() {
        AutismModule mod = AutismModule.get();
        boolean modAllow = mod != null && mod.isXCarryUseArmor();
        boolean bypass = autismclient.util.AutismSharedState.get().isXCarryArmorBypass();
        return modAllow || bypass;
    }
}
