package autismclient.mixin;

import autismclient.modules.NoRenderState;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class AutismNoRenderScreenMixin {
    @Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void autism$noGuiBackground(CallbackInfo ci) {
        if (NoRenderState.noGuiBackground()) ci.cancel();
    }
}
