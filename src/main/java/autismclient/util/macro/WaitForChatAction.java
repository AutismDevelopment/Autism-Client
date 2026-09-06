package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitForChatAction implements MacroAction, WaitsForGui {
    public String pattern = "";
    public String patternJson = "";
    public boolean useRegex = false;
    public MacroCapturePattern.Mode matchMode = MacroCapturePattern.Mode.MATCH;
    public String saveAs = "";
    public int fuzzyPercent = 100;
    public boolean serverMessageOnly = false;
    public int timeoutMs = 0;
    private boolean enabled = true;
    public boolean waitForGuiBefore = false;
    public boolean waitForGuiAfter = false;
    public String waitGuiName = "";
    public boolean listenDuringPreviousAction = false;

    public WaitForChatAction() {}

    public WaitForChatAction(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("pattern", patternJson == null || patternJson.isBlank() ? MacroExecutor.normalizeManualText(pattern) : pattern);
        tag.putString("patternJson", patternJson == null ? "" : patternJson);
        tag.putBoolean("useRegex", useRegex);
        tag.putString("matchMode", effectiveMatchMode().name());
        tag.putString("saveAs", saveAs == null ? "" : saveAs);
        tag.putInt("fuzzyPercent", fuzzyPercent);
        tag.putBoolean("serverMessageOnly", false);
        tag.putInt("timeoutMs", timeoutMs);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("waitForGuiBefore", waitForGuiBefore);
        tag.putBoolean("waitForGuiAfter", waitForGuiAfter);
        tag.putString("waitGuiName", waitGuiName);
        MacroWaitOptions.write(tag, this);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("patternJson")) patternJson = tag.getStringOr("patternJson", "");
        else patternJson = "";
        if (tag.contains("pattern")) {
            String loadedPattern = tag.getStringOr("pattern", "");
            pattern = patternJson == null || patternJson.isBlank() ? MacroExecutor.normalizeManualText(loadedPattern) : loadedPattern;
        }
        if (tag.contains("useRegex")) useRegex = tag.getBooleanOr("useRegex", false);
        matchMode = MacroStringList.enumValue(MacroCapturePattern.Mode.class,
            tag.getStringOr("matchMode", useRegex ? "REGEX" : "MATCH"), useRegex ? MacroCapturePattern.Mode.REGEX : MacroCapturePattern.Mode.MATCH);
        useRegex = matchMode == MacroCapturePattern.Mode.REGEX;
        saveAs = tag.getStringOr("saveAs", "");
        if (tag.contains("fuzzyPercent")) fuzzyPercent = clampFuzzyPercent(tag.getIntOr("fuzzyPercent", 100));
        else fuzzyPercent = 100;
        serverMessageOnly = false;
        if (tag.contains("timeoutMs")) timeoutMs = tag.getIntOr("timeoutMs", 0);
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        waitForGuiBefore = WaitsForGui.loadBefore(tag, false);
        waitForGuiAfter = WaitsForGui.loadAfter(tag, false);
        if (tag.contains("waitGuiName")) waitGuiName = tag.getStringOr("waitGuiName", "");
        MacroWaitOptions.read(tag, this);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.WAIT_CHAT;
    }

    @Override
    public String getDisplayName() {
        String desc = pattern.isEmpty() ? "Any Chat" : pattern;
        if (effectiveMatchMode() == MacroCapturePattern.Mode.MATCH) desc += " (~" + clampFuzzyPercent(fuzzyPercent) + "%)";
        else desc += " [" + effectiveMatchMode().name().toLowerCase(java.util.Locale.ROOT) + "]";
        if (timeoutMs > 0) desc += " (" + timeoutMs + "ms)";
        return "Wait Chat: " + desc + WaitsForGui.timingLabel(this);
    }

    @Override
    public String getIcon() {
        return "WCH";
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }

    @Override public boolean isWaitForGuiBefore() { return waitForGuiBefore; }
    @Override public void setWaitForGuiBefore(boolean v) { this.waitForGuiBefore = v; }
    @Override public boolean isWaitForGuiAfter() { return waitForGuiAfter; }
    @Override public void setWaitForGuiAfter(boolean v) { this.waitForGuiAfter = v; }
    @Override public String getWaitGuiName() { return waitGuiName; }
    @Override public void setWaitGuiName(String name) { this.waitGuiName = name; }

    public static int clampFuzzyPercent(int percent) {
        int clamped = Math.max(40, Math.min(100, percent));
        int snapped = Math.round(clamped / 10.0f) * 10;
        return Math.max(40, Math.min(100, snapped));
    }

    public MacroCapturePattern.Mode effectiveMatchMode() {
        if (matchMode == null) return useRegex ? MacroCapturePattern.Mode.REGEX : MacroCapturePattern.Mode.MATCH;
        if (useRegex && matchMode == MacroCapturePattern.Mode.MATCH) return MacroCapturePattern.Mode.REGEX;
        return matchMode;
    }
}
