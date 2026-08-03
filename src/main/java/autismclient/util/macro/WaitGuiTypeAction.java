package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitGuiTypeAction implements MacroAction, MacroCaptureOutput {
    public enum WaitMode { OPEN, CLOSE }

    public WaitMode waitMode = WaitMode.OPEN;
    public String guiType = "ANY";
    public String title = "";
    public MacroCapturePattern.Mode matchMode = MacroCapturePattern.Mode.MATCH;
    public String saveAs = "";
    public int timeoutMs = 0;
    public boolean listenDuringPreviousAction = false;

    @Override public void execute(Minecraft mc) {}
    @Override public MacroActionType getType() { return MacroActionType.WAIT_GUI_TYPE; }

    public boolean matches(Minecraft mc) {
        return capture(mc).isPresent();
    }

    public java.util.Optional<java.util.Map<String, MacroValue>> capture(Minecraft mc) {
        if (mc == null || !MacroGuiMatcher.matches(mc.gui.screen(), guiType, "")) return java.util.Optional.empty();
        var screen = mc.gui.screen();
        String actual = screen.getTitle() == null ? "" : screen.getTitle().getString();
        java.util.Map<String, MacroValue> values = new java.util.LinkedHashMap<>();
        if (matchMode == MacroCapturePattern.Mode.MATCH) {
            MacroTemplate.Resolution resolved = MacroVariables.resolve(title, mc);
            if (!resolved.success()) return java.util.Optional.empty();
            if (!resolved.value().isBlank() && !MacroGuiMatcher.matches(screen, guiType, resolved.value())) return java.util.Optional.empty();
        } else {
            var result = MacroCapturePattern.match(matchMode, title, actual);
            if (result.isEmpty()) return java.util.Optional.empty();
            values.putAll(result.get().values());
        }
        if (saveAs != null && !saveAs.isBlank()) {
            values.put(saveAs, MacroValue.structured(MacroValue.Kind.GUI, actual, java.util.Map.of(
                "title", MacroValue.text(actual), "type", MacroValue.text(MacroGuiMatcher.semanticName(screen)))));
        }
        return java.util.Optional.of(values);
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "WAIT_GUI_TYPE");
        tag.putString("waitMode", waitMode.name());
        tag.putString("guiType", guiType);
        tag.putString("title", title);
        tag.putString("matchMode", (matchMode == null ? MacroCapturePattern.Mode.MATCH : matchMode).name());
        tag.putString("saveAs", saveAs == null ? "" : saveAs);
        tag.putInt("timeoutMs", timeoutMs);
        MacroWaitOptions.write(tag, this);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        waitMode = MacroStringList.enumValue(WaitMode.class, tag.getStringOr("waitMode", "OPEN"), WaitMode.OPEN);
        guiType = tag.getStringOr("guiType", "ANY");
        title = tag.getStringOr("title", "");
        matchMode = MacroStringList.enumValue(MacroCapturePattern.Mode.class, tag.getStringOr("matchMode", "MATCH"), MacroCapturePattern.Mode.MATCH);
        saveAs = tag.getStringOr("saveAs", "");
        timeoutMs = tag.getIntOr("timeoutMs", 0);
        MacroWaitOptions.read(tag, this);
    }

    @Override public String getDisplayName() { return "Wait GUI " + guiType; }
    @Override public String getIcon() { return "W"; }
    @Override public String getSaveAs() { return saveAs; }
    @Override public void setSaveAs(String value) { saveAs = value == null ? "" : value; }
}
