package autismclient.util;

import autismclient.modules.AutismModule;
import net.minecraft.client.gui.Font;

public final class AutismHostScreenOverlays {
    private final AutismLANSyncOverlay lanSyncOverlay;
    private final AutismMacroListOverlay macroListOverlay;
    private final AutismQueueEditorOverlay queueEditorOverlay;
    private final AutismCustomFilterOverlay customFilterOverlay;
    private final AutismCustomFilterPresetOverlay customFilterPresetOverlay;
    private final AutismMacroEditorOverlay macroEditorOverlay;
    private final AutismKeybindOverlay keybindOverlay;
    private final AutismLauncherOverlay launcherOverlay;
    private AutismPacketLoggerOverlay packetLoggerOverlay;
    private AutismServerInfoOverlay serverInfoOverlay;

    private AutismHostScreenOverlays(Font font) {
        AutismLANSync.getInstance().setOnSessionStateChanged(() -> {});

        lanSyncOverlay = AutismLANSyncOverlay.getSharedOverlay(font);
        macroListOverlay = new AutismMacroListOverlay(font);
        queueEditorOverlay = new AutismQueueEditorOverlay(font);
        customFilterOverlay = new AutismCustomFilterOverlay(font);
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
        manager.register(autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay());

        AutismItemNbtInspectOverlay itemNbtInspectOverlay = AutismItemNbtInspectOverlay.getSharedOverlay(font);
        if (itemNbtInspectOverlay != null) manager.register(itemNbtInspectOverlay);

        AutismModule autismModule = AutismModule.get();

        if (!autismclient.util.AutismLiteVariant.enabled()) {
            IAutismOverlay multiOverlay = autismModule == null ? null : autismModule.getMultiOverlayIfExists();
            if (multiOverlay != null && multiOverlay.isVisible()) manager.register(multiOverlay);
        }

        keybindOverlay = new AutismKeybindOverlay();
        keybindOverlay.restoreLayout();
        manager.register(keybindOverlay);

        launcherOverlay = new AutismLauncherOverlay(macroListOverlay, null, lanSyncOverlay, queueEditorOverlay,
            packetLoggerOverlay, customFilterOverlay);
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
        if (serverInfoOverlay == null && AutismServerInfoOverlay.shouldRestoreSavedVisible()) {
            serverInfoOverlay = AutismModule.get().getServerDataOverlay();
            if (serverInfoOverlay != null) {
                serverInfoOverlay.restoreState();
                if (serverInfoOverlay.isVisible()) manager.register(serverInfoOverlay);
            }
        }

        if (!autismclient.util.AutismLiteVariant.enabled() && autismModule != null) {
            IAutismOverlay matchmakingOverlay = autismModule.getMatchmakingOverlay();
            if (matchmakingOverlay != null && matchmakingOverlay.isVisible()) manager.register(matchmakingOverlay);
            IAutismOverlay profilesOverlay = autismModule.getProfilesOverlay();
            if (profilesOverlay != null && profilesOverlay.isVisible()) manager.register(profilesOverlay);
        }
        launcherOverlay.restoreLayout();
        manager.register(launcherOverlay);
    }

    public static AutismHostScreenOverlays build(Font font) {
        return new AutismHostScreenOverlays(font);
    }

    public void saveAndClear() {
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
