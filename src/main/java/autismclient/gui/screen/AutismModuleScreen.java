package autismclient.gui.screen;

import autismclient.util.PacketListCodec;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.module.VanillaModuleMenuController;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.modules.Module;
import autismclient.api.module.Setting;
import autismclient.util.AutismBindUtil;
import autismclient.util.AutismConfig;
import autismclient.util.AutismAdminToolsOverlay;
import autismclient.util.AutismCustomFilterOverlay;
import autismclient.util.AutismFabricatorOverlay;
import autismclient.util.AutismKeybindOverlay;
import autismclient.util.AutismLANSyncOverlay;
import autismclient.util.AutismLauncherOverlay;
import autismclient.util.AutismMacroEditorOverlay;
import autismclient.util.AutismMacroListOverlay;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismPacketLoggerOverlay;
import autismclient.util.AutismPacketSelectorOverlay;
import autismclient.util.AutismQueueEditorOverlay;
import autismclient.util.AutismServerInfoOverlay;
import autismclient.util.AutismSharedState;
import autismclient.util.AutismUiScale;
import autismclient.util.IAutismOverlay;
import autismclient.util.macro.ToggleModuleAction;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;

public class AutismModuleScreen extends AutismScreen {
    public enum Mode {
        IN_GAME,
        TITLE_SETUP
    }

    private static final Set<String> TEMPORARILY_HIDDEN_UTILITY_OVERLAYS = new LinkedHashSet<>();

    private final Screen parent;
    private final Mode mode;
    private VanillaModuleMenuController menu;
    private AutismPacketSelectorOverlay packetSelectorOverlay;
    private AutismMacroListOverlay utilityMacroListOverlay;
    private AutismFabricatorOverlay utilityFabricatorOverlay;
    private AutismLANSyncOverlay utilityLanSyncOverlay;
    private AutismQueueEditorOverlay utilityQueueEditorOverlay;
    private AutismCustomFilterOverlay utilityCustomFilterOverlay;
    private AutismKeybindOverlay utilityKeybindOverlay;
    private AutismAdminToolsOverlay utilityAdminToolsOverlay;
    private String returnSettingsModuleId;
    private final Set<String> titleSetupHiddenOverlayIds = new LinkedHashSet<>();

    public AutismModuleScreen(Screen parent) {
        this(parent, Mode.IN_GAME);
    }

    public AutismModuleScreen(Screen parent, Mode mode) {
        super(Component.literal("Autism Modules"));
        this.parent = parent;
        this.mode = mode == null ? Mode.IN_GAME : mode;
    }

    public boolean isTitleSetup() {
        return mode == Mode.TITLE_SETUP;
    }

    @Override
    protected void init() {
        syncUtilityOverlays();
        if (isTitleSetup()) hideRuntimeOverlaysForTitleSetup();
        else restoreTemporarilyHiddenUtilityOverlays();
        menu = new VanillaModuleMenuController(new ModuleMenuHost());
        menu.init();
        if (returnSettingsModuleId != null && !returnSettingsModuleId.isBlank()) {
            menu.openSettingsByModuleId(returnSettingsModuleId);
            returnSettingsModuleId = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public boolean blocksGlobalKeybinds() {
        return (menu != null && menu.blocksGlobalKeybinds())
            || (packetSelectorOverlay != null && packetSelectorOverlay.isVisible());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (menu == null) {
            menu = new VanillaModuleMenuController(new ModuleMenuHost());
            menu.init();
        }
        int mx = AutismUiScale.toVirtualInt(mouseX);
        int my = AutismUiScale.toVirtualInt(mouseY);
        boolean selectorOpen = packetSelectorOverlay != null && packetSelectorOverlay.isVisible();
        syncUtilityOverlaysForTopLayer(selectorOpen || menu.hasTopLayer());
        boolean overlayBlocksMenuHover = !selectorOpen
            && !menu.hasTopLayer()
            && AutismOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY);
        int menuMouseX = overlayBlocksMenuHover ? AutismOverlayManager.HOVER_BLOCKED_MOUSE : mx;
        int menuMouseY = overlayBlocksMenuHover ? AutismOverlayManager.HOVER_BLOCKED_MOUSE : my;

        AutismUiScale.pushOverlayScale(graphics);
        try {
            if (selectorOpen) {
                UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), 0xAA050507);
                packetSelectorOverlay.render(graphics, mx, my, delta);
            } else {
                menu.render(graphics, menuMouseX, menuMouseY, delta, screenWidth(), screenHeight());
            }
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }

