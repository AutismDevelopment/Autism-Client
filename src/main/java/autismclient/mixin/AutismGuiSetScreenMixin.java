package autismclient.mixin;

import autismclient.gui.screen.AutismPauseScreen;
import autismclient.gui.screen.AutismPanicTitleScreen;
import autismclient.gui.screen.AutismTitleScreen;
import autismclient.gui.screen.AutismWelcomeScreen;
import autismclient.modules.PackHideState;
import autismclient.gui.vanillaui.components.CompactTextInput;
import autismclient.util.AutismConfig;
import autismclient.util.AutismMenuPrefs;
import autismclient.util.AutismWelcomeGate;
import autismclient.util.macro.MacroExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class AutismGuiSetScreenMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private boolean autism$replacingScreen;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void autism$replaceScreen(Screen screen, CallbackInfo ci) {

        CompactTextInput.clearFocusedInput();
        if (!autism$replacingScreen) MacroExecutor.recordRecentGuiScreen(screen);
        if (autism$replacingScreen || screen == null) return;

        if (PackHideState.isActive() && screen instanceof PauseScreen pauseScreen && pauseScreen.showsPauseMenu()) {
            autism$setScreen(new AutismPauseScreen());
            ci.cancel();
            return;
        }

        if (!(screen instanceof TitleScreen)) return;

        if (!PackHideState.isActive() && AutismWelcomeGate.shouldShow(AutismConfig.getGlobal())) {
            AutismWelcomeGate.markShown(AutismConfig.getGlobal());
            autism$setScreen(new AutismWelcomeScreen());
            ci.cancel();
            return;
        }

        if (PackHideState.isActive()) {
            autism$setScreen(new AutismPanicTitleScreen());
            ci.cancel();
            return;
        }

        if (!autismclient.util.AutismLiteVariant.enabled() && AutismMenuPrefs.customMainMenuEnabled()) {
            autism$setScreen(new autismclient.gui.screen.AutismTitleScreen());
            ci.cancel();
        }
    }

    @Unique
    private void autism$setScreen(Screen screen) {
        autism$replacingScreen = true;
        try {
            minecraft.gui.setScreen(screen);
        } finally {
            autism$replacingScreen = false;
        }
    }
}
