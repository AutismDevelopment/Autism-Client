package autismclient.mixin;

import autismclient.ducks.AutismExternalButtonScreen;
import autismclient.gui.screen.AutismJoinMacroScreen;
import autismclient.modules.PackHideState;
import autismclient.util.AutismJoinMacroController;
import autismclient.util.AutismMacroManager;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SelectWorldScreen.class, priority = 2000)
public abstract class AutismSelectWorldScreenMixin extends Screen implements AutismExternalButtonScreen {
    @Unique private static final int BUTTON_HEIGHT = 20;
    @Unique private static final int BUTTON_WIDTH = 104;
    @Unique private static final int MACRO_BUTTON_WIDTH = 50;
    @Unique private static final int BUTTON_GAP = 3;
    @Unique private Button autism$macroButton;
    @Unique private Button autism$recordButton;

    protected AutismSelectWorldScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void autism$repositionElements(CallbackInfo ci) {
        autism$layoutReplayButton();
    }

    @Unique
    private void autism$layoutReplayButton() {
        AbstractWidget back = null;
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof AbstractWidget widget)) continue;
            if (widget == autism$recordButton || widget == autism$macroButton) continue;
            if (autism$isReplayRecordButton(widget)) {
                widget.visible = false;
                widget.active = false;
                continue;
            }
            if (autism$isBackButton(widget)) back = widget;
        }

        if (PackHideState.isActive()) {
            if (autism$macroButton != null) {
                autism$macroButton.visible = false;
                autism$macroButton.active = false;
            }
            if (autism$recordButton != null) {
                autism$recordButton.visible = false;
                autism$recordButton.active = false;
            }
            return;
        }

        if (autism$macroButton == null) {
            autism$macroButton = this.addRenderableWidget(Button.builder(Component.literal("Macro"),
                ignored -> this.minecraft.gui.setScreen(new AutismJoinMacroScreen(this))).bounds(0, 0, MACRO_BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }

        if (autism$recordButton == null) {
            autism$recordButton = this.addRenderableWidget(Button.builder(autism$replaySingleplayerLabel(), ignored -> {
                autism$toggleReplaySingleplayerRecording();
                autism$recordButton.setMessage(autism$replaySingleplayerLabel());
            }).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }

        int macroX = back != null ? back.getRight() + BUTTON_GAP : (this.width / 2) + 104;
        int y = back != null ? back.getY() : this.height - 28;
        if (macroX + MACRO_BUTTON_WIDTH > this.width - 4) {
            macroX = Math.max(4, this.width - 4 - MACRO_BUTTON_WIDTH);
            y = Math.max(4, y - BUTTON_HEIGHT - BUTTON_GAP);
        }
        autism$macroButton.setX(macroX);
        autism$macroButton.setY(y);
        autism$macroButton.setSize(MACRO_BUTTON_WIDTH, BUTTON_HEIGHT);
        autism$macroButton.visible = true;
        autism$macroButton.active = true;

        if (!FabricLoader.getInstance().isModLoaded("replaymod")) {
            autism$recordButton.visible = false;
            autism$recordButton.active = false;
            return;
        }

        int width = Math.min(BUTTON_WIDTH, Math.max(60, this.width - 8));
        int recordX = macroX + MACRO_BUTTON_WIDTH + BUTTON_GAP;
        int recordY = y;
        if (recordX + width > this.width - 4) {
            recordX = Math.max(4, this.width - 4 - width);
            recordY = Math.max(4, y - BUTTON_HEIGHT - BUTTON_GAP);
        }
        autism$recordButton.setX(recordX);
        autism$recordButton.setY(recordY);
        autism$recordButton.setSize(width, BUTTON_HEIGHT);
        autism$recordButton.setMessage(autism$replaySingleplayerLabel());
        autism$recordButton.visible = true;
        autism$recordButton.active = true;
    }

    @Unique
    private boolean autism$isBackButton(AbstractWidget widget) {
        String label = widget.getMessage().getString();
        return label.equals(Component.translatable("gui.back").getString()) || "back".equalsIgnoreCase(label);
    }

    @Unique
    private boolean autism$isReplayRecordButton(AbstractWidget widget) {
        if (!FabricLoader.getInstance().isModLoaded("replaymod")) return false;
        String className = widget.getClass().getName().toLowerCase(Locale.ROOT);
        String normalizedLabel = widget.getMessage().getString().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace(".", "");
        return className.contains("replaymod")
            || normalizedLabel.contains("recordsingleplayer")
            || normalizedLabel.contains("replaymodguisettingsrecordsingleplayer");
    }

    @Unique
    private Component autism$replaySingleplayerLabel() {
        boolean enabled = autism$getReplayBoolean("RECORD_SINGLEPLAYER", true);
        return Component.literal(enabled ? "Replay: On" : "Replay: Off");
    }

    @Unique
    private void autism$toggleReplaySingleplayerRecording() {
        boolean enabled = autism$getReplayBoolean("RECORD_SINGLEPLAYER", true);
        autism$setReplayBoolean("RECORD_SINGLEPLAYER", !enabled);
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

    @Override
    public void autism$renderExternalButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        autism$layoutReplayButton();
        if (PackHideState.isActive()) return;

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
        graphics.text(this.font, autism$fitPlain(macroLabel, 220), 4, 4, macroColor, false);
    }

    @Unique
    private String autism$fitPlain(String label, int maxWidth) {
        if (label == null) return "";
        if (this.font.width(label) <= maxWidth) return label;
        return this.font.plainSubstrByWidth(label, Math.max(1, maxWidth - 4));
    }
}
