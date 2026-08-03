package autismclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import autismclient.modules.AutismModule;
import autismclient.util.AutismCustomFilterOverlay;
import autismclient.util.AutismCustomFilterPresetOverlay;
import autismclient.util.AutismLANSync;
import autismclient.util.AutismLANSyncOverlay;
import autismclient.util.AutismLauncherOverlay;
import autismclient.util.AutismMacroEditorOverlay;
import autismclient.util.AutismMacroListOverlay;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismPacketLoggerOverlay;
import autismclient.util.AutismQueueEditorOverlay;
import autismclient.util.AutismKeybindOverlay;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

@Mixin({DialogScreen.class, net.minecraft.client.gui.screens.dialog.WaitingForResponseScreen.class})
public abstract class AutismDialogScreenMixin extends Screen {
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
    @Unique private boolean autism$overlaysBuilt;

    protected AutismDialogScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void autism$init(CallbackInfo ci) {

        Screen screen = (Screen) (Object) this;
        ScreenEvents.afterExtract(screen).register((scrn, drawContext, mouseX, mouseY, tickDelta) -> {
            if (!autism$isAutismActive()) return;
            try {
                AutismOverlayManager.get().renderAll(drawContext, mouseX, mouseY, tickDelta);
            } catch (Throwable ignored) {

            }
        });
        ScreenEvents.remove(screen).register(scrn -> {

            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            boolean stillOnDialog = mc != null
                && (mc.gui.screen() instanceof DialogScreen<?>
                    || mc.gui.screen() instanceof net.minecraft.client.gui.screens.dialog.WaitingForResponseScreen);
            if (!stillOnDialog) {
                autism$overlaysBuilt = false;
                autism$saveOverlays();
            }
        });
        if (!autism$isAutismActive()) return;
        if (autism$overlaysBuilt && AutismOverlayManager.get().hasRegisteredOverlays()) {

            if (launcherOverlay != null) launcherOverlay.setVisible(true);
            return;
        }
        try {
            autism$buildOverlays();
            autism$overlaysBuilt = true;
        } catch (Throwable ignored) {

        }
    }

    @Unique
    private void autism$buildOverlays() {
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

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        boolean inWorld = mc != null && mc.player != null && mc.level != null;

        macroEditorOverlay = AutismMacroEditorOverlay.getSharedOverlay();
        if (macroEditorOverlay != null) {
            macroEditorOverlay.restoreState();
            macroEditorOverlay.setConfigurationOnly(!inWorld);
        }

        AutismOverlayManager manager = AutismOverlayManager.get();
        manager.clear();
        manager.register(lanSyncOverlay);
        manager.register(macroListOverlay);
        manager.register(queueEditorOverlay);
        manager.register(customFilterOverlay);
        if (customFilterPresetOverlay != null) manager.register(customFilterPresetOverlay);
        if (macroEditorOverlay != null) manager.register(macroEditorOverlay);

        autismclient.gui.macro.editor.ActionEditorOverlay actionEditor =
            autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay();
        actionEditor.setWorldCaptureAllowed(inWorld);
        manager.register(actionEditor);

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
                serverInfoOverlay = AutismModule.get().getServerDataOverlay();
            }
            if (serverInfoOverlay != null) manager.register(serverInfoOverlay);
            return serverInfoOverlay;
        });
        if (packetLoggerOverlay == null && AutismPacketLoggerOverlay.shouldRestoreSavedVisible()) {
            packetLoggerOverlay = AutismModule.get().getPacketLoggerOverlay();
            if (packetLoggerOverlay != null) {
                packetLoggerOverlay.restoreState();
                if (packetLoggerOverlay.isVisible()) manager.register(packetLoggerOverlay);
            }
        }
        if (serverInfoOverlay == null && autismclient.util.AutismServerInfoOverlay.shouldRestoreSavedVisible()) {
            serverInfoOverlay = AutismModule.get().getServerDataOverlay();
            if (serverInfoOverlay != null) {
                serverInfoOverlay.restoreState();
                if (serverInfoOverlay.isVisible()) manager.register(serverInfoOverlay);
            }
        }

        autismclient.util.AutismMatchmakingOverlay matchmakingOverlay = AutismModule.get().getMatchmakingOverlay();
        if (matchmakingOverlay != null && matchmakingOverlay.isVisible()) manager.register(matchmakingOverlay);
        autismclient.util.AutismProfilesOverlay profilesOverlay = AutismModule.get().getProfilesOverlay();
        if (profilesOverlay != null && profilesOverlay.isVisible()) manager.register(profilesOverlay);

        launcherOverlay.restoreLayout();

        launcherOverlay.setVisible(true);
        manager.register(launcherOverlay);
    }

    @Unique
    private void autism$saveOverlays() {
        if (!autism$isAutismActive()) return;
        try {
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
        } finally {
            AutismOverlayManager.get().clear();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (autism$isAutismActive() && AutismOverlayManager.get().handleMouseClicked(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (autism$isAutismActive() && AutismOverlayManager.get().handleMouseReleased(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (autism$isAutismActive() && AutismOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (autism$isAutismActive() && AutismOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (autism$isAutismActive() && AutismOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (autism$isAutismActive() && AutismOverlayManager.get().handleCharTyped((char) input.codepoint(), 0)) {
            return true;
        }
        return super.charTyped(input);
    }

    @Unique
    private boolean autism$isAutismActive() {

        AutismModule module = AutismModule.get();
        return module != null && module.isUsable();
    }
}
