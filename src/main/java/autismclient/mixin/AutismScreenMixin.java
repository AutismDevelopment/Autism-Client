package autismclient.mixin;

import autismclient.gui.vanillaui.components.ScreenButton;
import autismclient.ducks.AutismExternalButtonScreen;
import autismclient.mixin.accessor.AutismScreenAccessor;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.util.AutismLecternButtons;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismPacketLoggerOverlay;
import autismclient.util.AutismQueueEditorOverlay;
import autismclient.util.AutismUiScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class AutismScreenMixin {
    @Unique private static final Minecraft MC = Minecraft.getInstance();

    @Unique private boolean autism$lecternInitialized;
    @Unique private AutismQueueEditorOverlay autism$queueEditorOverlay;
    @Unique private AutismPacketLoggerOverlay autism$packetLoggerOverlay;

    @Inject(method = "init()V", at = @At("TAIL"))
    private void autism$onInit(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!(screen instanceof LecternScreen) || !autism$isModuleActive()) return;

        if (autism$lecternInitialized) {
            if (autism$queueEditorOverlay != null) autism$queueEditorOverlay.restoreState();
            if (autism$queueEditorOverlay != null) AutismOverlayManager.get().register(autism$queueEditorOverlay);
            return;
        }

        Font textRenderer = ((AutismScreenAccessor) this).getFont();
        autism$queueEditorOverlay = new AutismQueueEditorOverlay(textRenderer);
        autism$queueEditorOverlay.restoreState();
        AutismOverlayManager.get().register(autism$queueEditorOverlay);

        autism$lecternInitialized = true;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void autism$render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (screen instanceof AutismExternalButtonScreen externalButtonScreen) {
            externalButtonScreen.autism$renderExternalButtons(context, mouseX, mouseY, delta);
        }

        if (!(screen instanceof LecternScreen) || MC.player == null) return;

        if (!autism$isModuleActive()) return;

        Font textRenderer = ((AutismScreenAccessor) this).getFont();
        if (textRenderer == null) return;

        AbstractContainerMenu handler = MC.player.containerMenu;
        if (handler != null) {
            int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
            int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
            AutismUiScale.pushOverlayScale(context);
            try {
                for (ScreenButton button : AutismLecternButtons.build(MC, autism$queueEditorOverlay)) {
                    button.render(context, textRenderer, virtualMouseX, virtualMouseY);
                }
            } finally {
                AutismUiScale.popOverlayScale(context);
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void autism$renderTopmostNotifications(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {

        if (!AutismNotifications.hasVisible()) return;
        context.nextStratum();
        AutismUiScale.pushOverlayScale(context);
        try {
            AutismNotifications.render(context);
        } finally {
            AutismUiScale.popOverlayScale(context);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void autism$onClose(CallbackInfo ci) {
        autismclient.util.AutismConfig.enqueuePendingSaveNow();
        Screen screen = (Screen) (Object) this;
        if (screen instanceof LecternScreen) {
            AutismOverlayManager.get().unregister(autism$queueEditorOverlay);
            AutismOverlayManager.get().unregister(autism$packetLoggerOverlay);
        }
    }

    @Unique
    private boolean autism$isModuleActive() {
        if (PackHideState.isHardLocked()) return false;
        AutismModule module = AutismModule.get();
        return module != null && module.isActive();
    }

}
