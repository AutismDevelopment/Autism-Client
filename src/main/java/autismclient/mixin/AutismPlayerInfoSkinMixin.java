package autismclient.mixin;

import autismclient.modules.NameCensorModule;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public class AutismPlayerInfoSkinMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void autism$defaultSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerInfo self = (PlayerInfo) (Object) this;
        if (NameCensorModule.shouldDisableSkinFor(self.getProfile())) {
            cir.setReturnValue(DefaultPlayerSkin.get(self.getProfile()));
        }
    }
}
