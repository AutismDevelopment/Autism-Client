package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitForMacroStepAction implements MacroAction {
    public String macroName = "";
    public int step = 1;
    public WaitMode mode = WaitMode.COMPLETED_STEP;
    public int timeoutMs = 0;
    public boolean listenDuringPreviousAction = false;
    private boolean enabled = true;

    public enum WaitMode {
        STARTED_STEP,
        COMPLETED_STEP,
        FINISHED
    }

    @Override
    public void execute(Minecraft mc) {

    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("macroName", macroName == null ? "" : macroName);
        tag.putInt("step", Math.max(1, step));
        tag.putString("mode", mode.name());
        tag.putInt("timeoutMs", Math.max(0, timeoutMs));
        tag.putBoolean("enabled", enabled);
        MacroWaitOptions.write(tag, this);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        macroName = tag.getStringOr("macroName", "");
        step = Math.max(1, tag.getIntOr("step", 1));
        try {
            mode = WaitMode.valueOf(tag.getStringOr("mode", WaitMode.COMPLETED_STEP.name()));
        } catch (Exception ignored) {
            mode = WaitMode.COMPLETED_STEP;
        }
        timeoutMs = Math.max(0, tag.getIntOr("timeoutMs", 0));
        enabled = tag.getBooleanOr("enabled", true);
        MacroWaitOptions.read(tag, this);
    }

    @Override public MacroActionType getType() { return MacroActionType.WAIT_MACRO_STEP; }
    @Override public String getDisplayName() {
        String name = macroName == null || macroName.isBlank() ? "macro" : macroName;
        return mode == WaitMode.FINISHED ? "Wait: " + name + " finished" : "Wait: " + name + " step " + step;
    }
    @Override public String getIcon() { return "WMS"; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