        if (!PackHideState.isActive() && !selectorOpen && !menu.hasSelectedModule() && !menu.hasTopLayer()) {
            AutismOverlayManager.get().renderAll(graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible()) {
            return packetSelectorOverlay.mouseClicked(mx, my, event.button());
        }
        if (menu != null && !menu.hasTopLayer() && AutismOverlayManager.get().handleMouseClicked(event.x(), event.y(), event.button())) return true;
        return menu == null || menu.mouseClicked(mx, my, event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible() && packetSelectorOverlay.mouseReleased(mx, my, event.button())) return true;
        if (menu != null && !menu.hasTopLayer() && AutismOverlayManager.get().handleMouseReleased(event.x(), event.y(), event.button())) return true;
        return menu == null || menu.mouseReleased(mx, my, event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible() && packetSelectorOverlay.mouseDragged(mx, my, event.button(), dx, dy)) return true;
        if (menu != null && !menu.hasTopLayer() && AutismOverlayManager.get().handleMouseDragged(event.x(), event.y(), event.button(), dx, dy)) return true;
        return menu == null || menu.mouseDragged(mx, my, event.button(), dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int mx = AutismUiScale.toVirtualInt(x);
        int my = AutismUiScale.toVirtualInt(y);
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible()) return packetSelectorOverlay.mouseScrolled(mx, my, scrollY);
        if (menu != null && !menu.hasTopLayer() && AutismOverlayManager.get().handleMouseScrolled(x, y, scrollY)) return true;
        return menu == null || menu.mouseScrolled(mx, my, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible() && packetSelectorOverlay.keyPressed(input.key(), input.scancode(), input.modifiers())) return true;
        if (menu != null && !menu.hasTopLayer() && AutismOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) return true;
        if (menu != null && menu.keyPressed(input.key(), input.scancode(), input.modifiers())) return true;
        if (passMovementKey(input, true)) return false;
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyEvent input) {
        if (passMovementKey(input, false)) return false;
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        char chr = (char) input.codepoint();
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible() && packetSelectorOverlay.charTyped(chr, 0)) return true;
        if (menu != null && !menu.hasTopLayer() && AutismOverlayManager.get().handleCharTyped(chr, 0)) return true;
        if (menu != null && menu.charTyped(chr)) return true;
        return super.charTyped(input);
    }

    @Override
    public void onClose() {
        saveUtilityOverlayStates();
        hideUtilityOverlaysForMenuClose();
        restoreRuntimeOverlaysHiddenForTitleSetup();
        resetConfigurationOnlyOverlays();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void openPacketSelector(Module module, Setting<?, ?> option) {
        if (module == null || option == null) return;
        if (packetSelectorOverlay == null) packetSelectorOverlay = new AutismPacketSelectorOverlay(font);
        boolean c2s = PacketListCodec.isC2SOption(option.id());
        Set<Class<? extends Packet<?>>> selected = PacketListCodec.resolvePackets(module.value(option.id()), c2s);
        if (c2s) {
            packetSelectorOverlay.openToggleC2S((packetClass, enabled) -> setPacketSelected(module, option, true, packetClass, enabled), selected);
        } else {
            packetSelectorOverlay.openToggleS2C((packetClass, enabled) -> setPacketSelected(module, option, false, packetClass, enabled), selected);
        }
    }

    private void setPacketSelected(Module module, Setting<?, ?> option, boolean c2s, Class<? extends Packet<?>> packetClass, boolean selected) {
        Set<Class<? extends Packet<?>>> packets = new LinkedHashSet<>(PacketListCodec.resolvePackets(module.value(option.id()), c2s));
        if (selected) packets.add(packetClass);
        else packets.remove(packetClass);
        String encoded = PacketListCodec.encodePackets(packets);
        if (isTitleSetup()) module.setConfiguredValue(option.id(), encoded);
        else module.setValue(option.id(), encoded);
    }

    private void syncUtilityOverlays() {
        AutismOverlayManager manager = AutismOverlayManager.get();
        utilityMacroListOverlay = findRegisteredOverlay(AutismMacroListOverlay.class, null);
        if (utilityMacroListOverlay == null) {
            utilityMacroListOverlay = new AutismMacroListOverlay(font);
            utilityMacroListOverlay.restoreState();
        }
        manager.register(utilityMacroListOverlay);

        AutismMacroEditorOverlay macroEditor = AutismMacroEditorOverlay.getSharedOverlay();
        utilityMacroListOverlay.setConfigurationOnly(isTitleSetup());
        if (macroEditor != null) macroEditor.setConfigurationOnly(isTitleSetup());

        if (isTitleSetup()) {
            utilityLanSyncOverlay = AutismLANSyncOverlay.getSharedOverlay(font);
            utilityLanSyncOverlay.restoreState();
            utilityLanSyncOverlay.setConfigurationOnly(true);
            manager.register(utilityLanSyncOverlay);

            utilityQueueEditorOverlay = findRegisteredOverlay(AutismQueueEditorOverlay.class, null);
            if (utilityQueueEditorOverlay == null) {
                utilityQueueEditorOverlay = new AutismQueueEditorOverlay(font);
                utilityQueueEditorOverlay.restoreState();
            }
            utilityQueueEditorOverlay.setConfigurationOnly(true);
            manager.register(utilityQueueEditorOverlay);

            utilityCustomFilterOverlay = findRegisteredOverlay(AutismCustomFilterOverlay.class, null);
            if (utilityCustomFilterOverlay == null) {
                utilityCustomFilterOverlay = new AutismCustomFilterOverlay(font);
                utilityCustomFilterOverlay.restoreLayout();
            }
            manager.register(utilityCustomFilterOverlay);
            if (utilityCustomFilterOverlay.getPresetManagerOverlay() != null) {
                utilityCustomFilterOverlay.getPresetManagerOverlay().restoreLayout();
                manager.register(utilityCustomFilterOverlay.getPresetManagerOverlay());
            }

            utilityKeybindOverlay = findRegisteredOverlay(AutismKeybindOverlay.class, null);
            if (utilityKeybindOverlay == null) {
                utilityKeybindOverlay = new AutismKeybindOverlay();
                utilityKeybindOverlay.restoreLayout();
            }
            manager.register(utilityKeybindOverlay);
            manager.setTemporarilyHidden(utilityMacroListOverlay, false);
            manager.setTemporarilyHidden(utilityKeybindOverlay, false);
            if (macroEditor != null) {
                macroEditor.restoreState();
                manager.register(macroEditor);
                manager.setTemporarilyHidden(macroEditor, false);
            }
            autismclient.gui.macro.editor.ActionEditorOverlay actionEditor = autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay();
            actionEditor.setConfigurationOnly(true);
            manager.register(actionEditor);
            manager.setTemporarilyHidden(actionEditor, false);

            AutismModule global = AutismModule.get();
            if (global != null) {
                AutismPacketLoggerOverlay logger = global.getPacketLoggerOverlay();
                if (logger != null) {
                    logger.restoreState();
                    logger.setConfigurationOnly(true);
                    manager.register(logger);
                }
                AutismServerInfoOverlay serverInfo = global.getServerDataOverlay();
                if (serverInfo != null) {
                    serverInfo.restoreState();
                    serverInfo.setConfigurationOnly(true);
                    manager.register(serverInfo);
                }
            }
            return;
        }

        if (parent instanceof AbstractContainerScreen<?> handledScreen) {
            utilityFabricatorOverlay = AutismFabricatorOverlay.getSharedOverlay(handledScreen);
            utilityFabricatorOverlay.restoreState();
            manager.register(utilityFabricatorOverlay);
        }

        utilityLanSyncOverlay = AutismLANSyncOverlay.getSharedOverlay(font);
        utilityLanSyncOverlay.restoreState();
        utilityLanSyncOverlay.setConfigurationOnly(false);
        manager.register(utilityLanSyncOverlay);

        utilityQueueEditorOverlay = findRegisteredOverlay(AutismQueueEditorOverlay.class, null);
        if (utilityQueueEditorOverlay == null) {
            utilityQueueEditorOverlay = new AutismQueueEditorOverlay(font);
            utilityQueueEditorOverlay.restoreState();
        }
        utilityQueueEditorOverlay.setConfigurationOnly(false);
        manager.register(utilityQueueEditorOverlay);

        utilityCustomFilterOverlay = findRegisteredOverlay(AutismCustomFilterOverlay.class, null);
        if (utilityCustomFilterOverlay == null) {
            utilityCustomFilterOverlay = new AutismCustomFilterOverlay(font);
            utilityCustomFilterOverlay.restoreLayout();
        }
        manager.register(utilityCustomFilterOverlay);
        if (utilityCustomFilterOverlay.getPresetManagerOverlay() != null) {
            utilityCustomFilterOverlay.getPresetManagerOverlay().restoreLayout();
            manager.register(utilityCustomFilterOverlay.getPresetManagerOverlay());
        }

        utilityKeybindOverlay = findRegisteredOverlay(AutismKeybindOverlay.class, null);
        if (utilityKeybindOverlay == null) {
            utilityKeybindOverlay = new AutismKeybindOverlay();
            utilityKeybindOverlay.restoreLayout();
        }
        manager.register(utilityKeybindOverlay);

        utilityAdminToolsOverlay = AutismAdminToolsOverlay.getSharedOverlay();
        utilityAdminToolsOverlay.restoreLayout();
        manager.register(utilityAdminToolsOverlay);

        if (macroEditor != null) {
            macroEditor.restoreState();
            manager.register(macroEditor);
        }
        autismclient.gui.macro.editor.ActionEditorOverlay actionEditor = autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay();
        actionEditor.setConfigurationOnly(false);
        manager.register(actionEditor);

        AutismModule global = AutismModule.get();
        if (global != null) {
            AutismPacketLoggerOverlay logger = global.getPacketLoggerOverlay();
            if (logger != null) {
                logger.restoreState();
                logger.setConfigurationOnly(false);
                manager.register(logger);
            }
            AutismServerInfoOverlay serverInfo = global.getServerDataOverlay();
            if (serverInfo != null) {
                serverInfo.restoreState();
                serverInfo.setConfigurationOnly(false);
                manager.register(serverInfo);
            }
        }
    }

    private void syncUtilityOverlaysForTopLayer(boolean hidden) {
        if (hidden) hideUtilityOverlaysForMenuClose();
        else restoreTemporarilyHiddenUtilityOverlays();
    }

    private void hideUtilityOverlaysForMenuClose() {
        TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.clear();
        setOverlayHiddenForMenuClose(utilityMacroListOverlay);
        setOverlayHiddenForMenuClose(utilityFabricatorOverlay);
        setOverlayHiddenForMenuClose(utilityLanSyncOverlay);
        setOverlayHiddenForMenuClose(utilityQueueEditorOverlay);
        setOverlayHiddenForMenuClose(utilityCustomFilterOverlay);
        setOverlayHiddenForMenuClose(utilityKeybindOverlay);
        setOverlayHiddenForMenuClose(utilityAdminToolsOverlay);
        AutismModule global = AutismModule.get();
        if (global != null) {
            setOverlayHiddenForMenuClose(global.getPacketLoggerOverlayIfExists());
            setOverlayHiddenForMenuClose(global.getServerDataOverlayIfExists());

            setOverlayHiddenForMenuClose(global.getMultiOverlayIfExists());
        }
        setOverlayHiddenForMenuClose(AutismMacroEditorOverlay.getSharedOverlay());
        setOverlayHiddenForMenuClose(autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay());
        if (utilityCustomFilterOverlay != null) setOverlayHiddenForMenuClose(utilityCustomFilterOverlay.getPresetManagerOverlay());
    }

    private void setOverlayHiddenForMenuClose(IAutismOverlay overlay) {
        if (overlay == null || !overlay.isVisible()) return;
        TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.add(overlay.getOverlayId());
        AutismOverlayManager.get().setTemporarilyHidden(overlay, true);
    }

    private void restoreTemporarilyHiddenUtilityOverlays() {
        if (TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.isEmpty()) return;
        Set<String> restoreIds = new LinkedHashSet<>(TEMPORARILY_HIDDEN_UTILITY_OVERLAYS);
        TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.clear();
        for (IAutismOverlay overlay : AutismOverlayManager.get().getOverlays()) {
            if (overlay != null && restoreIds.contains(overlay.getOverlayId())) {
                AutismOverlayManager.get().setTemporarilyHidden(overlay, false);
            }
        }
    }

    private void hideRuntimeOverlaysForTitleSetup() {
        AutismOverlayManager manager = AutismOverlayManager.get();
        titleSetupHiddenOverlayIds.clear();
        for (IAutismOverlay overlay : manager.getOverlays()) {
            if (overlay == null || !overlay.isVisible() || isTitleSetupOverlay(overlay)) continue;
            titleSetupHiddenOverlayIds.add(overlay.getOverlayId());
            manager.setTemporarilyHidden(overlay, true);
        }
    }

    private boolean isTitleSetupOverlay(IAutismOverlay overlay) {
        return overlay == utilityMacroListOverlay
            || overlay == utilityLanSyncOverlay
            || overlay == utilityQueueEditorOverlay
            || overlay == utilityCustomFilterOverlay
            || utilityCustomFilterOverlay != null && overlay == utilityCustomFilterOverlay.getPresetManagerOverlay()
            || overlay == utilityKeybindOverlay
            || AutismModule.get() != null && overlay == AutismModule.get().getPacketLoggerOverlayIfExists()
            || AutismModule.get() != null && overlay == AutismModule.get().getServerDataOverlayIfExists()
            || overlay == AutismMacroEditorOverlay.getSharedOverlay()
            || overlay == autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay();
    }

    private void restoreRuntimeOverlaysHiddenForTitleSetup() {
        if (titleSetupHiddenOverlayIds.isEmpty()) return;
        AutismOverlayManager manager = AutismOverlayManager.get();
        for (IAutismOverlay overlay : manager.getOverlays()) {
            if (overlay != null && titleSetupHiddenOverlayIds.contains(overlay.getOverlayId())) {
                manager.setTemporarilyHidden(overlay, false);
            }
        }
        titleSetupHiddenOverlayIds.clear();
    }

    private <T extends IAutismOverlay> T findRegisteredOverlay(Class<T> type, String overlayId) {
        for (IAutismOverlay overlay : AutismOverlayManager.get().getOverlays()) {
            if (overlay == null || !type.isInstance(overlay)) continue;
            if (overlayId != null && !overlayId.equals(overlay.getOverlayId())) continue;
            return type.cast(overlay);
        }
        return null;
    }

    private void runUtility(String id) {
        if ("macros".equals(id)) {
            toggleMacroPanel();
            return;
        }
        if ("keys".equals(id)) {
            toggleOverlay(utilityKeybindOverlay);
            return;
        }
        if (isTitleSetup()) {
            switch (id) {
                case "lan" -> toggleOverlay(utilityLanSyncOverlay);
                case "queue" -> toggleOverlay(utilityQueueEditorOverlay);
                case "packets" -> toggleOverlay(utilityCustomFilterOverlay);
                case "logger" -> {
                    AutismModule global = AutismModule.get();
                    if (global != null) toggleOverlay(global.getPacketLoggerOverlay());
                }
                case "server" -> {
                    AutismModule global = AutismModule.get();
                    if (global != null) toggleOverlay(global.getServerDataOverlay());
                }
                case "matchmaking" -> {
                    AutismModule global = AutismModule.get();
                    if (global != null) {
                        autismclient.util.AutismMatchmakingOverlay mm = global.getMatchmakingOverlay();
                        if (mm != null) {
                            AutismOverlayManager.get().register(mm);
                            mm.setMainMenuMode(true);
                            mm.setVisible(true);
                            if (minecraft != null) minecraft.gui.setScreen(new AutismOverlayHostScreen(mm, this, true));
                        }
                    }
                }
                case "multi" -> {
                    if (minecraft != null) {
                        autismclient.gui.screen.AutismMultiDisclaimerScreen.open(
                            minecraft, this, () -> minecraft.gui.setScreen(new AutismMultiScreen(this, "")));
                    }
                }
                default -> {
                }
            }
            return;
        }
        AutismModule global = AutismModule.get();
        if (global == null) return;
        switch (id) {
            case "admin" -> toggleOverlay(utilityAdminToolsOverlay);
            case "lan" -> toggleOverlay(utilityLanSyncOverlay);
            case "queue" -> toggleOverlay(utilityQueueEditorOverlay);
            case "logger" -> {
                AutismPacketLoggerOverlay logger = global.getPacketLoggerOverlay();
                if (logger != null) {
                    logger.restoreLayout();
                    AutismOverlayManager.get().register(logger);
                    toggleOverlay(logger);
                }
            }
            case "packets" -> toggleOverlay(utilityCustomFilterOverlay);
            case "server" -> {
                AutismServerInfoOverlay serverInfo = global.getServerDataOverlay();
                if (serverInfo != null) {
                    AutismOverlayManager.get().register(serverInfo);
                    toggleOverlay(serverInfo);
                }
            }
            case "matchmaking" -> {
                autismclient.util.AutismMatchmakingOverlay matchmaking = global.getMatchmakingOverlay();
                if (matchmaking != null) {
                    AutismOverlayManager.get().register(matchmaking);
                    matchmaking.setMainMenuMode(false);
                    toggleOverlay(matchmaking);
                }
            }
            case "multi" -> global.toggleMultiUiBehavior();
            case "send" -> {
                boolean newValue = !AutismSharedState.get().shouldSendGuiPackets();
                global.applySendGuiPacketsUiBehavior(newValue);
                autismclient.util.AutismNotifications.show("Send Packets " + (newValue ? "on" : "off"), newValue ? 0xFF35D873 : 0xFFFF3B3B);
            }
            case "delay" -> {
                boolean newValue = !AutismSharedState.get().shouldDelayGuiPackets();
                int sent = global.applyDelayGuiPacketsUiBehavior(newValue);
                global.notifyDelayPacketsUiResult(newValue, sent);
            }
            case "flush" -> {
                int count = global.flushQueuedPacketsUiBehavior();
                global.notifyFlushQueuedPacketsUiResult(count);
            }
            case "clear" -> {
                int count = global.clearQueuedPacketsUiBehavior();
                global.notifyClearQueuedPacketsUiResult(count);
            }
            default -> {
            }
        }
    }

    private void toggleMacroPanel() {
        AutismMacroEditorOverlay editor = AutismMacroEditorOverlay.getSharedOverlay();
        if (editor != null) AutismOverlayManager.get().register(editor);
        if (editor != null && editor.isVisible()) {
            if (utilityMacroListOverlay != null) utilityMacroListOverlay.setVisible(false);
            AutismOverlayManager.get().bringToFront(editor);
            return;
        }
        toggleOverlay(utilityMacroListOverlay);
    }

    private void toggleOverlay(IAutismOverlay overlay) {
        if (overlay == null) return;
        AutismOverlayManager.get().register(overlay);
        overlay.setVisible(!overlay.isVisible());
        if (overlay.isVisible()) AutismOverlayManager.get().bringToFront(overlay);
    }

    private AutismFabricatorOverlay utilityFabricatorOverlay() {
        if (!(parent instanceof AbstractContainerScreen<?> handledScreen)) return null;
        utilityFabricatorOverlay = AutismFabricatorOverlay.getSharedOverlay(handledScreen);
        utilityFabricatorOverlay.restoreState();
        AutismOverlayManager.get().register(utilityFabricatorOverlay);
        return utilityFabricatorOverlay;
    }

    private void addQuickToggleMacroStep(Module module) {
        if (module == null) return;
        AutismMacroEditorOverlay editor = AutismMacroEditorOverlay.getSharedOverlay();
        if (editor == null) return;
        editor.setConfigurationOnly(isTitleSetup());
        AutismOverlayManager manager = AutismOverlayManager.get();
        manager.register(editor);
        manager.setTemporarilyHidden(editor, false);
        if (!editor.isVisible() || AutismSharedState.get().getEditingMacro() == null) {
            editor.open(null, true);
        } else {
            editor.setVisible(true);
            manager.bringToFront(editor);
        }
        if (utilityMacroListOverlay != null) utilityMacroListOverlay.setVisible(false);
        editor.addAction(new ToggleModuleAction(module.name()));
    }

    private void resetConfigurationOnlyOverlays() {
        if (utilityMacroListOverlay != null) utilityMacroListOverlay.setConfigurationOnly(false);
        if (utilityLanSyncOverlay != null) utilityLanSyncOverlay.setConfigurationOnly(false);
        if (utilityQueueEditorOverlay != null) utilityQueueEditorOverlay.setConfigurationOnly(false);
        autismclient.gui.macro.editor.ActionEditorOverlay actionEditor = autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
        if (actionEditor != null) actionEditor.setConfigurationOnly(false);
        AutismModule global = AutismModule.get();
        if (global != null) {
            AutismPacketLoggerOverlay logger = global.getPacketLoggerOverlayIfExists();
            if (logger != null) logger.setConfigurationOnly(false);
            AutismServerInfoOverlay serverInfo = global.getServerDataOverlayIfExists();
            if (serverInfo != null) serverInfo.setConfigurationOnly(false);
        }
        AutismMacroEditorOverlay editor = AutismMacroEditorOverlay.getSharedOverlay();
        if (editor != null) editor.setConfigurationOnly(false);
    }

    private void saveUtilityOverlayStates() {
        saveOverlayState(utilityMacroListOverlay);
        saveOverlayState(utilityFabricatorOverlay);
        saveOverlayState(utilityLanSyncOverlay);
        saveOverlayState(utilityQueueEditorOverlay);
        saveOverlayState(utilityCustomFilterOverlay);
        saveOverlayState(utilityKeybindOverlay);
        saveOverlayState(utilityAdminToolsOverlay);
        AutismModule global = AutismModule.get();
        if (global != null) {
            saveOverlayState(global.getPacketLoggerOverlayIfExists());
            saveOverlayState(global.getServerDataOverlayIfExists());
        }
        saveOverlayState(AutismMacroEditorOverlay.getSharedOverlay());
        saveOverlayState(autismclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay());
        if (utilityCustomFilterOverlay != null) saveOverlayState(utilityCustomFilterOverlay.getPresetManagerOverlay());
    }

    private void saveOverlayState(IAutismOverlay overlay) {
        if (overlay == null) return;
        if (overlay instanceof AutismMacroListOverlay macroList) macroList.saveState();
        else if (overlay instanceof AutismFabricatorOverlay fabricator) fabricator.saveState();
        else if (overlay instanceof AutismLANSyncOverlay lanSync) lanSync.saveState();
        else if (overlay instanceof AutismQueueEditorOverlay queue) queue.saveState();
        else if (overlay instanceof AutismCustomFilterOverlay filter) filter.saveLayout();
        else if (overlay instanceof AutismKeybindOverlay keybind) keybind.saveLayout();
        else if (overlay instanceof AutismPacketLoggerOverlay logger) logger.saveState();
        else if (overlay instanceof AutismServerInfoOverlay serverInfo) serverInfo.saveState();
        else if (overlay instanceof AutismMacroEditorOverlay macroEditor) macroEditor.saveState();
        else overlay.saveLayout();
    }

    private boolean passMovementKey(KeyEvent input, boolean down) {
        if (blocksGlobalKeybinds()) return false;
        if (minecraft == null || minecraft.options == null) return false;
        KeyMapping[] movement = {
            minecraft.options.keyUp,
            minecraft.options.keyDown,
            minecraft.options.keyLeft,
            minecraft.options.keyRight,
            minecraft.options.keyJump,
            minecraft.options.keyShift,
            minecraft.options.keySprint
        };
        for (KeyMapping key : movement) {
            if (key != null && key.matches(input)) {
                key.setDown(down);
                return true;
            }
        }
        return false;
    }

    private final class ModuleMenuHost implements VanillaModuleMenuController.Host {
        @Override
        public boolean offlineSetup() {
            return isTitleSetup();
        }

        @Override
        public Screen screen() {
            return AutismModuleScreen.this;
        }

        @Override
        public Font font() {
            return AutismModuleScreen.this.font;
        }

        @Override
        public void closeMenu() {
            AutismModuleScreen.this.onClose();
        }

        @Override
        public void saveConfig() {
            AutismConfig.getGlobal().save();
        }

        @Override
        public void openPacketSelector(Module module, Setting<?, ?> option) {
            AutismModuleScreen.this.openPacketSelector(module, option);
        }

        @Override
        public void openStringListEditor(Module module, Setting<?, ?> option) {
            if (minecraft != null) {
                returnSettingsModuleId = module == null ? null : module.id();
                minecraft.gui.setScreen(new AutismStringListSettingScreen(AutismModuleScreen.this, module, option));
            }
        }

        @Override
        public void openRegistryListEditor(Module module, Setting<?, ?> option) {
            if (minecraft != null) {
                returnSettingsModuleId = module == null ? null : module.id();
                minecraft.gui.setScreen(new AutismRegistryListSettingScreen(AutismModuleScreen.this, module, option));
            }
        }

        @Override
        public void openMacroCreator(Module module, Setting<?, ?> option) {
            openMacroCreator(module, option, null);
        }

        @Override
        public void openMacroCreator(Module module, Setting<?, ?> option, autismclient.util.AutismMacro macro) {
            AutismMacroEditorOverlay editor = AutismMacroEditorOverlay.getSharedOverlay();
            if (editor == null) return;
            editor.setConfigurationOnly(isTitleSetup());
            AutismOverlayManager manager = AutismOverlayManager.get();
            manager.register(editor);
            manager.setTemporarilyHidden(editor, false);
            editor.open(macro, true);
            editor.setVisible(true);
            manager.bringToFront(editor);
            if (utilityMacroListOverlay != null) utilityMacroListOverlay.setVisible(false);
        }

        @Override
        public void runUtility(String id) {
            AutismModuleScreen.this.runUtility(id);
        }

        @Override
        public void addToggleMacro(Module module) {
            AutismModuleScreen.this.addQuickToggleMacroStep(module);
        }
    }
}
