package autismclient.mixin;

import autismclient.gui.screen.AutismModuleScreen;
import autismclient.gui.screen.AutismOverlayHostScreen;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.util.AutismLinks;
import autismclient.util.AutismMatchmakingOverlay;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismProfilesOverlay;
import autismclient.util.IAutismOverlay;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class AutismTitleScreenSupportMixin extends Screen {
    protected AutismTitleScreenSupportMixin() {
        super(null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void autism$addSupportButtons(CallbackInfo ci) {
        if (PackHideState.isActive()) return;

        autism$rightButton(Component.literal("Modules & Macros"), 4, b -> {
            if (!PackHideState.isHardLocked()) {
                this.minecraft.gui.setScreen(new AutismModuleScreen(this, AutismModuleScreen.Mode.TITLE_SETUP));
            }
        });
        autism$rightButton(Component.literal("Matchmaking"), 28, b -> autism$openMenuOverlay(true));
        autism$rightButton(Component.literal("Profiles"), 52, b -> autism$openMenuOverlay(false));

        autism$leftButton(Component.literal("Card/PayPal"), 4, AutismLinks.KOFI);
        autism$leftButton(Component.literal("Crypto"), 28, AutismLinks.CRYPTO_DONATE);
        autism$leftButton(Component.literal("AUTISM INC"), 52, AutismLinks.AUTISM_INC_DISCORD);
        autism$leftButton(Component.literal("AUTISM Client"), 76, AutismLinks.DISCORD);
    }

    @Unique
    private void autism$rightButton(Component label, int y, Button.OnPress onPress) {
        int w = autism$buttonWidth(label);
        this.addRenderableWidget(Button.builder(label, onPress).bounds(this.width - w - 4, y, w, 20).build());
    }

    @Unique
    private void autism$leftButton(Component label, int y, String url) {
        this.addRenderableWidget(Button.builder(label, b -> AutismLinks.open(url))
            .bounds(4, y, autism$buttonWidth(label), 20).build());
    }

    @Unique
    private int autism$buttonWidth(Component label) {
        return Math.max(96, Math.min(this.width - 8, this.font.width(label) + 20));
    }

    @Unique
    private void autism$openMenuOverlay(boolean matchmaking) {
        if (PackHideState.isHardLocked()) return;
        AutismModule mod = AutismModule.get();
        if (mod == null) return;
        IAutismOverlay overlay = matchmaking ? mod.getMatchmakingOverlay() : mod.getProfilesOverlay();
        if (overlay == null) return;
        AutismOverlayManager.get().register(overlay);
        if (overlay instanceof AutismMatchmakingOverlay mm) mm.setMainMenuMode(true);
        else if (overlay instanceof AutismProfilesOverlay pf) pf.setMainMenuMode(true);
        overlay.setVisible(true);
        this.minecraft.gui.setScreen(new AutismOverlayHostScreen(overlay, this, true));
    }
}
