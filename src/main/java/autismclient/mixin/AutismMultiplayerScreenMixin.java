package autismclient.mixin;

import autismclient.ducks.AutismExternalButtonScreen;
import autismclient.gui.screen.AutismAccountsScreen;
import autismclient.gui.screen.AutismJoinMacroScreen;
import autismclient.gui.screen.AutismMultiConsoleScreen;
import autismclient.gui.screen.AutismMultiDisclaimerScreen;
import autismclient.gui.screen.AutismMultiScreen;
import autismclient.gui.screen.AutismProxiesScreen;
import autismclient.gui.screen.AutismVoiceChatPromptScreen;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.util.AutismConfig;
import autismclient.util.AutismProxy;
import autismclient.util.AutismProxyManager;
import autismclient.util.AutismJoinMacroController;
import autismclient.util.AutismMacroManager;
import autismclient.util.multi.MultiManager;
import java.util.Locale;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JoinMultiplayerScreen.class, priority = 2000)
public abstract class AutismMultiplayerScreenMixin extends Screen implements AutismExternalButtonScreen {
    @Unique private static final int BUTTON_HEIGHT = 20;
    @Unique private static final int BUTTON_WIDTH = 60;
    @Unique private static final int MACRO_BUTTON_WIDTH = 50;
    @Unique private static final int MULTI_BUTTON_WIDTH = 50;
    @Unique private static final int STACK_WIDTH = 104;
    @Unique private static final int MARGIN = 4;
    @Unique private static final int GAP = 3;
    @Unique private static final int EXTERNAL_NONE = 0;
    @Unique private static final int EXTERNAL_VIA_FABRIC_PLUS = 1;
    @Unique private static final int EXTERNAL_REPLAY_RECORD = 2;
    @Unique private static final int EXTERNAL_OPSEC = 3;

    @Unique private Button autism$accountsButton;
    @Unique private Button autism$joinMacroButton;
    @Unique private Button autism$multiButton;
    @Unique private Button autism$proxiesButton;
    @Unique private Button autism$spoofButton;
    @Unique private Button autism$packsButton;
    @Unique private Button autism$libraryButton;
    @Unique private Button autism$recordButton;
    @Unique private boolean autism$voicePromptChecked;
    @Unique private boolean autism$meteorUiConfigSuppressed;
    @Unique private int autism$topButtonsLeft = MARGIN;

    @Shadow protected ServerSelectionList serverSelectionList;

