package autismclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import autismclient.modules.AutismModule;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismCustomFilterOverlay;
import autismclient.util.AutismCustomFilterPresetOverlay;
import autismclient.util.AutismLANSync;
import autismclient.util.AutismLANSyncOverlay;
import autismclient.util.AutismLauncherOverlay;
import autismclient.util.AutismLecternButtons;
import autismclient.util.AutismMacroEditorOverlay;
import autismclient.util.AutismMacroListOverlay;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismPacketLoggerOverlay;
import autismclient.util.AutismQueueEditorOverlay;
import autismclient.util.AutismKeybindOverlay;
import autismclient.util.AutismSpecialGuiActions;
import autismclient.util.AutismUiScale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

@Mixin(BookViewScreen.class)
public abstract class AutismBookScreenMixin extends Screen implements AutismSpecialGuiActions {
    @Unique private static final Minecraft MC = Minecraft.getInstance();

    @Unique private AutismLauncherOverlay launcherOverlay;
    @Unique private AutismLANSyncOverlay lanSyncOverlay;
    @Unique private AutismMacroListOverlay macroListOverlay;
    @Unique private AutismQueueEditorOverlay queueEditorOverlay;
    @Unique private AutismPacketLoggerOverlay packetLoggerOverlay;
    @Unique private AutismCustomFilterOverlay customFilterOverlay;
    @Unique private AutismCustomFilterPresetOverlay customFilterPresetOverlay;
    @Unique private AutismMacroEditorOverlay macroEditorOverlay;
    @Unique private AutismKeybindOverlay keybindOverlay;
    @Unique private autismclient.util.AutismServerInfoOverlay serverInfoOverlay;

    protected AutismBookScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void yang$init(CallbackInfo ci) {
        if (!yang$isAutismActive()) return;

        AutismLANSync.getInstance().setOnSessionStateChanged(() -> {});

        lanSyncOverlay = AutismLANSyncOverlay.getSharedOverlay(this.font);
        macroListOverlay = new AutismMacroListOverlay(this.font);
        queueEditorOverlay = new AutismQueueEditorOverlay(this.font);
        customFilterOverlay = new AutismCustomFilterOverlay(this.font);
        customFilterPresetOverlay = customFilterOverlay.getPresetManagerOverlay();

        lanSyncOverlay.restoreState();
        macroListOverlay.restoreState();
        queueEditorOverlay.restoreState();
        customFilterOverlay.restoreLayout();
        if (customFilterPresetOverlay != null) customFilterPresetOverlay.restoreLayout();

        macroEditorOverlay = AutismMacroEditorOverlay.getSharedOverlay();
        if (macroEditorOverlay != null) macroEditorOverlay.restoreState();

        AutismOverlayManager manager = AutismOverlayManager.get();
        manager.clear();
        manager.register(lanSyncOverlay);
        manager.register(macroListOverlay);
        manager.register(queueEditorOverlay);
        manager.register(customFilterOverlay);
        if (customFilterPresetOverlay != null) manager.register(customFilterPresetOverlay);
        if (macroEditorOverlay != null) manager.register(macroEditorOverlay);

        AutismModule autismModule = AutismModule.get();
        autismclient.util.AutismMultiOverlay multiOverlay = autismModule == null ? null : autismModule.getMultiOverlayIfExists();
        if (multiOverlay != null && multiOverlay.isVisible()) manager.register(multiOverlay);

        keybindOverlay = new AutismKeybindOverlay();
        keybindOverlay.restoreLayout();
        manager.register(keybindOverlay);

        launcherOverlay = new AutismLauncherOverlay(macroListOverlay, null, lanSyncOverlay, queueEditorOverlay, packetLoggerOverlay, customFilterOverlay);
        launcherOverlay.setKeybindOverlay(keybindOverlay);
        launcherOverlay.setPacketLoggerOverlaySupplier(() -> {
            if (packetLoggerOverlay == null && autismModule != null) {
                packetLoggerOverlay = autismModule.getPacketLoggerOverlay();
                if (packetLoggerOverlay != null) packetLoggerOverlay.restoreState();
            }
            if (packetLoggerOverlay != null) manager.register(packetLoggerOverlay);
            return packetLoggerOverlay;
        });
        launcherOverlay.setServerDataOverlaySupplier(() -> {
            if (serverInfoOverlay == null) {
                serverInfoOverlay = autismclient.modules.AutismModule.get().getServerDataOverlay();
            }
            if (serverInfoOverlay != null) manager.register(serverInfoOverlay);
            return serverInfoOverlay;
        });
        if (packetLoggerOverlay == null && autismclient.util.AutismPacketLoggerOverlay.shouldRestoreSavedVisible()) {
            packetLoggerOverlay = autismclient.modules.AutismModule.get().getPacketLoggerOverlay();
            if (packetLoggerOverlay != null) {
                packetLoggerOverlay.restoreState();
                if (packetLoggerOverlay.isVisible()) manager.register(packetLoggerOverlay);
            }
        }
        if (serverInfoOverlay == null && autismclient.util.AutismServerInfoOverlay.shouldRestoreSavedVisible()) {
            serverInfoOverlay = autismclient.modules.AutismModule.get().getServerDataOverlay();
            if (serverInfoOverlay != null) {
                serverInfoOverlay.restoreState();
                if (serverInfoOverlay.isVisible()) manager.register(serverInfoOverlay);
            }
        }
        launcherOverlay.restoreLayout();
        manager.register(launcherOverlay);

        Screen screen = (Screen) (Object) this;
        ScreenEvents.afterExtract(screen).register((scrn, drawContext, mouseX, mouseY, tickDelta) -> {
            if (yang$isAutismActive()) {
                AutismOverlayManager.get().renderAll(drawContext, mouseX, mouseY, tickDelta);
            }
        });
    }

