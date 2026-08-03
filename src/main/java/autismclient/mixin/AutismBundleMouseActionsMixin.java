package autismclient.mixin;

import autismclient.modules.InventoryTweaksModule;
import net.minecraft.client.gui.BundleMouseActions;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BundleMouseActions.class)
public abstract class AutismBundleMouseActionsMixin {
    @Redirect(
        method = {"onMouseScrolled", "toggleSelectedBundleItem"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BundleItem;getNumberOfItemsToShow(Lnet/minecraft/world/item/ItemStack;)I"),
        require = 0
    )
    private int autism$uncapBundleScroll(ItemStack stack) {
        return InventoryTweaksModule.bundleScrollLimit(stack, BundleItem.getNumberOfItemsToShow(stack));
    }
}
