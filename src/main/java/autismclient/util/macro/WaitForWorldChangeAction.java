package autismclient.util.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class WaitForWorldChangeAction implements MacroAction, MacroCaptureOutput {
    public String targetDimension = "";
    public boolean listenDuringPreviousAction = false;
    public String saveAs = "";
    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("targetDimension", targetDimension == null ? "" : targetDimension);
        tag.putBoolean("enabled", enabled);
        MacroWaitOptions.write(tag, this);
        tag.putString("saveAs", saveAs);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        targetDimension = tag.getStringOr("targetDimension", "");
        if (tag.contains("enabled")) enabled = tag.getBooleanOr("enabled", true);
        MacroWaitOptions.read(tag, this);
        saveAs = tag.getStringOr("saveAs", "");
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.WAIT_WORLD_CHANGE;
    }

    @Override
    public String getDisplayName() {
        return targetDimension == null || targetDimension.isBlank()
            ? "Wait World Change"
            : "Wait World: " + targetDimension;
    }

    @Override
    public String getIcon() {
        return "WRL";
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) { enabled = e; }
    @Override public String getSaveAs() { return saveAs; }
    @Override public void setSaveAs(String name) { saveAs = name == null ? "" : name; }
}
