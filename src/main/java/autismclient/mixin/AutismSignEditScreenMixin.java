package autismclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import autismclient.modules.AutismModule;
import autismclient.modules.AutoSignModule;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.modules.PackHideState;
import autismclient.util.AutismClientMessaging;
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
import autismclient.util.AutismSharedState;
import autismclient.util.AutismSpecialGuiActions;
import autismclient.util.AutismSignEditAccess;
import autismclient.util.AutismKeybindOverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

@Mixin(AbstractSignEditScreen.class)
public abstract class AutismSignEditScreenMixin extends Screen implements AutismSpecialGuiActions, AutismSignEditAccess {
    @Unique private static final Minecraft MC = Minecraft.getInstance();

    @Shadow @Final protected SignBlockEntity sign;
    @Shadow @Final private String[] messages;
    @Shadow @Final private boolean isFrontText;

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

    protected AutismSignEditScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void yang$autoSignFill(CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        Module raw = ModuleRegistry.get("auto-sign");
        if (!(raw instanceof AutoSignModule autoSign) || !autoSign.isEnabled()) return;
        if (messages == null || this.font == null) return;

        boolean empty = true;
        for (String message : messages) {
            if (message != null && !message.isEmpty()) {
                empty = false;
                break;
            }
        }

        if (!empty && !autoSign.editExisting()) return;

        String[] lines = autoSign.signLines();
        int maxWidth = sign == null ? 90 : sign.getMaxTextLineWidth();
        for (int i = 0; i < messages.length && i < lines.length; i++) {
            messages[i] = this.font.plainSubstrByWidth(lines[i], maxWidth);
        }

        yang$autoSignDonePending = autoSign.autoDone();
    }

    @Unique private boolean yang$autoSignDonePending;

    @Inject(method = "tick", at = @At("HEAD"))
    private void yang$autoSignDone(CallbackInfo ci) {
        if (!yang$autoSignDonePending) return;
        yang$autoSignDonePending = false;
        this.onClose();
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

        if (!autismclient.util.AutismLiteVariant.enabled()) {
            autismclient.util.IAutismOverlay multiOverlay = autismModule == null ? null : autismModule.getMultiOverlayIfExists();
            if (multiOverlay != null && multiOverlay.isVisible()) manager.register(multiOverlay);
        }

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

    @Inject(method = "removed", at = @At("HEAD"))
    private void yang$removed(CallbackInfo ci) {
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
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (yang$isAutismActive() && AutismOverlayManager.get().handleMouseClicked(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseClicked(click, doubled);
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

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void yang$charTyped(CharacterEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!yang$isAutismActive()) return;
        if (AutismOverlayManager.get().handleCharTyped((char) input.codepoint(), 0)) {
            cir.setReturnValue(true);
        }
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
        if (MC.getConnection() != null) {
            AutismSharedState.get().setForceNextSignUpdatePacket(true);
        }
        MC.gui.setScreen(null);
    }

    @Override
    public void autism$closeWithoutPacket() {
        autism$closeWithoutPacket(true);
    }

    @Override
    public void autism$closeWithoutPacket(boolean notify) {
        AutismSharedState.get().setSuppressNextSignUpdatePacket(true);
        MC.gui.setScreen(null);
        if (notify) AutismClientMessaging.sendPrefixed("Sign edit closed without packet.");
    }

    @Override
    public void autism$desync() {
        autism$desync(true);
    }

    @Override
    public void autism$desync(boolean notify) {
        if (MC.getConnection() == null) {
            if (notify) AutismClientMessaging.sendPrefixed("Failed to desync: no network.");
            return;
        }
        AutismSharedState.get().setForceNextSignUpdatePacket(true);
        MC.getConnection().send(new ServerboundSignUpdatePacket(
            sign.getBlockPos(),
            isFrontText,
            messages[0],
            messages[1],
            messages[2],
            messages[3]
        ));
        if (notify) AutismClientMessaging.sendPrefixed("Sign update packet sent; editor intentionally stays open.");
    }

    @Override
    public BlockPos autism$getSignPos() {
        return sign == null ? BlockPos.ZERO : sign.getBlockPos();
    }

    @Override
    public boolean autism$isFrontText() {
        return isFrontText;
    }

    @Override
    public String[] autism$getSignLines() {
        return messages == null ? new String[]{"", "", "", ""} : messages.clone();
    }
}