    @Override
    public void removed() {
        if (yang$isAutismActive()) {
            if (lanSyncOverlay != null) lanSyncOverlay.saveState();
            if (macroListOverlay != null) macroListOverlay.saveState();
            if (queueEditorOverlay != null) queueEditorOverlay.saveState();
            if (macroEditorOverlay != null) macroEditorOverlay.saveState();
            if (launcherOverlay != null) launcherOverlay.saveLayout();
            if (packetLoggerOverlay != null) packetLoggerOverlay.saveState();
            if (customFilterOverlay != null) customFilterOverlay.saveLayout();
            if (customFilterPresetOverlay != null) customFilterPresetOverlay.saveLayout();
            if (keybindOverlay != null) keybindOverlay.saveLayout();
            if (serverInfoOverlay != null) serverInfoOverlay.saveState();
            AutismOverlayManager.get().clear();
        }
        super.removed();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void yang$mouseClicked(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!yang$isAutismActive()) return;
        if ((Object) this instanceof LecternScreen) {
            double mouseX = AutismUiScale.toVirtual(click.x());
            double mouseY = AutismUiScale.toVirtual(click.y());
            for (autismclient.gui.vanillaui.components.ScreenButton button : AutismLecternButtons.build(MC, queueEditorOverlay)) {
                if (button.click(mouseX, mouseY, click.button())) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
        if (AutismOverlayManager.get().handleMouseClicked(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (yang$isAutismActive() && AutismOverlayManager.get().handleMouseReleased(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (yang$isAutismActive() && AutismOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (yang$isAutismActive() && AutismOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void yang$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!yang$isAutismActive()) return;
        if (AutismOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (yang$isAutismActive() && AutismOverlayManager.get().handleCharTyped((char) input.codepoint(), 0)) {
            return true;
        }
        return super.charTyped(input);
    }

    @Unique
    private boolean yang$isAutismActive() {
        AutismModule module = AutismModule.get();
        return module != null && module.isActive();
    }

    @Override
    public void autism$closeWithPacket() {
        autism$closeWithPacket(true);
    }

    @Override
    public void autism$closeWithPacket(boolean notify) {
        MC.gui.setScreen(null);
    }

    @Override
    public void autism$closeWithoutPacket() {
        autism$closeWithoutPacket(true);
    }

    @Override
    public void autism$closeWithoutPacket(boolean notify) {
        MC.gui.setScreen(null);
        if (notify) AutismClientMessaging.sendPrefixed("Book screen closed locally.");
    }

    @Override
    public void autism$desync() {
        autism$desync(true);
    }

    @Override
    public void autism$desync(boolean notify) {
        if (notify) AutismClientMessaging.sendPrefixed("This book screen has no update packet to desync.");
    }
}
