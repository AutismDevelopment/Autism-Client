package autismclient.mixin.compat;

import autismclient.modules.PackHideState;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.replaymod.recording.handler.GuiHandler", remap = false)
public abstract class AutismReplayModGuiHandlerMixin {
    @Inject(method = "onGuiInit", at = @At("HEAD"), cancellable = true, remap = false)
    private void autism$skipBuiltInRecordCheckbox(Screen screen, CallbackInfo ci) {
        if (screen == null) return;
        String className = screen.getClass().getName();
        if (className.endsWith(".JoinMultiplayerScreen")
            || className.endsWith(".MultiplayerScreen")
            || className.endsWith(".SelectWorldScreen")) {
            ci.cancel();
            return;
        }

        if (PackHideState.isActive()) {
            ci.cancel();
        }
    }
}