    protected AutismMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void autism$repositionElements(CallbackInfo ci) {
        autism$layoutButtons();
        autism$maybeShowVoiceChatPrompt();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void autism$refreshMultiButton(CallbackInfo ci) {
        if (autism$multiButton == null) return;
        String label = autism$multiLabel();
        if (!label.equals(autism$multiButton.getMessage().getString())) {
            autism$multiButton.setMessage(Component.literal(label));
        }
    }

    @Unique
    private static String autism$multiLabel() {
        MultiManager manager = MultiManager.get();
        return manager.isActive() ? "Multi " + manager.readyFraction() : "Multi";
    }

    @Unique
    private void autism$maybeShowVoiceChatPrompt() {
        if (autism$voicePromptChecked) return;
        autism$voicePromptChecked = true;
        if (PackHideState.isActive()) return;
        if (!FabricLoader.getInstance().isModLoaded("voicechat")) return;
        AutismModule module = AutismModule.get();
        if (module == null || !module.isSpoofClientVanilla()) return;
        AutismConfig config = AutismConfig.getGlobal();
        if (config == null || config.voiceChatModdedPromptShown) return;
        config.voiceChatModdedPromptShown = true;
        config.save();
        Screen parent = this;

        this.minecraft.execute(() -> {
            if (this.minecraft.gui.screen() == parent) {
                this.minecraft.gui.setScreen(new AutismVoiceChatPromptScreen(parent));
            }
        });
    }

    @Unique
    private void autism$layoutButtons() {
        autism$suppressMeteorWidgets();
        AbstractWidget via = null;
        AbstractWidget opsec = null;
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof AbstractWidget widget) || autism$isOwned(widget)) continue;
            int kind = autism$externalKind(widget);
            if (kind == EXTERNAL_REPLAY_RECORD) {
                autism$setVisible(widget, false);
            } else if (kind == EXTERNAL_VIA_FABRIC_PLUS) {
                via = widget;
            } else if (kind == EXTERNAL_OPSEC) {
                opsec = widget;
            }
        }

        boolean hidden = PackHideState.isActive();
        if (hidden) {
            autism$setVisible(autism$accountsButton, false);
            autism$setVisible(autism$joinMacroButton, false);
            autism$setVisible(autism$multiButton, false);
            autism$setVisible(autism$proxiesButton, false);
            autism$setVisible(autism$spoofButton, false);
            autism$setVisible(autism$packsButton, false);
            autism$setVisible(autism$libraryButton, false);
            autism$setVisible(autism$recordButton, false);
            autism$setVisible(via, false);
            autism$setVisible(opsec, false);
            return;
        }

        autism$ensureOwnedButtons();
        int topRight = this.width - MARGIN;
        int topAvailable = Math.max(4, this.width - MARGIN * 2);

        int topCell = Math.max(1, (topAvailable - GAP * 4) / 5);
        int accountsW = Math.min(BUTTON_WIDTH, topCell);
        int proxiesW = Math.min(BUTTON_WIDTH, topCell);
        int libraryW = Math.min(BUTTON_WIDTH, topCell);
        int macroW = Math.min(MACRO_BUTTON_WIDTH, topCell);
        int multiW = Math.min(MULTI_BUTTON_WIDTH, topCell);
        int cursor = topRight;
        cursor -= accountsW;
        autism$place(autism$accountsButton, cursor, MARGIN, accountsW, BUTTON_HEIGHT);
        cursor -= GAP + proxiesW;
        autism$place(autism$proxiesButton, cursor, MARGIN, proxiesW, BUTTON_HEIGHT);
        cursor -= GAP + libraryW;
        autism$libraryButton.setMessage(autism$fitLabel(libraryW, "Library"));
        autism$place(autism$libraryButton, cursor, MARGIN, libraryW, BUTTON_HEIGHT);
        cursor -= GAP + macroW;
        autism$place(autism$joinMacroButton, cursor, MARGIN, macroW, BUTTON_HEIGHT);
        cursor -= GAP + multiW;
        autism$place(autism$multiButton, cursor, MARGIN, multiW, BUTTON_HEIGHT);
        autism$topButtonsLeft = Math.max(MARGIN, cursor);
        String multiLabel = autism$multiLabel();
        if (!multiLabel.equals(autism$multiButton.getMessage().getString())) {
            autism$multiButton.setMessage(Component.literal(multiLabel));
        }

        int footerRight = (this.width / 2) - 154 - GAP;
        int footerWidth = Math.max(60, Math.min(STACK_WIDTH, footerRight - MARGIN));
        int footerX = Math.max(MARGIN, footerRight - footerWidth);
        int footerY = Math.max(MARGIN, this.height - (BUTTON_HEIGHT * 2 + GAP + 8));
        autism$spoofButton.setMessage(autism$spoofClientLabel(footerWidth));
        autism$packsButton.setMessage(autism$bypassPacksLabel(footerWidth));
        autism$place(autism$spoofButton, footerX, footerY, footerWidth, BUTTON_HEIGHT);
        autism$place(autism$packsButton, footerX, footerY + BUTTON_HEIGHT + GAP, footerWidth, BUTTON_HEIGHT);

        int rightX = Math.min(this.width - MARGIN - STACK_WIDTH, (this.width / 2) + 154 + GAP);
        int rightWidth = Math.max(60, Math.min(STACK_WIDTH, this.width - MARGIN - rightX));
        int count = (FabricLoader.getInstance().isModLoaded("replaymod") ? 1 : 0) + (via != null ? 1 : 0) + (opsec != null ? 1 : 0);
        int rightY = Math.max(MARGIN, this.height - 8 - Math.max(0, count * BUTTON_HEIGHT + Math.max(0, count - 1) * GAP));
        int slot = 0;

        if (FabricLoader.getInstance().isModLoaded("replaymod")) {
            autism$recordButton.setMessage(autism$replayServerLabel(rightWidth));
            autism$place(autism$recordButton, rightX, rightY + slot++ * (BUTTON_HEIGHT + GAP), rightWidth, BUTTON_HEIGHT);
        } else {
            autism$setVisible(autism$recordButton, false);
        }
        if (via != null) {
            autism$place(via, rightX, rightY + slot++ * (BUTTON_HEIGHT + GAP), rightWidth, BUTTON_HEIGHT);
        }
        if (opsec != null) {
            autism$place(opsec, rightX, rightY + slot * (BUTTON_HEIGHT + GAP), rightWidth, BUTTON_HEIGHT);
        }
    }

    @Unique
    private void autism$ensureOwnedButtons() {
        if (autism$accountsButton == null) {
            autism$accountsButton = this.addRenderableWidget(Button.builder(Component.literal("Accounts"),
                ignored -> this.minecraft.gui.setScreen(new autismclient.gui.screen.AutismAccountsScreen(this))).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (autism$joinMacroButton == null) {
            autism$joinMacroButton = this.addRenderableWidget(Button.builder(Component.literal("Macro"),
                ignored -> this.minecraft.gui.setScreen(new AutismJoinMacroScreen(this))).bounds(0, 0, MACRO_BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (autism$multiButton == null) {
            autism$multiButton = this.addRenderableWidget(Button.builder(Component.literal("Multi"),
                ignored -> autism$openMulti()).bounds(0, 0, MULTI_BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (autism$proxiesButton == null) {
            autism$proxiesButton = this.addRenderableWidget(Button.builder(Component.literal("Proxies"),
                ignored -> this.minecraft.gui.setScreen(new autismclient.gui.screen.AutismProxiesScreen(this))).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (autism$spoofButton == null) {
            autism$spoofButton = this.addRenderableWidget(Button.builder(Component.literal("Client"),
                ignored -> {
                    AutismModule module = AutismModule.get();
                    if (module != null) module.setSpoofClientVanilla(!module.isSpoofClientVanilla());
                    autism$layoutButtons();
                }).bounds(0, 0, STACK_WIDTH, BUTTON_HEIGHT).build());
        }
        if (autism$packsButton == null) {
            autism$packsButton = this.addRenderableWidget(Button.builder(Component.literal("Packs"),
                ignored -> {
                    AutismModule module = AutismModule.get();
                    if (module != null) module.setBypassResourcePack(!module.isBypassResourcePack());
                    autism$layoutButtons();
                }).bounds(0, 0, STACK_WIDTH, BUTTON_HEIGHT).build());
        }
        if (autism$libraryButton == null) {
            autism$libraryButton = this.addRenderableWidget(Button.builder(Component.literal("Library"),
                ignored -> autism$openPluginLibrary()).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (autism$recordButton == null) {
            autism$recordButton = this.addRenderableWidget(Button.builder(Component.literal("Replay"),
                ignored -> {
                    autism$toggleReplayServerRecording();
                    autism$layoutButtons();
                }).bounds(0, 0, STACK_WIDTH, BUTTON_HEIGHT).build());
        }
    }

    @Unique
    private void autism$openPluginLibrary() {
        this.minecraft.gui.setScreen(new autismclient.gui.screen.AutismPluginLibraryScreen(this));
    }

    @Unique
    private void autism$openMulti() {
        net.minecraft.client.multiplayer.ServerData selected = autism$selectedServerData();
        String address = selected == null ? "" : selected.ip;
        Runnable proceed = () -> {
            if (MultiManager.get().isActive()) {
                this.minecraft.gui.setScreen(new AutismMultiConsoleScreen(this));
            } else {
                MultiManager.get().rememberSelectedServer(selected);
                this.minecraft.gui.setScreen(new AutismMultiScreen(this, address));
            }
        };
        AutismMultiDisclaimerScreen.open(this.minecraft, this, proceed);
    }

    @Unique
    private boolean autism$isOwned(AbstractWidget widget) {
        return widget == autism$accountsButton || widget == autism$joinMacroButton || widget == autism$multiButton
            || widget == autism$proxiesButton || widget == autism$spoofButton
            || widget == autism$packsButton || widget == autism$libraryButton || widget == autism$recordButton;
    }

    @Unique
    private net.minecraft.client.multiplayer.ServerData autism$selectedServerData() {
        if (serverSelectionList == null) return null;
        ServerSelectionList.Entry selected = serverSelectionList.getSelected();
        if (selected instanceof ServerSelectionList.OnlineServerEntry online) {
            return online.getServerData();
        }
        return null;
    }

    @Unique
    private static void autism$place(AbstractWidget widget, int x, int y, int width, int height) {
        if (widget == null) return;
        widget.setX(Math.max(MARGIN, x));
        widget.setY(Math.max(MARGIN, y));
        widget.setSize(Math.max(1, width), Math.max(1, height));
        autism$setVisible(widget, true);
    }

    @Unique
    private static void autism$setVisible(AbstractWidget widget, boolean visible) {
        if (widget == null) return;
        widget.visible = visible;
        widget.active = visible;
    }

    @Unique
    private Component autism$spoofClientLabel(int width) {
        AutismModule module = AutismModule.get();
        boolean enabled = module != null && module.isSpoofClientVanilla();
        return autism$fitLabel(width, enabled ? "Client: Vanilla" : "Client: Modded", enabled ? "Vanilla" : "Modded", "Client");
    }

    @Unique
    private Component autism$bypassPacksLabel(int width) {
        AutismModule module = AutismModule.get();
        boolean enabled = module != null && module.isBypassResourcePack();
        return autism$fitLabel(width, enabled ? "Packs: Bypass" : "Packs: Normal", enabled ? "Bypass" : "Normal", "Packs");
    }

    @Unique
    private Component autism$replayServerLabel(int width) {
        boolean enabled = autism$getReplayBoolean("RECORD_SERVER", true);
        return autism$fitLabel(width, enabled ? "Replay: On" : "Replay: Off", enabled ? "Rec: On" : "Rec: Off", "Replay");
    }

    @Unique
    private Component autism$fitLabel(int width, String... candidates) {
        int available = Math.max(1, width - 8);
        for (String candidate : candidates) {
            if (this.font.width(candidate) <= available) return Component.literal(candidate);
        }
        return Component.literal(candidates[candidates.length - 1]);
    }

    @Unique
    private int autism$externalKind(AbstractWidget widget) {
        String label = widget.getMessage().getString();
        String normalized = label.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace(".", "");
        String className = widget.getClass().getName().toLowerCase(Locale.ROOT);
        if (FabricLoader.getInstance().isModLoaded("viafabricplus") && "ViaFabricPlus".equals(label)) return EXTERNAL_VIA_FABRIC_PLUS;
        if (FabricLoader.getInstance().isModLoaded("replaymod")
            && (className.contains("replaymod") || normalized.contains("recordserver") || normalized.contains("replaymodguisettingsrecordserver"))) {
            return EXTERNAL_REPLAY_RECORD;
        }
        if (className.contains("opsec") || normalized.contains("opsec")) return EXTERNAL_OPSEC;
        return EXTERNAL_NONE;
    }

    @Unique
    private void autism$suppressMeteorWidgets() {
        if (!FabricLoader.getInstance().isModLoaded("meteor-client")) return;
        if (!autism$meteorUiConfigSuppressed) {
            autism$meteorUiConfigSuppressed = true;
            autism$disableMeteorMultiplayerUiConfig();
        }
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof Button button) || autism$isOwned(button)) continue;
            String label = button.getMessage().getString();
            if ("Accounts".equals(label) || "Proxies".equals(label)) autism$setVisible(button, false);
        }
    }

    @Unique
    private void autism$toggleReplayServerRecording() {
        boolean enabled = autism$getReplayBoolean("RECORD_SERVER", true);
        autism$setReplayBoolean("RECORD_SERVER", !enabled);
    }

    @Unique
    private static boolean autism$getReplayBoolean(String settingField, boolean fallback) {
        try {
            Object settings = autism$replaySettingsRegistry();
            Object key = Class.forName("com.replaymod.recording.Setting").getField(settingField).get(null);
            Object value = settings.getClass().getMethod("get", Class.forName("com.replaymod.core.SettingsRegistry$SettingKey")).invoke(settings, key);
            return value instanceof Boolean bool ? bool : fallback;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return fallback;
        }
    }

    @Unique
    private static void autism$setReplayBoolean(String settingField, boolean value) {
        try {
            Object settings = autism$replaySettingsRegistry();
            Object key = Class.forName("com.replaymod.recording.Setting").getField(settingField).get(null);
            Class<?> settingKeyClass = Class.forName("com.replaymod.core.SettingsRegistry$SettingKey");
            settings.getClass().getMethod("set", settingKeyClass, Object.class).invoke(settings, key, value);
            settings.getClass().getMethod("save").invoke(settings);
        } catch (ReflectiveOperationException | LinkageError ignored) {  }
    }

    @Unique
    private static Object autism$replaySettingsRegistry() throws ReflectiveOperationException {
        Object replayMod = Class.forName("com.replaymod.core.ReplayMod").getField("instance").get(null);
        return replayMod.getClass().getMethod("getSettingsRegistry").invoke(replayMod);
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void autism$disableMeteorMultiplayerUiConfig() {
        try {
            Class<?> configClass = Class.forName("meteordevelopment.meteorclient.systems.config.Config");
            Object config = configClass.getMethod("get").invoke(null);
            Class<?> buttonPositionClass = Class.forName("meteordevelopment.meteorclient.systems.config.Config$ButtonPosition");
            Object hidden = Enum.valueOf((Class<? extends Enum>) buttonPositionClass.asSubclass(Enum.class), "Hidden");
            autism$setMeteorSetting(configClass.getField("accountButtonAnchor").get(config), hidden);
            autism$setMeteorSetting(configClass.getField("proxiesButtonAnchor").get(config), hidden);
            autism$setMeteorSetting(configClass.getField("showAccountStatus").get(config), false);
            autism$setMeteorSetting(configClass.getField("showProxiesStatus").get(config), false);
        } catch (ReflectiveOperationException ignored) {  }
    }

    @Unique
    private static void autism$setMeteorSetting(Object setting, Object value) throws ReflectiveOperationException {
        setting.getClass().getSuperclass().getMethod("set", Object.class).invoke(setting, value);
    }

    @Override
    public void autism$renderExternalButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        autism$layoutButtons();
        if (PackHideState.isActive()) return;
        int leftTextWidth = Math.max(0, autism$topButtonsLeft - MARGIN - GAP);
        String username = this.minecraft.getUser().getName();
        if (leftTextWidth > 8) {
            graphics.text(this.font, autism$fitPlain("Logged in as " + username, leftTextWidth), MARGIN, MARGIN, 0xFFFFFFFF, false);
        }
        AutismProxy proxy = AutismProxyManager.get().getEnabled();
        int statusY = MARGIN + 12;
        if (proxy != null) {
            String proxyLabel = "Using proxy " + proxy.address + ":" + proxy.port;
            if (leftTextWidth > 8) graphics.text(this.font, autism$fitPlain(proxyLabel, leftTextWidth), MARGIN, statusY, 0xFFAFAFAF, false);
            statusY += 12;
        }
        String macroName = AutismJoinMacroController.selectedMacroName();
        String macroLabel;
        int macroColor;
        if (macroName.isBlank()) {
            macroLabel = "Join Macro: none";
            macroColor = 0xFF8F8A8A;
        } else if (AutismMacroManager.get().get(macroName) == null) {
            macroLabel = "Join Macro missing: " + macroName;
            macroColor = 0xFFFF6B6B;
        } else {
            macroLabel = "Join Macro: " + macroName + " - " + AutismJoinMacroController.modeSummary();
            macroColor = 0xFF66E08A;
        }
        if (leftTextWidth > 8) graphics.text(this.font, autism$fitPlain(macroLabel, leftTextWidth), MARGIN, statusY, macroColor, false);
    }

    @Unique
    private String autism$fitPlain(String label, int maxWidth) {
        if (label == null) return "";
        if (this.font.width(label) <= maxWidth) return label;
        return this.font.plainSubstrByWidth(label, Math.max(1, maxWidth - 4));
    }

}
