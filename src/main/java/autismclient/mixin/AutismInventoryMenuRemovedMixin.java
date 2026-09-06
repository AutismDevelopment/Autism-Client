package autismclient.mixin;

import autismclient.modules.AutismModule;
import autismclient.util.AutismSharedState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenu.class)
public abstract class AutismInventoryMenuRemovedMixin {

    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void autism$skipDrainWhenXCarryForced(Player player, CallbackInfo ci) {
        if (!(player instanceof LocalPlayer)) return;
        AutismSharedState shared = AutismSharedState.get();
        AutismModule module = AutismModule.get();
        boolean passive = module != null && module.isXCarryEnabled();

        if (!passive && !shared.isXCarryForced()) return;

        InventoryMenu self = (InventoryMenu) (Object) this;
        if (player.inventoryMenu != self) return;

        ci.cancel();
    }
}
